ALTER TABLE ${myuniversity}_${mymodule}.instance
DISABLE TRIGGER check_subject_references_on_insert_or_update;

UPDATE ${myuniversity}_${mymodule}.instance
SET jsonb = jsonb || '{"deleted": "false"}'
WHERE jsonb ->> 'deleted' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.instance
ENABLE TRIGGER check_subject_references_on_insert_or_update;
