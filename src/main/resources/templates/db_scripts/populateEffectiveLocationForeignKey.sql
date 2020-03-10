START TRANSACTION;

UPDATE pg_trigger
  SET tgenabled = 'D'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'item')
  AND tgisinternal IS FALSE
  AND tgenabled = 'O';

UPDATE ${myuniversity}_${mymodule}.item
  SET effectiveLocationId = (jsonb->>'effectiveLocationId')::uuid;

UPDATE pg_trigger
  SET tgenabled = 'O'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'item')
  AND tgisinternal IS FALSE
  AND tgenabled = 'D';

END TRANSACTION;
