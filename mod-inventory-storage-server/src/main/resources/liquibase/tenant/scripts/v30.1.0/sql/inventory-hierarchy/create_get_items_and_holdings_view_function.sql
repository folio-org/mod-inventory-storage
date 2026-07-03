CREATE OR REPLACE FUNCTION get_items_and_holdings_view(
	instanceids uuid[],
	skipsuppressedfromdiscoveryrecords boolean DEFAULT true)
    RETURNS TABLE("instanceId" uuid, source character varying, "modeOfIssuance" character varying, "natureOfContent" jsonb, holdings jsonb, items jsonb) 
    LANGUAGE 'sql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    ROWS 1000

AS $BODY$
WITH
  -- Base CTE for location data to avoid repeated joins
  locations_data AS (
    SELECT loc.id AS locId,
           loc.jsonb AS locJsonb,
           locCamp.jsonb AS locCampJsonb,
           locLib.jsonb AS locLibJsonb,
           locInst.jsonb AS locInstJsonb
    FROM location loc
           LEFT JOIN locinstitution locInst ON (loc.jsonb ->> 'institutionId')::uuid = locInst.id
           LEFT JOIN loccampus locCamp ON (loc.jsonb ->> 'campusId')::uuid = locCamp.id
           LEFT JOIN loclibrary locLib ON (loc.jsonb ->> 'libraryId')::uuid = locLib.id
  ),
  -- Prepare holdings data for aggregation
  holdings_data AS (
    SELECT
      hr.instanceid,
      jsonb_strip_nulls(jsonb_build_object(
        'id', hr.id,
        'hrId', hr.jsonb ->> 'hrId',
        'suppressFromDiscovery', COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false),
        'holdingsType', ht.jsonb ->> 'name',
        'formerIds', hr.jsonb -> 'formerIds',
        'location', json_build_object(
          'permanentLocation', jsonb_build_object('name', COALESCE(holdPermLoc.locJsonb ->> 'discoveryDisplayName', holdPermLoc.locJsonb ->> 'name'), 'code', holdPermLoc.locJsonb ->> 'code', 'id', holdPermLoc.locJsonb ->> 'id', 'isActive', (holdPermLoc.locJsonb ->> 'isActive')::bool, 'campusName', holdPermLoc.locCampJsonb ->> 'name', 'libraryName', holdPermLoc.locLibJsonb ->> 'name', 'libraryCode', holdPermLoc.locLibJsonb ->> 'code', 'institutionName', holdPermLoc.locInstJsonb ->> 'name', 'locationName', holdPermLoc.locJsonb ->> 'name'),
          'temporaryLocation', jsonb_build_object('name', COALESCE(holdTempLoc.locJsonb ->> 'discoveryDisplayName', holdTempLoc.locJsonb ->> 'name'), 'code', holdTempLoc.locJsonb ->> 'code', 'id', holdTempLoc.locJsonb ->> 'id', 'isActive', (holdTempLoc.locJsonb ->> 'isActive')::bool, 'campusName', holdTempLoc.locCampJsonb ->> 'name', 'libraryName', holdTempLoc.locLibJsonb ->> 'name', 'libraryCode', holdTempLoc.locLibJsonb ->> 'code', 'institutionName', holdTempLoc.locInstJsonb ->> 'name', 'locationName', holdTempLoc.locJsonb ->> 'name'),
          'effectiveLocation', jsonb_build_object('name', COALESCE(holdEffLoc.locJsonb ->> 'discoveryDisplayName', holdEffLoc.locJsonb ->> 'name'), 'code', holdEffLoc.locJsonb ->> 'code', 'id', holdEffLoc.locJsonb ->> 'id', 'isActive', (holdEffLoc.locJsonb ->> 'isActive')::bool, 'campusName', holdEffLoc.locCampJsonb ->> 'name', 'libraryName', holdEffLoc.locLibJsonb ->> 'name', 'libraryCode', holdEffLoc.locLibJsonb ->> 'code', 'institutionName', holdEffLoc.locInstJsonb ->> 'name', 'locationName', holdEffLoc.locJsonb ->> 'name')
                    ),
        'callNumber', json_build_object('prefix', hr.jsonb ->> 'callNumberPrefix', 'suffix', hr.jsonb ->> 'callNumberSuffix', 'typeId', hr.jsonb ->> 'callNumberTypeId', 'typeName', hrcnt.jsonb ->> 'name', 'callNumber', hr.jsonb ->> 'callNumber'),
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
                        )) AS holding_json
    FROM holdings_record hr
           JOIN instance i ON hr.instanceid = i.id
           LEFT JOIN holdings_type ht ON hr.holdingstypeid = ht.id
           LEFT JOIN locations_data holdPermLoc ON (hr.jsonb ->> 'permanentLocationId')::uuid = holdPermLoc.locId
           LEFT JOIN locations_data holdTempLoc ON (hr.jsonb ->> 'temporaryLocationId')::uuid = holdTempLoc.locId
           LEFT JOIN locations_data holdEffLoc ON (hr.jsonb ->> 'effectiveLocationId')::uuid = holdEffLoc.locId
           LEFT JOIN call_number_type hrcnt ON (hr.jsonb ->> 'callNumberTypeId')::uuid = hrcnt.id
           LEFT JOIN ill_policy ilp ON hr.illpolicyid = ilp.id
    WHERE hr.instanceid = ANY($1)
      AND NOT ($2 AND COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false))
  ),
  -- Prepare item data for aggregation, including bound-with items
  items_data AS (
    SELECT instanceid, item_json
    FROM (
      SELECT *,
        ROW_NUMBER() OVER (
          PARTITION BY instanceid, item_json->>'id'
        ) AS rn
      FROM (
        SELECT
          hr.instanceid,
          jsonb_strip_nulls(jsonb_build_object(
            'id', item.id,
            'hrId', item.jsonb ->> 'hrId',
            'holdingsRecordId', (item.jsonb ->> 'holdingsRecordId')::UUID,
            'order', (item.jsonb ->> 'order')::int,
            'suppressFromDiscovery', COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false) OR COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false),
            'status', item.jsonb #>> '{status, name}',
            'formerIds', item.jsonb -> 'formerIds',
            'location', json_build_object(
              'location', jsonb_build_object('name', COALESCE(itemEffLoc.locJsonb ->> 'discoveryDisplayName', itemEffLoc.locJsonb ->> 'name'), 'code', itemEffLoc.locJsonb ->> 'code', 'id', itemEffLoc.locJsonb ->> 'id', 'isActive', (itemEffLoc.locJsonb ->> 'isActive')::bool, 'campusName', itemEffLoc.locCampJsonb ->> 'name', 'libraryName', itemEffLoc.locLibJsonb ->> 'name', 'libraryCode', itemEffLoc.locLibJsonb ->> 'code', 'institutionName', itemEffLoc.locInstJsonb ->> 'name', 'locationName', itemEffLoc.locJsonb ->> 'name'),
              'permanentLocation', jsonb_build_object('name', COALESCE(itemPermLoc.locJsonb ->> 'discoveryDisplayName', itemPermLoc.locJsonb ->> 'name'), 'code', itemPermLoc.locJsonb ->> 'code', 'id', itemPermLoc.locJsonb ->> 'id', 'isActive', (itemPermLoc.locJsonb ->> 'isActive')::bool, 'campusName', itemPermLoc.locCampJsonb ->> 'name', 'libraryName', itemPermLoc.locLibJsonb ->> 'name', 'libraryCode', itemPermLoc.locLibJsonb ->> 'code', 'institutionName', itemPermLoc.locInstJsonb ->> 'name', 'locationName', itemPermLoc.locJsonb ->> 'name'),
              'temporaryLocation', jsonb_build_object('name', COALESCE(itemTempLoc.locJsonb ->> 'discoveryDisplayName', itemTempLoc.locJsonb ->> 'name'), 'code', itemTempLoc.locJsonb ->> 'code', 'id', itemTempLoc.locJsonb ->> 'id', 'isActive', (itemTempLoc.locJsonb ->> 'isActive')::bool, 'campusName', itemTempLoc.locCampJsonb ->> 'name', 'libraryName', itemTempLoc.locLibJsonb ->> 'name', 'libraryCode', itemTempLoc.locLibJsonb ->> 'code', 'institutionName', itemTempLoc.locInstJsonb ->> 'name', 'locationName', itemTempLoc.locJsonb ->> 'name')
                        ),
            'callNumber', item.jsonb -> 'effectiveCallNumberComponents' || jsonb_build_object('typeName', cnt.jsonb ->> 'name'),
            'accessionNumber', item.jsonb ->> 'accessionNumber',
            'barcode', item.jsonb ->> 'barcode',
            'copyNumber', item.jsonb ->> 'copyNumber',
            'volume', item.jsonb ->> 'volume',
            'enumeration', item.jsonb ->> 'enumeration',
            'chronology', item.jsonb ->>'chronology',
            'displaySummary', item.jsonb ->>'displaySummary',
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
            'materialTypeId', mt.jsonb ->> 'id',
            'permanentLoanType', plt.jsonb ->> 'name',
            'temporaryLoanType', tlt.jsonb ->> 'name',
            'electronicAccess', COALESCE(getElectronicAccessName(COALESCE(item.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb),
            'notes', COALESCE(getItemNoteTypeName(item.jsonb -> 'notes'), '[]'::jsonb),
            'tags', item.jsonb -> 'tags',
            'statisticalCodes', COALESCE(getStatisticalCodes(item.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
          )) AS item_json
        FROM item
          JOIN holdings_record hr ON item.holdingsrecordid = hr.id
          JOIN instance i ON hr.instanceid = i.id
          LEFT JOIN locations_data itemEffLoc ON (item.jsonb ->> 'effectiveLocationId')::uuid = itemEffLoc.locId
          LEFT JOIN locations_data itemPermLoc ON (item.jsonb ->> 'permanentLocationId')::uuid = itemPermLoc.locId
          LEFT JOIN locations_data itemTempLoc ON (item.jsonb ->> 'temporaryLocationId')::uuid = itemTempLoc.locId
          LEFT JOIN material_type mt ON item.materialtypeid = mt.id
          LEFT JOIN call_number_type cnt ON (item.jsonb #>> '{effectiveCallNumberComponents, typeId}')::uuid = cnt.id
          LEFT JOIN item_damaged_status itemDmgStat ON (item.jsonb ->> 'itemDamagedStatusId')::uuid = itemDmgStat.id
          LEFT JOIN loan_type plt ON (item.jsonb ->> 'permanentLoanTypeId')::uuid = plt.id
          LEFT JOIN loan_type tlt ON (item.jsonb ->> 'temporaryLoanTypeId')::uuid = tlt.id
        WHERE hr.instanceid = ANY($1) AND NOT ($2 AND (COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false) OR COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false)))
        UNION ALL
        SELECT
          hr.instanceid,
          jsonb_strip_nulls(jsonb_build_object(
            'id', item.id,
            'hrId', item.jsonb ->> 'hrId',
            'holdingsRecordId', (bwp.holdingsrecordid)::UUID,
            'suppressFromDiscovery', COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false) OR COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false),
            'status', item.jsonb #>> '{status, name}',
            'formerIds', item.jsonb -> 'formerIds',
            'location', json_build_object(
              'location', jsonb_build_object('name', COALESCE(itemEffLoc.locJsonb ->> 'discoveryDisplayName', itemEffLoc.locJsonb ->> 'name'), 'code', itemEffLoc.locJsonb ->> 'code', 'id', itemEffLoc.locJsonb ->> 'id', 'isActive', (itemEffLoc.locJsonb ->> 'isActive')::bool, 'campusName', itemEffLoc.locCampJsonb ->> 'name', 'libraryName', itemEffLoc.locLibJsonb ->> 'name', 'libraryCode', itemEffLoc.locLibJsonb ->> 'code', 'institutionName', itemEffLoc.locInstJsonb ->> 'name', 'locationName', itemEffLoc.locJsonb ->> 'name'),
              'permanentLocation', jsonb_build_object('name', COALESCE(itemPermLoc.locJsonb ->> 'discoveryDisplayName', itemPermLoc.locJsonb ->> 'name'), 'code', itemPermLoc.locJsonb ->> 'code', 'id', itemPermLoc.locJsonb ->> 'id', 'isActive', (itemPermLoc.locJsonb ->> 'isActive')::bool, 'campusName', itemPermLoc.locCampJsonb ->> 'name', 'libraryName', itemPermLoc.locLibJsonb ->> 'name', 'libraryCode', itemPermLoc.locLibJsonb ->> 'code', 'institutionName', itemPermLoc.locInstJsonb ->> 'name', 'locationName', itemPermLoc.locJsonb ->> 'name'),
              'temporaryLocation', jsonb_build_object('name', COALESCE(itemTempLoc.locJsonb ->> 'discoveryDisplayName', itemTempLoc.locJsonb ->> 'name'), 'code', itemTempLoc.locJsonb ->> 'code', 'id', itemTempLoc.locJsonb ->> 'id', 'isActive', (itemTempLoc.locJsonb ->> 'isActive')::bool, 'campusName', itemTempLoc.locCampJsonb ->> 'name', 'libraryName', itemTempLoc.locLibJsonb ->> 'name', 'libraryCode', itemTempLoc.locLibJsonb ->> 'code', 'institutionName', itemTempLoc.locInstJsonb ->> 'name', 'locationName', itemTempLoc.locJsonb ->> 'name')
                        ),
            'callNumber', item.jsonb -> 'effectiveCallNumberComponents' || jsonb_build_object('typeName', cnt.jsonb ->> 'name'),
            'accessionNumber', item.jsonb ->> 'accessionNumber',
            'barcode', item.jsonb ->> 'barcode',
            'copyNumber', item.jsonb ->> 'copyNumber',
            'volume', item.jsonb ->> 'volume',
            'enumeration', item.jsonb ->> 'enumeration',
            'chronology', item.jsonb ->>'chronology',
            'displaySummary', item.jsonb ->>'displaySummary',
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
            'materialTypeId', mt.jsonb ->> 'id',
            'permanentLoanType', plt.jsonb ->> 'name',
            'temporaryLoanType', tlt.jsonb ->> 'name',
            'electronicAccess', COALESCE(getElectronicAccessName(COALESCE(item.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb),
            'notes', COALESCE(getItemNoteTypeName(item.jsonb -> 'notes'), '[]'::jsonb),
            'tags', item.jsonb -> 'tags',
            'statisticalCodes', COALESCE(getStatisticalCodes(item.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
          )) AS item_json
        FROM bound_with_part bwp
          JOIN item ON bwp.itemid = item.id
          JOIN holdings_record hr ON bwp.holdingsrecordid = hr.id
          JOIN instance i ON hr.instanceid = i.id
          LEFT JOIN locations_data itemEffLoc ON (item.jsonb ->> 'effectiveLocationId')::uuid = itemEffLoc.locId
          LEFT JOIN locations_data itemPermLoc ON (item.jsonb ->> 'permanentLocationId')::uuid = itemPermLoc.locId
          LEFT JOIN locations_data itemTempLoc ON (item.jsonb ->> 'temporaryLocationId')::uuid = itemTempLoc.locId
          LEFT JOIN material_type mt ON item.materialtypeid = mt.id
          LEFT JOIN call_number_type cnt ON (item.jsonb #>> '{effectiveCallNumberComponents, typeId}')::uuid = cnt.id
          LEFT JOIN item_damaged_status itemDmgStat ON (item.jsonb ->> 'itemDamagedStatusId')::uuid = itemDmgStat.id
          LEFT JOIN loan_type plt ON (item.jsonb ->> 'permanentLoanTypeId')::uuid = plt.id
          LEFT JOIN loan_type tlt ON (item.jsonb ->> 'temporaryLoanTypeId')::uuid = tlt.id
        WHERE hr.instanceid = ANY($1) AND NOT ($2 AND (COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false) OR COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false)))
      ) all_items
    ) deduped_items
    WHERE deduped_items.rn = 1
  ),
  -- Aggregate prepared holdings
  aggregated_holdings AS (
    SELECT instanceid, jsonb_agg(holding_json) AS holdings
    FROM holdings_data
    GROUP BY instanceid
  ),
  -- Aggregate prepared items
  aggregated_items AS (
    SELECT instanceid, jsonb_agg(item_json) AS items
    FROM items_data
    GROUP BY instanceid
  )
-- Final SELECT to join everything together
SELECT
  i.id AS "instanceId",
  i.jsonb ->> 'source' AS "source",
  moi.jsonb ->> 'name' AS "modeOfIssuance",
  COALESCE(getNatureOfContentName(i.jsonb #> '{natureOfContentTermIds}'), '[]'::jsonb) AS "natureOfContent",
  COALESCE(ah.holdings, '[]'::jsonb) AS "holdings",
  COALESCE(ai.items, '[]'::jsonb) AS "items"
FROM instance i
       LEFT JOIN mode_of_issuance moi ON (i.jsonb ->> 'modeOfIssuanceId')::uuid = moi.id
       LEFT JOIN aggregated_holdings ah ON i.id = ah.instanceid
       LEFT JOIN aggregated_items ai ON i.id = ai.instanceid
WHERE i.id = ANY($1);
$BODY$;