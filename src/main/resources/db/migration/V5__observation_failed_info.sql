-- V5：Observation Batch 失败可追溯信息
-- 不删除失败 Batch；error_message 不包含 API Key / 完整聊天记录。

ALTER TABLE observation_batch
    ADD COLUMN error_code   VARCHAR(60)  NULL COMMENT '失败类型标识' AFTER elapsed_ms,
    ADD COLUMN error_message VARCHAR(500) NULL COMMENT '失败原因（脱敏）' AFTER error_code;
