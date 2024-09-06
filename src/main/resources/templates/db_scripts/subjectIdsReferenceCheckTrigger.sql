CREATE OR REPLACE FUNCTION check_subject_references()
RETURNS TRIGGER AS $$
DECLARE
  invalid_subject_source_id UUID;
  invalid_subject_type_id UUID;
BEGIN

SELECT subj->>'subjectSourceId'
INTO invalid_subjectSourceId
FROM jsonb_array_elements(NEW.jsonb->'subjects') subj
  LEFT JOIN subject_source ON id = (subj->>'subjectSourceId')::UUID
WHERE subj->>'subjectSourceId' IS NOT NULL AND id IS NULL
  LIMIT 1;

IF FOUND THEN
    RAISE foreign_key_violation USING
      MESSAGE = 'subjectSourceId doesn''t exist: ' || invalid_subjectSourceId,
      DETAIL = 'foreign key violation in subjects array of ' || TG_TABLE_NAME || ' with id=' || NEW.id,
      SCHEMA = TG_TABLE_SCHEMA,
      TABLE = TG_TABLE_NAME;
END IF;

SELECT subj->>'subjectTypeId'
INTO invalid_subjectTypeId
FROM jsonb_array_elements(NEW.jsonb->'subjects') subj
  LEFT JOIN subject_type ON id = (subj->>'subjectTypeId')::UUID
WHERE subj->>'subjectSourceId' IS NOT NULL AND id IS NULL
  LIMIT 1;

IF FOUND THEN
    RAISE foreign_key_violation USING
      MESSAGE = 'subject type doesn''t exist: ' || invalid_subjectTypeId,
      DETAIL = 'foreign key violation in subjects array of ' || TG_TABLE_NAME || ' with id=' || NEW.id,
      SCHEMA = TG_TABLE_SCHEMA,
      TABLE = TG_TABLE_NAME;
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS check_subject_references_on_insert ON instance CASCADE;
CREATE TRIGGER check_subject_references_on_insert
  BEFORE INSERT OR UPDATE ON instance
                     FOR EACH ROW
                     WHEN (NEW.jsonb->'subjects' IS NOT NULL AND NEW.jsonb->'subjects' <> '[]')
                     EXECUTE FUNCTION check_subject_references();

DROP TRIGGER IF EXISTS check_subject_references_on_insert_or_update ON instance CASCADE;
CREATE TRIGGER check_subject_references_on_insert_or_update
  BEFORE INSERT OR UPDATE ON instance
                     FOR EACH ROW
                     WHEN (NEW.jsonb->'subjects' IS NOT NULL AND NEW.jsonb->'subjects' <> '[]')
                     EXECUTE FUNCTION check_subject_references();
