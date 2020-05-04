-- Ensure that there will be one row only.
ALTER TABLE ${myuniversity}_${mymodule}.${table.tableName}
  ADD COLUMN IF NOT EXISTS
    lock boolean DEFAULT true UNIQUE CHECK(lock=true);
INSERT INTO ${myuniversity}_${mymodule}.${table.tableName}
  SELECT 'a501f2a8-5b31-48b2-874d-2191e48db8cd', jsonb_build_object(
    'instances', jsonb_build_object('prefix', 'in', 'startNumber', 1),
    'holdings', jsonb_build_object('prefix', 'ho', 'startNumber', 1),
    'items', jsonb_build_object('prefix', 'it', 'startNumber', 1)
  )
  ON CONFLICT DO NOTHING;

-- create initial sequences for HRID generation
CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.hrid_instances_seq
  AS INT
  INCREMENT BY 1
  START WITH 1
  MAXVALUE 99999999
  OWNED BY ${myuniversity}_${mymodule}.${table.tableName}.jsonb;
CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.hrid_holdings_seq
  AS INT
  INCREMENT BY 1
  START WITH 1
  MAXVALUE 99999999;
CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.hrid_items_seq
  AS INT
  INCREMENT BY 1
  START WITH 1
  MAXVALUE 99999999;

GRANT ALL ON SEQUENCE ${myuniversity}_${mymodule}.hrid_instances_seq TO ${myuniversity}_${mymodule};
GRANT ALL ON SEQUENCE ${myuniversity}_${mymodule}.hrid_holdings_seq TO ${myuniversity}_${mymodule};
GRANT ALL ON SEQUENCE ${myuniversity}_${mymodule}.hrid_items_seq TO ${myuniversity}_${mymodule};
