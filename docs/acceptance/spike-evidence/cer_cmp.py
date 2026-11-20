
import wave, numpy as np, sherpa_onnx, sqlite3, json, unicodedata

SPIKE = r"D:\Projects\MultiProject\boguan\.spike"
M = SPIKE + r"\models"
WAV = SPIKE + r"\culture.wav"
SID = "6ed211f2-3b13-4de5-a080-4d7f2e1970f7"

db = sqlite3.connect(SPIKE + r"\device.db")
rows = db.execute("select start_ms,end_ms,text,corrected_text from segment where session_id=? order by start_ms", (SID,)).fetchall()

w = wave.open(WAV, "rb")
sr = w.getframerate()
x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0

def norm(s):
    return "".join(ch for ch in s if ch.isalnum())

def cer(ref, hyp):
    r, h = norm(ref), norm(hyp)
    if not r: return 0.0
    prev = list(range(len(h) + 1)); cur = [0]*(len(h)+1)
    for i in range(1, len(r)+1):
        cur[0] = i
        for j in range(1, len(h)+1):
            cost = 0 if r[i-1]==h[j-1] else 1
            cur[j] = min(prev[j]+1, cur[j-1]+1, prev[j-1]+cost)
        prev, cur = cur, prev
    return prev[len(h)] / len(r)

def mk(base, **kw):
    return sherpa_onnx.OnlineRecognizer.from_transducer(
        tokens=base + r"\tokens.txt", encoder=base + r"\encoder.int8.onnx",
        decoder=base + r"\decoder.onnx", joiner=base + r"\joiner.int8.onnx",
        num_threads=2, sample_rate=16000, feature_dim=80,
        decoding_method="greedy_search", debug=False, model_type="", **kw)

models = {
  "x-asr-480ms": mk(M + r"\sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05"),
  "x-asr-1920ms": mk(M + r"\sherpa-onnx-x-asr-1920ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05"),
}
para = sherpa_onnx.OfflineRecognizer.from_paraformer(
    paraformer=M + r"\paraformer-zh-int8\model.int8.onnx", tokens=M + r"\paraformer-zh-int8\tokens.txt",
    num_threads=2, sample_rate=16000, feature_dim=80, decoding_method="greedy_search", debug=False)

def online_text(rec, a, b):
    s = rec.create_stream()
    s.accept_waveform(sr, x[int(a*sr/1000):int(b*sr/1000)])
    s.accept_waveform(sr, np.zeros(8000, dtype=np.float32))
    s.input_finished()
    while rec.is_ready(s):
        rec.decode_stream(s)
    r = rec.get_result(s)
    return r if isinstance(r, str) else r.text

def offline_text(rec, a, b):
    s = rec.create_stream()
    s.accept_waveform(sr, x[int(a*sr/1000):int(b*sr/1000)])
    rec.decode_stream(s)
    r = s.result
    return r if isinstance(r, str) else r.text

results = {}
for name, rec in models.items():
    errs = []
    for (a, b, rt, cor) in rows:
        hyp = online_text(rec, a, b)
        errs.append(cer(cor, hyp))
    results[name] = round(sum(errs)/len(errs), 4)
    print(name, "mean CER vs corrected:", results[name])

errs = []
for (a, b, rt, cor) in rows:
    hyp = offline_text(para, a, b)
    errs.append(cer(cor, hyp))
results["paraformer-offline"] = round(sum(errs)/len(errs), 4)
print("paraformer-offline mean CER:", results["paraformer-offline"])

errs = [cer(cor, rt) for (a,b,rt,cor) in rows]
results["realtime-live(480, as recorded)"] = round(sum(errs)/len(errs), 4)
print("live 480 CER:", results["realtime-live(480, as recorded)"])

json.dump(results, open(SPIKE + r"\cer-compare.json", "w"), indent=1)
