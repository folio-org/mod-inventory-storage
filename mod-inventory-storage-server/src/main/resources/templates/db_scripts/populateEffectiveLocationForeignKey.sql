START TRANSACTION;

ALTER TABLE ${myuniversity}_${mymodule}.item DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.item
  SET effectiveLocationId = (jsonb->>'effectiveLocationId')::uuid;

ALTER TABLE ${myuniversity}_${mymodule}.item ENABLE TRIGGER USER;

END TRANSACTION;
