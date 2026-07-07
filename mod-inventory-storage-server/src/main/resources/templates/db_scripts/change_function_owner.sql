------------------
-- To find the owner:
--
-- SELECT p.proname, r.rolname
-- FROM pg_catalog.pg_proc p
-- JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
-- JOIN pg_catalog.pg_roles r ON p.proowner = r.oid
-- WHERE  n.nspname = 'diku_mod_kb_ebsco_java';
------------------

DO $$
DECLARE
    rec RECORD;
BEGIN
	FOR rec IN (
		SELECT 'ALTER FUNCTION '
            || quote_ident(n.nspname) || '.'
            || quote_ident(p.proname) || '('
            || pg_catalog.pg_get_function_identity_arguments(p.oid)
            || ') OWNER TO '
		        || n.nspname || ';'
    	      AS sql_statement
		  FROM   pg_catalog.pg_proc p
		  JOIN   pg_catalog.pg_namespace n ON n.oid = p.pronamespace
		  WHERE  n.nspname = '${myuniversity}_${mymodule}'
	) LOOP
		EXECUTE rec.sql_statement;
	END LOOP;
END $$ LANGUAGE plpgsql;
