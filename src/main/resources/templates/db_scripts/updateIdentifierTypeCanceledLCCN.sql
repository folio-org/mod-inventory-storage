UPDATE ${myuniversity}_${mymodule}.identifier_type
SET jsonb = jsonb_set(jsonb, '{name}', to_jsonb(CONCAT(jsonb ->> 'name', '_custom')))
WHERE jsonb ->> 'name' = 'Canceled LCCN'
  AND id <> 'c858e4f2-2b6b-4385-842b-60532ee34abb';

INSERT INTO ${myuniversity}_${mymodule}.identifier_type (id, jsonb)
VALUES ('c858e4f2-2b6b-4385-842b-60532ee34abb',
        json_build_object('id','c858e4f2-2b6b-4385-842b-60532ee34abb', 'name', 'Canceled LCCN',  'source', 'folio'))
    ON CONFLICT (id) DO UPDATE SET jsonb=EXCLUDED.jsonb;
