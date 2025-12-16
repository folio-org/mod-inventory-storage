START TRANSACTION;

-- Populate isShadow for existing libraries
ALTER TABLE ${myuniversity}_${mymodule}.loclibrary DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.loclibrary
  SET jsonb = JSONB_SET(loclibrary.jsonb, '{isShadow}', TO_JSONB(false))
WHERE jsonb ->> 'isShadow' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.loclibrary ENABLE TRIGGER USER;

END TRANSACTION;
