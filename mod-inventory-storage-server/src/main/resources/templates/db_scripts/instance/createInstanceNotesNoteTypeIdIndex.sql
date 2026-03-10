CREATE INDEX IF NOT EXISTS instance_notes_instancenotesnotetypeid_idx ON ${myuniversity}_${mymodule}.instance
  USING gin (
             ${myuniversity}_${mymodule}.normalize_jsonb_array(
               ${myuniversity}_${mymodule}.extract_values(jsonb, 'notes', 'instanceNoteTypeId'))
             jsonb_path_ops
    );
