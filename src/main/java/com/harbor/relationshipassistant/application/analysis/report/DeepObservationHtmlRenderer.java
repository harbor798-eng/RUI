package com.harbor.relationshipassistant.application.analysis.report;

import com.harbor.relationshipassistant.domain.analysis.AnalysisObservation;
import com.harbor.relationshipassistant.domain.analysis.EmotionObservation;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.Fact;
import com.harbor.relationshipassistant.domain.analysis.Possibility;
import com.harbor.relationshipassistant.domain.analysis.Recommendation;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;
import com.harbor.relationshipassistant.domain.analysis.Unknown;
import com.harbor.relationshipassistant.domain.analysis.UserIssue;
import com.harbor.relationshipassistant.domain.analysis.deep.LongTermPattern;
import com.harbor.relationshipassistant.domain.analysis.deep.RelationshipChange;
import com.harbor.relationshipassistant.domain.analysis.deep.TheoryExplanation;
import com.harbor.relationshipassistant.domain.analysis.report.DeepObservationReport;
import com.harbor.relationshipassistant.domain.analysis.report.ReportSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DeepObservationReport → 自包含 HTML 字符串。
 * 纯表现层：不调用 LLM、不访问 DB、不修改任何数据。
 * 所有动态文本经过 HTML Escape。
 */
public class DeepObservationHtmlRenderer {

