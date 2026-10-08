import sys, json, time, base64, io
import numpy as np
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

# Force UTF-8 stdout so Chinese text is not mangled by Windows cp936.
try:
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
except Exception:
    pass

sys.stderr.write("[Worker] loading model...\n"); sys.stderr.flush()
t0 = time.time()
engine = RapidOCR()
sys.stderr.write(f"[Worker] model ready in {(time.time()-t0)*1000:.0f}ms\n"); sys.stderr.flush()

# 握手
print(json.dumps({"type": "ready", "success": True}), flush=True)

for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    try:
        req = json.loads(line)
        if req.get("type") != "ocr":
            continue
        rid = req.get("requestId")
        t1 = time.time()
        png = base64.b64decode(req["image"])
        img = np.array(Image.open(io.BytesIO(png)).convert("RGB"))
        result, _ = engine(img)
        items = []
        if result:
            for box, text, score in result:
                xs = [p[0] for p in box]; ys = [p[1] for p in box]
                items.append({"text": text,
                              "box": [round(min(xs)), round(min(ys)), round(max(xs)), round(max(ys))],
                              "score": float(score)})
        print(json.dumps({"type": "ocr_result", "requestId": rid, "success": True,
                          "items": items, "elapsedMs": round((time.time()-t1)*1000, 1)}), flush=True)
    except Exception as e:
        print(json.dumps({"type": "error", "requestId": req.get("requestId") if 'req' in dir() else None,
                          "success": False, "error": str(e)}), flush=True)
