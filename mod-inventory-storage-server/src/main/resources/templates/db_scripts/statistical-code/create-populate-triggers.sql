-- Create the function for instance
CREATE OR REPLACE FUNCTION update_instance_statistical_code()
    RETURNS trigger AS
$$
BEGIN
    -- Delete existing entries for the instance
    IF (TG_OP = 'UPDATE') THEN
        DELETE
        FROM ${myuniversity}_${mymodule}.instance_statistical_code
        WHERE instance_id = OLD.id;
    END IF;

    -- Insert new entries for the instance
    INSERT INTO ${myuniversity}_${mymodule}.instance_statistical_code (instance_id, statistical_code_id)
    SELECT NEW.id, jsonb_array_elements_text(NEW.jsonb->'statisticalCodeIds')::uuid;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create the trigger for instance
DROP TRIGGER IF EXISTS trg_update_instance_statistical_code ON instance CASCADE;
CREATE TRIGGER trg_update_instance_statistical_code
    AFTER INSERT OR UPDATE
    ON ${myuniversity}_${mymodule}.instance
    FOR EACH ROW
EXECUTE FUNCTION update_instance_statistical_code();

-- Create the function for item
CREATE OR REPLACE FUNCTION update_item_statistical_code()
    RETURNS trigger AS
$$
BEGIN
    -- Delete existing entries for the item
    IF (TG_OP = 'UPDATE') THEN
        DELETE
        FROM ${myuniversity}_${mymodule}.item_statistical_code
        WHERE item_id = NEW.id;
    END IF;

    -- Insert new entries for the item
    INSERT INTO ${myuniversity}_${mymodule}.item_statistical_code (item_id, statistical_code_id)
    SELECT NEW.id, jsonb_array_elements_text(NEW.jsonb->'statisticalCodeIds')::uuid;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create the trigger for item
DROP TRIGGER IF EXISTS trg_update_item_statistical_code ON item CASCADE;
CREATE TRIGGER trg_update_item_statistical_code
    AFTER INSERT OR UPDATE
    ON ${myuniversity}_${mymodule}.item
    FOR EACH ROW
EXECUTE FUNCTION update_item_statistical_code();

-- Create the function for holdings_record
CREATE OR REPLACE FUNCTION update_holdings_record_statistical_code()
    RETURNS trigger AS
$$
BEGIN
    -- Delete existing entries for the holdings_record
    IF (TG_OP = 'UPDATE') THEN
        DELETE
        FROM ${myuniversity}_${mymodule}.holdings_record_statistical_code
        WHERE holdings_record_id = NEW.id;
    END IF;

    -- Insert new entries for the holdings_record
    INSERT INTO ${myuniversity}_${mymodule}.holdings_record_statistical_code (holdings_record_id, statistical_code_id)
    SELECT NEW.id, jsonb_array_elements_text(NEW.jsonb->'statisticalCodeIds')::uuid;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create the trigger for holdings_record
DROP TRIGGER IF EXISTS trg_update_holdings_record_statistical_code ON holdings_record CASCADE;
CREATE TRIGGER trg_update_holdings_record_statistical_code
    AFTER INSERT OR UPDATE
    ON ${myuniversity}_${mymodule}.holdings_record
    FOR EACH ROW
EXECUTE FUNCTION update_holdings_record_statistical_code();