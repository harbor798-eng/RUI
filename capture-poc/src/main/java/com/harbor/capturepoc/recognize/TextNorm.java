package com.harbor.capturepoc.recognize;

/**
 * 统一的 OCR 文本归一化。
 * 用于 CaptureSession / ActiveDedup / HistoryAnchor / sourceMessageId，
 * 确保同一条逻辑消息无论 OCR 抖动（空格/下划线/尾部标点/大小写）都得到同一身份。
 *
 * 规则（最小化，避免过度归一化）：
 *   1. trim
 *   2. 转小写
 *   3. 删除所有空白
 *   4. 删除下划线 _
 *   5. 仅剥离尾部标点（. 。 ! ！ ? ？ , ， 、）
 * 不删除内部标点、不合并字符、不做拼音/模糊匹配。
 */
public final class TextNorm {
    private TextNorm() {}

    public static String norm(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        t = t.replaceAll("\\s+", "");
        t = t.replace("_", "");
        // 只剥离尾部标点，保留内部标点
        t = t.replaceAll("[.。!！?？,，、；;：:]+$", "");
        return t;
    }
}
