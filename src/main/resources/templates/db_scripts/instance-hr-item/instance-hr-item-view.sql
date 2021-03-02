CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.instance_holdings_item_view
AS
SELECT instance.id as id,
  JSONB_BUILD_OBJECT(
	  'instanceId', instance.id,
	  'instance', instance.jsonb,
	  'holdingsRecords', jsonb_agg(DISTINCT holdings_record.jsonb),
	  'items', jsonb_agg(item.jsonb)) AS jsonb
  FROM ${myuniversity}_${mymodule}.instance
    JOIN ${myuniversity}_${mymodule}.holdings_record ON instance.id = holdings_record.instanceId
    JOIN ${myuniversity}_${mymodule}.item ON holdings_record.id = item.holdingsRecordId
  GROUP BY instance.id;
