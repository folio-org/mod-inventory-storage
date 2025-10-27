CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_order()
    RETURNS trigger AS
$$
DECLARE
    income_order text;
    holding_record_id UUID;
    new_order INT;
BEGIN
    holding_record_id := (NEW.jsonb ->> 'holdingsRecordId')::uuid;
    income_order := NEW.jsonb ->> 'order';

    IF holding_record_id IS NOT NULL THEN
        -- Lock the row in the item_order_tracker table for the given holdings_id
        PERFORM 1 FROM ${myuniversity}_${mymodule}.item_order_tracker
        WHERE holdings_id = holding_record_id
            FOR UPDATE;

        -- Check if the order field is null
        IF income_order IS NULL THEN
            -- Check if there is at least one item for this holdings record
            IF EXISTS (
              SELECT 1
              FROM ${myuniversity}_${mymodule}.item
              WHERE holdingsrecordid = holding_record_id
            ) THEN
                -- Update or insert the max order value in the tracker table
                INSERT INTO ${myuniversity}_${mymodule}.item_order_tracker (holdings_id)
                VALUES (holding_record_id)
                ON CONFLICT (holdings_id) DO UPDATE
                    SET max_order = ${myuniversity}_${mymodule}.item_order_tracker.max_order + 1
                RETURNING max_order INTO new_order;
            ELSE
                -- This is the first item for this holdings record, set max_order to 1 by default
                new_order := 1;
                INSERT INTO ${myuniversity}_${mymodule}.item_order_tracker (holdings_id, max_order)
                VALUES (holding_record_id, 1)
                ON CONFLICT (holdings_id) DO UPDATE
                    SET max_order = 1;
            END IF;

            -- Set the new order value in the item
            NEW.jsonb := jsonb_set(
                            NEW.jsonb,
                            '{order}',
                            to_jsonb(new_order)
                        );
        ELSE
            -- Update item_order_tracker if the incoming order is greater than the current max_order
            INSERT INTO ${myuniversity}_${mymodule}.item_order_tracker (holdings_id, max_order)
            VALUES (holding_record_id, income_order::int)
            ON CONFLICT (holdings_id) DO UPDATE
                SET max_order = GREATEST(${myuniversity}_${mymodule}.item_order_tracker.max_order, income_order::int);
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
