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
    need_deletion BOOLEAN;
BEGIN
    -- STEP 0: Check if deletion is required
    SELECT EXISTS (
        SELECT 1
        FROM ${myuniversity}_${mymodule}.instance shared_instance
        WHERE shared_instance.jsonb ? 'source'
          AND (shared_instance.jsonb ->> 'source') ILIKE 'CONSORTIUM-%%'
          AND NOT EXISTS (
              SELECT 1
              FROM ${central}_${mymodule}.instance local_instance
              WHERE local_instance.id = shared_instance.id
              LIMIT 1
          )
          LIMIT 1
    ) INTO need_deletion;

    IF need_deletion THEN
        -- STEP 1: Disable triggers
        FOREACH trigger IN ARRAY triggers LOOP
            EXECUTE 'ALTER TABLE ${myuniversity}_${mymodule}.instance DISABLE TRIGGER '
            || trigger;
        END LOOP;

        -- STEP 2: Do deletions
        lower := '00000000-0000-0000-0000-000000000000';
        FOREACH cur IN ARRAY arr LOOP
            RAISE INFO 'range: % - %', lower, cur;
            -- Delete scripts
            EXECUTE format($q$
                DELETE FROM ${myuniversity}_${mymodule}.instance shared_instance
                WHERE shared_instance.jsonb ? 'source'
                  AND (shared_instance.jsonb ->> 'source') ILIKE 'CONSORTIUM-%%'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM  ${central}_${mymodule}.instance local_instance
                      WHERE local_instance.id = shared_instance.id
                      LIMIT 1
                  )
                  AND (id > %L AND id <= %L);
            $q$, lower, cur);

            GET DIAGNOSTICS rowcount = ROW_COUNT;
            RAISE INFO 'deleted % instances', rowcount;

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
