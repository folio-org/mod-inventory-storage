CREATE INDEX IF NOT EXISTS instance_administrativenotes_idx ON ${myuniversity}_${mymodule}.instance
  USING gin (
             ${myuniversity}_${mymodule}.normalize_jsonb_array(jsonb -> 'administrativeNotes')
             jsonb_path_ops
    );
