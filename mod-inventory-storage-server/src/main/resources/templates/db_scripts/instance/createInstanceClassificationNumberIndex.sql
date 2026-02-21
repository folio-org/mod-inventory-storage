CREATE INDEX IF NOT EXISTS instance_classifications_number_idx ON ${myuniversity}_${mymodule}.instance
  USING gin (
             ${myuniversity}_${mymodule}.normalize_jsonb_array(
               ${myuniversity}_${mymodule}.extract_values(jsonb, 'classifications', 'classificationNumber'))
             jsonb_path_ops
    );
