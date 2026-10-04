-- =====================================================================
-- AI 恋爱关系助手 V1 - 初始库表（对应后端技术设计文档 §41 核心表清单）
-- 引擎 InnoDB / 字符集 utf8mb4 / BIGINT 主键
-- 注意：已发布后禁止修改本文件，结构变更请新增 V2__xxx.sql
-- =====================================================================

-- ---------- Relationship ----------
CREATE TABLE relationship (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '对方当前昵称/备注',
    my_name         VARCHAR(100) NULL COMMENT '用户在该关系中的称呼',
    current_stage   VARCHAR(20)  NOT NULL DEFAULT 'INITIAL_CONTACT'
                    COMMENT 'INITIAL_CONTACT/AMBIGUOUS/DATING/BROKEN_UP/RECONNECTED',
    avatar_path     VARCHAR(500) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                    COMMENT 'ACTIVE/ARCHIVED/DELETED',
    goal_note       VARCHAR(500) NULL COMMENT '我的恋爱目标（V1 仅写给自己，不进 AI Context）',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_rel_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关系根实体，一个对象一条';

CREATE TABLE relationship_stage_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    stage           VARCHAR(20)  NOT NULL,
    started_at      DATETIME     NOT NULL,
    ended_at        DATETIME     NULL,
    created_by      VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stage_rel (relationship_id, started_at),
    CONSTRAINT fk_stage_rel FOREIGN KEY (relationship_id) REFERENCES relationship (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关系阶段变更历史，用户手动设置';

-- ---------- Chat Message ----------
CREATE TABLE chat_message (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id   BIGINT       NOT NULL,
    sender_type       VARCHAR(10)  NOT NULL COMMENT 'ME/OTHER',
    message_type      VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    content           MEDIUMTEXT   NULL,
    message_time      DATETIME     NOT NULL,
    source_type       VARCHAR(30)  NOT NULL DEFAULT 'IMPORTED'
                      COMMENT 'IMPORTED/USER_ORIGINAL/USER_ADDED/AI_GENERATED/AI_GENERATED_EDITED/UNKNOWN',
    source_message_id VARCHAR(200) NULL COMMENT '外部来源消息 ID，幂等去重用',
    source_hash       VARCHAR(64)  NULL COMMENT 'HTML 等无稳定 ID 来源的内容哈希',
    source_content    MEDIUMTEXT   NULL COMMENT '导入时原始内容（只读，不被用户修改覆盖）',
    source_ai_message_id BIGINT    NULL COMMENT '最终消息来自哪条 AI 候选（不靠文本相似度猜测）',
    metadata          JSON         NULL COMMENT 'IMAGE/CALL/TRANSFER 等结构化扩展',
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_rel_time (relationship_id, message_time),
    KEY idx_chat_source (source_type, source_message_id),
    KEY idx_chat_ai (source_ai_message_id),
    UNIQUE KEY uk_chat_source (relationship_id, source_type, source_message_id),
    CONSTRAINT fk_chat_rel FOREIGN KEY (relationship_id) REFERENCES relationship (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前有效聊天消息（Raw/Effective 分离）';

CREATE TABLE chat_message_revision (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    message_id      BIGINT       NOT NULL,
    content         MEDIUMTEXT   NOT NULL,
    sender_type     VARCHAR(10)  NOT NULL,
    message_type    VARCHAR(20)  NOT NULL,
    edited_by       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_rev_msg (message_id),
    CONSTRAINT fk_rev_msg FOREIGN KEY (message_id) REFERENCES chat_message (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户修改历史版本';

CREATE TABLE chat_import_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    importer_type   VARCHAR(30)  NOT NULL COMMENT 'HTML/WECHAT_SQLITE/...',
    source_location VARCHAR(500) NOT NULL,
    total_parsed    INT          NOT NULL DEFAULT 0,
    inserted_count  INT          NOT NULL DEFAULT 0,
    duplicate_count INT          NOT NULL DEFAULT 0,
    conflict_count  INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PREVIEWING'
                    COMMENT 'PREVIEWING/CONFIRMED/FAILED',
    created_at      DATETIME     NOT NULL,
    confirmed_at    DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_import_rel (relationship_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入批次记录，幂等与审计';

-- ---------- Profile ----------
CREATE TABLE profile (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    owner_type      VARCHAR(10)  NOT NULL COMMENT 'ME/OTHER',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_rel_owner (relationship_id, owner_type),
    CONSTRAINT fk_profile_rel FOREIGN KEY (relationship_id) REFERENCES relationship (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一方档案（我的/对方各一条）';

CREATE TABLE profile_item (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    profile_id    BIGINT       NOT NULL,
    category      VARCHAR(30)  NOT NULL COMMENT 'BASIC/PERSONALITY/INTEREST/COMMUNICATION/SENSITIVE/...',
    item_key      VARCHAR(50)  NOT NULL,
    item_value     VARCHAR(500) NOT NULL,
    source_type   VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER/AI/IMPORT',
    confidence    DECIMAL(4,3) NULL COMMENT 'AI 判断置信度 0~1',
    usage_weight  VARCHAR(10)  NOT NULL DEFAULT 'NORMAL'
                  COMMENT 'NONE/LOW/NORMAL/HIGH（Context Builder 按此决定是否带入 AI）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_item_profile (profile_id),
    KEY idx_item_key (profile_id, item_key),
    CONSTRAINT fk_item_profile FOREIGN KEY (profile_id) REFERENCES profile (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案条目；AI 原始判断不可覆盖用户确认值';

CREATE TABLE ai_profile_observation (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    owner_type      VARCHAR(10)  NOT NULL,
    category        VARCHAR(30)  NOT NULL,
    item_key        VARCHAR(50)  NOT NULL,
    observed_value  VARCHAR(500) NOT NULL,
    confidence      DECIMAL(4,3) NULL,
    source_message_ids JSON      NULL,
    provider        VARCHAR(50)  NULL,
    model           VARCHAR(100) NULL,
    prompt_version  VARCHAR(30)  NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_obs_rel (relationship_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 原始档案判断，独立保留，不覆盖用户确认值';

CREATE TABLE profile_nickname_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    nickname        VARCHAR(100) NOT NULL,
    started_at      DATETIME     NULL,
    ended_at        DATETIME     NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_nick_rel (relationship_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对方历史昵称，仅在对方档案查看';

CREATE TABLE private_profile_data (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    owner_type      VARCHAR(10)  NOT NULL,
    data_type       VARCHAR(30)  NOT NULL COMMENT 'PHONE/ID_CARD/DETAILED_ADDRESS/PROVINCE/...',
    encrypted_value VARCHAR(500) NOT NULL,
    ai_visible      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'PHONE/ID_CARD/DETAILED_ADDRESS=0；PROVINCE=1',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_priv_rel (relationship_id, owner_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私密信息加密存储；省份以下地址不得进 AI Context';

-- ---------- AI Generated Message 链路（V1 核心数据链） ----------
CREATE TABLE ai_generated_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    context_id      VARCHAR(64)  NULL COMMENT '本次生成批次上下文 ID',
    strategy        VARCHAR(30)  NOT NULL COMMENT 'NATURAL/FUN/PROACTIVE/LIGHT_FLIRT/WARM/TEASING/INTIMATE_FLIRT',
    original_text   TEXT         NOT NULL,
    provider        VARCHAR(50)  NULL,
    model           VARCHAR(100) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'GENERATED'
                    COMMENT 'GENERATED/SELECTED/SENT/ABANDONED',
    created_at      DATETIME     NOT NULL,
    selected_at     DATETIME     NULL,
    sent_at         DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_ai_rel_status (relationship_id, status),
    KEY idx_ai_context (context_id),
    CONSTRAINT fk_aimsg_rel FOREIGN KEY (relationship_id) REFERENCES relationship (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 生成的候选回复原文';

CREATE TABLE ai_message_edit_analysis (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    ai_message_id   BIGINT       NOT NULL,
    final_message_id BIGINT      NOT NULL,
    original_text   TEXT         NOT NULL,
    final_text      TEXT         NOT NULL,
    similarity_score DECIMAL(5,4) NOT NULL,
    modification_rate DECIMAL(5,4) NOT NULL,
    modification_type VARCHAR(20) NOT NULL
                    COMMENT 'NONE/SMALL/MEDIUM/HEAVY/REWRITE',
    algorithm_version VARCHAR(20) NOT NULL COMMENT '算法版本，历史数据可追溯',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_edit_ai (ai_message_id),
    CONSTRAINT fk_edit_ai FOREIGN KEY (ai_message_id) REFERENCES ai_generated_message (id) ON DELETE CASCADE,
    CONSTRAINT fk_edit_final FOREIGN KEY (final_message_id) REFERENCES chat_message (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 原文与用户最终表达的修改程度分析';

-- ---------- AI Suggestion ----------
CREATE TABLE ai_suggestion (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id   BIGINT       NOT NULL,
    category          VARCHAR(30)  NOT NULL
                      COMMENT 'MY_INFO/OTHER_INFO/PERSONALITY/INTEREST/COMMUNICATION/SENSITIVE/TIMELINE_EVENT/RELATIONSHIP',
    content           TEXT         NOT NULL,
    evidence          TEXT         NULL,
    importance_level  VARCHAR(10)  NOT NULL DEFAULT 'P5' COMMENT 'P5..P0（内部）；UI 映射黄/橙/红',
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      COMMENT 'PENDING/CONFIRMED/EDITED/IGNORED',
    source_message_ids JSON        NULL,
    provider          VARCHAR(50)  NULL,
    model             VARCHAR(100) NULL,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sug_rel_status (relationship_id, status),
    KEY idx_sug_rel_cat (relationship_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 建议卡片，需用户确认才进正式档案';

CREATE TABLE ai_suggestion_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    suggestion_id   BIGINT       NOT NULL,
    action          VARCHAR(30)  NOT NULL COMMENT 'GENERATED/UPGRADED/CONFIRMED/EDITED/IGNORED',
    before_level    VARCHAR(10)  NULL,
    after_level     VARCHAR(10)  NULL,
    detail          TEXT         NULL,
    operator        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sugh_sug (suggestion_id),
    CONSTRAINT fk_sugh_sug FOREIGN KEY (suggestion_id) REFERENCES ai_suggestion (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='建议审计：时间/操作/等级变化/用户操作';

-- ---------- Memory（五层：近期原文/阶段摘要/重要事件/人物信息/互动模式） ----------
CREATE TABLE memory_item (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    memory_type     VARCHAR(30)  NOT NULL
                    COMMENT 'RECENT_CHAT/PERIOD_SUMMARY/IMPORTANT_EVENT/PERSON_FACT/RELATIONSHIP_PATTERN',
    title           VARCHAR(200) NULL,
    content         TEXT         NOT NULL,
    weight          DECIMAL(4,3) NOT NULL DEFAULT 1.0,
    valid_from      DATETIME     NULL,
    valid_to        DATETIME     NULL,
    source_message_ids JSON      NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_mem_rel_type (relationship_id, memory_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长期记忆；原始聊天仍是事实来源，Memory 不替代';

-- ---------- Timeline ----------
CREATE TABLE timeline_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    event_type      VARCHAR(40)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    event_time      DATETIME     NULL,
    source_type     VARCHAR(10)  NOT NULL DEFAULT 'USER' COMMENT 'USER/AI',
    source_message_id BIGINT     NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED' COMMENT 'PROPOSED/CONFIRMED',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tl_rel_time (relationship_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关系时间线；AI 建议事件必须用户确认';

-- ---------- Analysis ----------
CREATE TABLE analysis_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NOT NULL,
    analysis_type   VARCHAR(30)  NOT NULL
                    COMMENT 'OBJECTIVE_DATA/MY_PROFILE/OTHER_PROFILE/RELATIONSHIP/INTERACTION_INVESTMENT/CHAT_QUALITY/LONG_TERM_PATTERN',
    range_label     VARCHAR(20)  NOT NULL COMMENT '3D/7D/30D/6M/1Y',
    start_time      DATETIME     NULL,
    end_time        DATETIME     NULL,
    provider        VARCHAR(50)  NULL,
    model           VARCHAR(100) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/FAILED',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_task_rel (relationship_id, analysis_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析任务';

CREATE TABLE analysis_result (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    task_id         BIGINT       NOT NULL,
    metric_key      VARCHAR(50)  NOT NULL,
    metric_value    VARCHAR(500) NOT NULL,
    extra           JSON         NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_res_task (task_id),
    CONSTRAINT fk_res_task FOREIGN KEY (task_id) REFERENCES analysis_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化分析指标；客观数据优先，AI 主观分析其次';

-- ---------- AI Provider 配置（API Key 加密存储） ----------
CREATE TABLE ai_provider_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    provider_name   VARCHAR(50)  NOT NULL,
    base_url        VARCHAR(500) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    encrypted_api_key VARCHAR(500) NOT NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key 加密保存，不写日志、不进 Context';

-- ---------- 审计日志 ----------
CREATE TABLE audit_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT       NULL,
    operation       VARCHAR(50)  NOT NULL,
    detail          TEXT         NULL,
    operator        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_rel (relationship_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键操作审计；严禁记录 API Key/身份证/手机号/详细地址';
