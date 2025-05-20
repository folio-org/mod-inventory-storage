-- for each tenant show the number of records of the holdings_record table that have no sourceId
-- and therefore need a sourceId migration
DO $$
  DECLARE
    s TEXT;
    n BIGINT;
  BEGIN
    RAISE INFO 'holdings records that need sourceid migration for Ramsons:';
    FOR s IN    
      SELECT schemaname
      FROM pg_catalog.pg_tables
      WHERE schemaname LIKE '%_mod_inventory_storage' AND tablename='holdings_record'
      ORDER BY schemaname
    LOOP
      EXECUTE format('SELECT count(*) FROM %I.holdings_record WHERE sourceid IS NULL', s) INTO n;
      RAISE INFO '%', format('%12s %s', n, s);
    END LOOP;
  END;
$$ LANGUAGE 'plpgsql';

