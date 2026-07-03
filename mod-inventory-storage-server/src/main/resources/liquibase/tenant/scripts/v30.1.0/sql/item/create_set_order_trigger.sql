CREATE OR REPLACE FUNCTION set_item_order()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
AS $BODY$
DECLARE
    income_order text;
    holding_record_id UUID;
    new_order INT;
BEGIN
    holding_record_id := (NEW.jsonb ->> 'holdingsRecordId')::uuid;
    income_order := NEW.jsonb ->> 'order';

    IF holding_record_id IS NOT NULL THEN
        -- Ensure the tracker row exists (prevents race condition)
        INSERT INTO item_order_tracker (holdings_id)
        VALUES (holding_record_id)
        ON CONFLICT (holdings_id) DO NOTHING;

        -- Lock the row in the item_order_tracker table for the given holdings_id
        PERFORM 1 FROM item_order_tracker
        WHERE holdings_id = holding_record_id
            FOR UPDATE;

        -- Check if the order field is null
        IF income_order IS NULL THEN
            -- Check if there is at least one item for this holdings record
            IF EXISTS (
                SELECT 1
                FROM item
                WHERE holdingsrecordid = holding_record_id
                LIMIT 1
            ) THEN
                -- Update or insert the max order value in the tracker table
                INSERT INTO item_order_tracker (holdings_id)
                VALUES (holding_record_id)
                ON CONFLICT (holdings_id) DO UPDATE
                    SET max_order = item_order_tracker.max_order + 1
                RETURNING max_order INTO new_order;
            ELSE
                -- This is the first item for this holdings record, set max_order to 1 by default
                new_order := 1;
                INSERT INTO item_order_tracker (holdings_id)
                VALUES (holding_record_id)
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
            INSERT INTO item_order_tracker (holdings_id, max_order)
            VALUES (holding_record_id, income_order::int)
            ON CONFLICT (holdings_id) DO UPDATE
                SET max_order = GREATEST(item_order_tracker.max_order, income_order::int);
        END IF;
    END IF;

    RETURN NEW;
END;
$BODY$;

DROP TRIGGER IF EXISTS set_order_trigger ON item CASCADE;
CREATE OR REPLACE TRIGGER item_bi_bu_set_order_trg
    BEFORE INSERT OR UPDATE
    ON item
    FOR EACH ROW
EXECUTE FUNCTION set_item_order();
