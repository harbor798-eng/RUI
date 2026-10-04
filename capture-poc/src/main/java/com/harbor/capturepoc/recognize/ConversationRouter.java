package com.harbor.capturepoc.recognize;

import java.sql.*;
import java.util.*;

/**
 * 微信会话标题 → Relationship.id 路由。
 *
 * 启动时从 relationship 表加载所有 ACTIVE 关系的 (id, name)。
 * 匹配规则（保守，宁可 BLOCKED 也不错配）：
 *   1. 精确相等（trim 后）
 *   2. 去空白后相等
 *   3. 包含关系：长度差 ≤ 2 且结果唯一
 * 不使用编辑距离 / 模糊相似度。
 */
public class ConversationRouter {

    private static class Rel {
        final long id;
        final String name;
        Rel(long id, String name) { this.id = id; this.name = name; }
    }

    private final List<Rel> relations = new ArrayList<>();

    public ConversationRouter(String url, String user, String pass) {
        load(url, user, pass);
    }

    private void load(String url, String user, String pass) {
        String sql = "SELECT id, name FROM relationship WHERE status='ACTIVE' ORDER BY id";
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                relations.add(new Rel(rs.getLong(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            System.out.println("[ConversationRouter] load failed: " + e.getMessage());
        }
        System.out.println("[ConversationRouter] loaded " + relations.size() + " active relationship(s)");
        for (Rel r : relations) {
            System.out.println("[ConversationRouter]   relId=" + r.id + " name=" + r.name);
        }
    }

    /** 路由：返回匹配到的 relationshipId；无匹配返回 empty。 */
    public Optional<Long> route(String title) {
        if (title == null || title.isBlank() || "UNKNOWN".equals(title)) {
            System.out.println("[ConversationRouter] BLOCKED title=" + title + " (empty/unknown)");
            return Optional.empty();
        }
        String t = title.trim();

        // 1. 精确匹配
        for (Rel r : relations) {
            if (r.name.equals(t)) {
                System.out.println("[ConversationRouter] matched relId=" + r.id + " name=" + r.name + " (exact)");
                return Optional.of(r.id);
            }
        }
        // 2. 去空白后精确匹配
        String tNs = t.replaceAll("\\s+", "");
        for (Rel r : relations) {
            String rNs = r.name.replaceAll("\\s+", "");
            if (rNs.equals(tNs)) {
                System.out.println("[ConversationRouter] matched relId=" + r.id + " name=" + r.name + " (no-space)");
                return Optional.of(r.id);
            }
        }
        // 3. 包含关系：长度差 ≤ 2 且唯一
        List<Rel> candidates = new ArrayList<>();
        for (Rel r : relations) {
            if (r.name.length() == 0 || t.length() == 0) continue;
            int diff = Math.abs(r.name.length() - t.length());
            if (diff > 2) continue;
            if (r.name.contains(t) || t.contains(r.name)) {
                candidates.add(r);
            }
        }
        if (candidates.size() == 1) {
            Rel r = candidates.get(0);
            System.out.println("[ConversationRouter] matched relId=" + r.id + " name=" + r.name + " (contains)");
            return Optional.of(r.id);
        }
        if (candidates.size() > 1) {
            System.out.println("[ConversationRouter] BLOCKED title=" + t + " ambiguous, " + candidates.size() + " candidates");
            return Optional.empty();
        }
        System.out.println("[ConversationRouter] BLOCKED title=" + t + " no match");
        return Optional.empty();
    }
}
