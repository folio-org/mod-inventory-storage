CREATE OR REPLACE FUNCTION change_owner (table_name varchar, owner varchar)
RETURNS VOID AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s OWNER TO %s', table_name, owner);
END;
$$
LANGUAGE plpgsql volatile;

CREATE OR REPLACE FUNCTION change_owner_all ()
RETURNS VOID AS $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN (SELECT table_schema || '.' || table_name table_name, '${myuniversity}_${mymodule}' as owner
                FROM information_schema.tables
                WHERE table_schema = '${myuniversity}_${mymodule}')
    LOOP
        EXECUTE change_owner(rec.table_name, rec.owner);
    END LOOP;
END;
$$
LANGUAGE plpgsql volatile;

DO $$ BEGIN EXECUTE change_owner_all(); END $$ language plpgsql;

DROP FUNCTION change_owner_all;
DROP FUNCTION change_owner;
