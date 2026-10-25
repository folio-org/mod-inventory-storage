-- Queue of deferred custom field value removals: deleting a field only records what has to go,
-- CustomFieldsValueCleanup removes the values in batches afterwards.
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.custom_fields_cleanup (
  id uuid PRIMARY KEY,
  entity_type text NOT NULL,
  ref_id text NOT NULL,
  created_date timestamptz NOT NULL DEFAULT now()
);
