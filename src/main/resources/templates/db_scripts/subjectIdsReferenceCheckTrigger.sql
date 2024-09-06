CREATE OR REPLACE FUNCTION check_subject_references()
RETURNS TRIGGER AS $$
DECLARE
invalid_source_id UUID;
invalid_type_id UUID;
BEGIN

SELECT subj->>'sourceId'
INTO invalid_source_id
FROM jsonb_array_elements(NEW.jsonb->'subjects') subj
  LEFT JOIN subject_source ON id = (subj->>'sourceId')::UUID
WHERE subj->>'sourceId' IS NOT NULL AND id IS NULL
  LIMIT 1;

IF FOUND THEN
    RAISE foreign_key_violation USING
      MESSAGE = 'subject source doesn''t exist: ' || invalid_source_id,
      DETAIL = 'foreign key violation in subjects array of ' || TG_TABLE_NAME || ' with id=' || NEW.id,
      SCHEMA = TG_TABLE_SCHEMA,
      TABLE = TG_TABLE_NAME;
END IF;

SELECT subj->>'typeId'
INTO invalid_type_id
FROM jsonb_array_elements(NEW.jsonb->'subjects') subj
  LEFT JOIN subject_type ON id = (subj->>'typeId')::UUID
WHERE subj->>'typeId' IS NOT NULL AND id IS NULL
  LIMIT 1;

IF FOUND THEN
    RAISE foreign_key_violation USING
      MESSAGE = 'subject type doesn''t exist: ' || invalid_type_id,
      DETAIL = 'foreign key violation in subjects array of ' || TG_TABLE_NAME || ' with id=' || NEW.id,
      SCHEMA = TG_TABLE_SCHEMA,
      TABLE = TG_TABLE_NAME;
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS check_subject_references_on_insert_or_update ON instance CASCADE;
CREATE TRIGGER check_subject_references_on_insert_or_update
  BEFORE INSERT OR UPDATE ON instance
                     FOR EACH ROW
                     WHEN (NEW.jsonb->'subjects' IS NOT NULL AND NEW.jsonb->'subjects' <> '[]')
                     EXECUTE FUNCTION check_subject_references();
