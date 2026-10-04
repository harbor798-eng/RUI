package com.harbor.capturepoc.recognize;

import java.util.ArrayList;
import java.util.List;

/** 合并多行 OCR line 为一条气泡消息，并判断 sender/type。 */
public class MessageRecognizer {
    public enum Sender { ME, OTHER, UNKNOWN }
    public enum Type { TEXT, IMAGE, EMOJI, UNKNOWN }

    public static class Candidate {
        public String text; public Sender sender; public Type type = Type.TEXT;
        public int x1,y1,x2,y2; public double confidence;
        public String toString(){ return "["+sender+"/"+type+"] "+text; }
    }

    public List<Candidate> recognize(List<OcrItem> accepted, int frameW, int frameH) {
        // 按 y 排序，贪心合并：同 sender、y 间隙 < 14px、x 区间重叠/相近
        List<OcrItem> sorted = new ArrayList<>(accepted);
        sorted.sort((a,b)->a.y1-b.y1);
        List<Candidate> out = new ArrayList<>();
        for (OcrItem it : sorted) {
            Sender s = senderOf(it, frameW);
            Candidate last = out.isEmpty()?null:out.get(out.size()-1);
            if (last != null && last.sender==s) {
                int gap = it.y1 - last.y2;
                boolean xOverlap = it.x1 < last.x2 + 30 && last.x1 < it.x2 + 30;
                if (gap >= -2 && gap < 16 && xOverlap) {
                    last.text = last.text + "\n" + it.text;
                    last.y2 = Math.max(last.y2, it.y2);
                    last.x1 = Math.min(last.x1, it.x1); last.x2 = Math.max(last.x2, it.x2);
                    continue;
                }
            }
            Candidate c = new Candidate();
            c.text = it.text; c.sender = s;
            c.x1=it.x1;c.y1=it.y1;c.x2=it.x2;c.y2=it.y2;
            c.confidence = s==Sender.UNKNOWN?0.4:0.9;
            out.add(c);
        }
        return out;
    }

    private Sender senderOf(OcrItem it, int frameW) {
        double listW = frameW * 0.25;
        double chatW = frameW - listW;
        double relCenter = (it.cx() - listW) / chatW;
        double relRight = (it.x2 - listW) / chatW;
        double relLeft = (it.x1 - listW) / chatW;
        // ME：右对齐。长气泡看右边缘/中心；短气泡（OCR 只框文字）看中心是否偏右。
        if (relRight > 0.85 || relCenter > 0.55) return Sender.ME;
        // OTHER：左对齐。
        if (relLeft < 0.15 || relCenter < 0.45) return Sender.OTHER;
        return Sender.UNKNOWN;
    }
}
