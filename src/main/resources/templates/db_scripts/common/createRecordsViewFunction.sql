-- Creates auxiliary functions
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.strToTimestamp(text) RETURNS timestamptz AS
$$
SELECT $1::timestamptz
$$ LANGUAGE sql immutable strict;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.dateOrMin(timestamptz) RETURNS timestamptz AS
$$
SELECT COALESCE($1, timestamptz '1970-01-01')
$$ LANGUAGE sql immutable;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.dateOrMax(timestamptz) RETURNS timestamptz AS
$$
SELECT COALESCE($1, timestamptz '2050-01-01')
$$ LANGUAGE sql immutable;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getElectronicAccessName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e || jsonb_build_object('name', ( SELECT jsonb ->> 'name'
                                                FROM ${myuniversity}_${mymodule}.electronic_access_relationship ear
                                                WHERE id = (e ->> 'relationshipId')::uuid )) e
       FROM jsonb_array_elements($1) AS e ) e1
$$ LANGUAGE sql strict;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getItemNoteTypeName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'itemNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('itemNoteTypeName', ( SELECT jsonb ->> 'name'
                                 FROM item_note_type
                                 WHERE id = (e ->> 'itemNoteTypeId')::uuid )) e
       FROM jsonb_array_elements( $1 ) AS e
	   WHERE NOT (e ->> 'staffOnly')::bool ) e1
$$ LANGUAGE sql strict;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getHoldingNoteTypeName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'holdingsNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('holdingsNoteTypeName', ( SELECT jsonb ->> 'name'
                                   FROM holdings_note_type
                                   WHERE id = (e ->> 'holdingsNoteTypeId')::uuid )) e
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
	WHERE sc.statCodeId = (e ->> 0)::uuid;
$$ LANGUAGE sql strict;

-- Drop additional indexes
DROP INDEX IF EXISTS ${myuniversity}_${mymodule}.item_pmh_metadata_updateddate_idx;
DROP INDEX IF EXISTS ${myuniversity}_${mymodule}.holdings_record_pmh_metadata_updateddate_idx;
DROP INDEX IF EXISTS ${myuniversity}_${mymodule}.audit_instance_pmh_createddate_idx;
DROP INDEX IF EXISTS ${myuniversity}_${mymodule}.audit_holdings_record_pmh_createddate_idx;
DROP INDEX IF EXISTS ${myuniversity}_${mymodule}.audit_item_pmh_createddate_idx;
DROP INDEX IF EXISTS ${myuniversity}_${mymodule}.instance_pmh_metadata_updateddate_idx;

-- Creates additional indexes
CREATE INDEX IF NOT EXISTS instance_pmh_metadata_updateddate_idx ON ${myuniversity}_${mymodule}.instance ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));
CREATE INDEX IF NOT EXISTS item_pmh_metadata_updateddate_idx ON ${myuniversity}_${mymodule}.item ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));
CREATE INDEX IF NOT EXISTS holdings_record_pmh_metadata_updateddate_idx ON ${myuniversity}_${mymodule}.holdings_record ((strToTimestamp(jsonb -> 'metadata' ->> 'updatedDate')));
CREATE INDEX IF NOT EXISTS audit_instance_pmh_createddate_idx ON ${myuniversity}_${mymodule}.audit_instance ((strToTimestamp(jsonb ->> 'createdDate')));
CREATE INDEX IF NOT EXISTS audit_holdings_record_pmh_createddate_idx ON ${myuniversity}_${mymodule}.audit_holdings_record ((strToTimestamp(jsonb -> 'record' ->> 'updatedDate')));
CREATE INDEX IF NOT EXISTS audit_item_pmh_createddate_idx ON ${myuniversity}_${mymodule}.audit_item ((strToTimestamp(jsonb -> 'record' ->> 'updatedDate')));

