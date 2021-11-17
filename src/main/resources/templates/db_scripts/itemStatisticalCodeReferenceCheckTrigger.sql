SET search_path TO ${myuniversity}_${mymodule};

CREATE OR REPLACE FUNCTION check_statistical_code_references()
RETURNS TRIGGER AS $$
DECLARE
  invalid text;
BEGIN
  SELECT ref
    INTO invalid
    FROM jsonb_array_elements_text(NEW.jsonb->'statisticalCodeIds') ref
    LEFT JOIN statistical_code ON id=ref::uuid
    WHERE id IS NULL
    LIMIT 1;
  IF FOUND THEN
    RAISE foreign_key_violation USING
      MESSAGE='statistical code doesn''t exist: ' || invalid,
      DETAIL='foreign key violation in statisticalCodeIds array of ' || TG_TABLE_NAME || ' with id=' || NEW.id,
      SCHEMA=TG_TABLE_SCHEMA,
      TABLE=TG_TABLE_NAME;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS check_statistical_code_references_on_insert ON item CASCADE;
CREATE TRIGGER check_statistical_code_references_on_insert
  BEFORE INSERT ON item
  FOR EACH ROW
  WHEN (NEW.jsonb->'statisticalCodeIds' IS NOT NULL AND NEW.jsonb->'statisticalCodeIds' <> '[]')
  EXECUTE FUNCTION check_statistical_code_references();

DROP TRIGGER IF EXISTS check_statistical_code_references_on_update ON item CASCADE;
CREATE TRIGGER check_statistical_code_references_on_update
  BEFORE UPDATE ON item
  FOR EACH ROW
  WHEN (NEW.jsonb->'statisticalCodeIds' IS NOT NULL AND NEW.jsonb->'statisticalCodeIds' <> '[]'
             AND OLD.jsonb->'statisticalCodeIds' IS DISTINCT FROM NEW.jsonb->'statisticalCodeIds')
  EXECUTE FUNCTION check_statistical_code_references();
