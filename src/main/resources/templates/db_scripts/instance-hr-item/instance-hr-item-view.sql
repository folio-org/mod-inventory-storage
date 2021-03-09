CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.instance_holdings_item_view
AS
SELECT instance.id as id, (instance.jsonb ||
  JSONB_BUILD_OBJECT(
	  'holdings', COALESCE(
		  jsonb_agg(DISTINCT holdings_record.jsonb)
		  FILTER (WHERE holdings_record.id IS NOT NULL),
		  '[]'::jsonb),
	  'items', COALESCE(
		  jsonb_agg(DISTINCT item.jsonb)
		  FILTER (WHERE item.id IS NOT NULL),
		  '[]'::jsonb))
	) AS jsonb
  FROM ${myuniversity}_${mymodule}.instance
    LEFT JOIN ${myuniversity}_${mymodule}.holdings_record ON instance.id = holdings_record.instanceId
    LEFT JOIN ${myuniversity}_${mymodule}.item ON holdings_record.id = item.holdingsRecordId
  GROUP BY instance.id;
