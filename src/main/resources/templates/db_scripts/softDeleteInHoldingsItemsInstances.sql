-- add deleted_date field to the tables
-- Rename tables
-- Create views with same name

-- alter table ${myuniversity}_${mymodule}.item rename to item_log;
-- alter table ${myuniversity}_${mymodule}.instance rename to instance_log;
-- alter table ${myuniversity}_${mymodule}.holdings_record rename to holdings_record_log;
---
alter table ${myuniversity}_${mymodule}.instance_log add "deleted_date" timestamp;
alter table ${myuniversity}_${mymodule}.item_log add "deleted_date" timestamp;
alter table ${myuniversity}_${mymodule}.holdings_record_log add "deleted_date" timestamp;
--
CREATE OR REPLACE VIEW holdings_record AS SELECT * from holdings_record_log where deleted_date IS NULL;
CREATE OR REPLACE VIEW instance AS SELECT * from instance_log where deleted_date IS NULL;
CREATE OR REPLACE VIEW item AS SELECT * from item_log where deleted_date IS NULL;
