 -- Fix completeUpdatedDate_for_holdings_insert_update to also update the source instance
 -- when a holdings record is moved from one instance to another (instanceid changes).

 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_holdings_record_insert_update
 ON ${myuniversity}_${mymodule}.holdings_record;

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_holdings_insert_update()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
     WHERE inst.id = NEW.instanceid
        OR (TG_OP = 'UPDATE' AND inst.id = OLD.instanceid AND OLD.instanceid <> NEW.instanceid);
  RETURN NEW;
 END;
 $BODY$;

 CREATE TRIGGER updateCompleteUpdatedDate_holdings_record_insert_update
     BEFORE UPDATE OR INSERT
     ON ${myuniversity}_${mymodule}.holdings_record
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_holdings_insert_update();

 -- Fix completeUpdatedDate_for_item_insert_update to also update the source instance
 -- when an item is moved from one holdings record to another (holdingsrecordid changes),
 -- which may result in a different parent instance.

 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_item_insert_update
 ON ${myuniversity}_${mymodule}.item;

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_item_insert_update()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
     WHERE inst.id IN (
         SELECT instanceid
         FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
         WHERE hold_rec.id = NEW.holdingsrecordid);

     -- When item is moved to a different holdings record, also update the source instance
     IF TG_OP = 'UPDATE' AND OLD.holdingsrecordid <> NEW.holdingsrecordid THEN
         UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
         WHERE inst.id IN (
             SELECT instanceid
             FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
             WHERE hold_rec.id = OLD.holdingsrecordid);
     END IF;

     -- Update instances linked via bound-with parts
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
