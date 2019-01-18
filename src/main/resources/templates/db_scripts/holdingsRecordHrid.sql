-- Generates an eight digit HRID prefixed with 'ho' for new holdings_record records, starting with ho00000001.
-- Ignores the value in 'hrid' -- if any -- in the holdingsRecord POSTed by the client 
CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.holdings_record_hrid_seq
  INCREMENT BY 1
  START WITH 1
  OWNED BY ${myuniversity}_${mymodule}.holdings_record.jsonb;

GRANT ALL ON SEQUENCE ${myuniversity}_${mymodule}.holdings_record_hrid_seq TO ${myuniversity}_${mymodule};

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_holdings_record_hrid()
  RETURNS TRIGGER AS $$
  DECLARE
    injectedProp text;
  BEGIN
    injectedProp = '"ho' || LPAD(nextval('${myuniversity}_${mymodule}.holdings_record_hrid_seq')::text, 8, '0') ||'"';
    NEW.jsonb := jsonb_set(NEW.jsonb, '{hrid}', injectedProp::jsonb);
    RETURN NEW;
  END;
  $$ language 'plpgsql';

DROP TRIGGER IF EXISTS set_holdings_record_hrid ON ${myuniversity}_${mymodule}.holdings_record CASCADE;
CREATE TRIGGER set_holdings_record_hrid
  BEFORE INSERT ON ${myuniversity}_${mymodule}.holdings_record
  FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.set_holdings_record_hrid();


