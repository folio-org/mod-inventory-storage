-- Queue of deferred custom field value removals: deleting a field only records what has to go,
-- CustomFieldsValueCleanup removes the values in batches afterwards.
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.custom_fields_cleanup (
  id uuid PRIMARY KEY,
  entity_type text NOT NULL,
  ref_id text NOT NULL,
  created_date timestamptz NOT NULL DEFAULT now()
);

-- The trigger queues a removal in the transaction of the definition delete, so a queue row exists
-- exactly when the delete committed, whichever folio-custom-fields endpoint made it.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.queue_custom_field_cleanup()
    RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO ${myuniversity}_${mymodule}.custom_fields_cleanup (id, entity_type, ref_id)
    VALUES (gen_random_uuid(), OLD.jsonb->>'entityType', OLD.jsonb->>'refId');
  RETURN OLD;
END;
$$ language 'plpgsql';
DROP TRIGGER IF EXISTS queue_custom_field_cleanup ON ${myuniversity}_${mymodule}.custom_fields CASCADE;
CREATE TRIGGER queue_custom_field_cleanup
    AFTER DELETE ON ${myuniversity}_${mymodule}.custom_fields
    FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.queue_custom_field_cleanup();
