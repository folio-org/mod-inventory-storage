INSERT INTO ${myuniversity}_${mymodule}.identifier_type (id, jsonb)
VALUES ('b3ea81fb-3324-4c64-9efc-7c0c93d5943c',
        json_build_object('id','b3ea81fb-3324-4c64-9efc-7c0c93d5943c', 'name', 'Invalid UPC',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.identifier_type (id, jsonb)
VALUES ('ebfd00b6-61d3-4d87-a6d8-810c941176d5',
        json_build_object('id','ebfd00b6-61d3-4d87-a6d8-810c941176d5', 'name', 'ISMN',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.identifier_type (id, jsonb)
VALUES ('4f07ea37-6c7f-4836-add2-14249e628ed1',
        json_build_object('id','4f07ea37-6c7f-4836-add2-14249e628ed1', 'name', 'Invalid ISMN',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.identifier_type (id, jsonb)
VALUES ('1795ea23-6856-48a5-a772-f356e16a8a6c',
        json_build_object('id','1795ea23-6856-48a5-a772-f356e16a8a6c', 'name', 'UPC',  'source', 'folio'))
ON CONFLICT DO NOTHING;
