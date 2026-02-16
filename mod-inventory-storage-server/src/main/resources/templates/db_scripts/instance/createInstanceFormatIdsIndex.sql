CREATE INDEX IF NOT EXISTS instance_format_ids_idx ON ${myuniversity}_${mymodule}.instance
  USING gin ((jsonb -> 'instanceFormatIds') jsonb_path_ops);
