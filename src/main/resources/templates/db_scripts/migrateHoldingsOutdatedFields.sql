do
$$
  declare
    trigger        varchar;
    triggers       varchar[] default array [
      'audit_holdings_record',
      'set_holdings_record_md_json_trigger',
      'set_holdings_record_md_trigger',
      'set_holdings_record_ol_version_trigger',
      'set_id_in_jsonb',
      'update_holdings_record_references',
      'updatecompleteupdateddate_holdings_record_delete',
      'updatecompleteupdateddate_holdings_record_insert_update'
      ];
    arr            uuid[] default array [
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
    lower          uuid;
    cur            uuid;
    rowcount       bigint;
    need_migration boolean;
  begin
    -- STEP 0 Check if migration is required
    select exists(SELECT id
                  from ${myuniversity}_${mymodule}.holdings_record
                  WHERE jsonb ? 'permanentLocation'
                     OR jsonb ? 'illPolicy'
                     OR jsonb ? 'holdingsItems'
                     OR jsonb ? 'bareHoldingsItems'
                     OR jsonb ? 'holdingsInstance'
                  limit 1)
    into need_migration;

    if need_migration then
      -- STEP 1 disable triggers
      foreach trigger in array triggers
        loop
          execute 'ALTER TABLE ${myuniversity}_${mymodule}.holdings_record '
                    || 'DISABLE TRIGGER ' || trigger;
        end loop;

      -- STEP 2 do updates
      lower = '00000000-0000-0000-0000-000000000000';
      foreach cur in array arr
        loop

          raise info 'range: % - %', lower, cur;
          -- Update scripts
          execute format($q$ UPDATE ${myuniversity}_${mymodule}.holdings_record
                                            SET jsonb=jsonb - 'permanentLocation' - 'illPolicy' - 'holdingsItems' - 'bareHoldingsItems' -
                                                      'holdingsInstance'
                                            WHERE (jsonb ? 'permanentLocation'
                                                OR jsonb ? 'illPolicy'
                                                OR jsonb ? 'holdingsItems'
                                                OR jsonb ? 'bareHoldingsItems'
                                                OR jsonb ? 'holdingsInstance')

                                                and (id > %L and id <= %L);$q$
            , lower, cur);

          GET DIAGNOSTICS rowcount = ROW_COUNT;
          raise info 'updated % records', rowcount;

          lower = cur;
        end loop;

      -- STEP 3 enable triggers
      foreach trigger in array triggers
        loop
          execute 'ALTER TABLE ${myuniversity}_${mymodule}.holdings_record '
                    || 'ENABLE TRIGGER ' || trigger;
        end loop;

    end if;
  end;

$$ LANGUAGE 'plpgsql';
