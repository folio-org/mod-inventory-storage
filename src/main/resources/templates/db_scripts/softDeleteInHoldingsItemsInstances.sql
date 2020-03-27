-- add deleted_date field to the tables
-- Rename tables
-- Create views with same name

alter table ${myuniversity}_${mymodule}.instance add "deleted_date" timestamp;
alter table ${myuniversity}_${mymodule}.item add "deleted_date" timestamp;
alter table ${myuniversity}_${mymodule}.holding_record add "deleted_date" timestamp;

alter table ${myuniversity}_${mymodule}.item rename to item_log;
alter table ${myuniversity}_${mymodule}.instance rename to instance_log;
alter table ${myuniversity}_${mymodule}.holding_record rename to holding_record_log;

CREATE OR REPLACE VIEW holdings_record AS SELECT * from holdings_record_log where deleted_date IS NULL;
CREATE OR REPLACE VIEW instance AS SELECT * from instance_log where deleted_date IS NULL;
CREATE OR REPLACE VIEW item AS SELECT * from item_log where deleted_date IS NULL;
