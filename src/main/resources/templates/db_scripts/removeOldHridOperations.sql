DROP TRIGGER IF EXISTS set_instance_hrid ON ${myuniversity}_${mymodule}.instance CASCADE;
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.set_instance_hrid CASCADE;
DROP SEQUENCE IF EXISTS ${myuniversity}_${mymodule}.instance_hrid_seq CASCADE;

DROP TRIGGER IF EXISTS set_holdings_record_hrid ON ${myuniversity}_${mymodule}.holdings_record CASCADE;
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.set_holdings_record_hrid CASCADE;
DROP SEQUENCE IF EXISTS ${myuniversity}_${mymodule}.holdings_record_hrid_seq CASCADE;

DROP TRIGGER IF EXISTS set_item_hrid ON ${myuniversity}_${mymodule}.item CASCADE;
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.set_item_hrid CASCADE;
DROP SEQUENCE IF EXISTS ${myuniversity}_${mymodule}.item_hrid_seq CASCADE;
