CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.instance_holdings_item_view
AS
WITH hri AS (
	SELECT hr.instanceId,
	  JSONB_BUILD_OBJECT(
		  'holding',     hr.jsonb,
		  'items',       COALESCE(jsonb_agg(item.jsonb) FILTER (WHERE item.jsonb IS NOT NULL), '[]'::jsonb)
	  ) as jsonb
	 FROM ${myuniversity}_${mymodule}.holdings_record as hr
	 LEFT JOIN ${myuniversity}_${mymodule}.item on item.holdingsRecordId = hr.id
	 GROUP BY hr.id
) SELECT instance.id AS id, JSONB_BUILD_OBJECT(
    'instanceId',       instance.id,
    'instance',         instance.jsonb,
	  'holdingsAndItems', COALESCE(jsonb_agg(hri.jsonb) FILTER (WHERE hri.jsonb IS NOT NULL), '[]'::jsonb)
) AS jsonb
FROM ${myuniversity}_${mymodule}.instance
  LEFT JOIN hri ON hri.instanceId = instance.id
GROUP BY instance.id;
