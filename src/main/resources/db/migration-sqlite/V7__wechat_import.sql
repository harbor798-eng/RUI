-- V7: WeChat import V1
-- 1) relationship 增加微信身份列；partial unique index 允许多个 NULL，禁止两个相同非 NULL wxid。
-- 2) 应用级 KV 配置表（V1 仅用于 wechat.self_wxid）。
ALTER TABLE relationship ADD COLUMN wechat_wxid TEXT;
CREATE UNIQUE INDEX idx_rel_wechat_wxid ON relationship(wechat_wxid) WHERE wechat_wxid IS NOT NULL;

CREATE TABLE app_config (
    config_key   TEXT PRIMARY KEY,
    config_value TEXT,
    updated_at   TEXT NOT NULL
);
