START TRANSACTION;

ALTER TABLE item DISABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item DISABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item DISABLE TRIGGER update_effective_location;
ALTER TABLE item DISABLE TRIGGER update_item_references;
ALTER TABLE item DISABLE TRIGGER update_item_status_date;

-- Temporary Trigger: perform conditional logic to perform upgrades in one update without a WHERE.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.temp_upgrade_items()
  RETURNS TRIGGER AS $$
  BEGIN
    CASE TG_OP
    WHEN 'UPDATE' THEN
      IF (NEW.jsonb->>'copyNumbers' IS NOT NULL) THEN
        IF (jsonb_array_length(NEW.jsonb->'copyNumbers') > 0) THEN
          NEW.jsonb := jsonb_set(NEW.jsonb, '{copyNumber}', NEW.jsonb#>'{copyNumbers, 0}');
        END IF;
        NEW.jsonb := NEW.jsonb - 'copyNumbers';
      END IF;
    END CASE;
    RETURN NEW;
  END;
  $$ language 'plpgsql';
CREATE TRIGGER upgrade_items_to_edelweiss_trigger
  BEFORE UPDATE ON ${myuniversity}_${mymodule}.item
  FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.temp_upgrade_items();

UPDATE ${myuniversity}_${mymodule}.item
  SET effectiveLocationId = (jsonb->>'effectiveLocationId')::uuid;

DROP TRIGGER upgrade_items_to_edelweiss_trigger ON ${myuniversity}_${mymodule}.item;
DROP FUNCTION ${myuniversity}_${mymodule}.temp_upgrade_items;

ALTER TABLE item ENABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item ENABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item ENABLE TRIGGER update_effective_location;
ALTER TABLE item ENABLE TRIGGER update_item_references;
ALTER TABLE item ENABLE TRIGGER update_item_status_date;

END TRANSACTION;
