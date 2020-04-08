-- add deleted_date field to the tables
-- rename instance, holdings and item tables
-- create index on deleted date (?)
-- Create views ith the name of the corresponding tables to filter rows with no deleted date

alter table ${myuniversity}_${mymodule}.instance add "deleted_date" timestamp;
alter table ${myuniversity}_${mymodule}.item add "deleted_date" timestamp;
alter table ${myuniversity}_${mymodule}.holdings_record add "deleted_date" timestamp;
--
--create index instance_log__index on ${myuniversity}_${mymodule}.instance (deleted_date);
--
alter table ${myuniversity}_${mymodule}.instance rename to instance_log;
alter table ${myuniversity}_${mymodule}.item rename to item_log;
alter table ${myuniversity}_${mymodule}.holdings_record rename to holdings_record_log;
--
CREATE OR REPLACE VIEW instance AS SELECT * from instance_log where deleted_date IS NULL;
CREATE OR REPLACE VIEW holdings_record AS SELECT * from holdings_record_log where deleted_date IS NULL;
CREATE OR REPLACE VIEW item AS SELECT * from item_log where deleted_date IS NULL;
