INSERT INTO ${myuniversity}_${mymodule}.identifier_type (id, jsonb)
VALUES ('c858e4f2-2b6b-4385-842b-60532ee34abb',
        json_build_object('id','c858e4f2-2b6b-4385-842b-60532ee34abb', 'name', 'Cancelled LCCN',  'source', 'folio'))
  ON CONFLICT DO NOTHING;
