 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_item_insert_update
 ON ${myuniversity}_${mymodule}.item;

 DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.completeUpdatedDate_for_item_insert_update CASCADE;

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