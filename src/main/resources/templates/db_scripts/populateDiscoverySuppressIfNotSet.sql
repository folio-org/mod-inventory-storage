START TRANSACTION;

UPDATE pg_trigger
  SET tgenabled = 'D'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'instance')
  AND tgisinternal IS FALSE;

UPDATE ${myuniversity}_${mymodule}.instance
  SET jsonb = JSONB_SET(instance.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;

UPDATE pg_trigger
  SET tgenabled = 'O'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'instance')
  AND tgisinternal IS FALSE;

END TRANSACTION;
