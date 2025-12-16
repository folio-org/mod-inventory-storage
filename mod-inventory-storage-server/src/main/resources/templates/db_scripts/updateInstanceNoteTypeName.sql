START TRANSACTION;

ALTER TABLE ${myuniversity}_${mymodule}.instance_note_type DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.instance_note_type
  SET jsonb = jsonb_set(jsonb, '{name}', '"Cumulative Index / Finding Aids notes"', false)
WHERE jsonb ->> 'name' = 'Cumulative Index / Finding Aides notes';

ALTER TABLE ${myuniversity}_${mymodule}.instance_note_type ENABLE TRIGGER USER;

END TRANSACTION;
