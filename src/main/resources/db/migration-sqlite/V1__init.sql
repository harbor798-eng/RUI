-- V1 SQLite: initial schema (mirror of V1__init.sql MySQL DDL)
-- Types: INTEGER PRIMARY KEY AUTOINCREMENT for id; TEXT for varchar/text/json/datetime; INTEGER for tinyint; REAL for decimal.

CREATE TABLE relationship (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,
    my_name         TEXT,
    current_stage   TEXT NOT NULL DEFAULT 'INITIAL_CONTACT',
    avatar_path     TEXT,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    goal_note       TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);
CREATE INDEX idx_rel_status ON relationship(status);

CREATE TABLE relationship_stage_history (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    stage           TEXT NOT NULL,
    started_at      TEXT NOT NULL,
    ended_at        TEXT,
    created_by      TEXT NOT NULL DEFAULT 'USER',
    created_at      TEXT NOT NULL,
    FOREIGN KEY (relationship_id) REFERENCES relationship(id) ON DELETE CASCADE
);
CREATE INDEX idx_stage_rel ON relationship_stage_history(relationship_id, started_at);

CREATE TABLE chat_message (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id   INTEGER NOT NULL,
    sender_type       TEXT NOT NULL,
    message_type      TEXT NOT NULL DEFAULT 'TEXT',
    content           TEXT,
    message_time      TEXT NOT NULL,
    source_type       TEXT NOT NULL DEFAULT 'IMPORTED',
    source_message_id TEXT,
    source_hash       TEXT,
    source_content    TEXT,
    source_ai_message_id INTEGER,
    metadata          TEXT,
    created_at        TEXT NOT NULL,
    updated_at        TEXT NOT NULL,
    status            TEXT NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (relationship_id) REFERENCES relationship(id) ON DELETE CASCADE,
    UNIQUE (relationship_id, source_type, source_message_id)
);
CREATE INDEX idx_chat_rel_time ON chat_message(relationship_id, message_time);
CREATE INDEX idx_chat_source ON chat_message(source_type, source_message_id);
CREATE INDEX idx_chat_ai ON chat_message(source_ai_message_id);

CREATE TABLE chat_message_revision (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id      INTEGER NOT NULL,
    content         TEXT NOT NULL,
    sender_type     TEXT NOT NULL,
    message_type    TEXT NOT NULL,
    edited_by       TEXT NOT NULL DEFAULT 'USER',
    created_at      TEXT NOT NULL,
    message_time    TEXT,
    operation       TEXT NOT NULL DEFAULT 'EDIT',
    FOREIGN KEY (message_id) REFERENCES chat_message(id) ON DELETE CASCADE
);
CREATE INDEX idx_rev_msg ON chat_message_revision(message_id);

CREATE TABLE chat_import_record (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    importer_type   TEXT NOT NULL,
    source_location TEXT NOT NULL,
    total_parsed    INTEGER NOT NULL DEFAULT 0,
    inserted_count  INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    conflict_count  INTEGER NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'PREVIEWING',
    created_at      TEXT NOT NULL,
    confirmed_at    TEXT
);
CREATE INDEX idx_import_rel ON chat_import_record(relationship_id, created_at);

CREATE TABLE profile (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    owner_type      TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    FOREIGN KEY (relationship_id) REFERENCES relationship(id) ON DELETE CASCADE,
    UNIQUE (relationship_id, owner_type)
);

CREATE TABLE profile_item (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id    INTEGER NOT NULL,
    category      TEXT NOT NULL,
    item_key      TEXT NOT NULL,
    item_value    TEXT NOT NULL,
    source_type   TEXT NOT NULL DEFAULT 'USER',
    confidence    REAL,
    usage_weight  TEXT NOT NULL DEFAULT 'NORMAL',
    status        TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL,
    FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE
);
CREATE INDEX idx_item_profile ON profile_item(profile_id);
CREATE INDEX idx_item_key ON profile_item(profile_id, item_key);

