DO $$
BEGIN
  -- succeeds and does nothing if foreign key already exists
  ALTER TABLE ${myuniversity}_${mymodule}.${table.tableName}
    ALTER CONSTRAINT ${table.tableName}_id_fkey;
EXCEPTION WHEN undefined_object THEN
  ALTER TABLE ${myuniversity}_${mymodule}.${table.tableName}
    ADD FOREIGN KEY (id) REFERENCES ${myuniversity}_${mymodule}.instance ON DELETE CASCADE;
END;
$$ language 'plpgsql';

-- Trigger: If instance changes then enforce a correct value in instance.jsonb->sourceRecordFormat
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_instance_sourceRecordFormat()
  RETURNS TRIGGER AS $$
  BEGIN
    CASE TG_OP
    WHEN 'INSERT' THEN
      -- a newly inserted instance cannot have a source record because of foreign key relationship
      NEW.jsonb := NEW.jsonb - 'sourceRecordFormat';
    ELSE
      NEW.jsonb := CASE (SELECT count(*) FROM ${myuniversity}_${mymodule}.instance_source_marc WHERE id=NEW.id)
                   WHEN 0 THEN NEW.jsonb - 'sourceRecordFormat'
                   ELSE jsonb_set(NEW.jsonb, '{sourceRecordFormat}', '"MARC-JSON"')
                   END;
    END CASE;
    RETURN NEW;
  END;
  $$ language 'plpgsql';
DROP TRIGGER IF EXISTS set_instance_sourceRecordFormat ON ${myuniversity}_${mymodule}.instance CASCADE;
CREATE TRIGGER set_instance_sourceRecordFormat
  BEFORE INSERT OR UPDATE ON ${myuniversity}_${mymodule}.instance
  FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.set_instance_sourceRecordFormat();

-- Trigger: If instance_source_marc changes then update instance.jsonb->sourceRecordFormat
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_${table.tableName}()
  RETURNS TRIGGER AS $$
  BEGIN
    IF (TG_OP = 'DELETE') THEN
      UPDATE ${myuniversity}_${mymodule}.instance
        SET jsonb = jsonb - 'sourceRecordFormat'
        WHERE id = OLD.id;
    ELSE
      UPDATE ${myuniversity}_${mymodule}.instance
        SET jsonb = jsonb_set(jsonb, '{sourceRecordFormat}', '"MARC-JSON"')
        WHERE id = NEW.id;
    END IF;
    RETURN NULL;
  END;
  $$ language 'plpgsql';
DROP TRIGGER IF EXISTS update_${table.tableName} ON ${myuniversity}_${mymodule}.${table.tableName} CASCADE;
CREATE TRIGGER update_${table.tableName}
  AFTER DELETE OR INSERT OR UPDATE ON ${myuniversity}_${mymodule}.${table.tableName}
  FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.update_${table.tableName}();
