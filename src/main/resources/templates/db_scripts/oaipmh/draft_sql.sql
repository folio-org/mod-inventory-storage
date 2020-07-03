set search_path = "diku_mod_inventory_storage";


create or replace function getItemNoteTypeName(val jsonb) returns jsonb as
$$
select jsonb_agg(distinct e)
from ( select e || jsonb_build_object('noteTypeName', ( select jsonb ->> 'name'
                                                from item_note_type
                                                where id = (e ->> 'itemNoteTypeId')::uuid )) e
       from jsonb_array_elements($1) as e ) e1
$$ language sql strict;

explain analyse WITH rng(sd, ed) as (
    select '01/01/2000 10:59:00'::timestamptz,
           '07/03/2022 10:59:00'::timestamptz

),
     instanceIdsInRange as ( select inst.id as instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) as maxDate
                             from rng, instance inst
                             where (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin(rng.sd) and dateOrMax(rng.ed)

                             union all
                             select instanceid,
                                    greatest((strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')),
                                             (strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate'))) as maxDate
                             from rng,holdings_record hr
                                      join item item on item.holdingsrecordid = hr.id
                             where ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin(rng.sd) and dateOrMax(rng.ed) or
                                    (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin(rng.sd) and dateOrMax(rng.ed))

                             union all
                             select (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strtotimestamp(audit_item.jsonb -> 'record' ->> 'updatedDate')),
                                             (strtotimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate'))) as maxDate
                             from rng, audit_holdings_record audit_holdings_record
                                           join audit_item audit_item
                                                on (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                                   audit_holdings_record.id
                             where ((strToTimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate')) between dateOrMin(rng.sd) and dateOrMax(rng.ed) or
                                    (strToTimestamp(audit_item.jsonb #>> '{record,updatedDate}')) between dateOrMin(rng.sd) and dateOrMax(rng.ed)) ),
     instanceIdsAndDatesInRange as ( select instanceId, max(instanceIdsInRange.maxDate) as maxDate
                                     from rng, instanceIdsInRange,
                                          instance

                                     where instanceIdsInRange.maxDate between dateOrMin(rng.sd) and dateOrMax(rng.ed)
                                       and instance.id = instanceIdsInRange.instanceId
                                       and not (false and coalesce((instance.jsonb ->> 'discoverySuppress')::bool, false))
                                     group by 1 )

select instanceIdsAndDatesInRange.instanceId,
       instanceIdsAndDatesInRange.maxDate,
       false as deleted,
       ( select to_jsonb(itemAndHoldingsAttrs) as instanceFields
         from ( select hr.instanceid as "instanceId",
                       (inst.jsonb ->> 'discoverySuppress')::bool as suppressDiscovery,
                       jsonb_agg(jsonb_build_object('id', item.id, 'callNumber',
                                                    item.jsonb -> 'effectiveCallNumberComponents'
                                                        || jsonb_build_object('typeName',cnt.jsonb ->> 'name'),
                                                    'location',
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
                                                                                                     itemLocLib.jsonb ->> 'name'),
                                                                                                     'name',
                                                                                                     coalesce(loc.jsonb ->> 'discoveryDisplayName', loc.jsonb ->> 'name')),
                                                    'volume',
                                                    item.jsonb -> 'volume',
                                                    'enumeration',
                                                    item.jsonb -> 'enumeration',
                                                    'materialType',
                                                    mt.jsonb -> 'name',
                                                    'electronicAccess',
                                                    getElectronicAccessName(
                                                                coalesce(item.jsonb #> '{electronicAccess}', '[]'::jsonb) ||
                                                                coalesce(hr.jsonb #> '{electronicAccess}', '[]'::jsonb)),
													'suppressDiscovery',
													case
													when (inst.jsonb->>'discoverySuppress')::bool
												        then true
													else
													    coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false) or
														coalesce((item.jsonb ->> 'discoverySuppress')::bool, false)
													end,
													'notes',
                                                    getItemNoteTypeName(item.jsonb-> 'notes'),
													'barcode',
													item.jsonb->>'barcode',
													'chronology',
													item.jsonb->>'chronology',
													'copyNumber',
													item.jsonb->>'copyNumber',
													'holdingsRecordId',
													hr.id
												   )) items
                from holdings_record hr
                    join instance inst on hr.instanceid = inst.id
                    join item item on item.holdingsrecordid = hr.id
                    join location loc
                       on (item.jsonb ->> 'effectiveLocationId')::uuid = loc.id and
                                 (loc.jsonb ->> 'isActive')::bool = true
                    join locinstitution itemLocInst
                              on (loc.jsonb ->> 'institutionId')::uuid = itemLocInst.id
                    join loccampus itemLocCamp
                              on (loc.jsonb ->> 'campusId')::uuid = itemLocCamp.id
                    join loclibrary itemLocLib
                              on (loc.jsonb ->> 'libraryId')::uuid = itemLocLib.id
                    left join material_type mt on item.materialtypeid = mt.id
					left join call_number_type cnt on (item.jsonb #>> '{effectiveCallNumberComponents, typeId}')::uuid = cnt.id
                where instanceId = instanceIdsAndDatesInRange.instanceId
                  and not (false and coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  and not (false and coalesce((item.jsonb ->> 'discoverySuppress')::bool, false))
                group by 1, 2) itemAndHoldingsAttrs )
from instanceIdsAndDatesInRange
union all
select (audit_instance.jsonb #>> '{record,id}')::uuid      as instanceId,
       strToTimestamp(jsonb ->> 'createdDate')         as maxDate,
       true                                                as deleted,
       null                                                as itemFields
from rng, audit_instance
where true
  and strtotimestamp(jsonb ->> 'createdDate') between dateOrMin(rng.sd) and dateOrMax(rng.ed);

explain analyse select * from pmh_view_function('01/01/2020 10:59:00'::timestamptz,
                                '07/03/2022 10:59:00'::timestamptz, false, false);

