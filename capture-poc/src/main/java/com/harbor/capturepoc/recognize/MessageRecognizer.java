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
        // 后处理：把被其他 Candidate 矩形包含的小 Candidate 合并进去。
        // 这解决 OCR 把一个气泡里的小字（如"使用"）误拆成独立消息的问题。
        mergeContained(out);
        return out;
    }

    /** 如果 candidate A 完全位于 candidate B 的矩形内（或 80% 重叠），合并到 B。 */
    private void mergeContained(List<Candidate> out) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < out.size(); i++) {
                for (int j = 0; j < out.size(); j++) {
                    if (i == j) continue;
                    Candidate a = out.get(i); // 可能被包含
                    Candidate b = out.get(j); // 容器
                    // a 完全在 b 内部，或重叠面积 > 70% of a
                    int ax1=Math.max(a.x1,b.x1), ay1=Math.max(a.y1,b.y1);
                    int ax2=Math.min(a.x2,b.x2), ay2=Math.min(a.y2,b.y2);
                    if (ax2 <= ax1 || ay2 <= ay1) continue;
                    int overlap = (ax2-ax1)*(ay2-ay1);
                    int aArea = (a.x2-a.x1)*(a.y2-a.y1);
                    if (aArea > 0 && overlap * 100 / aArea >= 70) {
                        // 合并 a 到 b
                        b.text = b.text + "\n" + a.text;
                        b.x1 = Math.min(b.x1, a.x1);
                        b.y1 = Math.min(b.y1, a.y1);
                        b.x2 = Math.max(b.x2, a.x2);
                        b.y2 = Math.max(b.y2, a.y2);
                        out.remove(i);
                        changed = true;
                        break;
                    }
                }
                if (changed) break;
            }
        }
    }

    private Sender senderOf(OcrItem it, int frameW) {
        double listW = frameW * 0.25;
        double chatW = frameW - listW;
        double relRight = (it.x2 - listW) / chatW;
        double relLeft = (it.x1 - listW) / chatW;
        double relCenter = (it.cx() - listW) / chatW;
        // 绝对边距判定（更稳定）：
        // ME 气泡文本右缘应贴近 frameW 右侧（>70% frameW）；
        // OTHER 气泡文本左缘应贴近 frameW 左侧（<55% frameW）。
        double absRight = (double) it.x2 / frameW;
        double absLeft = (double) it.x1 / frameW;
        if (absRight > 0.70 || relRight > 0.85 || relCenter > 0.55) return Sender.ME;
        if (absLeft < 0.55 || relLeft < 0.15 || relCenter < 0.45) return Sender.OTHER;
        return Sender.UNKNOWN;
    }
}
