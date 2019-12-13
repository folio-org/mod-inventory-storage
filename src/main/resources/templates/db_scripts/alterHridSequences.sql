-- Increase the initial size of the HRID sequences

ALTER SEQUENCE IF EXISTS ${myuniversity}_${mymodule}.hrid_instances_seq
  AS BIGINT MAXVALUE 99999999999
  OWNED BY ${myuniversity}_${mymodule}.hrid_settings.jsonb;

ALTER SEQUENCE IF EXISTS ${myuniversity}_${mymodule}.hrid_holdings_seq
  AS BIGINT MAXVALUE 99999999999
  OWNED BY ${myuniversity}_${mymodule}.hrid_settings.jsonb;

ALTER SEQUENCE IF EXISTS ${myuniversity}_${mymodule}.hrid_items_seq
  AS BIGINT MAXVALUE 99999999999
  OWNED BY ${myuniversity}_${mymodule}.hrid_settings.jsonb;
