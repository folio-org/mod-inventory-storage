START TRANSACTION;

-- Populate isShadow for existing institutions
ALTER TABLE ${myuniversity}_${mymodule}.locinstitution DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.locinstitution
  SET jsonb = JSONB_SET(locinstitution.jsonb, '{isShadow}', TO_JSONB(false))
WHERE jsonb ->> 'isShadow' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.locinstitution ENABLE TRIGGER USER;

END TRANSACTION;
