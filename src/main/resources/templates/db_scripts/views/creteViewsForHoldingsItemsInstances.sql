CREATE OR REPLACE VIEW holdings_record AS SELECT * from holdings_record_log where deletedDate IS NULL;
CREATE OR REPLACE VIEW instance AS SELECT * from instance_log where deletedDate IS NULL;
CREATE OR REPLACE VIEW item AS SELECT * from item_log where deletedDate IS NULL;
