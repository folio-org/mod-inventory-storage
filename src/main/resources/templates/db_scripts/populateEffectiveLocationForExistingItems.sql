-- 1st step - update items with holding records linked.
UPDATE  ${myuniversity}_${mymodule}.item AS it
SET jsonb =
	CASE
		WHEN it.jsonb->'temporaryLocationId' IS NOT NULL
			THEN jsonb_set(it.jsonb, '{effectiveLocationId}', it.jsonb->'temporaryLocationId')
		WHEN it.jsonb->'permanentLocationId' IS NOT NULL
			THEN jsonb_set(it.jsonb, '{effectiveLocationId}', it.jsonb->'permanentLocationId')
		WHEN hr.jsonb->'temporaryLocationId' IS NOT NULL
			THEN jsonb_set(it.jsonb, '{effectiveLocationId}', hr.jsonb->'temporaryLocationId')
		WHEN hr.jsonb->'permanentLocationId' IS NOT NULL
			THEN jsonb_set(it.jsonb, '{effectiveLocationId}', hr.jsonb->'permanentLocationId')
		-- do nothing if all locations are nulls
		ELSE it.jsonb
	END
FROM  ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE it.jsonb->'effectiveLocationId' IS NULL AND hr.id = it.holdingsrecordid;

-- 2nd step - update 'orphaned' items.
UPDATE  ${myuniversity}_${mymodule}.item it
SET jsonb =
	CASE
		WHEN it.jsonb->'temporaryLocationId' IS NOT NULL
			THEN jsonb_set(it.jsonb, '{effectiveLocationId}', it.jsonb->'temporaryLocationId')
		WHEN it.jsonb->'permanentLocationId' IS NOT NULL
			THEN jsonb_set(it.jsonb, '{effectiveLocationId}', it.jsonb->'permanentLocationId')
		-- do nothing if all locations are nulls
		ELSE it.jsonb
	END
WHERE it.jsonb->'effectiveLocationId' IS NULL AND it.holdingsrecordid IS NULL;
