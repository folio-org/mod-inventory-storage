CREATE OR REPLACE FUNCTION pmh_get_updated_instances_ids(
	startdate timestamp with time zone,
	enddate timestamp with time zone,
	deletedrecordsupport boolean DEFAULT true,
	skipsuppressedfromdiscoveryrecords boolean DEFAULT true)
    RETURNS TABLE(instanceid uuid, updateddate timestamp with time zone, suppressfromdiscovery boolean, deleted boolean)
    LANGUAGE 'sql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    ROWS 1000

AS $BODY$
with instanceIdsInRange as ( select inst.id                                                       as instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) as maxDate
                             from instance inst
                             where (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2)

                             union all
                             select instanceid,
                                    greatest((strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')),
                                             (strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate'))) as maxDate
                             from holdings_record hr
                                      join item item on item.holdingsrecordid = hr.id
                             where ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2) or
                                    (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2))

                             union all
                             select (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strtotimestamp(audit_item.jsonb -> 'record' ->> 'updatedDate')),
                                             (strtotimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate'))) as maxDate
                             from audit_holdings_record audit_holdings_record
                                      join audit_item audit_item
                                           on (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              audit_holdings_record.id
                             where ((strToTimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2) or
                                    (strToTimestamp(audit_item.jsonb #>> '{record,updatedDate}')) between dateOrMin($1) and dateOrMax($2)) )

select instanceId,
                    max(instanceIdsInRange.maxDate)    as maxDate,
        (instance.jsonb ->> 'discoverySuppress')::bool as suppressFromDiscovery,
                                                 false as deleted
                                     from instanceIdsInRange,
                                          instance
                                     where instanceIdsInRange.maxDate between dateOrMin($1) and dateOrMax($2)
                                       and instance.id = instanceIdsInRange.instanceId
                                       and not ($4 and coalesce((instance.jsonb ->> 'discoverySuppress')::bool, false))
                                     group by 1, 3
union all
select (audit_instance.jsonb #>> '{record,id}')::uuid as instanceId,
       strToTimestamp(jsonb ->> 'createdDate')        as maxDate,
       false                                          as suppressFromDiscovery,
       true                                           as deleted
from audit_instance
where $3
  and strToTimestamp(jsonb ->> 'createdDate') between dateOrMin($1) and dateOrMax($2)

$BODY$;
