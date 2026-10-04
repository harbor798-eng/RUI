-- V6：长期观察版本选择
-- longterm_version: NULL=未纳入长期观察；AI_RAW=纳入且使用 AI 原始观察；USER_EDITED=纳入且使用用户修改后版本。

ALTER TABLE observation
    ADD COLUMN longterm_version VARCHAR(20) NULL COMMENT 'AI_RAW / USER_EDITED' AFTER include_in_longterm;
