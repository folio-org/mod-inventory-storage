START TRANSACTION;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.parse_start_year(jsonb) RETURNS int AS $$
  SELECT COALESCE(
    (regexp_match($1->'publication'->0->>'dateOfPublication', '(\d{4})\w{0,2}(?:\s?-|\sand|\s?,)'))[1],
    (regexp_match($1->'publication'->0->>'dateOfPublication', '\d{4}'))[1]
  )::int;
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.parse_end_year(jsonb) RETURNS int AS $$
  SELECT COALESCE(
    (regexp_match($1->'publication'->-1->>'dateOfPublication', '(?:-\s?|and\s|,\s?)\w{0,2}(\d{4})'))[1],
    (regexp_match($1->'publication'->-1->>'dateOfPublication', '\d{4}'))[1],
    (regexp_match($1->'publication'-> 0->>'dateOfPublication', '(?:-\s?|and\s|,\s?)\w{0,2}(\d{4})'))[1],
    (regexp_match($1->'publication'-> 0->>'dateOfPublication', '\d{4}'))[1]
  )::int;
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.parse_publication_period(jsonb) RETURNS jsonb AS $$
  SELECT CASE
    WHEN $1->'publication' IS NULL THEN NULL
    WHEN jsonb_array_length($1->'publication') = 0 THEN NULL
    WHEN parse_start_year($1) IS NULL AND parse_end_year($1) IS NULL THEN NULL
    WHEN parse_start_year($1) IS NULL THEN jsonb_build_object('end', parse_end_year($1))
    WHEN parse_end_year($1) IS NULL OR parse_start_year($1) >= parse_end_year($1)
        THEN jsonb_build_object('start', parse_start_year($1))
    ELSE jsonb_build_object('start', parse_start_year($1), 'end', parse_end_year($1))
  END;
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

ALTER TABLE ${myuniversity}_${mymodule}.instance DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.instance
SET jsonb = jsonb || jsonb_build_object(
  'publicationPeriod', parse_publication_period(jsonb),
  '_version', to_jsonb(coalesce((jsonb->>'_version')::numeric + 1, 1)))
WHERE jsonb->>'publicationPeriod' IS NULL AND parse_publication_period(jsonb) IS NOT NULL;

ALTER TABLE ${myuniversity}_${mymodule}.instance ENABLE TRIGGER USER;

DROP FUNCTION
  ${myuniversity}_${mymodule}.parse_start_year(jsonb),
  ${myuniversity}_${mymodule}.parse_end_year(jsonb),
  ${myuniversity}_${mymodule}.parse_publication_period(jsonb);

END TRANSACTION;
