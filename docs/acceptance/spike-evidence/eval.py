
import sqlite3, wave, json, time
import numpy as np
import sherpa_onnx

SPIKE = r"D:\Projects\MultiProject\boguan\.spike"
db = sqlite3.connect(SPIKE + r"\device.db")
cur = db.cursor()

def load_wav(p):
    w = wave.open(p, "rb")
    sr = w.getframerate()
    x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
    return sr, x

def lev(a, b):
    if a == b: return 0
    if not a: return len(b)
    if not b: return len(a)
    prev = list(range(len(b) + 1)); cur_ = [0] * (len(b) + 1)
    for i in range(1, len(a) + 1):
        cur_[0] = i
        for j in range(1, len(b) + 1):
            cost = 0 if a[i-1] == b[j-1] else 1
            cur_[j] = min(prev[j] + 1, cur_[j-1] + 1, prev[j-1] + cost)
        prev, cur_ = cur_, prev
    return prev[len(b)]

def norm(s):
    return "".join(ch.lower() for ch in s if ch.isalnum())

def sim(a, b):
    x, y = norm(a), norm(b)
    if not x and not y: return 1.0
    if not x or not y: return 0.0
    return 1.0 - lev(x, y) / max(len(x), len(y))

sessions = ["e1bc5396-fd2a-43f1-98a0-7ba6281885d6", "0b207105-2093-4597-bd22-0b7bc2734793", "d140024b-9b48-47ce-8b13-6e49dc3627f3"]

para = sherpa_onnx.OfflineRecognizer.from_paraformer(
    paraformer=SPIKE + r"\models\paraformer-zh-int8\model.int8.onnx",
    tokens=SPIKE + r"\models\paraformer-zh-int8\tokens.txt",
    num_threads=2, sample_rate=16000, feature_dim=80, decoding_method="greedy_search", debug=False)

sv = sherpa_onnx.OfflineRecognizer.from_sense_voice(
    model=SPIKE + r"\models\sense-voice-int8-2025-09-09\model.int8.onnx",
    tokens=SPIKE + r"\models\sense-voice-int8-2025-09-09\tokens.txt",
    num_threads=2, use_itn=False, language="zh", debug=False)

def decode(rec, x, sr, a, b):
    s = rec.create_stream()
    s.accept_waveform(sr, x[int(a*sr/1000):int(b*sr/1000)])
    rec.decode_stream(s)
    return s.result.text.strip()

out = []
for sid in sessions:
    sr, x = load_wav(SPIKE + "\\" + sid[:8] + ".wav")
    segs = cur.execute("select start_ms,end_ms,text from segment where session_id=? order by start_ms", (sid,)).fetchall()
    for a, b, rt in segs:
        if b - a < 500: continue
        p = decode(para, x, sr, a, b)
        v = decode(sv, x, sr, a, b)
        out.append({"sid": sid[:8], "a": a, "b": b, "rt": rt,
                    "para": p, "sv": v,
                    "sim_para": round(sim(rt, p), 3), "sim_sv": round(sim(rt, v), 3)})

with open(SPIKE + r"\eval.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print("segments:", len(out))
print("mean sim para:", round(sum(o["sim_para"] for o in out)/len(out), 3))
print("mean sim sv:  ", round(sum(o["sim_sv"] for o in out)/len(out), 3))
