UPDATE ${myuniversity}_${mymodule}.ill_policy
SET jsonb = jsonb_set(jsonb, '{name}', '"Will not lend"')
WHERE id = 'b0f97013-87f5-4bab-87f2-ac4a5191b489';