-- Creates function returned instances with records updates/deleted/added in specified period o time
CREATE OR REPLACE function ${myuniversity}_${mymodule}.get_updated_instance_ids_view(startDate                          timestamptz,
                                                                                     endDate                            timestamptz,
                                                                                     deletedRecordSupport               bool DEFAULT TRUE,
                                                                                     skipSuppressedFromDiscoveryRecords bool DEFAULT TRUE,
                                                                                     onlyInstanceUpdateDate             bool DEFAULT TRUE)
    RETURNS TABLE
            (
                instanceId            uuid,
                updatedDate           timestamptz,
                suppressFromDiscovery boolean,
                deleted               boolean
            )
AS
$BODY$
WITH instanceIdsInRange AS ( SELECT inst.id AS instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) AS maxDate
                             FROM ${myuniversity}_${mymodule}.instance inst
                             WHERE (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)

                             UNION ALL
                             SELECT instanceid,
                                    greatest((strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')),
                                             (strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate'))) AS maxDate
                             FROM ${myuniversity}_${mymodule}.holdings_record hr
                                      JOIN ${myuniversity}_${mymodule}.item item ON item.holdingsrecordid = hr.id
                             WHERE ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2) OR
                                    (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                    AND EXISTS (SELECT null WHERE $5)

                             UNION ALL
                             SELECT (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strtotimestamp(audit_item.jsonb -> 'record' ->> 'updatedDate')),
                                             (strtotimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate'))) AS maxDate
                             FROM ${myuniversity}_${mymodule}.audit_holdings_record audit_holdings_record
                                      JOIN ${myuniversity}_${mymodule}.audit_item audit_item
                                           ON (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              audit_holdings_record.id
                             WHERE ((strToTimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2) OR
                                    (strToTimestamp(audit_item.jsonb #>> '{record,updatedDate}')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                    AND EXISTS (SELECT null WHERE $5) )
SELECT instanceId,
       instance.jsonb ->> 'source' AS source,
       MAX(instanceIdsInRange.maxDate) AS maxDate,
       (instance.jsonb ->> 'discoverySuppress')::bool AS suppressFromDiscovery,
       false AS deleted
FROM instanceIdsInRange,
    ${myuniversity}_${mymodule}.instance
WHERE instanceIdsInRange.maxDate BETWEEN dateOrMin($1) AND dateOrMax($2)
      AND instance.id = instanceIdsInRange.instanceId
      AND NOT ($4 AND COALESCE((instance.jsonb ->> 'discoverySuppress')::bool, false))
GROUP BY 1, 2, 4

UNION ALL
SELECT (ai.jsonb #>> '{record,id}')::uuid             AS instanceId,
       i.jsonb ->> 'source'                           AS source,
       strToTimestamp(ai.jsonb ->> 'createdDate')     AS maxDate,
       false                                          AS suppressFromDiscovery,
       true                                           AS deleted
FROM ${myuniversity}_${mymodule}.audit_instance ai, ${myuniversity}_${mymodule}.instance i
WHERE $3
      AND i.id = (ai.jsonb #>> '{record,id}')::uuid
      AND strToTimestamp(ai.jsonb ->> 'createdDate') BETWEEN dateOrMin($1) AND dateOrMax($2);

$BODY$ LANGUAGE sql;

-- Creates function returned instance identifiers with holdings and items by specified instances ids
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.get_items_and_holdings_view(instanceIds                        uuid[],
                                                                                   skipSuppressedFromDiscoveryRecords bool DEFAULT TRUE)
    RETURNS TABLE
            (
                instanceId             uuid,
                itemsAndHoldingsFields jsonb
            )
AS
$BODY$
WITH
	-- Locations
	locations(locId, locJsonb, locCampJsonb, locLibJsonb, locInstJsonb) AS (
		SELECT loc.id AS locId,
			   loc.jsonb AS locJsonb,
			   locCamp.jsonb AS locCampJsonb,
			   locLib.jsonb AS locLibJsonb,
			   locInst.jsonb AS locInstJsonb
		FROM ${myuniversity}_${mymodule}.location loc
			 LEFT JOIN ${myuniversity}_${mymodule}.locinstitution locInst
				  ON (loc.jsonb ->> 'institutionId')::uuid = locInst.id
			 LEFT JOIN ${myuniversity}_${mymodule}.loccampus locCamp
				  ON (loc.jsonb ->> 'campusId')::uuid = locCamp.id
			 LEFT JOIN ${myuniversity}_${mymodule}.loclibrary locLib
				  ON (loc.jsonb ->> 'libraryId')::uuid = locLib.id
		WHERE (loc.jsonb ->> 'isActive')::bool = true
	)
SELECT instId,
i.jsonb ->> 'source' AS source,
(SELECT jsonb_strip_nulls(to_jsonb(itemAndHoldingsAttrs)) AS itemsAndHoldingsFields
         FROM ( SELECT hr.instanceid AS instanceid,
                i.jsonb ->> 'source' AS "source",
                jsonb_build_object(
                       'holdings',
                       jsonb_agg(DISTINCT jsonb_build_object('id', hr.id,
                                                             'hrid', hr.jsonb ->> 'hrid',
                                                             'suppressFromDiscovery', COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                                                      COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false),
                                                             'holdingsType', ht.jsonb ->> 'name',
                                                             'formerIds', hr.jsonb -> 'formerIds',
                                                             'location', json_build_object('permanentLocation', jsonb_build_object('name', COALESCE(holdPermLoc.locJsonb ->> 'discoveryDisplayName', holdPermLoc.locJsonb ->> 'name'),
                                                                                                                                   'campusName', holdPermLoc.locCampJsonb ->> 'name',
                                                                                                                                   'libraryName', holdPermLoc.locLibJsonb ->> 'name',
                                                                                                                                   'institutionName', holdPermLoc.locInstJsonb ->> 'name'),
                                                                                           'temporaryLocation', jsonb_build_object('name', COALESCE(holdTempLoc.locJsonb ->> 'discoveryDisplayName', holdTempLoc.locJsonb ->> 'name'),
                                                                                                                                   'campusName', holdTempLoc.locCampJsonb ->> 'name',
                                                                                                                                   'libraryName', holdTempLoc.locLibJsonb ->> 'name',
                                                                                                                                   'institutionName', holdTempLoc.locInstJsonb ->> 'name')
                                                                                          ),
                                                             'callNumber', json_build_object('prefix', hr.jsonb ->> 'callNumberPrefix',
                                                                                             'suffix', hr.jsonb ->> 'callNumberSuffix',
                                                                                             'typeId', hr.jsonb ->> 'callNumberTypeId',
                                                                                             'typeName', hrcnt.jsonb ->> 'name',
                                                                                             'callNumber', hr.jsonb ->> 'callNumber'
                                                                                            ),
                                                             'shelvingTitle', hr.jsonb ->> 'shelvingTitle',
                                                             'acquisitionFormat', hr.jsonb ->> 'acquisitionFormat',
                                                             'acquisitionMethod', hr.jsonb ->> 'acquisitionMethod',
                                                             'receiptStatus', hr.jsonb ->> 'receiptStatus',
                                                             'electronicAccess', COALESCE(getElectronicAccessName(COALESCE(hr.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb),
                                                             'notes', COALESCE(getHoldingNoteTypeName(hr.jsonb -> 'notes'), '[]'::jsonb),
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
                                                             'statisticalCodes', COALESCE(getStatisticalCodes(hr.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
                                                            )
								                ),
                       'items',
                       jsonb_agg(jsonb_build_object('id', item.id,
                                                    'hrid', item.jsonb ->> 'hrid',
                                                    'holdingsRecordId', hr.id,
                                                    'suppressFromDiscovery', COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                                             COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                                             COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false),
                                                    'status', item.jsonb #>> '{status, name}',
                                                    'formerIds', item.jsonb -> 'formerIds',
                                                    'location', json_build_object('location', jsonb_build_object('name', COALESCE(itemEffLoc.locJsonb ->> 'discoveryDisplayName', itemEffLoc.locJsonb ->> 'name'),
                                                                                                                 'campusName', itemEffLoc.locCampJsonb ->> 'name',
                                                                                                                 'libraryName', itemEffLoc.locLibJsonb ->> 'name',
                                                                                                                 'institutionName', itemEffLoc.locInstJsonb ->> 'name'),
                                                                                  'permanentLocation', jsonb_build_object('name', COALESCE(itemPermLoc.locJsonb ->> 'discoveryDisplayName', itemPermLoc.locJsonb ->> 'name'),
                                                                                                                          'campusName', itemPermLoc.locCampJsonb ->> 'name',
                                                                                                                          'libraryName', itemPermLoc.locLibJsonb ->> 'name',
                                                                                                                          'institutionName', itemPermLoc.locInstJsonb ->> 'name'),
                                                                                  'temporaryLocation', jsonb_build_object('name', COALESCE(itemTempLoc.locJsonb ->> 'discoveryDisplayName', itemTempLoc.locJsonb ->> 'name'),
                                                                                                                          'campusName', itemTempLoc.locCampJsonb ->> 'name',
                                                                                                                          'libraryName', itemTempLoc.locLibJsonb ->> 'name',
                                                                                                                          'institutionName', itemTempLoc.locInstJsonb ->> 'name')
                                                                                  ),
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
                                                    'electronicAccess', COALESCE(getElectronicAccessName(COALESCE(item.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb),
                                                    'notes', COALESCE(getItemNoteTypeName(item.jsonb -> 'notes'), '[]'::jsonb),
                                                    'tags', item.jsonb -> 'tags',
                                                    'statisticalCodes', COALESCE(getStatisticalCodes(item.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
                           							           )
								                )
			                            ) itemsAndHoldingsFields
                FROM ${myuniversity}_${mymodule}.holdings_record hr
                         JOIN ${myuniversity}_${mymodule}.item item
                              ON item.holdingsrecordid = hr.id
                         JOIN ${myuniversity}_${mymodule}.instance i
                              ON i.id = hr.instanceid
                         -- Item's Effective location relation
                         LEFT JOIN locations itemEffLoc
                              ON (item.jsonb ->> 'effectiveLocationId')::uuid = itemEffLoc.locId
                         -- Item's Permanent location relation
                         LEFT JOIN locations itemPermLoc
                              ON (item.jsonb ->> 'permanentLocationId')::uuid = itemPermLoc.locId
                         -- Item's Temporary location relation
                         LEFT JOIN locations itemTempLoc
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
                         LEFT JOIN locations holdPermLoc
                              ON (hr.jsonb ->> 'permanentLocationId')::uuid = holdPermLoc.locId
                         -- Holdings Temporary location relation
                         LEFT JOIN locations holdTempLoc
                              ON (hr.jsonb ->> 'temporaryLocationId')::uuid = holdTempLoc.locId
                         -- Holdings Call number type relation
                         LEFT JOIN ${myuniversity}_${mymodule}.call_number_type hrcnt
                              ON (hr.jsonb ->> 'callNumberTypeId')::uuid = hrcnt.id
                         -- Holdings Ill policy relation
                         LEFT JOIN ${myuniversity}_${mymodule}.ill_policy ilp
                              ON hr.illpolicyid = ilp.id
                WHERE instanceId = instId
                      AND NOT ($2 AND COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false))
                      AND NOT ($2 AND COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false))
                GROUP BY 1, 2
              ) itemAndHoldingsAttrs
)
FROM (SELECT DISTINCT * FROM UNNEST( $1 ) instId) instId
	 JOIN ${myuniversity}_${mymodule}.instance i
	       ON i.id = instid;

$BODY$ LANGUAGE sql;
