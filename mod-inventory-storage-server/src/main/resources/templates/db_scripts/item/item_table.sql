CREATE INDEX IF NOT EXISTS item_customfields_recordservice_idx_gin ON ${myuniversity}_${mymodule}.po_line
  USING GIN ((jsonb->'customFields'));
