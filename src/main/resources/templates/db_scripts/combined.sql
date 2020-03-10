\timing on

START TRANSACTION;

ALTER TABLE item DISABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item DISABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item DISABLE TRIGGER update_effective_location;
ALTER TABLE item DISABLE TRIGGER update_item_status_date;

UPDATE diku_mod_inventory_storage.item AS it
SET jsonb = jsonb_set(
  it.jsonb,
  '{effectiveCallNumberComponents}',
  jsonb_build_object(
    'callNumber', COALESCE(it.jsonb->'itemLevelCallNumber', hr.jsonb->'callNumber'),
    'prefix', COALESCE(it.jsonb->'itemLevelCallNumberPrefix', hr.jsonb->'callNumberPrefix'),
    'suffix', COALESCE(it.jsonb->'itemLevelCallNumberSuffix', hr.jsonb->'callNumberSuffix'),
    'typeId', COALESCE(it.jsonb->'itemLevelCallNumberTypeId', hr.jsonb->'callNumberTypeId')
  )
) ||
jsonb_build_object(
  -- Since holdings_record.permanentLocationId and item.holdingsRecordId are required there can not be a NULL value
  'effectiveLocationId', COALESCE(it.jsonb->'temporaryLocationId', it.jsonb->'permanentLocationId',
  hr.jsonb->'temporaryLocationId', hr.jsonb->'permanentLocationId')
)
FROM diku_mod_inventory_storage.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;

ALTER TABLE item ENABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item ENABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item ENABLE TRIGGER update_effective_location;
ALTER TABLE item ENABLE TRIGGER update_item_status_date;

--END TRANSACTION;



--START TRANSACTION;

ALTER TABLE item DISABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item DISABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item DISABLE TRIGGER update_effective_location;
ALTER TABLE item DISABLE TRIGGER update_item_references;
ALTER TABLE item DISABLE TRIGGER update_item_status_date;

-- Temporary Trigger: perform conditional logic to perform upgrades in one update without a WHERE.
CREATE OR REPLACE FUNCTION diku_mod_inventory_storage.upgrade_items_to_edelweiss()
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
  BEFORE UPDATE ON diku_mod_inventory_storage.item
  FOR EACH ROW EXECUTE PROCEDURE diku_mod_inventory_storage.upgrade_items_to_edelweiss();

UPDATE diku_mod_inventory_storage.item
  SET effectiveLocationId = (jsonb->>'effectiveLocationId')::uuid;

DROP TRIGGER upgrade_items_to_edelweiss_trigger ON diku_mod_inventory_storage.item;
DROP FUNCTION diku_mod_inventory_storage.upgrade_items_to_edelweiss;

ALTER TABLE item ENABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item ENABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item ENABLE TRIGGER update_effective_location;
ALTER TABLE item ENABLE TRIGGER update_item_references;
ALTER TABLE item ENABLE TRIGGER update_item_status_date;

--END TRANSACTION;



--START TRANSACTION;

ALTER TABLE item DISABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item DISABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item DISABLE TRIGGER update_effective_location;
ALTER TABLE item DISABLE TRIGGER update_item_references;
ALTER TABLE item DISABLE TRIGGER update_item_status_date;

UPDATE diku_mod_inventory_storage.instance
SET	jsonb = JSONB_SET(instance.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;

ALTER TABLE item ENABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item ENABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item ENABLE TRIGGER update_effective_location;
ALTER TABLE item ENABLE TRIGGER update_item_references;
ALTER TABLE item ENABLE TRIGGER update_item_status_date;

--END TRANSACTION;

ABORT;


--- BELOW ARE RESULTS
--UPDATE 3500000
--Time: 577498.245 ms (09:37.498)
--UPDATE 0
--Time: 64534.352 ms (01:04.534)
--UPDATE 0
--Time: 64041.000 ms (01:04.041)
--UPDATE 3500000
--Time: 593785.456 ms (09:53.785)
--UPDATE 999998
--Time: 775820.696 ms (12:55.821)
--((577498.245+64534.352+64041.000+593785.456+775820.696) / 1000) / 60 = 34.5946624833 minutes



--- BELOW ARE RESULTS
--UPDATE 3500000
--Time: 578110.371 ms (09:38.110)
--CREATE FUNCTION
--Time: 16.593 ms
--CREATE TRIGGER
--Time: 1.947 ms
--UPDATE 3500000
--Time: 619542.102 ms (10:19.542)
--DROP TRIGGER
--Time: 10.951 ms
--DROP FUNCTION
--Time: 1.969 ms
--UPDATE 999998
--Time: 739868.928 ms (12:19.869)
--ALTER TABLE
--Time: 2.241 ms
--((578110.371 + 16.593 +  1.947 +  619542.102 + 10.951 + 1.969 + 739868.928 + 2.241) / 1000) / 60 = 32.2925850333 minutes
