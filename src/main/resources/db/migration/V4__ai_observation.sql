-- V4: AI 长期观察（Observation）1.0
-- 设计要点：
-- 1) Batch 创建时把分析窗口内的 chat_message 子集复制到快照表，Runner 只读写快照表；
-- 2) chat_message 后续 新增/UPDATE/软删除 都不影响已创建的 Batch；
-- 3) source_chat_message_id 仅用于来源追踪，Runner 严禁据此回查 chat_message；
-- 4) 历史 Batch 永久保留，新分析 = 新 Batch。

CREATE TABLE observation_batch (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id       BIGINT       NOT NULL,
    targets_json          VARCHAR(200) NOT NULL COMMENT '本次分析主体，如 ["SELF","OTHER","RELATIONSHIP"]',
    range_start           DATETIME     NULL,
    range_end             DATETIME     NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/CANCELLED/FAILED/PARTIAL',
    snapshot_chat_count   INT          NOT NULL DEFAULT 0,
    context_snapshot_json MEDIUMTEXT   NULL COMMENT '创建时刻的本地统计/Profile摘要/行为统计快照',
    started_at            DATETIME     NULL,
    finished_at           DATETIME     NULL,
    elapsed_ms            BIGINT       NULL,
    created_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_obs_batch_rel (relationship_id)
) COMMENT='AI 长期分析批次';

CREATE TABLE observation_batch_chat_snapshot (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    batch_id              BIGINT      NOT NULL,
    source_chat_message_id BIGINT     NULL COMMENT '来源 chat_message.id，仅追踪用',
    sender_type            VARCHAR(10) NOT NULL,
    message_type           VARCHAR(20) NOT NULL,
    content                TEXT        NULL,
    message_time           DATETIME    NULL,
    PRIMARY KEY (id),
    KEY idx_obs_snap_batch (batch_id)
) COMMENT='Batch 创建时复制的聊天窗口快照';

CREATE TABLE observation (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id            BIGINT       NOT NULL,
    subject             VARCHAR(20)  NOT NULL COMMENT 'SELF/OTHER/RELATIONSHIP',
    ai_raw_text         MEDIUMTEXT   NOT NULL COMMENT 'AI 原始观察，永久保留',
    user_edited_text    MEDIUMTEXT   NULL,
    edit_reason         MEDIUMTEXT   NULL,
    include_in_longterm TINYINT(1)   NOT NULL DEFAULT 0,
    edit_submitted_at   DATETIME     NULL,
    created_at          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_obs_batch_subject (batch_id, subject),
    KEY idx_obs_batch (batch_id)
) COMMENT='一条主体观察';

CREATE TABLE observation_evidence (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    observation_id   BIGINT      NOT NULL,
    evidence_type    VARCHAR(30) NOT NULL COMMENT 'CHAT_STAT/CHAT_SNIPPET/PROFILE/BEHAVIOR',
    evidence_snapshot MEDIUMTEXT NOT NULL COMMENT 'AI 当时看到的证据文本快照',
    created_at       DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_evid_obs (observation_id)
) COMMENT='观察依据快照';
