INSERT INTO ${myuniversity}_${mymodule}.instance (id, jsonb)
  SELECT '9d1b77e4-f02e-4b7f-b296-3f2042ddac54',
    jsonb_build_object (
      'source', 'FOLIO',
      'title', 'DCBInstance',
      'instanceTypeId', id,
      'id', '9d1b77e4-f02e-4b7f-b296-3f2042ddac54'
  )
  FROM
    ${myuniversity}_${mymodule}.instance_type
  WHERE
    jsonb ->> 'name' = 'other'
ON CONFLICT DO NOTHING;
