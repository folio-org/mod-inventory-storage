CREATE INDEX IF NOT EXISTS instance_deleted_idx
ON ${myuniversity}_${mymodule}.instance ((jsonb ->> 'deleted'));
