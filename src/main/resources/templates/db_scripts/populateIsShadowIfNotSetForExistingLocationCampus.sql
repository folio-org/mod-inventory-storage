START TRANSACTION;

-- Populate isShadow for existing campuses
ALTER TABLE ${myuniversity}_${mymodule}.loccampus DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.loccampus
  SET jsonb = JSONB_SET(loccampus.jsonb, '{isShadow}', TO_JSONB(false))
WHERE jsonb ->> 'isShadow' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.loccampus ENABLE TRIGGER USER;

END TRANSACTION;
