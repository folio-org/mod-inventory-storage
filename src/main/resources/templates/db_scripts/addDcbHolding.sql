INSERT INTO ${myuniversity}_${mymodule}.holdings_record(id, jsonb)
  SELECT '10cd3a5a-d36f-4c7a-bc4f-e1ae3cf820c9',
    jsonb_build_object(
      'hrid', 'DCB',
      'instanceId', '9d1b77e4-f02e-4b7f-b296-3f2042ddac54',
      'sourceId', id,
      'permanentLocationId', (
        SELECT id FROM ${myuniversity}_${mymodule}.location
        WHERE jsonb ->> 'name' = 'Main Library'
        LIMIT 1
      ),
      'id', '10cd3a5a-d36f-4c7a-bc4f-e1ae3cf820c9'
    )
  FROM
    ${myuniversity}_${mymodule}.holdings_records_source
  WHERE
    jsonb ->> 'name'='FOLIO'
ON CONFLICT DO NOTHING;
