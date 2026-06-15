CREATE OR REPLACE FUNCTION pmh_instance_view_function(
	instanceids uuid[],
	skipsuppressedfromdiscoveryrecords boolean DEFAULT true)
    RETURNS TABLE(instanceid uuid, itemsandholdingsfields jsonb) 
    LANGUAGE 'sql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    ROWS 1000

AS $BODY$
select instId,
(select to_jsonb(itemAndHoldingsAttrs) as itemsAndHoldingsFields
         from ( select hr.instanceid,
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
                                                    'suppressFromDiscovery',
                                                                coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false) or
                                                                coalesce((item.jsonb ->> 'discoverySuppress')::bool, false),
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
                         left join call_number_type cnt on nullif(item.jsonb #>> '{effectiveCallNumberComponents, typeId}','')::uuid = cnt.id
                where instanceId = instId
                  and not ($2 and coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  and not ($2 and coalesce((item.jsonb ->> 'discoverySuppress')::bool, false))
                group by 1) itemAndHoldingsAttrs)
FROM unnest( $1 ) AS instId;

$BODY$;
