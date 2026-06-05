 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_holdings_record_insert_update
 ON holdings_record;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_holdings_record_delete
 ON holdings_record;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_item_insert_update
 ON item;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_item_delete
 ON item;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_instance
 ON instance;

 CREATE OR REPLACE FUNCTION completeupdateddate_for_instance()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     NEW.complete_updated_date = NOW();
     RETURN NEW;
 END;

 $BODY$;

 CREATE OR REPLACE FUNCTION completeupdateddate_for_item_insert_update()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     -- Update instance for the new (or current) holdings record.
     -- Uses index on holdings_record.id for fast lookup.
     UPDATE instance inst SET complete_updated_date = NOW()
     WHERE inst.id IN (
         SELECT instanceid
         FROM holdings_record hold_rec
         WHERE hold_rec.id = NEW.holdingsrecordid);

     -- When an item is moved to a different holdings record, also update the
     -- source instance. Kept as a separate statement to preserve index usage on
     -- holdings_record.id (combining with OR + subquery causes sequential scans).
     IF TG_OP = 'UPDATE' AND OLD.holdingsrecordid <> NEW.holdingsrecordid THEN
         UPDATE instance inst SET complete_updated_date = NOW()
         WHERE inst.id IN (
             SELECT instanceid
             FROM holdings_record hold_rec
             WHERE hold_rec.id = OLD.holdingsrecordid);
     END IF;

     -- Update instances reachable via bound-with parts.
     -- Uses index on bound_with_part.itemid and holdings_record.id for fast lookup.
     UPDATE instance inst SET complete_updated_date = NOW()
     WHERE inst.id IN (
         SELECT instanceid
         FROM holdings_record hold_rec
         WHERE hold_rec.id IN (
             SELECT holdingsrecordid
             FROM bound_with_part bwp
             WHERE bwp.itemid = NEW.id));

     RETURN NEW;
 END;

 $BODY$;

 CREATE OR REPLACE FUNCTION completeupdateddate_for_item_delete()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     UPDATE instance inst SET complete_updated_date = NOW()
     WHERE inst.id IN (
         SELECT instanceid
         FROM holdings_record hold_rec
         WHERE hold_rec.id = OLD.holdingsrecordid);
     RETURN OLD;
 END;

 $BODY$;

 CREATE OR REPLACE FUNCTION completeupdateddate_for_holdings_insert_update()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     UPDATE instance inst SET complete_updated_date = NOW()
     WHERE inst.id = NEW.instanceid
        OR (TG_OP = 'UPDATE' AND inst.id = OLD.instanceid AND OLD.instanceid <> NEW.instanceid);
     RETURN NEW;
 END;

 $BODY$;

 CREATE OR REPLACE FUNCTION completeupdateddate_for_holdings_delete()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     UPDATE instance inst SET complete_updated_date = NOW()
     WHERE inst.id = OLD.instanceid;
     RETURN OLD;
 END;

 $BODY$;

 CREATE TRIGGER updateCompleteUpdatedDate_holdings_record_insert_update
     BEFORE UPDATE OR INSERT
     ON holdings_record
     FOR EACH ROW
     EXECUTE FUNCTION completeUpdatedDate_for_holdings_insert_update();

 CREATE TRIGGER updateCompleteUpdatedDate_holdings_record_delete
     AFTER DELETE
     ON holdings_record
     FOR EACH ROW
     EXECUTE FUNCTION completeUpdatedDate_for_holdings_delete();

 CREATE TRIGGER updateCompleteUpdatedDate_item_insert_update
     BEFORE UPDATE OR INSERT
     ON item
     FOR EACH ROW
     EXECUTE FUNCTION completeUpdatedDate_for_item_insert_update();

 CREATE TRIGGER updateCompleteUpdatedDate_item_delete
     AFTER DELETE
     ON item
     FOR EACH ROW
     EXECUTE FUNCTION completeUpdatedDate_for_item_delete();

 CREATE TRIGGER updateCompleteUpdatedDate_instance
     BEFORE UPDATE OR INSERT
     ON instance
     FOR EACH ROW
     EXECUTE FUNCTION completeUpdatedDate_for_instance();
