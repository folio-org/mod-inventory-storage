CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.to_timestamp(text)
  RETURNS timestamp AS $$
BEGIN
  -- We assume the input text is always in a format (like ISO 8601)
  -- that casts consistently to timestamp. This function turns the
  -- ::timestamp cast into an IMMUTABLE function, so that it can be
  -- used in indexes
  RETURN $1::timestamp;
EXCEPTION WHEN others THEN
  RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
