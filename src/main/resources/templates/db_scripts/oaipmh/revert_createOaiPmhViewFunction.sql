drop index if exists item_metadata_updateddate_idx;

drop index if exists holdings_record_metadata_updateddate_idx;

drop index if exists audit_instance_createddate_idx;

drop index if exists audit_holdings_record_createddate_idx;

drop index if exists audit_item_createddate_idx;

drop function if exists ${myuniversity}_${mymodule}.pmh_view_function;

drop function if exists ${myuniversity}_${mymodule}.strToTimestamp;

drop function if exists ${myuniversity}_${mymodule}.dateOrMin;

drop function if exists ${myuniversity}_${mymodule}.dateOrMax;

drop function if exists ${myuniversity}_${mymodule}.getElectronicAccessName;

