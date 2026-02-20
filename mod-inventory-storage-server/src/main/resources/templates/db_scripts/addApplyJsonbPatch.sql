CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.apply_jsonb_patch(
  original jsonb,
  patch jsonb
)
RETURNS jsonb AS $$
DECLARE
  k text;          -- key name from patch
  v jsonb;         -- value from patch
  result jsonb := original;
  idx int;
  arr_original jsonb;
  arr_patch jsonb;
  merged jsonb;

BEGIN
  -- If patch is not an object, replace entirely
  IF jsonb_typeof(patch) <> 'object' THEN
    RETURN patch;
  END IF;

  -- Loop through each key in the patch
  FOR k, v IN SELECT key, value FROM jsonb_each(patch)
  LOOP
    -- Handle arrays
    IF jsonb_typeof(v) = 'array' THEN
      arr_original := COALESCE(original->k, '[]'::jsonb);
      arr_patch := v;
      merged := '[]'::jsonb;

      FOR idx IN 0 .. jsonb_array_length(arr_patch) - 1 LOOP
        IF idx < jsonb_array_length(arr_original) THEN
          merged := merged || jsonb_build_array(
            apply_jsonb_patch(
              arr_original->idx,
              arr_patch->idx
            )
          );
        ELSE
          merged := merged || jsonb_build_array(arr_patch->idx);
        END IF;
      END LOOP;

      result := jsonb_set(result, ARRAY[k], merged, true);

    -- Handle nested objects
    ELSIF jsonb_typeof(v) = 'object' THEN
      result := jsonb_set(
        result,
        ARRAY[k],
        apply_jsonb_patch(result->k, v),
        true
      );

    -- Handle scalars
    ELSE
      result := jsonb_set(result, ARRAY[k], v, true);
    END IF;
  END LOOP;

  RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
