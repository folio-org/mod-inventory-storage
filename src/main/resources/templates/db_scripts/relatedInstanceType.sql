DROP FUNCTION IF EXISTS set_related_instance_type_md_json;

CREATE FUNCTION set_related_instance_type_md_json ()
RETURNS trigger AS $$
  BEGIN
    IF NEW.creation_date IS NULL THEN
      RETURN NEW;
    end IF;

    NEW.jsonb = jsonb_set(NEW.jsonb, '[metadata, createdDate]', to_jsonb(NEW.creation_date));
    IF NEW.created_by IS NULL THEN
      NEW.jsonb = NEW.jsonb #- '{metadata, createdByUserId}';
    ELSE
      NEW.jsonb = jsonb_set(NEW.jsonb,'{metadata, createdByUserId}' to_jsonb(NEW.created_by));
    END IF;
    RETURN NEW;
  END;
$$ LANGUAGE plpgsql COST 100
GO
ALTER FUNCTION set_related_instance_type_md_json () OWNER TO folio
GO


DROP FUNCTION IF EXISTS related_instance_type_set_md;

CREATE FUNCTION related_instance_type_set_md ()
RETURNS trigger AS $$
DECLARE
  invalid text;
  BEGIN
    input = NEW.jsonb->'metadata'->>'createdDate';
    IF input IS NULL THEN
      RETURN NEW;
    END IF;
    -- time stamp without timezone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- createdDate has no timezone, normalize using ::timestamp
      createdDate = input::timestamp;
    ELSE
      -- createdDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      createdDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '[metadata, createdDate]', to_jsonb(createdDate));
    NEW.creation_date = createdDate;
    NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
    RETURN NEW;
  END;
$$ LANGUAGE plpgsql COST 100
GO
ALTER FUNCTION related_instance_type_set_md () OWNER TO folio
GO

