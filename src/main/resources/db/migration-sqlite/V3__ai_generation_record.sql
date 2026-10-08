-- V3 SQLite: ai_generation_record
CREATE TABLE ai_generation_record (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    relationship_id         INTEGER NOT NULL,
    request_id              TEXT NOT NULL,
    stage                   TEXT,
    provider                TEXT,
    model                   TEXT,
    candidate_count         INTEGER NOT NULL DEFAULT 0,
    candidates_snapshot      TEXT,
    context_snapshot        TEXT,
    selected_strategy       TEXT,
    selected_candidate_index INTEGER,
    selected_original_text  TEXT,
    selected_at             TEXT,
    final_text              TEXT,
    modified                INTEGER NOT NULL DEFAULT 0,
    sent_message_id         INTEGER,
    sent_at                 TEXT,
    created_at              TEXT NOT NULL,
    FOREIGN KEY (relationship_id) REFERENCES relationship(id) ON DELETE CASCADE,
    FOREIGN KEY (sent_message_id) REFERENCES chat_message(id) ON DELETE SET NULL,
    UNIQUE (request_id)
);
CREATE INDEX idx_gen_rel ON ai_generation_record(relationship_id, created_at);
CREATE INDEX idx_gen_sent ON ai_generation_record(sent_message_id);
