START TRANSACTION;

UPDATE pg_trigger
  SET tgenabled = 'D'
WHERE tgrelid = '${myuniversity}_${mymodule}.instance'::regclass::oid
  AND tgisinternal IS FALSE
  AND tgenabled = 'O';

UPDATE ${myuniversity}_${mymodule}.instance
  SET jsonb = JSONB_SET(instance.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;

UPDATE pg_trigger
  SET tgenabled = 'O'
WHERE tgrelid = '${myuniversity}_${mymodule}.instance'::regclass::oid
  AND tgisinternal IS FALSE
  AND tgenabled = 'D';

END TRANSACTION;
