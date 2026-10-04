-- =====================================================================
-- AI 恋爱关系助手 V1 - Phase 3：AI 生成行为记录（GenerationRecord）
-- 原则：不修改 V1/V2；旧 ai_generated_message 表保留不动
-- 一次生成一条记录；selected_*/final_text/sent_* 均可为空，
-- 以表达：未选 / 选而未发 / 选+改+发 / 选+未改+发 四种状态。
-- =====================================================================
CREATE TABLE ai_generation_record (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id         BIGINT       NOT NULL,
    request_id              VARCHAR(64)  NOT NULL COMMENT '对应 GenerationResult.requestId',
    stage                   VARCHAR(50)  NULL COMMENT '生成时关系阶段（中文 label）',
    provider                VARCHAR(50)  NULL,
    model                   VARCHAR(100) NULL,
    candidate_count         INT          NOT NULL DEFAULT 0,
    candidates_snapshot    JSON         NULL COMMENT '本次完整 9 候选快照（3策略×3条，全局 index 0-8）',
    context_snapshot        TEXT         NULL COMMENT '本次实际使用的 humanContext（不含私密/Key/prompt 约束）',
    selected_strategy       VARCHAR(50)  NULL,
    selected_candidate_index INT         NULL COMMENT '全局 0-8；NULL=未选择',
    selected_original_text   TEXT         NULL,
    selected_at             DATETIME     NULL,
    final_text              TEXT         NULL COMMENT '用户最终文本（未发送也保留）',
    modified                TINYINT(1)   NOT NULL DEFAULT 0,
    sent_message_id         BIGINT       NULL COMMENT '→ chat_message.id；未发送为 NULL',
    sent_at                 DATETIME     NULL,
    created_at              DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_request (request_id),
    KEY idx_gen_rel (relationship_id, created_at),
    KEY idx_gen_sent (sent_message_id),
    CONSTRAINT fk_gen_rel FOREIGN KEY (relationship_id) REFERENCES relationship (id) ON DELETE CASCADE,
    CONSTRAINT fk_gen_msg FOREIGN KEY (sent_message_id) REFERENCES chat_message (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 生成→选择→修改→发送 行为记录';
