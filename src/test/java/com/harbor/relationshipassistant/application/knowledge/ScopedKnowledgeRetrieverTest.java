package com.harbor.relationshipassistant.application.knowledge;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScopedKnowledgeRetrieverTest {

    private List<KnowledgeItem> catalog() {
        Path dir = Paths.get("skills", "goutoujunshi", "knowledge").toAbsolutePath();
        return GoutoujunshiKnowledgeSeeder.load(dir);
    }

    private List<KnowledgeItem> fakeCatalog() {
        return List.of(
            new KnowledgeItem("a", "A 依恋 焦虑 回避", "依恋 焦虑 回避 理论", "REL", "gtj", "/a"),
            new KnowledgeItem("b", "B 依恋 沟通", "依恋 沟通 冲突 修复", "REL", "gtj", "/b"),
            new KnowledgeItem("c", "C 冲突 修复 沟通", "冲突 修复 沟通 关系", "REL", "gtj", "/c"),
            new KnowledgeItem("d", "D 分手 复合", "分手 复合 挽回", "REL", "gtj", "/d"),
            new KnowledgeItem("e", "E 沟通 表达", "沟通 表达 情绪", "REL", "gtj", "/e"),
            new KnowledgeItem("f", "F 无关 美食", "拉面 咖喱 天气", "REL", "gtj", "/f")
        );
    }

    @Test
    void scope_restricts_to_selected_docs() {
        var cat = catalog();
        var r = new ScopedKnowledgeRetriever(cat);
        var ids = Set.of("03-依恋理论与情绪调节", "02-亲密关系心理学总论");
        var res = r.retrieve("依恋 焦虑 回避", ids, 5, 0.5);
        assertEquals(2, res.candidateCount());
        for (var d : res.results()) assertTrue(ids.contains(d.documentId()));
    }

    @Test
    void empty_scope_returns_empty() {
        var r = new ScopedKnowledgeRetriever(catalog());
        var res = r.retrieve("依恋", Set.of(), 5, 0.5);
        assertTrue(res.results().isEmpty());
    }

    @Test
    void irrelevant_query_returns_empty() {
        var cat = catalog();
        var r = new ScopedKnowledgeRetriever(cat);
        var ids = Set.of("03-依恋理论与情绪调节", "02-亲密关系心理学总论");
        var res = r.retrieve("今天天气真好 吃拉面", ids, 5, 1.0);
        // Should be empty or near-empty with threshold 1.0
        assertTrue(res.results().size() <= 1);
    }

    @Test
    void deterministic() {
        var r = new ScopedKnowledgeRetriever(catalog());
        var ids = Set.of("gtj-03-依恋理论与情绪调节", "gtj-02-亲密关系心理学总论", "gtj-07-沟通冲突与修复");
        var a = r.retrieve("依恋 沟通 冲突", ids, 3, 0.5);
        var b = r.retrieve("依恋 沟通 冲突", ids, 3, 0.5);
        assertEquals(a.results().size(), b.results().size());
        for (int i = 0; i < a.results().size(); i++) {
            assertEquals(a.results().get(i).documentId(), b.results().get(i).documentId());
        }
    }

    @Test
    void missing_doc_in_scope_does_not_crash() {
        var r = new ScopedKnowledgeRetriever(catalog());
        var ids = Set.of("03-依恋理论与情绪调节", "nonexistent-doc");
        var res = r.retrieve("依恋", ids, 5, 0.5);
        // Should not crash; only 1 real candidate
        assertEquals(1, res.candidateCount());
    }

    @Test
    void top3_limits_to_max_three() {
        var r = new ScopedKnowledgeRetriever(fakeCatalog());
        var ids = Set.of("a","b","c","d","e","f");
        var res = r.retrieve("依恋 沟通 冲突 修复", ids, 3, 0.5);
        assertTrue(res.results().size() <= 3);
    }

    @Test
    void top3_returns_fewer_when_threshold_not_met() {
        var r = new ScopedKnowledgeRetriever(fakeCatalog());
        var ids = Set.of("a","b","c","d","e","f");
        var res = r.retrieve("分手 复合", ids, 3, 1.0);
        // Only d should score high; others may score 0
        assertTrue(res.results().size() <= 2);
    }

    @Test
    void top3_returns_empty_when_nothing_relevant() {
        var r = new ScopedKnowledgeRetriever(fakeCatalog());
        var ids = Set.of("a","b","c","d","e","f");
        var res = r.retrieve("量子物理 火箭", ids, 3, 0.5);
        assertTrue(res.results().isEmpty());
    }

    @Test
    void top3_respects_scope() {
        var r = new ScopedKnowledgeRetriever(fakeCatalog());
        var ids = Set.of("d","e","f");
        var res = r.retrieve("沟通 冲突 依恋", ids, 3, 0.5);
        for (var d : res.results()) assertTrue(ids.contains(d.documentId()));
    }

    @Test
    void top3_deterministic_tiebreak() {
        var r = new ScopedKnowledgeRetriever(fakeCatalog());
        var ids = Set.of("a","b","c","d","e","f");
        var a = r.retrieve("沟通", ids, 3, 0.0);
        var b = r.retrieve("沟通", ids, 3, 0.0);
        assertEquals(a.results().size(), b.results().size());
        for (int i = 0; i < a.results().size(); i++) {
            assertEquals(a.results().get(i).documentId(), b.results().get(i).documentId());
        }
    }
}
