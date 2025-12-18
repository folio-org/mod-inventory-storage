DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.parse_start_year(jsonb) CASCADE;
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.parse_end_year(jsonb) CASCADE;
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.parse_publication_period(jsonb) CASCADE;

DROP INDEX IF EXISTS ${myuniversity}_${mymodule}.instance_publication_period;
