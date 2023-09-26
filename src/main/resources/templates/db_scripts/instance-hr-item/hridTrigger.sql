-- Fill in the next hrid from the sequence when the instance/holding/item
-- doesn't have a hrid.
CREATE OR REPLACE FUNCTION hrid_trigger() RETURNS TRIGGER
AS $$
DECLARE
  name TEXT;
  hrid TEXT;
  prefix TEXT;
  zeroes BOOLEAN;
BEGIN
  IF NEW.jsonb->'hrid' IS NOT NULL THEN
    RETURN NEW;
  END IF;
  name = CASE TG_TABLE_NAME
      WHEN 'instance'        THEN 'instances'
      WHEN 'holdings_record' THEN 'holdings'
      WHEN 'item'            THEN 'items'
      END;
  SELECT nextval('hrid_' || name || '_seq'), jsonb->name->>'prefix', jsonb->>'commonRetainLeadingZeroes'
      INTO STRICT hrid, prefix, zeroes FROM hrid_settings;
  IF zeroes IS TRUE THEN
    hrid = repeat('0', 11 - length(hrid)) || hrid;
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{hrid}', to_jsonb(concat(prefix, hrid)));
  RETURN NEW;
END;
$$ language 'plpgsql';

-- currently only the holding hrid has a trigger; instance and item hrid still use Java code

DROP TRIGGER IF EXISTS hrid_holdings_record ON holdings_record CASCADE;
CREATE TRIGGER hrid_holdings_record BEFORE INSERT ON holdings_record FOR EACH ROW EXECUTE FUNCTION hrid_trigger();
