-- Reject any instance, holding and item change if the HRID changes.
-- We cannot check this in Java because we don't load the old value
-- for performance reasons.

CREATE OR REPLACE FUNCTION lock_hrid() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.jsonb->'hrid' IS DISTINCT FROM OLD.jsonb->'hrid' THEN
      RAISE 'The hrid field cannot be changed: new=%, old=%. id=%',
          NEW.jsonb->>'hrid', OLD.jsonb->>'hrid', NEW.id
          USING ERRCODE = '23578', TABLE = TG_TABLE_NAME, SCHEMA = TG_TABLE_SCHEMA;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS lock_hrid ON instance CASCADE;
CREATE TRIGGER lock_hrid BEFORE UPDATE ON instance
  FOR EACH ROW EXECUTE FUNCTION lock_hrid();

DROP TRIGGER IF EXISTS lock_hrid ON holdings_record CASCADE;
CREATE TRIGGER lock_hrid BEFORE UPDATE ON holdings_record
  FOR EACH ROW EXECUTE FUNCTION lock_hrid();

DROP TRIGGER IF EXISTS lock_hrid ON item CASCADE;
CREATE TRIGGER lock_hrid BEFORE UPDATE ON item
  FOR EACH ROW EXECUTE FUNCTION lock_hrid();
