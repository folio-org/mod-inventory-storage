 DROP TRIGGER IF EXISTS completeUpdatedDate_holdings_record_inserted_updated
 ON ${myuniversity}_${mymodule}.holdings_record;
 DROP TRIGGER IF EXISTS completeUpdatedDate_holdings_record_deleted
 ON ${myuniversity}_${mymodule}.holdings_record;
 DROP TRIGGER IF EXISTS completeUpdatedDate_item_inserted_updated
 ON ${myuniversity}_${mymodule}.item;
 DROP TRIGGER IF EXISTS completeUpdatedDate_item_deleted
 ON ${myuniversity}_${mymodule}.item;

 DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.completeUpdatedDate_for_insert_update_holding;
 DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.completeUpdatedDate_for_delete_holding;
 DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.completeUpdatedDate_for_insert_update_item;
 DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.completeUpdatedDate_for_delete_item;

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_insert_update_holding()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
      UPDATE ${myuniversity}_${mymodule}.instance inst SET completeUpdatedDate =
          (NEW.jsonb -> 'metadata' ->> 'updatedDate')::timestamp with time zone
      WHERE inst.id = (NEW.jsonb ->> 'instanceId')::uuid;
   RETURN NEW;
 END;
 $BODY$;

 CREATE TRIGGER completeUpdatedDate_holdings_record_inserted_updated
     BEFORE UPDATE OR INSERT
     ON ${myuniversity}_${mymodule}.holdings_record
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_insert_update_holding();


 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_delete_holding()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
      UPDATE ${myuniversity}_${mymodule}.instance inst SET completeUpdatedDate = NOW()
      WHERE inst.id = OLD.instanceid;
   RETURN OLD;
 END;
 $BODY$;

 CREATE TRIGGER completeUpdatedDate_holdings_record_deleted
     AFTER DELETE
     ON ${myuniversity}_${mymodule}.holdings_record
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_delete_holding();

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_insert_update_item()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
      UPDATE ${myuniversity}_${mymodule}.instance inst SET completeUpdatedDate =
          (NEW.jsonb -> 'metadata' ->> 'updatedDate')::timestamp with time zone
      WHERE inst.id IN (
          SELECT instanceid FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
          WHERE hold_rec.id = (NEW.jsonb ->> 'holdingsRecordId')::uuid);
   RETURN NEW;
 END;
 $BODY$;

 CREATE TRIGGER completeUpdatedDate_item_inserted_updated
     BEFORE UPDATE OR INSERT
     ON ${myuniversity}_${mymodule}.item
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_insert_update_item();

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_delete_item()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
 AS $BODY$
 BEGIN
     UPDATE ${myuniversity}_${mymodule}.instance inst SET completeUpdatedDate = NOW()
     WHERE inst.id IN (
         SELECT instanceid
         FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
         WHERE hold_rec.id = (OLD.jsonb ->> 'holdingsRecordId')::uuid);
  RETURN OLD;
 END;
 $BODY$;

 CREATE TRIGGER completeUpdatedDate_item_deleted
    AFTER DELETE
    ON ${myuniversity}_${mymodule}.item
    FOR EACH ROW
    EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_delete_item();
