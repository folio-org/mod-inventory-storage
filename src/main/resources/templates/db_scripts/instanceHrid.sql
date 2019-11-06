-- Generates an eight digit HRID prefixed with 'in' for new instance records, starting with in00000001.
-- Ignores the value in 'hrid' -- if any -- in the instance POSTed by the client
CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.instance_hrid_seq
  INCREMENT BY 1
  START WITH 1
  OWNED BY ${myuniversity}_${mymodule}.instance.jsonb;

GRANT ALL ON SEQUENCE ${myuniversity}_${mymodule}.instance_hrid_seq TO ${myuniversity}_${mymodule};

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_instance_hrid()
  RETURNS TRIGGER AS $$
  DECLARE
    injectedProp text;
  BEGIN
    injectedProp = '"in' || LPAD(nextval('${myuniversity}_${mymodule}.instance_hrid_seq')::text, 8, '0') ||'"';
    NEW.jsonb := jsonb_set(NEW.jsonb, '{hrid}', injectedProp::jsonb);
    RETURN NEW;
  END;
  $$ language 'plpgsql';

DROP TRIGGER IF EXISTS set_instance_hrid ON ${myuniversity}_${mymodule}.instance CASCADE;
CREATE TRIGGER set_instance_hrid
  BEFORE INSERT ON ${myuniversity}_${mymodule}.instance
  FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.set_instance_hrid();

