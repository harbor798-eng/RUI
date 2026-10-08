-- V8: Message Identity & Cross-Source Consistency
-- 1) chat_message 增加 merged_into_id（MERGED 消息指向 Canonical Message）
-- 2) 新建 message_observation：一条 Canonical Message 可有多来源观察
-- 注意：observation 表名不与已有 observation（AI 长期记忆）冲突

ALTER TABLE chat_message ADD COLUMN merged_into_id INTEGER REFERENCES chat_message(id);

CREATE TABLE message_observation (
    observation_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id         INTEGER NOT NULL REFERENCES chat_message(id),
    source_type        TEXT NOT NULL,
    source_namespace   TEXT NOT NULL,
    source_message_id  TEXT NOT NULL,
    external_account_id TEXT,
    external_chat_id   TEXT,
    external_local_id  INTEGER,
    raw_content        TEXT,
    observed_sender    TEXT,
    observed_time      TEXT,
    confidence         TEXT DEFAULT 'MEDIUM',
    resolution_status  TEXT DEFAULT 'PROVISIONAL',
    resolution_reason  TEXT,
    observed_at        TEXT,
    created_at         TEXT NOT NULL,
    updated_at         TEXT NOT NULL,
    UNIQUE(source_namespace, source_message_id)
);

CREATE INDEX idx_msg_obs_message ON message_observation(message_id);
CREATE INDEX idx_msg_obs_ext ON message_observation(external_account_id, external_chat_id, external_local_id);
