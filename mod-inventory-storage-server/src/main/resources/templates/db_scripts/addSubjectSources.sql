INSERT INTO ${myuniversity}_${mymodule}.subject_source (id, jsonb)
VALUES ('e894d0dc-621d-4b1d-98f6-6f7120eb0d40',
        json_build_object('id','e894d0dc-621d-4b1d-98f6-6f7120eb0d40', 'name', 'Library of Congress Subject Headings', 'code', 'lcsh', 'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_source (id, jsonb)
VALUES ('e894d0dc-621d-4b1d-98f6-6f7120eb0d41',
        json_build_object('id','e894d0dc-621d-4b1d-98f6-6f7120eb0d41', 'name', 'Library of Congress Children''s and Young Adults'' Subject Headings', 'code', 'cyac', 'source', 'folio'))
ON CONFLICT DO NOTHING;
INSERT INTO ${myuniversity}_${mymodule}.subject_source (id, jsonb)
VALUES ('e894d0dc-621d-4b1d-98f6-6f7120eb0d42',
        json_build_object('id','e894d0dc-621d-4b1d-98f6-6f7120eb0d42', 'name', 'Medical Subject Headings', 'code', 'mesh', 'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_source (id, jsonb)
VALUES ('e894d0dc-621d-4b1d-98f6-6f7120eb0d43',
        json_build_object('id','e894d0dc-621d-4b1d-98f6-6f7120eb0d43', 'name', 'National Agricultural Library subject authority file', 'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_source (id, jsonb)
VALUES ('e894d0dc-621d-4b1d-98f6-6f7120eb0d44',
        json_build_object('id','e894d0dc-621d-4b1d-98f6-6f7120eb0d44', 'name', 'Source not specified', 'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_source (id, jsonb)
VALUES ('e894d0dc-621d-4b1d-98f6-6f7120eb0d45',
        json_build_object('id','e894d0dc-621d-4b1d-98f6-6f7120eb0d45', 'name', 'Canadian Subject Headings', 'code', 'cash',  'source', 'folio'))
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.subject_source (id, jsonb)
VALUES ('e894d0dc-621d-4b1d-98f6-6f7120eb0d46',
        json_build_object('id','e894d0dc-621d-4b1d-98f6-6f7120eb0d46', 'name', 'Répertoire de vedettes-matière', 'code', 'rvm',  'source', 'folio'))
ON CONFLICT DO NOTHING;
