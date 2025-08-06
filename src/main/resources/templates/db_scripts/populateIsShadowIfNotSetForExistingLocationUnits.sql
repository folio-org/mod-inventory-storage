START TRANSACTION;

-- Populate isShadow for existing institutions
ALTER TABLE ${myuniversity}_${mymodule}.locinstitution DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.locinstitution
  SET jsonb = JSONB_SET(locinstitution.jsonb, '{isShadow}', TO_JSONB(false))
WHERE jsonb ->> 'isShadow' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.locinstitution ENABLE TRIGGER USER;

-- Populate isShadow for existing campuses
ALTER TABLE ${myuniversity}_${mymodule}.loccampus DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.loccampus
  SET jsonb = JSONB_SET(loccampus.jsonb, '{isShadow}', TO_JSONB(false))
WHERE jsonb ->> 'isShadow' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.loccampus ENABLE TRIGGER USER;

-- Populate isShadow for existing libraries
ALTER TABLE ${myuniversity}_${mymodule}.loclibrary DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.loclibrary
  SET jsonb = JSONB_SET(loclibrary.jsonb, '{isShadow}', TO_JSONB(false))
WHERE jsonb ->> 'isShadow' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.loclibrary ENABLE TRIGGER USER;

END TRANSACTION;
