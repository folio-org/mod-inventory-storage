CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_instance_md_json()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
AS $BODY$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
    -- since this trigger is being executed after triggers on update/insert/delete items/holdings,
    -- need to check current value to avoid overwriting with the old updatedDate value
    IF ((NEW.jsonb -> 'metadata' ->> 'updatedDate')::timestamp with time zone > NEW.complete_updated_date) THEN
    	NEW.complete_updated_date = (NEW.jsonb -> 'metadata' ->> 'updatedDate')::timestamp with time zone;
    END IF;
  end if;
  RETURN NEW;
END;
$BODY$;
