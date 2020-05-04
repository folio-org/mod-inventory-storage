create or replace function ${myuniversity}_${mymodule}.strToTimestamp(text) returns timestamptz as
$$
select $1::timestamptz
$$ language sql immutable
                strict;

create or replace function ${myuniversity}_${mymodule}.dateOrMin(timestamptz) returns timestamptz as
$$
select coalesce($1, timestamptz '1970-01-01')
$$ language sql immutable;

create or replace function ${myuniversity}_${mymodule}.dateOrMax(timestamptz) returns timestamptz as
$$
select coalesce($1, timestamptz '2050-01-01')
$$ language sql immutable;

create or replace function ${myuniversity}_${mymodule}.getElectronicAccessName(val jsonb) returns jsonb as
$$
select jsonb_agg(distinct e)
from ( select e || jsonb_build_object('name', ( select jsonb ->> 'name'
                                                from electronic_access_relationship ear
                                                where id = (e ->> 'relationshipId')::uuid )) e
       from jsonb_array_elements($1) as e ) e1
$$ language sql strict;

create index if not exists item_metadata_updateddate_idx on ${myuniversity}_${mymodule}.item ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));

create index if not exists holdings_record_metadata_updateddate_idx on ${myuniversity}_${mymodule}.holdings_record ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));

create index if not exists audit_instance_createddate_idx on ${myuniversity}_${mymodule}.audit_instance ((strToTimestamp(jsonb ->> 'createdDate')));

create index if not exists audit_holdings_record_createddate_idx on ${myuniversity}_${mymodule}.audit_holdings_record ((strToTimestamp(jsonb ->> 'createdDate')));

create index if not exists audit_item_createddate_idx on ${myuniversity}_${mymodule}.audit_item ((strToTimestamp(jsonb ->> 'createdDate')));

create or replace function ${myuniversity}_${mymodule}.pmh_view_function(startDate timestamptz,
                                                                         endDate timestamptz,
                                                                         deletedRecordSupport bool default true,
                                                                         skipSuppressedFromDiscoveryRecords bool default true)
    returns table
            (
                instanceId             uuid,
                instanceUpdatedDate    timestamptz,
                deleted                boolean,
                itemsAndHoldingsFields jsonb
            )
as
$body$
with instanceIdsInRange as ( select inst.id                                              as instanceId,
                                    (inst.jsonb #>> '{metadata,updatedDate}')::timestamptz as maxDate
                             from ${myuniversity}_${mymodule}.instance inst
                             where (inst.jsonb #>> '{metadata,updatedDate}')::timestamptz between dateOrMin($1) and dateOrMax($2)

                             union all
                             select instanceid,
                                    greatest((item.jsonb #>> '{metadata,updatedDate}')::timestamptz,
                                             (hr.jsonb #>> '{metadata,updatedDate}')::timestamptz) as maxDate
                             from holdings_record hr
                                      join ${myuniversity}_${mymodule}.item item on item.holdingsrecordid = hr.id
                             where ((hr.jsonb #>> '{metadata,updatedDate}')::timestamptz between dateOrMin($1) and dateOrMax($2) or
                                    (item.jsonb #>> '{metadata,updatedDate}')::timestamptz between dateOrMin($1) and dateOrMax($2))

                             union all
                             select (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((audit_item.jsonb #>> '{record,updatedDate}')::timestamptz,
                                             (audit_holdings_record.jsonb #>> '{record,updatedDate}')::timestamptz) as maxDate
                             from audit_holdings_record audit_holdings_record
                                      join audit_item audit_item
                                           on (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              audit_holdings_record.id
                             where ((audit_holdings_record.jsonb #>> '{record,updatedDate}')::timestamptz between dateOrMin($1) and dateOrMax($2) or
                                    (audit_item.jsonb #>> '{record,updatedDate}')::timestamptz between dateOrMin($1) and dateOrMax($2)) ),
     instanceIdsAndDatesInRange as ( select instanceId, max(instanceIdsInRange.maxDate) as maxDate
                                     from instanceIdsInRange,
                                          instance

                                     where instanceIdsInRange.maxDate between dateOrMin($1) and dateOrMax($2)
                                       and instance.id = instanceIdsInRange.instanceId
                                       and not ($4 and coalesce((instance.jsonb ->> 'discoverySuppress')::bool, false))
                                     group by 1 )

select instanceIdsAndDatesInRange.instanceId,
       instanceIdsAndDatesInRange.maxDate,
       false as deleted,
       ( select to_jsonb(itemAndHoldingsAttrs) as instanceFields
         from ( select hr.instanceid,
                       jsonb_agg(jsonb_build_object('id', item.id, 'callNumber',
                                                    item.jsonb -> 'effectiveCallNumberComponents', 'location',
                                                    json_build_object('location', jsonb_build_object('institutionId',
                                                                                                     itemLocInst.id,
                                                                                                     'institutionName',
                                                                                                     itemLocInst.jsonb ->> 'name',
                                                                                                     'campusId',
                                                                                                     itemLocCamp.id,
                                                                                                     'campusName',
                                                                                                     itemLocCamp.jsonb ->> 'name',
                                                                                                     'libraryId',
                                                                                                     itemLocLib.id,
                                                                                                     'libraryName',
                                                                                                     itemLocLib.jsonb ->> 'name')),
                                                    'volume', item.jsonb -> 'volume', 'enumeration',
                                                    item.jsonb -> 'enumeration', 'materialType', mt.jsonb -> 'name',
                                                    'electronicAccess', getElectronicAccessName(
                                                                coalesce(item.jsonb #> '{electronicAccess}', '[]'::jsonb) ||
                                                                coalesce(hr.jsonb #> '{electronicAccess}', '[]'::jsonb)))) items
                from holdings_record hr
                         join ${myuniversity}_${mymodule}.item item on item.holdingsrecordid = hr.id
                         join ${myuniversity}_${mymodule}.location loc
                              on (item.jsonb ->> 'effectiveLocationId')::uuid = loc.id and
                                 (loc.jsonb ->> 'isActive')::bool = true
                         join ${myuniversity}_${mymodule}.locinstitution itemLocInst
                              on (loc.jsonb ->> 'institutionId')::uuid = itemLocInst.id
                         join ${myuniversity}_${mymodule}.loccampus itemLocCamp
                              on (loc.jsonb ->> 'campusId')::uuid = itemLocCamp.id
                         join ${myuniversity}_${mymodule}.loclibrary itemLocLib
                              on (loc.jsonb ->> 'libraryId')::uuid = itemLocLib.id
                         left join material_type mt on item.materialtypeid = mt.id
                where instanceId = instanceIdsAndDatesInRange.instanceId
                  and not ($4 and coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  and not ($4 and coalesce((item.jsonb ->> 'discoverySuppress')::bool, false))
                group by 1 ) itemAndHoldingsAttrs )
from instanceIdsAndDatesInRange
union all
select (audit_instance.jsonb #>> '{record,id}')::uuid      as instanceId,
       (jsonb ->> 'createdDate')::timestamptz as maxDate,
       true                                                as deleted,
       null                                                as itemFields
from ${myuniversity}_${mymodule}.audit_instance
where $3
  and (jsonb ->> 'createdDate')::timestamptz between dateOrMin($1) and dateOrMax($2)

$body$ language sql;
