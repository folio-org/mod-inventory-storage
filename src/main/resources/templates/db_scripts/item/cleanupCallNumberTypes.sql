-- This script cleans up references to call number types in the item table.
DO $$
    BEGIN
        -- Check if both tables exist
        IF EXISTS (
            SELECT 1
            FROM pg_catalog.pg_tables
            WHERE schemaname = '${myuniversity}_${mymodule}'
              AND tablename = 'item'
        ) AND EXISTS (
            SELECT 1
            FROM pg_catalog.pg_tables
            WHERE schemaname = '${myuniversity}_${mymodule}'
              AND tablename = 'call_number_type'
        ) THEN
            -- Execute the script
            -- 1. Clean up itemLevelCallNumberTypeId references
            UPDATE ${myuniversity}_${mymodule}.item
            SET jsonb = jsonb - 'itemLevelCallNumberTypeId'
            WHERE jsonb ? 'itemLevelCallNumberTypeId'
              AND jsonb->>'itemLevelCallNumberTypeId' NOT IN (
                SELECT id::text FROM ${myuniversity}_${mymodule}.call_number_type
            );

            -- 2. Clean up effectiveCallNumberComponents.typeId references
            UPDATE ${myuniversity}_${mymodule}.item
            SET jsonb = jsonb #- '{effectiveCallNumberComponents,typeId}'
            WHERE jsonb ? 'effectiveCallNumberComponents'
              AND jsonb->'effectiveCallNumberComponents' ? 'typeId'
              AND jsonb->'effectiveCallNumberComponents'->>'typeId' NOT IN (
                SELECT id::text FROM ${myuniversity}_${mymodule}.call_number_type
            );

            -- 3. Remove empty effectiveCallNumberComponents object if it becomes empty
            UPDATE ${myuniversity}_${mymodule}.item
            SET jsonb = jsonb - 'effectiveCallNumberComponents'
            WHERE jsonb ? 'effectiveCallNumberComponents'
              AND jsonb->'effectiveCallNumberComponents' = '{}'::jsonb;
        END IF;
    END $$;