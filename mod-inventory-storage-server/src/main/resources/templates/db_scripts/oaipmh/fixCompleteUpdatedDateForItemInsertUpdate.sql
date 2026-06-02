 -- Fix performance regression in completeUpdatedDate_for_item_insert_update introduced in 30.0.2.
 -- The previous version merged three independent indexed lookups into a single UPDATE with OR
 -- conditions including a subquery, which caused PostgreSQL to fall back to a sequential scan
 -- of the holdings_record table (potentially millions of rows) on every item INSERT/UPDATE.
 -- This fix restores separate UPDATE statements so each query uses indexed lookups only.

 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_item_insert_update
 ON ${myuniversity}_${mymodule}.item;

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_item_insert_update()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     -- Update instance for the new (or current) holdings record.
     -- Uses index on holdings_record.id for fast lookup.
     UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
     WHERE inst.id IN (
         SELECT instanceid
         FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
         WHERE hold_rec.id = NEW.holdingsrecordid);

     -- When an item is moved to a different holdings record, also update the
     -- source instance. Kept as a separate statement to preserve index usage on
     -- holdings_record.id (combining with OR + subquery causes sequential scans).
     IF TG_OP = 'UPDATE' AND OLD.holdingsrecordid <> NEW.holdingsrecordid THEN
         UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
         WHERE inst.id IN (
             SELECT instanceid
             FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
             WHERE hold_rec.id = OLD.holdingsrecordid);
     END IF;

     -- Update instances reachable via bound-with parts.
     -- Uses index on bound_with_part.itemid and holdings_record.id for fast lookup.
     UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
     WHERE inst.id IN (
         SELECT instanceid
         FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
         WHERE hold_rec.id IN (
             SELECT holdingsrecordid
             FROM ${myuniversity}_${mymodule}.bound_with_part bwp
             WHERE bwp.itemid = NEW.id));

     RETURN NEW;
 END;
 $BODY$;

 CREATE TRIGGER updateCompleteUpdatedDate_item_insert_update
     BEFORE UPDATE OR INSERT
     ON ${myuniversity}_${mymodule}.item
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_item_insert_update();

