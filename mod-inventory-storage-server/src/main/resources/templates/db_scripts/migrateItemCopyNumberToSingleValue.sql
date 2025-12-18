START TRANSACTION;

ALTER TABLE ${myuniversity}_${mymodule}.item DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.item
SET jsonb = CASE WHEN
  jsonb->>'copyNumbers' IS NOT NULL
  AND jsonb_array_length(jsonb->'copyNumbers') > 0
  THEN jsonb_set(jsonb - 'copyNumbers', '{copyNumber}', jsonb#>'{copyNumbers, 0}')
  ELSE jsonb - 'copyNumbers'
END
WHERE jsonb->'copyNumbers' IS NOT NULL;

ALTER TABLE ${myuniversity}_${mymodule}.item ENABLE TRIGGER USER;

END TRANSACTION;
