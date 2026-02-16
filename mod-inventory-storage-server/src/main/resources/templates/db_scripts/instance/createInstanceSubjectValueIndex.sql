CREATE INDEX IF NOT EXISTS instance_subjects_values_idx ON ${myuniversity}_${mymodule}.instance
  USING gin (
             ${myuniversity}_${mymodule}.normalize_jsonb_array(
               ${myuniversity}_${mymodule}.extract_values(jsonb, 'subjects', 'value'))
             jsonb_path_ops
    );
