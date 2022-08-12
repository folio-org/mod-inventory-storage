SET search_path TO ${myuniversity}_${mymodule};

CREATE OR REPLACE FUNCTION process_statistical_code_delete() RETURNS TRIGGER
AS $process_statistical_code_delete$
  DECLARE
    item_fk_counter integer := 0;
    holding_fk_counter integer := 0;
    instance_fk_counter integer := 0;
  BEGIN
    IF (TG_OP = 'DELETE') THEN
      SELECT COUNT(*) INTO item_fk_counter FROM item WHERE jsonb->'statisticalCodeIds' ? OLD.id::text;
      IF (item_fk_counter > 0) THEN
        RAISE foreign_key_violation USING DETAIL = format('Key (id)=(%s) is still referenced from table "item".', OLD.id::text);
      END IF;

      SELECT COUNT(*) INTO holding_fk_counter FROM holdings_record WHERE jsonb->'statisticalCodeIds' ? OLD.id::text;
      IF (holding_fk_counter > 0) THEN
        RAISE foreign_key_violation USING DETAIL = format('Key (id)=(%s) is still referenced from table "holdings record".', OLD.id::text);
      END IF;

      SELECT COUNT(*) INTO instance_fk_counter FROM instance WHERE jsonb->'statisticalCodeIds' ? OLD.id::text;
      IF (instance_fk_counter > 0) THEN
        RAISE foreign_key_violation USING DETAIL = format('Key (id)=(%s) is still referenced from table "instance".', OLD.id::text);
      END IF;
    END IF;
    RETURN OLD;
  END;
$process_statistical_code_delete$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS check_item_statistical_code_reference_on_delete ON statistical_code CASCADE;
CREATE TRIGGER check_item_statistical_code_reference_on_delete
  BEFORE DELETE ON statistical_code
  FOR EACH ROW EXECUTE FUNCTION process_statistical_code_delete();
