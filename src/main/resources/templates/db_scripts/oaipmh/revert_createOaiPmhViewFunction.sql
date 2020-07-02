drop index if exists ${myuniversity}_${mymodule}.instance_pmh_metadata_updateddate_idx;
drop index if exists ${myuniversity}_${mymodule}.item_pmh_metadata_updateddate_idx;
drop index if exists ${myuniversity}_${mymodule}.holdings_pmh_record_metadata_updateddate_idx;
drop index if exists ${myuniversity}_${mymodule}.audit_instance_pmh_createddate_idx;
drop index if exists ${myuniversity}_${mymodule}.audit_holdings_record_pmh_createddate_idx;
drop index if exists ${myuniversity}_${mymodule}.audit_item_pmh_createddate_idx;

drop function if exists ${myuniversity}_${mymodule}.pmh_view_function(timestamptz, timestamptz, bool, bool);
drop function if exists ${myuniversity}_${mymodule}.strToTimestamp(text);
drop function if exists ${myuniversity}_${mymodule}.dateOrMin(timestamptz);
drop function if exists ${myuniversity}_${mymodule}.dateOrMax(timestamptz);
drop function if exists ${myuniversity}_${mymodule}.getElectronicAccessName(val jsonb);
