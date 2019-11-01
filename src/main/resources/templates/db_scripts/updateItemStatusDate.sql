
-- Updates item status date if status name was changed.
-- Item status date stores with timezone.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_status_date() RETURNS TRIGGER
AS $$
  DECLARE
	  newStatus text;
  BEGIN
  	newStatus = NEW.jsonb->'status'->>'name';
  IF newStatus IS NULL THEN  RETURN NEW;
  END IF;
	IF (TG_OP='UPDATE') THEN
	  IF (newStatus!=OLD.jsonb->'status'->>'name') THEN
          NEW.jsonb = jsonb_set(NEW.jsonb, '{status,date}', to_jsonb(NOW()), true);
	  END IF;
	END IF;
	RETURN NEW;
  END;
  $$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS update_status_date ON ${myuniversity}_${mymodule}.item;
CREATE TRIGGER update_status_date BEFORE UPDATE
ON ${myuniversity}_${mymodule}.item FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.update_status_date();
