CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_effective_location_on_item_update()
    RETURNS void
    LANGUAGE sql
    as $$
	    SELECT * FROM pg_proc;
    $$;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_effective_location_on_holding_update()
    RETURNS void
    LANGUAGE sql
    as $$
	    SELECT * FROM pg_proc;
    $$;
