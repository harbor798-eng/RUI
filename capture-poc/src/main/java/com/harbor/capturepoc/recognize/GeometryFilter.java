package com.harbor.capturepoc.recognize;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GeometryFilter {
    public double listRatio = 0.25;
    public double headerRatio = 0.10; // 聊天区顶部标题栏（实测 cy<=72）
    public double inputRatio = 0.16;
    public boolean debug = false;

    private static final Pattern TIME = Pattern.compile("^\\d{1,2}[:：]\\d{2}$");
    private static final Pattern WEEK = Pattern.compile("^(星期一|星期二|星期三|星期四|星期五|星期六|星期日|周天|周[一二三四五六日])$");
    private static final Pattern WEEK_TIME = Pattern.compile("^(星期一|星期二|星期三|星期四|星期五|星期六|星期日|周[一二三四五六日])\\s*\\d{1,2}[:：]\\d{2}$");
    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}.*");

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
            else if (it.x1 < listW - 10) { reason = "LEFT_LIST"; r.rejected++; }
            else if (it.y1 >= inputTop) { reason = "INPUT_BAR"; r.rejected++; }
            else if (TIME.matcher(t).matches()) { reason = "TIMESTAMP"; r.rejected++; }
            else if (WEEK.matcher(t).matches()) { reason = "WEEK"; r.rejected++; }
            else if (WEEK_TIME.matcher(t).matches()) { reason = "WEEK_TIME"; r.rejected++; }
            else if (DATE_TIME.matcher(t).matches()) { reason = "DATE_TIME"; r.rejected++; }
            else if (t.contains("按住鼠标语音")) { reason = "INPUT_PLACEHOLDER"; r.rejected++; }
            else if (t.length()<=2 && !t.matches("^[\\u4e00-\\u9fa5]+$")) { reason = "SHORT_NOISE"; r.rejected++; }
            else { reason = "ACCEPT_CHAT"; r.accepted.add(it); }
            if (debug) {
                String disp = t.length()>20 ? t.substring(0,20)+"…" : t;
                System.out.println("[GeometryFilter] " + reason + " text='" + disp + "' x=" + it.x1 + "," + it.y1 + " cy=" + it.cy());
            }
        }
        return r;
    }
}
