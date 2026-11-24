CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.background_migration_state (
    name              text PRIMARY KEY,
    status            text NOT NULL,
    last_processed_id uuid,
    processed_count   bigint NOT NULL DEFAULT 0,
    inserted_count    bigint NOT NULL DEFAULT 0,
    skipped_count     bigint NOT NULL DEFAULT 0,
    job_id            uuid,
    started_at        timestamptz,
    updated_at        timestamptz,
    finished_at       timestamptz,
    error_message     text
);
