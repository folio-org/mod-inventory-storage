 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_holdings_record_insert_update
 ON ${myuniversity}_${mymodule}.holdings_record;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_holdings_record_delete
 ON ${myuniversity}_${mymodule}.holdings_record;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_item_insert_update
 ON ${myuniversity}_${mymodule}.item;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_item_delete
 ON ${myuniversity}_${mymodule}.item;
 DROP TRIGGER IF EXISTS updateCompleteUpdatedDate_instance
 ON ${myuniversity}_${mymodule}.instance;

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_instance()
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
  RETURN NEW;
 END;
 $BODY$;

 CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_item_delete()
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
          WHERE hold_rec.id = OLD.holdingsrecordid);
  RETURN OLD;
 END;
 $BODY$;

  CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_holdings_insert_update()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
  AS $BODY$
  BEGIN
      UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
      WHERE inst.id = NEW.instanceid;
   RETURN NEW;
  END;
  $BODY$;

  CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_holdings_delete()
     RETURNS trigger
     LANGUAGE 'plpgsql'
     COST 100
     VOLATILE NOT LEAKPROOF
  AS $BODY$
  BEGIN
      UPDATE ${myuniversity}_${mymodule}.instance inst SET complete_updated_date = NOW()
      WHERE inst.id = OLD.instanceid;
   RETURN OLD;
  END;
  $BODY$;

 CREATE TRIGGER updateCompleteUpdatedDate_holdings_record_insert_update
     BEFORE UPDATE OR INSERT
     ON ${myuniversity}_${mymodule}.holdings_record
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_holdings_insert_update();

 CREATE TRIGGER updateCompleteUpdatedDate_holdings_record_delete
     AFTER DELETE
     ON ${myuniversity}_${mymodule}.holdings_record
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_holdings_delete();

 CREATE TRIGGER updateCompleteUpdatedDate_item_insert_update
     BEFORE UPDATE OR INSERT
     ON ${myuniversity}_${mymodule}.item
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_item_insert_update();

 CREATE TRIGGER updateCompleteUpdatedDate_item_delete
     AFTER DELETE
     ON ${myuniversity}_${mymodule}.item
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_item_delete();

 CREATE TRIGGER updateCompleteUpdatedDate_instance
     BEFORE UPDATE OR INSERT
     ON ${myuniversity}_${mymodule}.instance
     FOR EACH ROW
     EXECUTE FUNCTION ${myuniversity}_${mymodule}.completeUpdatedDate_for_instance();
