UPDATE ${myuniversity}_${mymodule}.item AS it
-- Since holdings_record.permanentLocationId and item.holdingsRecordId are required there can not be a NULL value
SET	jsonb = JSONB_SET(it.jsonb,
			'{effectiveLocationId}',
			COALESCE(it.jsonb->'temporaryLocationId', it.jsonb->'permanentLocationId',
			hr.jsonb->'temporaryLocationId', hr.jsonb->'permanentLocationId')
)
FROM ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;
