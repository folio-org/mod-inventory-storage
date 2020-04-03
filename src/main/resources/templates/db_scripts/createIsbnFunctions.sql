-- Usage: SELECT normalize_isbns(jsonb->'identifiers') FROM instance
-- This takes each ISBN, normalizes it using RMB's normalize_digits(text),
-- and concatenates the results using a space as separator.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_isbns(jsonb_array jsonb) RETURNS text AS $$
  SELECT string_agg(${myuniversity}_${mymodule}.normalize_digits(identifier->>'value'), ' ')
  FROM jsonb_array_elements($1) as identifier
  WHERE identifier->>'identifierTypeId' = '8261054f-be78-422d-bd51-4ed9f33c3422';
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- Usage: SELECT normalize_invalid_isbns(jsonb->'identifiers') FROM instance
-- This takes each invalid ISBN, normalizes it using RMB's normalize_digits(text),
-- and concatenates the results using a space as separator.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_invalid_isbns(jsonb_array jsonb) RETURNS text AS $$
  SELECT string_agg(${myuniversity}_${mymodule}.normalize_digits(identifier->>'value'), ' ')
  FROM jsonb_array_elements($1) as identifier
  WHERE identifier->>'identifierTypeId' = 'fcca2643-406a-482a-b760-7a7f8aec640e';
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;
