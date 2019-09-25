UPDATE ${myuniversity}_${mymodule}.item AS it
SET jsonb = jsonb_set(jsonb, '{effectiveLocationId}',
	CASE
	  -- Check item's locations first
		WHEN jsonb->'temporaryLocationId' IS NOT NULL THEN jsonb->'temporaryLocationId'
		WHEN jsonb->'permanentLocationId' IS NOT NULL THEN jsonb->'permanentLocationId'
		-- If no item's locations present - check holding's locations, and return an empty string by default
		ELSE (
			SELECT COALESCE(hr.jsonb->'temporaryLocationId', hr.jsonb->'permanentLocationId', to_jsonb(''::text))
		    FROM ${myuniversity}_${mymodule}.holdings_record AS hr
			WHERE hr.id = it.holdingsrecordid LIMIT 1
		)
	END
)
WHERE jsonb->'effectiveLocationId' IS NULL;
