UPDATE ${myuniversity}_${mymodule}.hrid_settings
	SET creation_date = NOW()
WHERE creation_date IS NULL;

UPDATE ${myuniversity}_${mymodule}.hrid_settings
	SET created_by = '00000000-0000-0000-0000-000000000000'
WHERE created_by IS NULL;

UPDATE ${myuniversity}_${mymodule}.hrid_settings
	SET jsonb = jsonb_set(jsonb, '{metadata}', jsonb_build_object(
  'createdDate', to_jsonb(to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
  'updatedDate', to_jsonb(to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
  'createdByUserId', '00000000-0000-0000-0000-000000000000',
  'updatedByUserId', '00000000-0000-0000-0000-000000000000'
  ))
WHERE jsonb -> 'metadata' IS NULL;
