package com.harbor.capturepoc.recognize;

import java.util.List;

/** 会话标题识别：取顶部标题区最长/最靠左的文本，做粗容错。 */
public class ConversationRecognizer {
    private String current = "UNKNOWN";

    public String current() { return current; }

    public String update(List<OcrItem> titleCandidates) {
        if (titleCandidates.isEmpty()) return current;
        // 选最长的一条作为标题
        OcrItem best = null;
        for (OcrItem it : titleCandidates) if (best==null || it.text.length()>best.text.length()) best = it;
        if (best == null) return current;
        String name = normalize(best.text);
        if (!name.equals(current)) {
            System.out.println("[ConversationRecognizer] '"+current+"' -> '"+name+"'");
            current = name;
        }
        return current;
    }

    // 简单容错：去空格、把常见 OCR 误识首字符归一（仅用于展示，不强行匹配通讯录）
    private String normalize(String s) {
        return s.trim().replaceAll("\\s+","");
    }
}
