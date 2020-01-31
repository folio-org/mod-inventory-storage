START TRANSACTION;

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"integrating resource"')
WHERE it.jsonb->>'name' = 'Integrating Resource';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"multipart monograph"')
WHERE it.jsonb->>'name' = 'Sequential Monograph';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"serial"')
WHERE it.jsonb->>'name' = 'Serial';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"single unit"')
WHERE it.jsonb->>'name' = 'Monograph';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance
SET jsonb = jsonb || '{"name": "unspecified", "source": "folio"}'
WHERE jsonb->>'name' = 'Other';

END TRANSACTION;