    public String render(DeepObservationReport report, List<EvidenceWindow> evidenceWindows) {
        if (report == null) throw new IllegalArgumentException("report must not be null");
        if (evidenceWindows == null) evidenceWindows = List.of();
        System.out.println("[DeepObservationHtmlRenderer] Rendering report. reportId=" + report.getMetadata().getReportId()
                + " sections=" + report.getSections().size());

        Map<String, EvidenceWindow> evMap = new LinkedHashMap<>();
        for (EvidenceWindow w : evidenceWindows) evMap.put(w.getEvidenceId(), w);

        Set<String> referenced = collectReferencedEvidence(report);
        List<EvidenceWindow> used = new ArrayList<>();
        for (String eid : referenced) {
            EvidenceWindow w = evMap.get(eid);
            if (w == null) throw new IllegalArgumentException("Referenced evidence not found: " + eid);
            used.add(w);
        }
        used.sort(Comparator.comparing(EvidenceWindow::getImportance).reversed()
                .thenComparing(EvidenceWindow::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        System.out.println("[DeepObservationHtmlRenderer] referencedEvidenceCount=" + used.size());

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n<title>JEVE · 深度观察报告</title>\n")
          .append("<style>").append(css()).append("</style>\n</head>\n<body>\n");

        renderHeader(sb, report);
        renderMeta(sb, report);
        for (ReportSection s : report.getSections()) {
            System.out.println("[DeepObservationHtmlRenderer] Rendering section=" + s);
            switch (s) {
                case FACTS -> renderFacts(sb, report, evMap);
                case TIMELINE -> renderTimeline(sb, report, evMap);
                case BEHAVIOR_PATTERNS -> renderObservations(sb, report, evMap);
                case EMOTION_CHANGES -> renderEmotions(sb, report, evMap);
                case RELATIONSHIP_CHANGES -> renderRelationshipChanges(sb, report, evMap);
                case LONG_TERM_PATTERNS -> renderLongTermPatterns(sb, report, evMap);
                case POSSIBILITIES -> renderPossibilities(sb, report, evMap);
                case THEORY_EXPLANATIONS -> renderTheory(sb, report, evMap);
                case USER_ISSUES -> renderUserIssues(sb, report, evMap);
                case RECOMMENDATIONS -> renderRecommendations(sb, report, evMap);
            }
        }
        renderEvidenceDetails(sb, used);

        sb.append("<footer class=\"report-footer\">JEVE · Deep Observation</footer>\n</body>\n</html>");
        System.out.println("[DeepObservationHtmlRenderer] HTML render completed. length=" + sb.length());
        return sb.toString();
    }

    private Set<String> collectReferencedEvidence(DeepObservationReport r) {
        Set<String> ids = new HashSet<>();
        for (Fact f : r.getFacts()) ids.addAll(f.getEvidenceIds());
        for (TimelineEvent t : r.getTimeline()) ids.addAll(t.getEvidenceIds());
        for (AnalysisObservation o : r.getBehaviorPatterns()) ids.addAll(o.getEvidenceIds());
        for (EmotionObservation e : r.getEmotionChanges()) ids.addAll(e.getEvidenceIds());
        for (RelationshipChange c : r.getRelationshipChanges()) ids.addAll(c.getEvidenceIds());
        for (LongTermPattern p : r.getLongTermPatterns()) ids.addAll(p.getEvidenceIds());
        for (Possibility p : r.getPossibilities()) { ids.addAll(p.getEvidenceIds()); ids.addAll(p.getCounterEvidenceIds()); }
        for (TheoryExplanation t : r.getTheoryExplanations()) ids.addAll(t.getEvidenceIds());
        for (UserIssue u : r.getUserIssues()) ids.addAll(u.getEvidenceIds());
        for (Recommendation rec : r.getRecommendations()) ids.addAll(rec.getReasonEvidenceIds());
        return ids;
    }

    private void renderHeader(StringBuilder sb, DeepObservationReport r) {
        sb.append("<header class=\"report-header\"><div class=\"brand\">JEVE</div>")
          .append("<h1>深度观察报告</h1></header>\n");
    }

    private void renderMeta(StringBuilder sb, DeepObservationReport r) {
        var m = r.getMetadata();
        sb.append("<section class=\"report-meta\"><ul>");
        sb.append("<li>生成时间：").append(esc(m.getGeneratedAt().toString())).append("</li>");
        sb.append("<li>分析范围：").append(esc(String.valueOf(m.getAnalysisRange()))).append("</li>");
        sb.append("<li>Skill：").append(esc(m.getSkill() == null ? "NONE" : String.valueOf(m.getSkill()))).append("</li>");
        sb.append("<li>实际数据：").append(esc(String.valueOf(m.getActualDataStart()))).append(" ~ ")
          .append(esc(String.valueOf(m.getActualDataEnd()))).append("</li>");
        sb.append("<li>消息数：").append(m.getMessageCount()).append("</li>");
        sb.append("</ul>");
        if (!m.isDataComplete()) {
            sb.append("<p class=\"data-warn\">当前分析基于已有聊天数据。数据范围可能不覆盖用户选择的完整时间范围，因此报告无法据此判断缺失时间段内是否发生过相关事件。</p>");
        } else {
            sb.append("<p class=\"data-ok\">当前分析覆盖所选数据范围。</p>");
        }
        sb.append("</section>\n");
    }

    private void renderFacts(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"facts\"><h2>事实</h2><p class=\"sub\">聊天记录、统计数据以及用户确认信息</p>");
        for (Fact f : r.getFacts()) {
            sb.append("<div class=\"item\"><span class=\"badge ").append(cssClass(f.getType().name())).append("\">")
              .append(esc(f.getType().name())).append("</span> ")
              .append(esc(f.getContent())).append(" ").append(refs(f.getEvidenceIds(), ev)).append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderTimeline(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"timeline\"><h2>重要时间线</h2>");
        for (TimelineEvent t : r.getTimeline()) {
            sb.append("<div class=\"tl-item\"><div class=\"tl-time\">").append(esc(String.valueOf(t.getTime()))).append("</div>")
              .append("<div class=\"tl-type\">").append(esc(t.getType())).append("</div>")
              .append("<div class=\"tl-desc\">").append(esc(t.getDescription())).append(" ").append(refs(t.getEvidenceIds(), ev)).append("</div></div>");
        }
        sb.append("</section>\n");
    }

    private void renderObservations(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"behavior\"><h2>行为与互动模式</h2>");
        for (AnalysisObservation o : r.getBehaviorPatterns()) {
            sb.append("<div class=\"item\">").append(esc(o.getContent()))
              .append(" <span class=\"muted\">分析置信度：").append(pct(o.getConfidence())).append("</span> ")
              .append(refs(o.getEvidenceIds(), ev)).append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderEmotions(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"emotions\"><h2>情绪变化</h2>");
        for (EmotionObservation e : r.getEmotionChanges()) {
            sb.append("<div class=\"item\"><span class=\"badge\">").append(esc(e.getPerson().name())).append("</span> ")
              .append(esc(e.getEmotion()))
              .append(" <span class=\"muted\">强度：").append(esc(String.valueOf(e.getIntensity()))).append("</span>");
            if (e.getEstimatedProbability() != null) {
                sb.append(" <span class=\"muted\">AI估计：").append(pct(e.getEstimatedProbability())).append("</span>");
            }
            sb.append(" ").append(refs(e.getEvidenceIds(), ev)).append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderRelationshipChanges(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"relchg\"><h2>关系变化</h2>");
        for (RelationshipChange c : r.getRelationshipChanges()) {
            sb.append("<div class=\"item\"><span class=\"badge\">").append(esc(c.getType())).append("</span> ")
              .append(esc(c.getDescription()))
              .append(" <span class=\"muted\">分析置信度：").append(pct(c.getConfidence())).append("</span> ")
              .append(refs(c.getEvidenceIds(), ev)).append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderLongTermPatterns(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"ltp\"><h2>长期行为模式</h2>");
        for (LongTermPattern p : r.getLongTermPatterns()) {
            sb.append("<div class=\"item\"><span class=\"badge\">").append(esc(p.getPatternType())).append("</span> ")
              .append(esc(p.getDescription()))
              .append(" <span class=\"muted\">分析置信度：").append(pct(p.getConfidence())).append("</span><br>")
              .append(refs(p.getEvidenceIds(), ev));
            if (!p.getPatternIds().isEmpty()) {
                sb.append(" <span class=\"muted\">Pattern: ").append(esc(String.join(", ", p.getPatternIds()))).append("</span>");
            }
            sb.append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderPossibilities(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"poss\"><h2>可能性与未知</h2>");
        sb.append("<p class=\"muted\">以下百分比为 AI 基于当前证据给出的估计，不代表科学概率或客观统计概率。</p>");
        for (Possibility p : r.getPossibilities()) {
            sb.append("<div class=\"item\">").append(esc(p.getContent()));
            if (p.getEstimatedProbability() != null) {
                sb.append(" <span class=\"muted\">AI估计：").append(pct(p.getEstimatedProbability())).append("</span>");
            }
            sb.append(" ").append(refs(p.getEvidenceIds(), ev));
            if (!p.getCounterEvidenceIds().isEmpty()) {
                sb.append(" <span class=\"muted\">反向证据：").append(refs(p.getCounterEvidenceIds(), ev)).append("</span>");
            }
            sb.append("</div>");
        }
        for (Unknown u : r.getUnknowns()) {
            sb.append("<div class=\"item unknown\">未知：").append(esc(u.getContent())).append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderTheory(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"theory\"><h2>理论解释</h2>");
        sb.append("<p class=\"muted\">以下内容用于提供解释框架，不代表对任何人的人格、心理状态或关系事实作出诊断。</p>");
        for (TheoryExplanation t : r.getTheoryExplanations()) {
            sb.append("<div class=\"item\"><strong>").append(esc(t.getTitle())).append("</strong><br>")
              .append(esc(t.getExplanation())).append("<br>")
              .append(refs(t.getEvidenceIds(), ev));
            if (!t.getKnowledgeIds().isEmpty()) {
                sb.append(" <span class=\"muted\">知识框架：").append(esc(String.join(", ", t.getKnowledgeIds()))).append("</span>");
            }
            sb.append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderUserIssues(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"issues\"><h2>你可以关注的地方</h2>");
        for (UserIssue u : r.getUserIssues()) {
            sb.append("<div class=\"item\">").append(esc(u.getContent()))
              .append(" <span class=\"muted\">严重程度：").append(esc(String.valueOf(u.getSeverity())))
              .append("，分析置信度：").append(pct(u.getConfidence())).append("</span> ")
              .append(refs(u.getEvidenceIds(), ev)).append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderRecommendations(StringBuilder sb, DeepObservationReport r, Map<String, EvidenceWindow> ev) {
        sb.append("<section class=\"card\" id=\"recs\"><h2>下一步建议</h2>");
        for (Recommendation rec : r.getRecommendations()) {
            sb.append("<div class=\"item\"><span class=\"badge\">").append(esc(rec.getPriority().name())).append("</span> ")
              .append(esc(rec.getContent())).append(" ").append(refs(rec.getReasonEvidenceIds(), ev)).append("</div>");
        }
        sb.append("</section>\n");
    }

    private void renderEvidenceDetails(StringBuilder sb, List<EvidenceWindow> used) {
        if (used.isEmpty()) return;
        System.out.println("[DeepObservationHtmlRenderer] Rendering evidence details");
        sb.append("<section class=\"card\" id=\"evidence\"><h2>证据详情</h2>");
        for (EvidenceWindow w : used) {
            sb.append("<div class=\"ev-card\" id=\"evidence-").append(esc(w.getEvidenceId())).append("\">")
              .append("<div class=\"ev-head\"><strong>").append(esc(w.getEvidenceId())).append("</strong>")
              .append(" <span class=\"muted\">类型：").append(esc(w.getTypes().toString()))
              .append("，等级：").append(esc(w.getLevel().name()))
              .append("，时间：").append(esc(String.valueOf(w.getStartTime()))).append(" ~ ").append(esc(String.valueOf(w.getEndTime()))).append("</span></div>")
              .append("<div class=\"ev-msgs\">消息：").append(esc(w.getMessageIds().toString())).append("</div>")
              .append("</div>");
        }
        sb.append("</section>\n");
    }

    private static String refs(List<String> ids, Map<String, EvidenceWindow> ev) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<span class=\"refs\">证据：");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            String id = ids.get(i);
            if (ev.containsKey(id)) sb.append("<a href=\"#evidence-").append(esc(id)).append("\">").append(esc(id)).append("</a>");
            else sb.append("<span class=\"muted\">").append(esc(id)).append("</span>");
        }
        return sb.append("</span>").toString();
    }

    private static String pct(double d) {
        long p = Math.round(d * 100);
        return p + "%";
    }

    private static String cssClass(String s) {
        return "tag-" + s.toLowerCase();
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String css() {
        return """
            :root{--prime:#6d5bd0;--prime-l:#ece8fb;--text:#2b2b33;--muted:#7a7a86;--border:#e3e1ec;--bg:#f6f5fa;--card:#ffffff;}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,"Segoe UI","Microsoft YaHei",sans-serif;line-height:1.7;}
            .report-header{background:var(--prime);color:#fff;padding:28px 24px;}
            .report-header .brand{font-weight:700;letter-spacing:2px;}
            .report-header h1{margin:6px 0 0;font-size:26px;}
            main,.report-container{max-width:1100px;margin:0 auto;padding:20px;}
            .report-meta{background:var(--card);border:1px solid var(--border);border-radius:10px;padding:14px 20px;margin:16px 0;}
            .report-meta ul{margin:0;padding-left:18px;}
            .data-warn{background:#fff7e6;border-left:4px solid #f0a020;padding:8px 12px;border-radius:6px;}
            .data-ok{color:#2e7d32;font-size:14px;}
            .card{background:var(--card);border:1px solid var(--border);border-radius:10px;padding:18px 22px;margin:16px 0;}
            .card h2{margin:0 0 4px;font-size:19px;color:var(--prime);}
            .sub{color:var(--muted);margin:0 0 10px;font-size:14px;}
            .item{padding:8px 0;border-bottom:1px dashed var(--border);}
            .item:last-child{border-bottom:none;}
            .badge{display:inline-block;background:var(--prime-l);color:var(--prime);padding:1px 8px;border-radius:10px;font-size:12px;margin-right:6px;}
            .muted{color:var(--muted);font-size:13px;}
            .refs{font-size:13px;}
            .refs a{color:var(--prime);text-decoration:none;}
            .tl-item{border-left:3px solid var(--prime-l);padding:6px 12px;margin:6px 0;}
            .tl-time{color:var(--muted);font-size:13px;}
            .ev-card{border:1px solid var(--border);border-radius:8px;padding:10px 14px;margin:8px 0;background:#fbfafd;}
            .ev-head{margin-bottom:4px;}
            .report-footer{text-align:center;color:var(--muted);padding:20px;font-size:13px;}
            """;
    }
}
