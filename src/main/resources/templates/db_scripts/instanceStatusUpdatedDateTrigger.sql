CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_instance_status_updated_date()
RETURNS trigger
AS $function$
	BEGIN
		IF (OLD.jsonb->'statusId' IS DISTINCT FROM NEW.jsonb->'statusId') THEN
			-- Date time in "yyyy-MM-dd'T'HH:mm:ss.SSS" format at UTC (00:00) time zone
			NEW.jsonb = jsonb_set(NEW.jsonb, '{statusUpdatedDate}', to_jsonb(CURRENT_TIMESTAMP(3) AT TIME ZONE 'UTC'));
		END IF;
		RETURN NEW;
	END;
$function$  LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_instance_status_updated_date ON ${myuniversity}_${mymodule}.instance;

CREATE TRIGGER set_instance_status_updated_date BEFORE
UPDATE ON ${myuniversity}_${mymodule}.instance
FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.set_instance_status_updated_date();
