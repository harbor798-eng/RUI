package com.harbor.capturepoc.recognize;

import com.harbor.capturepoc.CaptureDiag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GeometryFilter {
    public double listRatio = 0.25;
    public double headerRatio = 0.10; // 聊天区顶部标题栏（实测 cy<=72）
    public double inputRatio = 0.16;
    public boolean debug = false;

    private static final Pattern TIME = Pattern.compile("^\\d{1,2}[·．.:：]\\d{2}$");
    // 日期分隔条：WeChat 在聊天区居中显示的时间戳。
    // OCR 可能截断为 "星期-" / "周-" 等，因此允许结尾残缺。
    private static final Pattern WEEK = Pattern.compile("^(星期一|星期二|星期三|星期四|星期五|星期六|星期日|周天|周[一二三四五六日])$");
    private static final Pattern WEEK_TRUNC = Pattern.compile("^(星期|周)[一二三四五六日天]?[-—.\\s]*$");
    private static final Pattern WEEK_TIME = Pattern.compile("^(星期一|星期二|星期三|星期四|星期五|星期六|星期日|周[一二三四五六日])\\s*\\d{1,2}[:：]\\d{2}$");
    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}.*");
    // WeChat 中部 "N 条新消息" 提示胶囊
    private static final Pattern NEW_MESSAGE_PILL = Pattern.compile("^\\d+\\s*条新消息$");

    public static class Result {
        public List<OcrItem> accepted = new ArrayList<>();
        public List<OcrItem> titleCandidates = new ArrayList<>();
        public int rejected;
    }

    public Result filter(List<OcrItem> items, int frameW, int frameH) {
        Result r = new Result();
        int listW = (int)(frameW * listRatio);
        int headerH = (int)(frameH * headerRatio);
        int inputTop = (int)(frameH * (1 - inputRatio));
        if (debug) System.out.println("[GeometryFilter] frame=" + frameW + "x" + frameH + " listW=" + listW + " headerH=" + headerH + " inputTop=" + inputTop);
        for (OcrItem it : items) {
            String t = it.text.trim().replaceAll("^\"|\"$","").trim();
            String reason;
            if (it.cy() <= headerH) { reason = "HEADER_REGION"; r.titleCandidates.add(it); }
            else if (it.x1 < listW + (frameW - listW) * 0.12) { reason = "LEFT_LIST"; r.rejected++; }
            else if (it.y1 >= inputTop) { reason = "INPUT_BAR"; r.rejected++; }
            else if (TIME.matcher(t).matches()) { reason = "TIMESTAMP"; r.rejected++; }
            else if (WEEK.matcher(t).matches() || WEEK_TRUNC.matcher(t).matches()) { reason = "WEEK"; r.rejected++; }
            else if (WEEK_TIME.matcher(t).matches()) { reason = "WEEK_TIME"; r.rejected++; }
            else if (DATE_TIME.matcher(t).matches()) { reason = "DATE_TIME"; r.rejected++; }
            else if (NEW_MESSAGE_PILL.matcher(t).matches()) { reason = "NEW_MESSAGE_PILL"; r.rejected++; }
            else if (t.contains("按住鼠标语音")) { reason = "INPUT_PLACEHOLDER"; r.rejected++; }
            else if (t.length()<=2 && !t.matches("^[\\u4e00-\\u9fa5]+$")) { reason = "SHORT_NOISE"; r.rejected++; }
            else {
                // 居中 UI 装饰：聊天气泡一定贴左(OTHER)或贴右(ME)对齐，
                // 其文本框 center 落在聊天区相对坐标 [0.45,0.55] 的死区里。
                // 居中窄条（时间戳 pill 等）在这里出现。短消息"你好"即使很窄，
                // 也会贴左/贴右对齐，不会落在死区，因此不会误伤。
                double chatW = frameW - listW;
                double relCx = chatW > 0 ? ((double)it.cx() - listW) / chatW : 0.5;
                int w = it.x2 - it.x1;
                if (relCx >= 0.45 && relCx <= 0.55 && w < 150) {
                    reason = "CENTERED_UI"; r.rejected++;
                } else {
                    // 右侧 UI 按钮：真正的 ME 气泡文本 x2 应贴近右缘(>0.80 frameW)。
                    // 右侧窄条若 x2 离右缘还远，多半是工具栏按钮（如"生成回复"）。
                    // 左侧聊天区没有独立 UI 按钮；联系人列表已由 LEFT_LIST 过滤。
                    double absRight = (double)it.x2 / frameW;
                    if (relCx > 0.55 && absRight < 0.80 && w < 120) {
                        reason = "RIGHT_SIDE_UI_BUTTON"; r.rejected++;
                    } else {
                        reason = "ACCEPT_CHAT"; r.accepted.add(it);
                    }
                }
            }
            if (!"ACCEPT_CHAT".equals(reason) && !"HEADER_REGION".equals(reason)) {
                String disp = t.length() > 30 ? t.substring(0,30) + "..." : t;
                CaptureDiag.log("[GF_REJECT] text=\"" + disp + "\" score=" + String.format("%.2f", it.score)
                        + " box=(" + it.x1 + "," + it.y1 + ")-(" + it.x2 + "," + it.y2 + ")"
                        + " cx=" + it.cx() + " cy=" + it.cy() + " reason=" + reason);
            }
            if (debug) {
                String disp = t.length()>20 ? t.substring(0,20)+"…" : t;
                System.out.println("[GeometryFilter] " + reason + " text='" + disp + "' x=" + it.x1 + "," + it.y1 + " cy=" + it.cy());
            }
        }
        return r;
    }
}
