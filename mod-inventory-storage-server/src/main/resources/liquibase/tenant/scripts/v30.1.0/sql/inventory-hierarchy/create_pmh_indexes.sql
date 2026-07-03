create index if not exists instance_pmh_metadata_updateddate_idx on instance ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));
create index if not exists item_pmh_metadata_updateddate_idx on item ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));
create index if not exists holdings_record_pmh_metadata_updateddate_idx on holdings_record ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));
create index if not exists audit_instance_pmh_createddate_idx on audit_instance ((strToTimestamp(jsonb ->> 'createdDate')));
create index if not exists audit_holdings_record_pmh_createddate_idx on audit_holdings_record ((strToTimestamp(jsonb -> 'record' ->> 'updatedDate')));
create index if not exists audit_item_pmh_createddate_idx on audit_item ((strToTimestamp(jsonb -> 'record' ->> 'updatedDate')));