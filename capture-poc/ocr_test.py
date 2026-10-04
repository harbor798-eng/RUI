import sys, time, json
from rapidocr_onnxruntime import RapidOCR

engine = RapidOCR()
img = r"C:\Users\Harbor\Desktop\demo\demo02\capture-poc\capture-poc\out\frame_0001.png"
t0 = time.time()
result, _ = engine(img)
print(f"OCR ms={(time.time()-t0)*1000:.0f}")
if not result:
    print("NO LINES"); sys.exit()
for box, text, score in result:
    xs = [p[0] for p in box]; ys = [p[1] for p in box]
    print(f"box=({min(xs):.0f},{min(ys):.0f},{max(xs):.0f},{max(ys):.0f}) score={float(score):.2f} text={text}")
