\timing on

START TRANSACTION;

UPDATE diku_mod_inventory_storage.item AS it
SET jsonb = jsonb_set(
  it.jsonb,
  '{effectiveCallNumberComponents}',
  jsonb_build_object(
    'callNumber', COALESCE(it.jsonb->'itemLevelCallNumber', hr.jsonb->'callNumber'),
    'prefix', COALESCE(it.jsonb->'itemLevelCallNumberPrefix', hr.jsonb->'callNumberPrefix'),
    'suffix', COALESCE(it.jsonb->'itemLevelCallNumberSuffix', hr.jsonb->'callNumberSuffix'),
    'typeId', COALESCE(it.jsonb->'itemLevelCallNumberTypeId', hr.jsonb->'callNumberTypeId')
  )
)
FROM diku_mod_inventory_storage.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;

--END TRANSACTION;



--START TRANSACTION;

UPDATE diku_mod_inventory_storage.item AS it
-- Since holdings_record.permanentLocationId and item.holdingsRecordId are required there can not be a NULL value
SET jsonb = JSONB_SET(it.jsonb,
  '{effectiveLocationId}',
  COALESCE(it.jsonb->'temporaryLocationId', it.jsonb->'permanentLocationId', hr.jsonb->'temporaryLocationId', hr.jsonb->'permanentLocationId')
)
FROM diku_mod_inventory_storage.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;

--END TRANSACTION;



--START TRANSACTION;

-- Make the array of copy numbers as a single value, take the first value from the array
UPDATE diku_mod_inventory_storage.item AS it
  SET jsonb = jsonb_set(it.jsonb, '{copyNumber}', it.jsonb#>'{copyNumbers, 0}')
WHERE it.jsonb->>'copyNumbers' IS NOT NULL AND jsonb_array_length(it.jsonb->'copyNumbers') > 0;

-- Remove the copyNumbers property even if it has zero length.
UPDATE diku_mod_inventory_storage.item AS it
  SET jsonb = it.jsonb - 'copyNumbers'
WHERE it.jsonb->'copyNumbers' IS NOT NULL;

--END TRANSACTION;



--START TRANSACTION;

UPDATE diku_mod_inventory_storage.item SET effectiveLocationId = (jsonb->>'effectiveLocationId')::uuid;

--END TRANSACTION;



--START TRANSACTION;

UPDATE diku_mod_inventory_storage.instance
SET	jsonb = JSONB_SET(instance.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;

--END TRANSACTION;
ABORT;


--- BELOW ARE RESULTS
--UPDATE 3500000
--Time: 675195.747 ms (11:15.196)
--UPDATE 3500000
--Time: 777869.857 ms (12:57.870)
--UPDATE 0
--Time: 64790.896 ms (01:04.791)
--UPDATE 0
--Time: 67723.513 ms (01:07.724)
--UPDATE 3500000
--Time: 814091.566 ms (13:34.092)
--UPDATE 999998
--Time: 792420.441 ms (13:12.420)
--((675195.747+777869.857+64790.896+67723.513+814091.566+792420.441) / 1000) / 60 = 53.2015336667 minutes
