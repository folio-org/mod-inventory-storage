INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b1',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b1', 'name', 'Personal name',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b2',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b2', 'name', 'Corporate name',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b3',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b3', 'name', 'Meeting name',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b4',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b4', 'name', 'Uniform title',  'source', 'folio'))
ON CONFLICT DO NOTHING;
INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b5',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b5', 'name', 'Named event',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b6',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b6', 'name', 'Chronological term',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b7',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b7', 'name', 'Topical term',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b8',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b8', 'name', 'Geographic name',  'source', 'folio'))
ON CONFLICT DO NOTHING;
INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff5b9',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b9', 'name', 'Uncontrolled',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff510',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff510', 'name', 'Faceted topical terms',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff511',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff511', 'name', 'Genre/form',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff512',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff512', 'name', 'Occupation',  'source', 'folio'))
ON CONFLICT DO NOTHING;
INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff513',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff5b13', 'name', 'Function',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff514',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff514', 'name', 'Curriculum objective',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff515',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff515', 'name', 'Hierarchical place name',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_type (id, jsonb)
VALUES ('d6488f88-1e74-40ce-81b5-b19a928ff516',
        json_build_object('id','d6488f88-1e74-40ce-81b5-b19a928ff516', 'name', 'Type of entity unspecified',  'source', 'folio'))
ON CONFLICT DO NOTHING;
