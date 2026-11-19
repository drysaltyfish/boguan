#!/usr/bin/env python3
"""博观约取 v0.1 自动化验收报告（无需人工转写，ADR-0016）。

输入：从设备拉下来的 boguan.db（可含多场次）+ 可选音频 / 术语表。
输出：Markdown 报告，覆盖：
  1. 稳定性与覆盖：时长、音频大小、段落数、端点间隔分布、空段率、长静默
  2. 实时可用性：字符速率（字/分钟）、段落长度分布
  3. 校正有效性：实时稿 → 校正稿 的字级改动率、待确认段数
  4. 术语命中：术语表里的词在 实时稿 / 校正稿 中出现的次数（校正带来的召回变化）
  5. 报告可信度：报告要点能否在转写文本中定位、待确认条目是否有锚点
  6. （可选）LLM 抽检：抽取 N 段让 LLM 从「可读性/术语正确性」打分，1-5

用法：
  python tools/acceptance_report.py --db device.db --out acceptance.md
  python tools/acceptance_report.py --db device.db --session <id> --terms terms.txt
  python tools/acceptance_report.py --db device.db --llm-review 20   # 需环境变量 BOGUAN_LLM_KEY 等
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sqlite3
import statistics as st
import urllib.request

# ---------------- 基础工具 ----------------

def normalize(text: str) -> str:
    return "".join(ch.lower() for ch in text if ch.isalnum())

def levenshtein(a: str, b: str) -> int:
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    cur = [0] * (len(b) + 1)
    for i in range(1, len(a) + 1):
        cur[0] = i
        ca = a[i - 1]
        for j in range(1, len(b) + 1):
            cost = 0 if ca == b[j - 1] else 1
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        prev, cur = cur, prev
    return prev[len(b)]

def similarity(a: str, b: str) -> float:
    x, y = normalize(a), normalize(b)
    if not x and not y:
        return 1.0
    if not x or not y:
        return 0.0
    return 1.0 - levenshtein(x, y) / max(len(x), len(y))

def pct(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    k = max(0, min(len(values) - 1, int(round((len(values) - 1) * p))))
    return values[k]

def fmt_sec(ms: float) -> str:
    s = int(ms // 1000)
    return f"{s // 60:02d}:{s % 60:02d}"

# ---------------- 数据读取 ----------------

class SessionData:
    def __init__(self, row, segments, micros, reports):
        self.id, self.type, self.started, self.ended, self.state, self.audio_path = row
        self.segments = segments
        self.micros = micros
        self.reports = reports

    @property
    def duration_ms(self):
        end = self.ended
        if not end and self.segments:
            end = self.started + self.segments[-1]["end"]
        return (end or self.started) - self.started

    def text_of(self, field):
        return "\n".join(s[field] for s in self.segments if s[field])


def load(db_path: str, session_id: str | None) -> SessionData:
    db = sqlite3.connect(db_path)
    cur = db.cursor()
    if session_id:
        row = cur.execute(
            "select id,type,started_at_ms,ended_at_ms,state,audio_path from session where id=?",
            (session_id,),
        ).fetchone()
    else:
        row = cur.execute(
            "select id,type,started_at_ms,ended_at_ms,state,audio_path from session "
            "order by started_at_ms desc limit 1"
        ).fetchone()
    if not row:
        raise SystemExit("会话不存在")
    sid = row[0]
    segments = [
        dict(start=s[0], end=s[1], text=s[2] or "", offline=s[3] or "", corrected=s[4] or "", final=s[5])
        for s in cur.execute(
            "select start_ms,end_ms,text,offline_text,corrected_text,is_final from segment "
            "where session_id=? order by start_ms",
            (sid,),
        ).fetchall()
    ]
    try:
        micros = [
            dict(kind=m[0], start=m[1], end=m[2], degraded=m[3])
            for m in cur.execute(
                "select kind,start_ms,end_ms,degraded from micro_summary where session_id=? order by end_ms",
                (sid,),
            ).fetchall()
        ]
    except sqlite3.OperationalError:
        micros = []
    try:
        reports = [
            dict(generated=r[0], md=r[1])
            for r in cur.execute(
                "select generated_at_ms, rendered_md from report where session_id=? order by generated_at_ms desc",
                (sid,),
            ).fetchall()
        ]
    except sqlite3.OperationalError:
        reports = []
    db.close()
    return SessionData(row, segments, micros, reports)

# ---------------- 各项指标 ----------------

def coverage_stats(sess: SessionData) -> dict:
    segs = sess.segments
    nonempty = [s for s in segs if s["text"].strip()]
    gaps = []
    prev_end = 0
    for s in segs:
        gaps.append(s["start"] - prev_end)
        prev_end = s["end"]
    durations = [s["end"] - s["start"] for s in segs]
    audio_bytes = 0
    audio_path = os.environ.get("BOGUAN_AUDIO_PATH") or sess.audio_path
    try:
        if audio_path and os.path.exists(audio_path):
            audio_bytes = os.path.getsize(audio_path)
    except OSError:
        pass
    minutes = sess.duration_ms / 60000 if sess.duration_ms else 0
    return {
        "duration_ms": sess.duration_ms,
        "minutes": minutes,
        "audio_mb": audio_bytes / 1048576 if audio_bytes else 0,
        "segments": len(segs),
        "nonempty": len(nonempty),
        "empty_ratio": (1 - len(nonempty) / len(segs)) if segs else 0,
        "chars": sum(len(s["text"]) for s in nonempty),
        "chars_per_min": (sum(len(s["text"]) for s in nonempty) / minutes) if minutes else 0,
        "seg_len_p50": pct(durations, 0.5),
        "seg_len_p90": pct(durations, 0.9),
        "gap_p50": pct(gaps, 0.5),
        "gap_p90": pct(gaps, 0.9),
        "gap_max": max(gaps) if gaps else 0,
        "long_silence": sum(1 for g in gaps if g > 30000),
    }

def correction_stats(sess: SessionData) -> dict:
    pairs = [(s["text"], s["corrected"]) for s in sess.segments if s["corrected"].strip()]
    sims = [similarity(rt, cor) for rt, cor in pairs]
    changed = [1 for sim in sims if sim < 0.999]
    review = sum(1 for s in sess.segments if s["text"].strip() and s["corrected"].strip()
                 and similarity(s["text"], s["corrected"]) < 0.85)
    return {
        "corrected_segments": len(pairs),
        "changed": sum(changed),
        "change_ratio": (sum(changed) / len(pairs)) if pairs else 0,
        "similarity_mean": st.mean(sims) if sims else 1.0,
        "review_segments": review,
    }

def term_stats(sess: SessionData, terms: list[str]) -> list[dict]:
    rt = sess.text_of("text")
    cor = "\n".join(s["corrected"] or s["text"] for s in sess.segments)
    out = []
    for t in terms:
        out.append({
            "term": t,
            "realtime": rt.count(t),
            "corrected": cor.count(t),
        })
    return out

def report_stats(sess: SessionData) -> dict:
    if not sess.reports:
        return {"present": False}
    md = sess.reports[0]["md"]
    transcript = "\n".join(s["corrected"] or s["text"] for s in sess.segments)
    norm_transcript = normalize(transcript)
    bullets = [line.strip("- ").strip() for line in md.splitlines() if line.startswith("- ")]
    locatable = 0
    for b in bullets:
        key = normalize(b.split("—")[0])[:12]
        if key and key in norm_transcript:
            locatable += 1
    unverified = [line for line in md.splitlines() if line.startswith("- ") and ("[0" in line or "[" in line)]
    anchored = sum(1 for line in unverified if "[" in line and "]" in line)
    return {
        "present": True,
        "bullets": len(bullets),
        "locatable": locatable,
        "locatable_ratio": (locatable / len(bullets)) if bullets else 0,
        "anchored_items": anchored,
        "md_chars": len(md),
    }

# ---------------- 可选 LLM 抽检 ----------------

def llm_review(sess: SessionData, sample: int, api_key: str, base_url: str, model: str) -> dict | None:
    segs = [s for s in sess.segments if (s["corrected"] or s["text"]).strip()]
    if not segs:
        return None
    step = max(1, len(segs) // sample)
    picked = segs[::step][:sample]
    numbered = "\n".join(f"[{i}] {s['corrected'] or s['text']}" for i, s in enumerate(picked))
    prompt = (
        "下面是一节中文课堂录音的转写片段。请只输出严格 JSON："
        '{"items":[{"i":编号,"score":1-5,"wrong":["可能听错的词"]}]}。'
        "score 含义：5=通顺且术语/数字正确，4=基本可读有个别错，3=能看懂但有明显错，2=多处错难以理解，1=不可用。"
        "\n\n" + numbered
    )
    body = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": "你是严格的中文转写质量评估器，只输出 JSON。"},
            {"role": "user", "content": prompt},
        ],
        "response_format": {"type": "json_object"},
        "temperature": 0.0,
    }).encode()
    req = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions", data=body,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + api_key},
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        payload = json.loads(resp.read())
    content = json.loads(payload["choices"][0]["message"]["content"])
    scores = [it["score"] for it in content.get("items", []) if isinstance(it.get("score"), (int, float))]
    wrong = [w for it in content.get("items", []) for w in it.get("wrong", [])]
    return {
        "sampled": len(scores),
        "score_mean": st.mean(scores) if scores else 0,
        "score_min": min(scores) if scores else 0,
        "wrong_terms": wrong[:20],
        "usage": payload.get("usage", {}),
    }

# ---------------- 报告渲染 ----------------

def render(sess: SessionData, cov: dict, cor: dict, terms: list[dict], rep: dict, review: dict | None) -> str:
    lines = []
    lines.append(f"# 验收报告 · {sess.type} · {sess.id}")
    lines.append("")
    lines.append("> 自动生成（无人工转写），指标口径见 ADR-0016。时间均为时长比值/毫秒换算。")
    lines.append("")
    lines.append("## 1. 稳定性与覆盖")
    lines.append("")
    lines.append(f"- 时长：**{fmt_sec(cov['duration_ms'])}**（{cov['minutes']:.1f} 分钟），音频 {cov['audio_mb']:.1f} MB")
    lines.append(f"- 段落：{cov['segments']} 段，非空 {cov['nonempty']}，空段率 {cov['empty_ratio']*100:.1f}%")
    lines.append(f"- 端点间隔：p50 {cov['gap_p50']/1000:.1f}s / p90 {cov['gap_p90']/1000:.1f}s / max {cov['gap_max']/1000:.1f}s，>30s 静默 {cov['long_silence']} 次")
    lines.append(f"- 段落时长：p50 {cov['seg_len_p50']/1000:.1f}s / p90 {cov['seg_len_p90']/1000:.1f}s")
    lines.append("")
    lines.append("## 2. 实时可用性")
    lines.append("")
    lines.append(f"- 有效字符数：{cov['chars']}，速率 **{cov['chars_per_min']:.0f} 字/分钟**（中文讲课正常约 150–250）")
    lines.append("")
    lines.append("## 3. 校正有效性")
    lines.append("")
    lines.append(f"- 有校正稿的段落：{cor['corrected_segments']}/{cov['segments']}")
    lines.append(f"- 校正改动率：**{cor['change_ratio']*100:.1f}%**，实时↔校正平均相似度 {cor['similarity_mean']:.3f}")
    lines.append(f"- 待确认（差异较大）：{cor['review_segments']} 段")
    lines.append("")
    if terms:
        lines.append("## 4. 术语命中")
        lines.append("")
        lines.append("| 术语 | 实时稿 | 校正稿 |")
        lines.append("|---|---|---|")
        for t in terms:
            lines.append(f"| {t['term']} | {t['realtime']} | {t['corrected']} |")
        lines.append("")
    lines.append("## 5. 报告可信度")
    lines.append("")
    if rep.get("present"):
        lines.append(f"- 报告要点：{rep['bullets']} 条，可在转写中定位 **{rep['locatable']}/{rep['bullets']}**（{rep['locatable_ratio']*100:.0f}%）")
        lines.append(f"- 待确认条目带锚点：{rep['anchored_items']} 条")
        lines.append(f"- 报告长度：{rep['md_chars']} 字符")
    else:
        lines.append("- 本场还没有生成报告（课后点「生成报告」再跑一次本脚本）")
    lines.append("")
    if review:
        lines.append("## 6. LLM 抽检（可读性 1–5）")
        lines.append("")
        lines.append(f"- 抽样 {review['sampled']} 段，平均分 **{review['score_mean']:.2f}**，最低 {review['score_min']}")
        if review["wrong_terms"]:
            lines.append(f"- 被标出的可疑片段（前 20）：{'、'.join(review['wrong_terms'])}")
        u = review.get("usage", {})
        if u:
            lines.append(f"- 抽检消耗：{u.get('prompt_tokens', 0)}→{u.get('completion_tokens', 0)} tokens")
        lines.append("")
    return "\n".join(lines)

# ---------------- main ----------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True)
    ap.add_argument("--session", default=None)
    ap.add_argument("--terms", default=None, help="术语表文件：一行一个词")
    ap.add_argument("--out", default=None, help="输出 Markdown 路径")
    ap.add_argument("--llm-review", type=int, default=0, help="抽检段数（需 BOGUAN_LLM_KEY）")
    ap.add_argument("--audio", default=None, help="本场音频文件路径（用于统计大小）")
    args = ap.parse_args()
    if args.audio:
        os.environ["BOGUAN_AUDIO_PATH"] = args.audio

    sess = load(args.db, args.session)
    cov = coverage_stats(sess)
    cor = correction_stats(sess)
    terms = []
    if args.terms and os.path.exists(args.terms):
        with open(args.terms, encoding="utf-8") as f:
            terms = [line.strip() for line in f if line.strip()]
    term_rows = term_stats(sess, terms) if terms else []
    rep = report_stats(sess)
    review = None
    key = os.environ.get("BOGUAN_LLM_KEY", "")
    if args.llm_review and key:
        review = llm_review(
            sess, args.llm_review, key,
            os.environ.get("BOGUAN_LLM_URL", "https://api.deepseek.com/v1"),
            os.environ.get("BOGUAN_LLM_MODEL", "deepseek-chat"),
        )
    md = render(sess, cov, cor, term_rows, rep, review)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(md)
        print(f"written {args.out}")
    else:
        print(md)

if __name__ == "__main__":
    main()