CREATE TABLE ai_profile_observation (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    owner_type      TEXT NOT NULL,
    category        TEXT NOT NULL,
    item_key        TEXT NOT NULL,
    observed_value  TEXT NOT NULL,
    confidence      REAL,
    source_message_ids TEXT,
    provider        TEXT,
    model           TEXT,
    prompt_version  TEXT,
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_obs_rel ON ai_profile_observation(relationship_id, category);

CREATE TABLE profile_nickname_history (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    nickname        TEXT NOT NULL,
    started_at      TEXT,
    ended_at        TEXT,
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_nick_rel ON profile_nickname_history(relationship_id, started_at);

CREATE TABLE private_profile_data (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    owner_type      TEXT NOT NULL,
    data_type       TEXT NOT NULL,
    encrypted_value TEXT NOT NULL,
    ai_visible      INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_priv_rel ON private_profile_data(relationship_id, owner_type);

CREATE TABLE ai_generated_message (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    context_id      TEXT,
    strategy        TEXT NOT NULL,
    original_text   TEXT NOT NULL,
    provider        TEXT,
    model           TEXT,
    status          TEXT NOT NULL DEFAULT 'GENERATED',
    created_at      TEXT NOT NULL,
    selected_at     TEXT,
    sent_at         TEXT,
    FOREIGN KEY (relationship_id) REFERENCES relationship(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_rel_status ON ai_generated_message(relationship_id, status);
CREATE INDEX idx_ai_context ON ai_generated_message(context_id);

CREATE TABLE ai_message_edit_analysis (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    ai_message_id   INTEGER NOT NULL,
    final_message_id INTEGER NOT NULL,
    original_text   TEXT NOT NULL,
    final_text      TEXT NOT NULL,
    similarity_score REAL NOT NULL,
    modification_rate REAL NOT NULL,
    modification_type TEXT NOT NULL,
    algorithm_version TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    FOREIGN KEY (ai_message_id) REFERENCES ai_generated_message(id) ON DELETE CASCADE,
    FOREIGN KEY (final_message_id) REFERENCES chat_message(id) ON DELETE CASCADE,
    UNIQUE (ai_message_id)
);

CREATE TABLE ai_suggestion (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id   INTEGER NOT NULL,
    category          TEXT NOT NULL,
    content           TEXT NOT NULL,
    evidence          TEXT,
    importance_level  TEXT NOT NULL DEFAULT 'P5',
    status            TEXT NOT NULL DEFAULT 'PENDING',
    source_message_ids TEXT,
    provider          TEXT,
    model             TEXT,
    created_at        TEXT NOT NULL,
    updated_at        TEXT NOT NULL
);
CREATE INDEX idx_sug_rel_status ON ai_suggestion(relationship_id, status);
CREATE INDEX idx_sug_rel_cat ON ai_suggestion(relationship_id, category);

CREATE TABLE ai_suggestion_history (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    suggestion_id   INTEGER NOT NULL,
    action          TEXT NOT NULL,
    before_level    TEXT,
    after_level     TEXT,
    detail          TEXT,
    operator        TEXT NOT NULL DEFAULT 'USER',
    created_at      TEXT NOT NULL,
    FOREIGN KEY (suggestion_id) REFERENCES ai_suggestion(id) ON DELETE CASCADE
);
CREATE INDEX idx_sugh_sug ON ai_suggestion_history(suggestion_id);

CREATE TABLE memory_item (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    memory_type     TEXT NOT NULL,
    title           TEXT,
    content         TEXT NOT NULL,
    weight          REAL NOT NULL DEFAULT 1.0,
    valid_from      TEXT,
    valid_to        TEXT,
    source_message_ids TEXT,
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_mem_rel_type ON memory_item(relationship_id, memory_type);

CREATE TABLE timeline_event (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    event_type      TEXT NOT NULL,
    title           TEXT NOT NULL,
    description     TEXT,
    event_time      TEXT,
    source_type     TEXT NOT NULL DEFAULT 'USER',
    source_message_id INTEGER,
    status          TEXT NOT NULL DEFAULT 'CONFIRMED',
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);
CREATE INDEX idx_tl_rel_time ON timeline_event(relationship_id, event_time);

CREATE TABLE analysis_task (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER NOT NULL,
    analysis_type   TEXT NOT NULL,
    range_label     TEXT NOT NULL,
    start_time      TEXT,
    end_time        TEXT,
    provider        TEXT,
    model           TEXT,
    status          TEXT NOT NULL DEFAULT 'RUNNING',
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_task_rel ON analysis_task(relationship_id, analysis_type);

CREATE TABLE analysis_result (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id         INTEGER NOT NULL,
    metric_key      TEXT NOT NULL,
    metric_value    TEXT NOT NULL,
    extra           TEXT,
    created_at      TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES analysis_task(id) ON DELETE CASCADE
);
CREATE INDEX idx_res_task ON analysis_result(task_id);

CREATE TABLE ai_provider_config (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_name   TEXT NOT NULL,
    base_url        TEXT NOT NULL,
    model           TEXT NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    is_active       INTEGER NOT NULL DEFAULT 1,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);

CREATE TABLE audit_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id INTEGER,
    operation       TEXT NOT NULL,
    detail          TEXT,
    operator        TEXT NOT NULL DEFAULT 'USER',
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_audit_rel ON audit_log(relationship_id, created_at);
