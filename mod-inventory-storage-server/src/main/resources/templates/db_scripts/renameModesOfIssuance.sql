START TRANSACTION;

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"integrating resource"')
WHERE it.jsonb->>'id' = '4fc0f4fe-06fd-490a-a078-c4da1754e03a';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"multipart monograph"')
WHERE it.jsonb->>'id' = 'f5cc2ab6-bb92-4cab-b83f-5a3d09261a41';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"serial"')
WHERE it.jsonb->>'id' = '068b5344-e2a6-40df-9186-1829e13cd344';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance AS it
SET jsonb = jsonb_set(it.jsonb, '{name}', '"single unit"')
WHERE it.jsonb->>'id' = '9d18a02f-5897-4c31-9106-c9abb5c7ae8b';

UPDATE ${myuniversity}_${mymodule}.mode_of_issuance
SET jsonb = jsonb || '{"name": "unspecified", "source": "folio"}'::jsonb
WHERE jsonb->>'id' = '612bbd3d-c16b-4bfb-8517-2afafc60204a';

END TRANSACTION;
