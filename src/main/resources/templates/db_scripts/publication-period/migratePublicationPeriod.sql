CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.migrate_publication_period(
    jsonb_data jsonb
) RETURNS jsonb AS $$
DECLARE
    pub_period jsonb;
    start_date text;
    end_date text;
    date_type_id text;
    dates jsonb := '{}'::jsonb;
BEGIN
    pub_period := jsonb_data -> 'publicationPeriod';
    start_date := pub_period ->> 'start';
    end_date := pub_period ->> 'end';

    -- Determine Date type
    IF (start_date IS NOT NULL AND end_date IS NOT NULL) THEN
        date_type_id := '8fa6d067-41ff-4362-96a0-96b16ddce267';
    ELSIF (start_date IS NOT NULL OR end_date IS NOT NULL) THEN
        date_type_id := '24a506e8-2a92-4ecc-bd09-ff849321fd5a';
    ELSE
        RETURN jsonb_data;
    END IF;

    -- Build the JSONB Dates object
    IF start_date IS NOT NULL THEN
        dates := jsonb_set(dates, '{date1}', to_jsonb(substring(start_date FROM 1 FOR 4)));
    END IF;
    IF end_date IS NOT NULL THEN
        dates := jsonb_set(dates, '{date2}', to_jsonb(substring(end_date FROM 1 FOR 4)));
    END IF;
    dates := jsonb_set(dates, '{dateTypeId}', to_jsonb(date_type_id));

    -- Set the dates into jsonb
    jsonb_data := jsonb_set(jsonb_data, '{dates}', dates);

    -- Remove publicationPeriod from jsonb
    jsonb_data := jsonb_data - 'publicationPeriod';

    RETURN jsonb_data;
END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


-- Migration Script
DO
$$
DECLARE
    trigger VARCHAR;
    triggers      VARCHAR[] DEFAULT ARRAY[
    'audit_instance',
    'check_subject_references_on_insert_or_update',
    'instance_check_statistical_code_references_on_insert',
    'instance_check_statistical_code_references_on_update',
    'set_id_in_jsonb',
    'set_instance_md_json_trigger',
    'set_instance_md_trigger',
    'set_instance_ol_version_trigger',
    'set_instance_sourcerecordformat',
    'set_instance_status_updated_date',
    'update_instance_references',
    'updatecompleteupdateddate_instance'];
    arr          UUID[] DEFAULT ARRAY[
        '10000000-0000-0000-0000-000000000000',
        '20000000-0000-0000-0000-000000000000',
        '30000000-0000-0000-0000-000000000000',
        '40000000-0000-0000-0000-000000000000',
        '50000000-0000-0000-0000-000000000000',
        '60000000-0000-0000-0000-000000000000',
        '70000000-0000-0000-0000-000000000000',
        '80000000-0000-0000-0000-000000000000',
        '90000000-0000-0000-0000-000000000000',
        'a0000000-0000-0000-0000-000000000000',
        'b0000000-0000-0000-0000-000000000000',
        'c0000000-0000-0000-0000-000000000000',
        'd0000000-0000-0000-0000-000000000000',
        'e0000000-0000-0000-0000-000000000000',
        'f0000000-0000-0000-0000-000000000000',
        'ffffffff-ffff-ffff-ffff-ffffffffffff'
    ];
    lower          UUID;
    cur            UUID;
    rowcount       BIGINT;
    need_migration BOOLEAN;
BEGIN
    -- STEP 0: Check if migration is required
    SELECT EXISTS (
        SELECT 1
        FROM ${myuniversity}_${mymodule}.instance
        WHERE jsonb ? 'publicationPeriod'
        LIMIT 1
    ) INTO need_migration;

    IF need_migration THEN
        -- STEP 1: Disable triggers
        FOREACH trigger IN ARRAY triggers LOOP
            EXECUTE 'ALTER TABLE ${myuniversity}_${mymodule}.instance DISABLE TRIGGER '
            || trigger;
        END LOOP;

        -- STEP 2: Do updates
        lower := '00000000-0000-0000-0000-000000000000';
        FOREACH cur IN ARRAY arr LOOP
            RAISE INFO 'range: % - %', lower, cur;
            -- Update scripts
            EXECUTE format($q$
                UPDATE ${myuniversity}_${mymodule}.instance
                SET jsonb = ${myuniversity}_${mymodule}.migrate_publication_period(jsonb)
                WHERE (jsonb -> 'publicationPeriod' IS NOT NULL)
                AND (id > %L AND id <= %L);
            $q$, lower, cur);

            GET DIAGNOSTICS rowcount = ROW_COUNT;
            RAISE INFO 'updated % instances', rowcount;

            lower := cur;
        END LOOP;

        -- STEP 3: Enable triggers
        FOREACH trigger IN ARRAY triggers LOOP
            EXECUTE 'ALTER TABLE ${myuniversity}_${mymodule}.instance ENABLE TRIGGER '
            || trigger;
        END LOOP;
    END IF;
END;
$$ LANGUAGE 'plpgsql';

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.migrate_publication_period(jsonb);
