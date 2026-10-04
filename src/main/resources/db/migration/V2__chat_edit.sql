-- =====================================================================
-- AI 恋爱关系助手 V1 - 阶段2 聊天编辑与手动新增（V2）
-- 原则：不修改已发布的 V1__init.sql；不删除/重建表；不删除现有数据
-- =====================================================================

-- 1) chat_message：软删除状态列（默认 ACTIVE；DELETED 时 UI 默认隐藏，原始 source_* 保留）
ALTER TABLE chat_message
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    COMMENT 'ACTIVE/DELETED（软删除，原始来源信息保留，可恢复）' AFTER updated_at;

-- 2) chat_message_revision：补全“修改前消息时间”与“修改操作类型”
ALTER TABLE chat_message_revision
    ADD COLUMN message_time DATETIME NULL COMMENT '修改前消息时间（墙钟 Asia/Shanghai）' AFTER message_type;
ALTER TABLE chat_message_revision
    ADD COLUMN operation VARCHAR(20) NOT NULL DEFAULT 'EDIT'
    COMMENT 'EDIT/ADD/DELETE/RESTORE（修改操作）' AFTER message_time;
