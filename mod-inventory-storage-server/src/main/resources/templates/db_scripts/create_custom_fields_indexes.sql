CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.check_values_and_get_tablename(jsonb_data jsonb)
RETURNS TEXT
AS $$
DECLARE
    table_name TEXT;
BEGIN
    -- Check if the required keys are present in the jsonb column
    IF (jsonb_data->>'refId') IS NULL
      OR (jsonb_data->>'type') IS NULL
      OR (jsonb_data->>'entityType') IS NULL THEN
          RAISE EXCEPTION 'One or more required keys (refId, type, entityType) are missing in the jsonb column';
          RETURN NULL;
    ELSE
        -- Set the tableName variable based on the entityType value
        IF (jsonb_data->>'entityType') = 'item' THEN
            table_name := 'item';
        ELSE
            RAISE EXCEPTION 'Invalid entityType value: %', jsonb_data->>'entityType';
            RETURN NULL;
        END IF;
    END IF;

    RETURN table_name;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.create_custom_fields_indexes(jsonb_data jsonb)
RETURNS VOID AS $$
DECLARE
    index_name TEXT;
    schema_name TEXT;
    table_name TEXT;
    ref_id TEXT;
BEGIN
    table_name := ${myuniversity}_${mymodule}.check_values_and_get_tablename(jsonb_data);
    ref_id := jsonb_data->>'refId';
    schema_name := '${myuniversity}_${mymodule}';
    index_name := table_name || '_custom_fields_' || ref_id || '_idx_gin';

    -- create gin index for custom field
    EXECUTE FORMAT(
      'CREATE INDEX IF NOT EXISTS %I ON %I.%I USING GIN (lower(%I.f_unaccent(jsonb->''customFields''->>''%s'')) public.gin_trgm_ops)',
      index_name,
      schema_name,
      table_name,
      schema_name,
      ref_id
    );

    -- create another btree index if custom field is of type DATE_PICKER
    IF (jsonb_data->>'type' = 'DATE_PICKER') THEN
        index_name := table_name || '_custom_fields_' || ref_id || '_idx';
        EXECUTE FORMAT(
          'CREATE INDEX IF NOT EXISTS %I ON %I.%I USING BTREE ((jsonb->''customFields''->>''%s''))',
          index_name,
          schema_name,
          table_name,
          ref_id
        );
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.drop_custom_fields_indexes(jsonb_data jsonb)
RETURNS VOID AS $$
DECLARE
    index_name TEXT;
    index_name2 TEXT;
    schema_name TEXT;
    table_name TEXT;
    ref_id TEXT;
BEGIN
    table_name := ${myuniversity}_${mymodule}.check_values_and_get_tablename(jsonb_data);
    ref_id := jsonb_data->>'refId';
    schema_name := '${myuniversity}_${mymodule}';
    index_name := table_name || '_custom_fields_' || ref_id || '_idx_gin';
    index_name2 := table_name || '_custom_fields_' || ref_id || '_idx';

    -- drop gin and btree index if they exist
    EXECUTE FORMAT('DROP INDEX IF EXISTS %I.%I', schema_name, index_name);
    EXECUTE FORMAT('DROP INDEX IF EXISTS %I.%I', schema_name, index_name2);
END;
$$ LANGUAGE plpgsql;

-- create trigger functions
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.custom_fields_create_idx()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM ${myuniversity}_${mymodule}.create_custom_fields_indexes(NEW.jsonb);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql
SECURITY DEFINER;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.custom_fields_drop_idx()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM ${myuniversity}_${mymodule}.drop_custom_fields_indexes(OLD.jsonb);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql
SECURITY DEFINER;

-- create triggers
DROP TRIGGER IF EXISTS trigger_custom_fields_insert ON ${myuniversity}_${mymodule}.custom_fields;
CREATE TRIGGER trigger_custom_fields_insert
AFTER INSERT ON ${myuniversity}_${mymodule}.custom_fields
FOR EACH ROW
EXECUTE PROCEDURE ${myuniversity}_${mymodule}.custom_fields_create_idx();

DROP TRIGGER IF EXISTS trigger_custom_fields_delete ON ${myuniversity}_${mymodule}.custom_fields;
CREATE TRIGGER trigger_custom_fields_delete
AFTER DELETE ON ${myuniversity}_${mymodule}.custom_fields
FOR EACH ROW
EXECUTE PROCEDURE ${myuniversity}_${mymodule}.custom_fields_drop_idx();

-- create indexes for existing custom fields if they do not exist
SELECT ${myuniversity}_${mymodule}.create_custom_fields_indexes(jsonb) FROM ${myuniversity}_${mymodule}.custom_fields;
