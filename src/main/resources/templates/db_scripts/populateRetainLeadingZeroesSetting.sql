START TRANSACTION;

UPDATE ${myuniversity}_${mymodule}.hrid_settings
  SET jsonb = (SELECT jsonb_insert(jsonb, '{commonRetainLeadingZeroes}','true', true)
               FROM ${myuniversity}_${mymodule}.hrid_settings
               WHERE id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd')
WHERE id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd'
     AND (SELECT jsonb::json->'commonRetainLeadingZeroes'
      FROM ${myuniversity}_${mymodule}.hrid_settings WHERE id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd') IS NULL;

END TRANSACTION;
