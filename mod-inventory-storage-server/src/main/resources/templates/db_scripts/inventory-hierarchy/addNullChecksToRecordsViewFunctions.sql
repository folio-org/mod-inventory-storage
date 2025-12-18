

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getElectronicAccessName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e || jsonb_build_object('name', ( SELECT jsonb ->> 'name'
                                                FROM ${myuniversity}_${mymodule}.electronic_access_relationship
                                                WHERE id = nullif(e ->> 'relationshipId','')::uuid )) e
       FROM jsonb_array_elements($1) AS e ) e1
$$ LANGUAGE sql strict;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getItemNoteTypeName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'itemNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('itemNoteTypeName', ( SELECT jsonb ->> 'name'
                                 FROM item_note_type
                                 WHERE id = nullif(e ->> 'itemNoteTypeId','')::uuid )) e
       FROM jsonb_array_elements( $1 ) AS e
	   WHERE NOT (e ->> 'staffOnly')::bool ) e1
$$ LANGUAGE sql strict;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getHoldingNoteTypeName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'holdingsNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('holdingsNoteTypeName', ( SELECT jsonb ->> 'name'
                                   FROM holdings_note_type
                                   WHERE id = nullif(e ->> 'holdingsNoteTypeId','')::uuid )) e
       FROM jsonb_array_elements( $1 ) AS e
	   WHERE NOT (e ->> 'staffOnly')::bool ) e1
$$ LANGUAGE sql strict;


CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getStatisticalCodes(val jsonb) RETURNS jsonb AS
$$
  WITH stat_codes(statCodeId, statCodeJsonb, statCodeTypeJsonb) AS (
    SELECT sc.id, sc.jsonb, sct.jsonb
      FROM statistical_code sc
        JOIN statistical_code_type sct ON sct.id = sc.statisticalcodetypeid
  )
	SELECT jsonb_agg(DISTINCT jsonb_build_object('id', sc.statCodeJsonb ->> 'id') ||
							  jsonb_build_object('code', sc.statCodeJsonb ->> 'code') ||
							  jsonb_build_object('name', sc.statCodeJsonb ->> 'name') ||
							  jsonb_build_object('statisticalCodeType', sc.statCodeTypeJsonb ->> 'name') ||
							  jsonb_build_object('source', sc.statCodeTypeJsonb ->> 'source'))
	FROM jsonb_array_elements( $1 ) AS e,
	 	 stat_codes sc
	WHERE sc.statCodeId = nullif(e ->> 0,'')::uuid
$$ LANGUAGE sql strict;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getNatureOfContentName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(DISTINCT e.name)
FROM (
	SELECT (jsonb ->> 'name') AS "name"
	FROM ${myuniversity}_${mymodule}.nature_of_content_term
		JOIN jsonb_array_elements($1) as insNoctIds
			ON id = nullif(insNoctIds ->> 0,'')::uuid) e
$$ LANGUAGE sql strict;




-- Retained for backward compatibility only. Should be removed for 20.0.0.
create or replace function ${myuniversity}_${mymodule}.pmh_view_function(startDate timestamptz,
                                                                         endDate timestamptz,
                                                                         deletedRecordSupport bool default true,
                                                                         skipSuppressedFromDiscoveryRecords bool default true)
    returns table
            (
                instanceId             uuid,
                updatedDate    timestamptz,
                deleted                boolean,
                itemsAndHoldingsFields jsonb
            )
