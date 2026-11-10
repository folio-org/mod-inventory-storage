-- index for the custom field statistics queries and the batches of CustomFieldsValueCleanup
-- (key existence and containment)
CREATE INDEX IF NOT EXISTS item_customfields_recordservice_idx_gin ON item
  USING GIN ((jsonb->'customFields'));
