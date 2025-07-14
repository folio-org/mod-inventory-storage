-- Create the function to calculate and set the order field
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_order()
    RETURNS trigger AS
$$
BEGIN
    -- Calculate the max order for the given holdings_id
    IF NEW.jsonb ->> 'order' IS NULL THEN
        NEW.jsonb := jsonb_set(
                NEW.jsonb,
                '{order}',
                to_jsonb(
                        coalesce(
                                (SELECT ((jsonb ->> 'order')::int) ord
                                 FROM ${myuniversity}_${mymodule}.item
                                 WHERE holdingsrecordid = (NEW.jsonb ->> 'holdingsRecordId')::uuid
                                   AND jsonb ->> 'order' IS NOT NULL
                                 ORDER BY ord DESC
                                 LIMIT 1),
                                0
                        ) + 1
                )
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- Drop the trigger if it already exists
DROP TRIGGER IF EXISTS set_order_trigger ON ${myuniversity}_${mymodule}.item;

-- Create the trigger
CREATE TRIGGER set_order_trigger
    BEFORE INSERT OR UPDATE
    ON ${myuniversity}_${mymodule}.item
    FOR EACH ROW
EXECUTE FUNCTION ${myuniversity}_${mymodule}.set_order();