as
$body$
with instanceIdsInRange as ( select inst.id                                                      as instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) as maxDate
                             from ${myuniversity}_${mymodule}.instance inst
                             where (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2)

                             union all
                             select instanceid,
                                    greatest((strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')),
                                             (strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate'))) as maxDate
                             from holdings_record hr
                                      join ${myuniversity}_${mymodule}.item item on item.holdingsrecordid = hr.id
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
                                    (strToTimestamp(audit_item.jsonb #>> '{record,updatedDate}')) between dateOrMin($1) and dateOrMax($2)) ),
     instanceIdsAndDatesInRange as ( select instanceId, max(instanceIdsInRange.maxDate) as maxDate,
                                            (instance.jsonb ->> 'discoverySuppress')::bool as suppressFromDiscovery
                                     from instanceIdsInRange,
                                          instance
                                     where instanceIdsInRange.maxDate between dateOrMin($1) and dateOrMax($2)
                                       and instance.id = instanceIdsInRange.instanceId
                                       and not ($4 and coalesce((instance.jsonb ->> 'discoverySuppress')::bool, false))
                                     group by 1, 3)

select instanceIdsAndDatesInRange.instanceId,
       instanceIdsAndDatesInRange.maxDate,
       false as deleted,
       ( select to_jsonb(itemAndHoldingsAttrs) as instanceFields
         from ( select hr.instanceid,
                       instanceIdsAndDatesInRange.suppressFromDiscovery as suppressFromDiscovery,
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
                                                    case
                                                        when instanceIdsAndDatesInRange.suppressFromDiscovery
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
                         left join ${myuniversity}_${mymodule}.material_type mt on item.materialtypeid = mt.id
                         left join ${myuniversity}_${mymodule}.call_number_type cnt on nullif(item.jsonb #>> '{effectiveCallNumberComponents, typeId}','')::uuid = cnt.id
                where instanceId = instanceIdsAndDatesInRange.instanceId
                  and not ($4 and coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  and not ($4 and coalesce((item.jsonb ->> 'discoverySuppress')::bool, false))
                group by 1) itemAndHoldingsAttrs )
from instanceIdsAndDatesInRange
union all
select (audit_instance.jsonb #>> '{record,id}')::uuid as instanceId,
       strToTimestamp(jsonb ->> 'createdDate')         as maxDate,
       true                                           as deleted,
       null                                           as itemFields
from ${myuniversity}_${mymodule}.audit_instance
where $3
  and strToTimestamp(jsonb ->> 'createdDate') between dateOrMin($1) and dateOrMax($2)

$body$ language sql;


create or replace function ${myuniversity}_${mymodule}.pmh_instance_view_function(instanceIds uuid[],
                                                                                  skipSuppressedFromDiscoveryRecords bool default true)
    returns table
            (
                instanceId             uuid,
                itemsAndHoldingsFields jsonb
            )
as
$body$
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
                         left join ${myuniversity}_${mymodule}.material_type mt on item.materialtypeid = mt.id
                         left join ${myuniversity}_${mymodule}.call_number_type cnt on nullif(item.jsonb #>> '{effectiveCallNumberComponents, typeId}','')::uuid = cnt.id
                where instanceId = instId
                  and not ($2 and coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  and not ($2 and coalesce((item.jsonb ->> 'discoverySuppress')::bool, false))
                group by 1) itemAndHoldingsAttrs)
FROM unnest( $1 ) AS instId;

$body$ language sql;




-- Creates function returned instance identifiers with holdings and items by specified instances ids
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.get_items_and_holdings_view(instanceIds                        uuid[],
                                                                                   skipSuppressedFromDiscoveryRecords bool DEFAULT TRUE)
    RETURNS TABLE
            (
                "instanceId"             uuid,
                "source"                 varchar,
                "modeOfIssuance"         varchar,
                "natureOfContent"        jsonb,
                "holdings"               jsonb,
                "items"                  jsonb
            )
AS
$BODY$
WITH
	-- Locations
	viewLocations(locId, locJsonb, locCampJsonb, locLibJsonb, locInstJsonb) AS (
	SELECT loc.id AS locId,
		     loc.jsonb AS locJsonb,
		     locCamp.jsonb AS locCampJsonb,
		     locLib.jsonb AS locLibJsonb,
		     locInst.jsonb AS locInstJsonb
	FROM location loc
		 LEFT JOIN locinstitution locInst
			    ON (loc.jsonb ->> 'institutionId')::uuid = locInst.id
		 LEFT JOIN loccampus locCamp
			    ON (loc.jsonb ->> 'campusId')::uuid = locCamp.id
		 LEFT JOIN loclibrary locLib
			    ON (loc.jsonb ->> 'libraryId')::uuid = locLib.id
	WHERE (loc.jsonb ->> 'isActive')::bool = true
	),
	-- Passed instances ids
	viewInstances(instId, source, modeOfIssuance, natureOfContent) AS (
  SELECT DISTINCT
         instId AS "instanceId",
         i.jsonb ->> 'source' AS source,
         moi.jsonb ->> 'name' AS modeOfIssuance,
         COALESCE(getNatureOfContentName(COALESCE(i.jsonb #> '{natureOfContentTermIds}', '[]'::jsonb)), '[]'::jsonb) AS natureOfContent
    FROM UNNEST( $1 ) instId
		     JOIN instance i
			        ON i.id = instId
			   LEFT JOIN mode_of_issuance moi
			        ON moi.id = nullif(i.jsonb ->> 'modeOfIssuanceId','')::uuid
	),
	-- Prepared items and holdings
	viewItemsAndHoldings(instId, records) AS (
	SELECT itemAndHoldingsAttrs.instanceId, jsonb_strip_nulls(itemAndHoldingsAttrs.itemsAndHoldings)
      FROM (SELECT
              i.id AS instanceId,
              jsonb_build_object('holdings',
                                 COALESCE(jsonb_agg(DISTINCT
                                            jsonb_build_object('id', hr.id,
                                                               'hrId', hr.jsonb ->> 'hrId',
                                                               'suppressFromDiscovery',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                                  COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false)
                                                               ELSE NULL END::bool,
                                                               'holdingsType', ht.jsonb ->> 'name',
                                                               'formerIds', hr.jsonb -> 'formerIds',
                                                               'location',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                   json_build_object('permanentLocation',
                                                                                     jsonb_build_object('name', COALESCE(holdPermLoc.locJsonb ->> 'discoveryDisplayName', holdPermLoc.locJsonb ->> 'name'),
                                                                                                        'code', holdPermLoc.locJsonb ->> 'code',
                                                                                                        'campusName', holdPermLoc.locCampJsonb ->> 'name',
                                                                                                        'libraryName', holdPermLoc.locLibJsonb ->> 'name',
                                                                                                        'institutionName', holdPermLoc.locInstJsonb ->> 'name'),
                                                                                     'temporaryLocation',
                                                                                     jsonb_build_object('name', COALESCE(holdTempLoc.locJsonb ->> 'discoveryDisplayName', holdTempLoc.locJsonb ->> 'name'),
                                                                                                        'code', holdTempLoc.locJsonb ->> 'code',
                                                                                                        'campusName', holdTempLoc.locCampJsonb ->> 'name',
                                                                                                        'libraryName', holdTempLoc.locLibJsonb ->> 'name',
                                                                                                        'institutionName', holdTempLoc.locInstJsonb ->> 'name'))
                                                               ELSE NULL END::jsonb,
                                                               'callNumber', json_build_object('prefix', hr.jsonb ->> 'callNumberPrefix',
                                                                                               'suffix', hr.jsonb ->> 'callNumberSuffix',
                                                                                               'typeId', hr.jsonb ->> 'callNumberTypeId',
                                                                                               'typeName', hrcnt.jsonb ->> 'name',
                                                                                               'callNumber', hr.jsonb ->> 'callNumber'),
                                                               'shelvingTitle', hr.jsonb ->> 'shelvingTitle',
                                                               'acquisitionFormat', hr.jsonb ->> 'acquisitionFormat',
                                                               'acquisitionMethod', hr.jsonb ->> 'acquisitionMethod',
                                                               'receiptStatus', hr.jsonb ->> 'receiptStatus',
                                                               'electronicAccess',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE(getElectronicAccessName(COALESCE(hr.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb)
                                                               ELSE NULL::jsonb END,
                                                               'notes',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE(getHoldingNoteTypeName(hr.jsonb -> 'notes'), '[]'::jsonb)
                                                               ELSE NULL END::jsonb,
                                                               'illPolicy', ilp.jsonb ->> 'name',
                                                               'retentionPolicy', hr.jsonb ->> 'retentionPolicy',
                                                               'digitizationPolicy', hr.jsonb ->> 'digitizationPolicy',
                                                               'holdingsStatements', hr.jsonb -> 'holdingsStatements',
                                                               'holdingsStatementsForIndexes', hr.jsonb -> 'holdingsStatementsForIndexes',
                                                               'holdingsStatementsForSupplements', hr.jsonb -> 'holdingsStatementsForSupplements',
                                                               'copyNumber', hr.jsonb ->> 'copyNumber',
                                                               'numberOfItems', hr.jsonb ->> 'numberOfItems',
                                                               'receivingHistory', hr.jsonb -> 'receivingHistory',
                                                               'tags', hr.jsonb -> 'tags',
                                                               'statisticalCodes',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE(getStatisticalCodes(hr.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
                                                               ELSE NULL END ::jsonb))
                                 FILTER (WHERE hr.id IS NOT NULL), '[]'::jsonb),
                                 'items',
                                 COALESCE(jsonb_agg(DISTINCT
                                      jsonb_build_object('id', item.id,
                                               'hrId', item.jsonb ->> 'hrId',
                                               'holdingsRecordId', (item.jsonb ->> 'holdingsRecordId')::UUID,
                                               'suppressFromDiscovery',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                  COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                  COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false)
                                               ELSE NULL END::bool,
                                               'status', item.jsonb #>> '{status, name}',
                                               'formerIds', item.jsonb -> 'formerIds',
                                               'location',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                   json_build_object('location',
                                                                     jsonb_build_object('name', COALESCE(itemEffLoc.locJsonb ->> 'discoveryDisplayName', itemEffLoc.locJsonb ->> 'name'),
                                                                                        'code', itemEffLoc.locJsonb ->> 'code',
                                                                                        'campusName', itemEffLoc.locCampJsonb ->> 'name',
                                                                                        'libraryName', itemEffLoc.locLibJsonb ->> 'name',
                                                                                        'institutionName', itemEffLoc.locInstJsonb ->> 'name'),
                                                                     'permanentLocation',
                                                                     jsonb_build_object('name', COALESCE(itemPermLoc.locJsonb ->> 'discoveryDisplayName', itemPermLoc.locJsonb ->> 'name'),
                                                                                        'code', itemPermLoc.locJsonb ->> 'code',
                                                                                        'campusName', itemPermLoc.locCampJsonb ->> 'name',
                                                                                        'libraryName', itemPermLoc.locLibJsonb ->> 'name',
                                                                                        'institutionName', itemPermLoc.locInstJsonb ->> 'name'),
                                                                     'temporaryLocation',
                                                                     jsonb_build_object('name', COALESCE(itemTempLoc.locJsonb ->> 'discoveryDisplayName', itemTempLoc.locJsonb ->> 'name'),
                                                                                        'code', itemTempLoc.locJsonb ->> 'code',
                                                                                        'campusName', itemTempLoc.locCampJsonb ->> 'name',
                                                                                        'libraryName', itemTempLoc.locLibJsonb ->> 'name',
                                                                                        'institutionName', itemTempLoc.locInstJsonb ->> 'name'))
                                               ELSE NULL END::jsonb,
                                               'callNumber', item.jsonb -> 'effectiveCallNumberComponents' ||
                                                             jsonb_build_object('typeName', cnt.jsonb ->> 'name'),
                                               'accessionNumber', item.jsonb ->> 'accessionNumber',
                                               'barcode', item.jsonb ->> 'barcode',
                                               'copyNumber', item.jsonb ->> 'copyNumber',
                                               'volume', item.jsonb ->> 'volume',
                                               'enumeration', item.jsonb ->> 'enumeration',
                                               'chronology', item.jsonb ->>'chronology',
                                               'yearCaption', item.jsonb -> 'yearCaption',
                                               'itemIdentifier', item.jsonb ->> 'itemIdentifier',
                                               'numberOfPieces', item.jsonb ->> 'numberOfPieces',
                                               'descriptionOfPieces', item.jsonb ->> 'descriptionOfPieces',
                                               'numberOfMissingPieces', item.jsonb ->> 'numberOfMissingPieces',
                                               'missingPieces', item.jsonb ->> 'missingPieces',
                                               'missingPiecesDate', item.jsonb ->> 'missingPiecesDate',
                                               'itemDamagedStatus', itemDmgStat.jsonb ->> 'name',
                                               'itemDamagedStatusDate', item.jsonb ->> 'itemDamagedStatusDate',
                                               'materialType', mt.jsonb ->> 'name',
                                               'permanentLoanType', plt.jsonb ->> 'name',
                                               'temporaryLoanType', tlt.jsonb ->> 'name',
                                               'electronicAccess',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE(getElectronicAccessName(COALESCE(item.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb)
                                               ELSE NULL::jsonb END,
                                               'notes',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE(getItemNoteTypeName(item.jsonb -> 'notes'), '[]'::jsonb)
                                               ELSE NULL END::jsonb,
                                               'tags', item.jsonb -> 'tags',
                                               'statisticalCodes',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE(getStatisticalCodes(item.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
                                               ELSE NULL END ::jsonb))
                                 FILTER (WHERE item.id IS NOT NULL), '[]'::jsonb)
                              ) itemsAndHoldings

            FROM ${myuniversity}_${mymodule}.holdings_record hr
                  JOIN ${myuniversity}_${mymodule}.instance i
                       ON i.id = hr.instanceid
                  JOIN viewInstances vi
                       ON vi.instId = i.id
                  LEFT JOIN ${myuniversity}_${mymodule}.item item
                       ON item.holdingsrecordid = hr.id
                  -- Item's Effective location relation
                  LEFT JOIN viewLocations itemEffLoc
                       ON (item.jsonb ->> 'effectiveLocationId')::uuid = itemEffLoc.locId
                  -- Item's Permanent location relation
                  LEFT JOIN viewLocations itemPermLoc
                       ON (item.jsonb ->> 'permanentLocationId')::uuid = itemPermLoc.locId
                  -- Item's Temporary location relation
                  LEFT JOIN viewLocations itemTempLoc
                       ON (item.jsonb ->> 'temporaryLocationId')::uuid = itemTempLoc.locId
                  -- Item's Material type relation
                  LEFT JOIN ${myuniversity}_${mymodule}.material_type mt
                       ON item.materialtypeid = mt.id
                  -- Item's Call number type relation
                  LEFT JOIN ${myuniversity}_${mymodule}.call_number_type cnt
                       ON (item.jsonb #>> '{effectiveCallNumberComponents, typeId}')::uuid = cnt.id
                  -- Item's Damaged status relation
                  LEFT JOIN ${myuniversity}_${mymodule}.item_damaged_status itemDmgStat
                       ON (item.jsonb ->> 'itemDamagedStatusId')::uuid = itemDmgStat.id
                  -- Item's Permanent loan type relation
                  LEFT JOIN ${myuniversity}_${mymodule}.loan_type plt
                       ON (item.jsonb ->> 'permanentLoanTypeId')::uuid = plt.id
                  -- Item's Temporary loan type relation
                  LEFT JOIN ${myuniversity}_${mymodule}.loan_type tlt
                       ON (item.jsonb ->> 'temporaryLoanTypeId')::uuid = tlt.id
                  -- Holdings type relation
                  LEFT JOIN ${myuniversity}_${mymodule}.holdings_type ht
                       ON ht.id = hr.holdingstypeid
                  -- Holdings Permanent location relation
                  LEFT JOIN viewLocations holdPermLoc
                       ON (hr.jsonb ->> 'permanentLocationId')::uuid = holdPermLoc.locId
                  -- Holdings Temporary location relation
                  LEFT JOIN viewLocations holdTempLoc
                       ON (hr.jsonb ->> 'temporaryLocationId')::uuid = holdTempLoc.locId
                  -- Holdings Call number type relation
                  LEFT JOIN ${myuniversity}_${mymodule}.call_number_type hrcnt
                       ON (hr.jsonb ->> 'callNumberTypeId')::uuid = hrcnt.id
                  -- Holdings Ill policy relation
                  LEFT JOIN ${myuniversity}_${mymodule}.ill_policy ilp
                       ON hr.illpolicyid = ilp.id
            WHERE true
                  AND NOT ($2 AND COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  AND NOT ($2 AND COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false))
            GROUP BY 1
           ) itemAndHoldingsAttrs
                                           )
-- Instances with items and holding records
SELECT
      vi.instId AS "instanceId",
      vi.source AS "source",
  	  vi.modeOfIssuance AS "modeOfIssuance",
  	  vi.natureOfContent AS "natureOfContent",
      COALESCE(viah.records -> 'holdings', '[]'::jsonb) AS "holdings",
      COALESCE(viah.records -> 'items', '[]'::jsonb) AS "items"
FROM viewInstances vi
	   LEFT JOIN viewItemsAndHoldings viah
		      ON viah.instId = vi.instId

$BODY$ LANGUAGE sql;
