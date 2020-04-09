-- add deleted_date field to the tables
-- rename instance, holdings and item tables
-- create views with the name of the corresponding tables

ALTER TABLE ${myuniversity}_${mymodule}.instance ADD "deleted_date" TIMESTAMP;
ALTER TABLE ${myuniversity}_${mymodule}.item ADD "deleted_date" TIMESTAMP;
ALTER TABLE ${myuniversity}_${mymodule}.holdings_record ADD "deleted_date" TIMESTAMP;
--
ALTER TABLE ${myuniversity}_${mymodule}.instance RENAME TO instance_log;
ALTER TABLE ${myuniversity}_${mymodule}.item RENAME TO item_log;
ALTER TABLE ${myuniversity}_${mymodule}.holdings_record RENAME TO holdings_record_log;
--
CREATE OR REPLACE VIEW instance AS SELECT * FROM instance_log WHERE deleted_date IS NULL;
CREATE OR REPLACE VIEW holdings_record AS SELECT * FROM holdings_record_log WHERE deleted_date IS NULL;
CREATE OR REPLACE VIEW item AS SELECT * FROM item_log WHERE deleted_date IS NULL;
