START TRANSACTION;

--UPDATE pg_trigger
--  SET tgenabled = 'D'
--WHERE tgrelid = '${myuniversity}_${mymodule}.item'::regclass::oid
--  AND tgisinternal IS FALSE
--  AND tgenabled = 'O';

UPDATE ${myuniversity}_${mymodule}.item
SET jsonb = CASE WHEN
  jsonb->>'copyNumbers' IS NOT NULL
  AND jsonb_array_length(jsonb->'copyNumbers') > 0
  THEN jsonb_set(jsonb - 'copyNumbers', '{copyNumber}', jsonb#>'{copyNumbers, 0}')
  ELSE jsonb - 'copyNumbers'
END
WHERE jsonb->'copyNumbers' IS NOT NULL;

--UPDATE pg_trigger
--  SET tgenabled = 'O'
--WHERE tgrelid = '${myuniversity}_${mymodule}.item'::regclass::oid
--  AND tgisinternal IS FALSE
--  AND tgenabled = 'D';

END TRANSACTION;
