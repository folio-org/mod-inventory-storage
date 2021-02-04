START TRANSACTION;

UPDATE ${myuniversity}_${mymodule}.hrid_settings
  SET jsonb = (SELECT jsonb_insert(jsonb, '{commonRetainLeadingZeroes}','true', true)
               FROM ${myuniversity}_${mymodule}.hrid_settings)
where id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd';

UPDATE ${myuniversity}_${mymodule}.hrid_settings
  SET jsonb = (SELECT jsonb_set(jsonb, '{instances}', (jsonb -> ('instances'))::jsonb || '{"retainLeadingZeroes": true}'::jsonb)
               FROM ${myuniversity}_${mymodule}.hrid_settings)
where id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd';

UPDATE ${myuniversity}_${mymodule}.hrid_settings
  SET jsonb = (SELECT jsonb_set(jsonb, '{holdings}', (jsonb -> ('holdings'))::jsonb || '{"retainLeadingZeroes": true}'::jsonb)
               FROM ${myuniversity}_${mymodule}.hrid_settings)
where id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd';

UPDATE ${myuniversity}_${mymodule}.hrid_settings
  SET jsonb = (SELECT jsonb_set(jsonb, '{items}', (jsonb -> ('items'))::jsonb || '{"retainLeadingZeroes": true}'::jsonb)
               FROM ${myuniversity}_${mymodule}.hrid_settings)
where id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd';

END TRANSACTION;
