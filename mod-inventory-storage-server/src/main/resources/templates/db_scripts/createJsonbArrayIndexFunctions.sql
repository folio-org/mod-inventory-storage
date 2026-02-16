-- Convert text to lowercase and remove accents for consistent indexing
-- Usage: normalize_token('Sømé Téxt wîth Åcçéñts') -> 'some text with accents'
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_token(input_text text)
  RETURNS text AS
$$
SELECT lower(${myuniversity}_${mymodule}.f_unaccent(input_text))
$$ LANGUAGE sql IMMUTABLE
                PARALLEL SAFE;

-- Normalize each element in a JSONB array of strings and aggregate back into a JSONB array
-- Usage: normalize_jsonb_array('["Sømé", "Téxt", "wîth", "Åcçéñts"]'::jsonb) -> '["some", "text", "with", "accents"]'
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_jsonb_array(input_array jsonb)
  RETURNS jsonb AS
$$
SELECT coalesce(jsonb_agg(${myuniversity}_${mymodule}.normalize_token(val)), '[]' ::jsonb)
FROM jsonb_array_elements_text(input_array) AS val
$$ LANGUAGE sql IMMUTABLE
                PARALLEL SAFE;

-- Extract values from a JSONB array of objects based on specified keys and aggregate into a JSONB array
-- Usage: extract_values('{"items": [{"name": "Item1", "value": "A"}, {"name": "Item2", "value": "B"}]}'::jsonb, 'items', 'value') -> '["A", "B"]'
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.extract_values(input_jsonb jsonb, array_key text, value_key text)
  RETURNS jsonb AS
$$
SELECT coalesce(jsonb_agg(elem -> value_key), '[]'::jsonb)
FROM jsonb_array_elements(input_jsonb -> array_key) AS elem
$$ LANGUAGE sql IMMUTABLE
                PARALLEL SAFE;
