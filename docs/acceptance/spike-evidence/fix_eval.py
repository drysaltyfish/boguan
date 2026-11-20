
import json, sqlite3, os, urllib.request, re

SPIKE = r"D:\Projects\MultiProject\boguan\.spike"
db = sqlite3.connect(SPIKE + r"\device.db")
cur = db.cursor()

def segments(sid):
    return cur.execute("select start_ms,end_ms,text,offline_text from segment where session_id=? order by start_ms", (sid,)).fetchall()

# 两场真实课堂录音：一场有实时+二遍，一场只有实时
datasets = {
    "d140024b": segments("d140024b-9b48-47ce-8b13-6e49dc3627f3"),
    "s": segments("127eb54e-4bd1-48b0-8c16-3ba6535fbe75"),
}

key = re.search(r'name="llm_api_key">([^<]+)<', open(SPIKE + r"\prefs-test.xml", encoding="utf-8").read()).group(1)

GLOSSARY = "行列式、余子式、代数余子式、矩阵、上三角、下三角、初等变换、第一行展开、n 阶、归纳法、特征值、公式"

def call(seg_texts):
    lines = []
    for i, s in enumerate(seg_texts):
        rt = s[2] or ""
        off = s[3] or ""
        lines.append(f"[{i}] 实时：{rt}\n     离线：{off}")
    system = (
        "你是中文大学课堂录音的转写校正器。用户会给出同一段的实时 ASR 稿与离线 ASR 稿（可能为空），"
        "以及本课术语表。任务：给出**校正后的唯一文本**。规则："
        "1) 只修正识别错误（同音字、术语、数字、英文变量名），不改变原意、不增删内容、不总结；"
        "2) 术语一律按术语表写法；数学符号与变量名（如 A_ij、a_i、n）保持清晰；"
        "3) 两份稿都错但上下文能确定时按上下文修正，不能确定就保留更通顺的一份；"
        "4) 输出严格 JSON，不要解释。"
    )
    user = (
        "【本课术语表】" + GLOSSARY + "\n\n【待校正段落】（实时与离线是同一段音频的两份识别结果）\n"
        + "\n".join(lines)
        + '\n\n输出 JSON：{"segments":[{"i":0,"text":"校正后的文本"}]}'
    )
    body = json.dumps({
        "model": "deepseek-chat",
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "response_format": {"type": "json_object"},
        "temperature": 0.1,
    }).encode()
    req = urllib.request.Request(
        "https://api.deepseek.com/v1/chat/completions", data=body,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + key})
    with urllib.request.urlopen(req, timeout=120) as r:
        j = json.loads(r.read())
    usage = j.get("usage", {})
    parsed = json.loads(j["choices"][0]["message"]["content"])
    return parsed["segments"], usage

out = {}
for name, segs in datasets.items():
    fixed, usage = call(segs)
    out[name] = {"usage": usage, "pairs": []}
    for s, f in zip(segs, fixed):
        out[name]["pairs"].append({"rt": s[2], "off": s[3], "fixed": f["text"]})
    print(name, "usage", usage)

json.dump(out, open(SPIKE + r"\fix-eval.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("ok")
