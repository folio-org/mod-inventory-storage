INSERT INTO ${myuniversity}_${mymodule}.instance_format (id, jsonb)
VALUES ('0d9b1c3d-2d13-4f18-9472-cc1b91bf1752',
        json_build_object('id','0d9b1c3d-2d13-4f18-9472-cc1b91bf1752', 'code','sb', 'name','audio -- audio belt', 'source','rdacarrier'))
ON CONFLICT DO NOTHING;
