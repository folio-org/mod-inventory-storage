START TRANSACTION;

ALTER TABLE ${myuniversity}_${mymodule}.instance DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.instance
  SET jsonb = JSONB_SET(instance.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;

ALTER TABLE ${myuniversity}_${mymodule}.instance ENABLE TRIGGER USER;

END TRANSACTION;
