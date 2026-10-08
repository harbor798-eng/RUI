-- V4 SQLite: observation tables
CREATE TABLE observation_batch (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id       INTEGER NOT NULL,
    targets_json          TEXT NOT NULL,
    range_start           TEXT,
    range_end             TEXT,
    status                TEXT NOT NULL DEFAULT 'RUNNING',
    snapshot_chat_count   INTEGER NOT NULL DEFAULT 0,
    context_snapshot_json TEXT,
    started_at            TEXT,
    finished_at           TEXT,
    elapsed_ms            INTEGER
);
CREATE INDEX idx_obs_batch_rel ON observation_batch(relationship_id);

CREATE TABLE observation_batch_chat_snapshot (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_id              INTEGER NOT NULL,
    source_chat_message_id INTEGER,
    sender_type            TEXT NOT NULL,
    message_type           TEXT NOT NULL,
    content                TEXT,
    message_time           TEXT
);
CREATE INDEX idx_obs_snap_batch ON observation_batch_chat_snapshot(batch_id);

CREATE TABLE observation (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_id            INTEGER NOT NULL,
    subject             TEXT NOT NULL,
    ai_raw_text         TEXT NOT NULL,
    user_edited_text    TEXT,
    edit_reason          TEXT,
    include_in_longterm INTEGER NOT NULL DEFAULT 0,
    edit_submitted_at   TEXT,
    created_at          TEXT NOT NULL,
    longterm_version    TEXT,
    UNIQUE (batch_id, subject)
);
CREATE INDEX idx_obs_batch ON observation(batch_id);

CREATE TABLE observation_evidence (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    observation_id   INTEGER NOT NULL,
    evidence_type    TEXT NOT NULL,
    evidence_snapshot TEXT NOT NULL,
    created_at       TEXT NOT NULL
);
CREATE INDEX idx_evid_obs ON observation_evidence(observation_id);
