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
) ||
jsonb_build_object(
  -- Since holdings_record.permanentLocationId and item.holdingsRecordId are required there can not be a NULL value
  'effectiveLocationId', COALESCE(it.jsonb->'temporaryLocationId', it.jsonb->'permanentLocationId',
  hr.jsonb->'temporaryLocationId', hr.jsonb->'permanentLocationId')
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
--Time: 699738.865 ms (11:39.739)
--UPDATE 0
--Time: 67294.319 ms (01:07.294)
--UPDATE 0
--Time: 65841.767 ms (01:05.842)
--UPDATE 3500000
--Time: 786608.793 ms (13:06.609)
--UPDATE 999998
--Time: 768086.138 ms (12:48.086)
--((699738.865+67294.319+65841.767+786608.793+768086.138) / 1000) / 60 = 39.7928313667 minutes
