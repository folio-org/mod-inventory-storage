INSERT INTO ${myuniversity}_${mymodule}.identifier_type (id, jsonb)
VALUES ('fc4e3f2a-887a-46e5-8057-aeeb271a4e56',
        json_build_object('id','fc4e3f2a-887a-46e5-8057-aeeb271a4e56', 'name', 'Cancelled system control number',  'source', 'folio'))
ON CONFLICT DO NOTHING;
