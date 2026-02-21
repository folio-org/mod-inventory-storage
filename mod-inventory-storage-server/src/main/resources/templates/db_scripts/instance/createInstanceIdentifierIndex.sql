CREATE INDEX IF NOT EXISTS instance_identifiers_values_idx ON ${myuniversity}_${mymodule}.instance
  USING gin (
             ${myuniversity}_${mymodule}.normalize_jsonb_array(
               ${myuniversity}_${mymodule}.extract_values(jsonb, 'identifiers', 'value'))
             jsonb_path_ops
    );
