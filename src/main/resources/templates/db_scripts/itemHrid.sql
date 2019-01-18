-- Generates an eight digit HRID prefixed with 'it' for new item records, starting with it00000001.
-- Ignores the value in 'hrid' -- if any -- in the item POSTed by the client 
CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.item_hrid_seq
  INCREMENT BY 1
  START WITH 1
  OWNED BY ${myuniversity}_${mymodule}.item.jsonb;

GRANT ALL ON SEQUENCE ${myuniversity}_${mymodule}.item_hrid_seq TO ${myuniversity}_${mymodule};

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_item_hrid()
  RETURNS TRIGGER AS $$
  DECLARE
    injectedProp text;
  BEGIN
    injectedProp = '"it' || LPAD(nextval('${myuniversity}_${mymodule}.item_hrid_seq')::text, 8, '0') ||'"';
    NEW.jsonb := jsonb_set(NEW.jsonb, '{hrid}', injectedProp::jsonb);
    RETURN NEW;
  END;
  $$ language 'plpgsql';

DROP TRIGGER IF EXISTS set_item_hrid ON ${myuniversity}_${mymodule}.item CASCADE;
CREATE TRIGGER set_item_hrid
  BEFORE INSERT ON ${myuniversity}_${mymodule}.item
  FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.set_item_hrid();



