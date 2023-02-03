CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.migrate_series_and_subjects(jsonb) RETURNS jsonb AS $$
DECLARE
  instance jsonb := $1;
BEGIN
  IF jsonb_typeof(instance->'series'->0) = 'string' THEN
    instance = jsonb_set(instance, '{series}',
      (SELECT COALESCE(jsonb_agg(v), '[]')
              FROM (SELECT jsonb_build_object('value', jsonb_array_elements_text(instance->'series')) AS v) x));
  END IF;
  IF jsonb_typeof(instance->'subjects'->0) = 'string' THEN
    instance = jsonb_set(instance, '{subjects}',
      (SELECT COALESCE(jsonb_agg(v), '[]')
              FROM (SELECT jsonb_build_object('value', jsonb_array_elements_text(instance->'subjects')) AS v) x));
  END IF;
  RETURN instance;
END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE;
