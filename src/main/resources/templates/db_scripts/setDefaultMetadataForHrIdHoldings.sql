UPDATE ${myuniversity}_${mymodule}.hrid_settings
	SET jsonb = jsonb_set(jsonb, '{metadata}', jsonb_build_object(
  'createdDate', to_jsonb(to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
  'updatedDate', to_jsonb(to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'))
  ))
WHERE jsonb -> 'metadata' IS NULL;
