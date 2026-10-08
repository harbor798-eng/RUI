-- V5 SQLite: observation_batch error fields
ALTER TABLE observation_batch ADD COLUMN error_code TEXT;
ALTER TABLE observation_batch ADD COLUMN error_message TEXT;
