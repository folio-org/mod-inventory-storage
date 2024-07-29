INSERT INTO ${myuniversity}_${mymodule}.instance_date_type (id, jsonb)
VALUES
    ('42dac21e-3c81-4cb1-9f16-9e50c81bacc4', '{"id":"42dac21e-3c81-4cb1-9f16-9e50c81bacc4","name":"Continuing resource ceased publication","code":"d","displayFormat":{"delimiter":"-","keepDelimiter":true},"source":"folio"}'),
    ('0750f52b-3bfc-458d-9307-e9afc8bcdffa', '{"id":"0750f52b-3bfc-458d-9307-e9afc8bcdffa","name":"Continuing resource currently published","code":"c","displayFormat":{"delimiter":"-","keepDelimiter":true},"source":"folio"}'),
    ('5a1a1adb-de71-45e6-ba94-1c0838969f04', '{"id":"5a1a1adb-de71-45e6-ba94-1c0838969f04","name":"Continuing resource status unknown","code":"e","displayFormat":{"delimiter":"-","keepDelimiter":true},"source":"folio"}'),
    ('5f84208a-0aa6-4694-a58f-5310b654f012', '{"id":"5f84208a-0aa6-4694-a58f-5310b654f012","name":"Date of distribution/release/issue and production/recording session when different","code":"p","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('e77bb7ed-2e53-4c62-8b06-5907b8934ba7', '{"id":"e77bb7ed-2e53-4c62-8b06-5907b8934ba7","name":"Dates unknown","code":"n","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('9669a463-5971-42dc-9eee-046bbd678fb1', '{"id":"9669a463-5971-42dc-9eee-046bbd678fb1","name":"Detailed date","code":"e","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('6de732c5-c29b-4a10-9db0-0729ca960f12', '{"id":"6de732c5-c29b-4a10-9db0-0729ca960f12","name":"Inclusive dates of collection","code":"i","displayFormat":{"delimiter":"-","keepDelimiter":true},"source":"folio"}'),
    ('8fa6d067-41ff-4362-96a0-96b16ddce267', '{"id":"8fa6d067-41ff-4362-96a0-96b16ddce267","name":"Multiple dates","code":"m","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('6f8cd9a8-26ac-4df6-8709-62fe2c0d04f8', '{"id":"6f8cd9a8-26ac-4df6-8709-62fe2c0d04f8","name":"No attempt to code","code":"|","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('77a09c3c-37bd-4ad3-aae4-9d86fc1b33d8', '{"id":"77a09c3c-37bd-4ad3-aae4-9d86fc1b33d8","name":"No dates given; BC date involved","code":"b","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('3a4296bf-504b-451b-9355-5806f8d88253', '{"id":"3a4296bf-504b-451b-9355-5806f8d88253","name":"Publication date and copyright date","code":"t","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('4afa7d3d-e6f5-4134-9ab5-32ad377d2432', '{"id":"4afa7d3d-e6f5-4134-9ab5-32ad377d2432","name":"Questionable date","code":"q","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('ccc293d5-9e88-4222-ac04-d058351ddb7b', '{"id":"ccc293d5-9e88-4222-ac04-d058351ddb7b","name":"Range of years of bulk of collection","code":"k","displayFormat":{"delimiter":"-","keepDelimiter":true},"source":"folio"}'),
    ('47622598-61eb-4348-899a-1208275c3882', '{"id":"47622598-61eb-4348-899a-1208275c3882","name":"Reprint/reissue date and original date","code":"r","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}'),
    ('24a506e8-2a92-4ecc-bd09-ff849321fd5a', '{"id":"24a506e8-2a92-4ecc-bd09-ff849321fd5a","name":"Single known date/probable date","code":"s","displayFormat":{"delimiter":",","keepDelimiter":false},"source":"folio"}')
ON CONFLICT (id) DO UPDATE SET jsonb=EXCLUDED.jsonb;
