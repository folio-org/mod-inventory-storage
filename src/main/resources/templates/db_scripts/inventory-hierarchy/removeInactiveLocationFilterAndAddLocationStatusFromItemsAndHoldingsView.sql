drop function if exists ${myuniversity}_${mymodule}.get_items_and_holdings_view;
-- Add support name and discoveryDisplayName of location separately
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
                                                                          json_build_object(                                                                                            'permanentLocation',
                                                                                            jsonb_build_object('name', COALESCE(holdPermLoc.locJsonb ->> 'discoveryDisplayName', holdPermLoc.locJsonb ->> 'name'),
                                                                                                               'code', holdPermLoc.locJsonb ->> 'code',
                                                                                                               'id', holdPermLoc.locJsonb ->> 'id',
                                                                                                               'isActive', (holdPermLoc.locJsonb ->> 'isActive')::bool,
                                                                                                               'campusName', holdPermLoc.locCampJsonb ->> 'name',
                                                                                                               'libraryName', holdPermLoc.locLibJsonb ->> 'name',
                                                                                                               'libraryCode', holdPermLoc.locLibJsonb ->> 'code',
                                                                                                               'institutionName', holdPermLoc.locInstJsonb ->> 'name',
                                                                                                               'locationName', holdPermLoc.locJsonb ->> 'name'),
                                                                                            'temporaryLocation',
                                                                                            jsonb_build_object('name', COALESCE(holdTempLoc.locJsonb ->> 'discoveryDisplayName', holdTempLoc.locJsonb ->> 'name'),
                                                                                                               'code', holdTempLoc.locJsonb ->> 'code',
                                                                                                               'id', holdTempLoc.locJsonb ->> 'id',
                                                                                                               'isActive', (holdTempLoc.locJsonb ->> 'isActive')::bool,
                                                                                                               'campusName', holdTempLoc.locCampJsonb ->> 'name',
                                                                                                               'libraryName', holdTempLoc.locLibJsonb ->> 'name',
                                                                                                               'libraryCode', holdTempLoc.locLibJsonb ->> 'code',
                                                                                                               'institutionName', holdTempLoc.locInstJsonb ->> 'name',
                                                                                                               'locationName', holdTempLoc.locJsonb ->> 'name'),
                                                                                            'effectiveLocation',
                                                                                            jsonb_build_object('name', COALESCE(holdEffLoc.locJsonb ->> 'discoveryDisplayName', holdEffLoc.locJsonb ->> 'name'),
                                                                                                               'code', holdEffLoc.locJsonb ->> 'code',
                                                                                                               'id', holdEffLoc.locJsonb ->> 'id',
                                                                                                               'isActive', (holdEffLoc.locJsonb ->> 'isActive')::bool,
                                                                                                               'campusName', holdEffLoc.locCampJsonb ->> 'name',
                                                                                                               'libraryName', holdEffLoc.locLibJsonb ->> 'name',
                                                                                                               'libraryCode', holdEffLoc.locLibJsonb ->> 'code',
                                                                                                               'institutionName', holdEffLoc.locInstJsonb ->> 'name',
                                                                                                               'locationName', holdEffLoc.locJsonb ->> 'name'))
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
                                                                 CASE WHEN item.id IS NOT NULL THEN                                                                                          json_build_object('location',
                                                                                            jsonb_build_object('name', COALESCE(itemEffLoc.locJsonb ->> 'discoveryDisplayName', itemEffLoc.locJsonb ->> 'name'),
                                                                                                               'code', itemEffLoc.locJsonb ->> 'code',
                                                                                                               'id', itemEffLoc.locJsonb ->> 'id',
                                                                                                               'isActive', (itemEffLoc.locJsonb ->> 'isActive')::bool,
                                                                                                               'campusName', itemEffLoc.locCampJsonb ->> 'name',
                                                                                                               'libraryName', itemEffLoc.locLibJsonb ->> 'name',
                                                                                                               'libraryCode', itemEffLoc.locLibJsonb ->> 'code',
                                                                                                               'institutionName', itemEffLoc.locInstJsonb ->> 'name',
                                                                                                               'locationName', itemEffLoc.locJsonb ->> 'name'),
                                                                                            'permanentLocation',
                                                                                            jsonb_build_object('name', COALESCE(itemPermLoc.locJsonb ->> 'discoveryDisplayName', itemPermLoc.locJsonb ->> 'name'),
                                                                                                               'code', itemPermLoc.locJsonb ->> 'code',
                                                                                                               'id', itemPermLoc.locJsonb ->> 'id',
                                                                                                               'isActive', (itemPermLoc.locJsonb ->> 'isActive')::bool,
                                                                                                               'campusName', itemPermLoc.locCampJsonb ->> 'name',
                                                                                                               'libraryName', itemPermLoc.locLibJsonb ->> 'name',
                                                                                                               'libraryCode', itemPermLoc.locLibJsonb ->> 'code',
                                                                                                               'institutionName', itemPermLoc.locInstJsonb ->> 'name',
                                                                                                               'locationName', itemPermLoc.locJsonb ->> 'name'),
                                                                                            'temporaryLocation',
                                                                                            jsonb_build_object('name', COALESCE(itemTempLoc.locJsonb ->> 'discoveryDisplayName', itemTempLoc.locJsonb ->> 'name'),
                                                                                                               'code', itemTempLoc.locJsonb ->> 'code',
                                                                                                               'id', itemTempLoc.locJsonb ->> 'id',
                                                                                                               'isActive', (itemTempLoc.locJsonb ->> 'isActive')::bool,
                                                                                                               'campusName', itemTempLoc.locCampJsonb ->> 'name',
                                                                                                               'libraryName', itemTempLoc.locLibJsonb ->> 'name',
                                                                                                               'libraryCode', itemTempLoc.locLibJsonb ->> 'code',
                                                                                                               'institutionName', itemTempLoc.locInstJsonb ->> 'name',
                                                                                                               'locationName', itemTempLoc.locJsonb ->> 'name'))
                                                                      ELSE NULL END::jsonb,
                                                                 'callNumber', item.jsonb -> 'effectiveCallNumberComponents' ||
                                                                               jsonb_build_object('typeName', cnt.jsonb ->> 'name'),
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
                                              FILTER (WHERE item.id IS NOT NULL AND NOT ($2 AND COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false))), '[]'::jsonb)
                      ) itemsAndHoldings

              FROM ${myuniversity}_${mymodule}.holdings_record hr
                       JOIN ${myuniversity}_${mymodule}.instance i
                            ON i.id = hr.instanceid
                       JOIN viewInstances vi
                            ON vi.instId = i.id
                       LEFT JOIN ${myuniversity}_${mymodule}.bound_with_part bwp on bwp.holdingsrecordid = hr.id
                       LEFT JOIN ${myuniversity}_${mymodule}.item item
                                 ON item.holdingsrecordid = hr.id OR item.id = bwp.itemid
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
                  -- Holdings Effective location relation
                       LEFT JOIN viewLocations holdEffLoc
                                 ON (hr.jsonb ->> 'effectiveLocationId')::uuid = holdEffLoc.locId
                  -- Holdings Call number type relation
                       LEFT JOIN ${myuniversity}_${mymodule}.call_number_type hrcnt
                                 ON (hr.jsonb ->> 'callNumberTypeId')::uuid = hrcnt.id
                  -- Holdings Ill policy relation
                       LEFT JOIN ${myuniversity}_${mymodule}.ill_policy ilp
                                 ON hr.illpolicyid = ilp.id
              WHERE true
                AND NOT ($2 AND COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false))
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
