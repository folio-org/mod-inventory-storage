START TRANSACTION;

ALTER TABLE ${myuniversity}_${mymodule}.location DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.location
  SET jsonb = JSONB_SET(location.jsonb, '{isShadow}', TO_JSONB(false))
WHERE jsonb ->> 'isShadow' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.location ENABLE TRIGGER USER;

END TRANSACTION;
