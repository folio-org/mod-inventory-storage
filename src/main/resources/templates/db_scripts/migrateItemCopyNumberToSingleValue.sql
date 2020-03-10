START TRANSACTION;

UPDATE pg_trigger
  SET tgenabled = 'D'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'item')
  AND tgisinternal IS FALSE;

-- Temporary Trigger: perform conditional logic to perform upgrades in one update without a WHERE.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.temp_upgrade_items()
  RETURNS TRIGGER AS $$
  BEGIN
    CASE TG_OP
    WHEN 'UPDATE' THEN
      IF (jsonb_array_length(NEW.jsonb->'copyNumbers') > 0) THEN
        NEW.jsonb := jsonb_set(NEW.jsonb, '{copyNumber}', NEW.jsonb#>'{copyNumbers, 0}');
      END IF;
      NEW.jsonb := NEW.jsonb - 'copyNumbers';
    END CASE;
    RETURN NEW;
  END;
  $$ language 'plpgsql';
CREATE TRIGGER upgrade_items_to_edelweiss_trigger
  BEFORE UPDATE ON ${myuniversity}_${mymodule}.item
  FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.temp_upgrade_items();

UPDATE ${myuniversity}_${mymodule}.item
  SET id = id
WHERE jsonb->>'copyNumbers' IS NOT NULL;

DROP TRIGGER upgrade_items_to_edelweiss_trigger ON ${myuniversity}_${mymodule}.item;
DROP FUNCTION ${myuniversity}_${mymodule}.temp_upgrade_items;

UPDATE pg_trigger
  SET tgenabled = 'O'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'item')
  AND tgisinternal IS FALSE;

END TRANSACTION;
