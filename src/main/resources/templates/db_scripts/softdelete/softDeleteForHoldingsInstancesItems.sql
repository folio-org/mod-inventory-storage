-- add deleted_date field to the tables
-- create indexees on deleted date
-- rename instance, holdings and item tables
-- create views with the name of the corresponding tables

ALTER TABLE ${myuniversity}_${mymodule}.instance ADD "deleted_date" TIMESTAMP;
ALTER TABLE ${myuniversity}_${mymodule}.item ADD "deleted_date" TIMESTAMP;
ALTER TABLE ${myuniversity}_${mymodule}.holdings_record ADD "deleted_date" TIMESTAMP;
--
CREATE INDEX instance_deleted_date__index ON ${myuniversity}_${mymodule}.instance (deleted_date);
CREATE INDEX item_deleted_date__index ON ${myuniversity}_${mymodule}.item (deleted_date);
CREATE INDEX holdings_record_deleted_date__index ON ${myuniversity}_${mymodule}.holdings_record (deleted_date);
--
ALTER TABLE ${myuniversity}_${mymodule}.instance RENAME TO instance_log;
ALTER TABLE ${myuniversity}_${mymodule}.item RENAME TO item_log;
ALTER TABLE ${myuniversity}_${mymodule}.holdings_record RENAME TO holdings_record_log;
--
CREATE OR REPLACE VIEW instance AS SELECT * FROM instance_log WHERE deleted_date IS NULL;
CREATE OR REPLACE VIEW holdings_record AS SELECT * FROM holdings_record_log WHERE deleted_date IS NULL;
CREATE OR REPLACE VIEW item AS SELECT * FROM item_log WHERE deleted_date IS NULL;
