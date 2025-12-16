CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.instance_holdings_item_view
AS
SELECT instance.id as id, JSONB_BUILD_OBJECT(
    'instanceId',      instance.id,
    'instance',        instance.jsonb,
	  'holdingsRecords', (SELECT jsonb_agg(jsonb) FROM ${myuniversity}_${mymodule}.holdings_record
	                      WHERE holdings_record.instanceId = instance.id),
	  'items',           (SELECT jsonb_agg(item.jsonb) FROM ${myuniversity}_${mymodule}.holdings_record as hr
	                      JOIN ${myuniversity}_${mymodule}.item
	                        ON item.holdingsRecordId=hr.id AND hr.instanceId = instance.id),
    'isBoundWith',     (SELECT EXISTS(SELECT 1 FROM ${myuniversity}_${mymodule}.bound_with_part as bw
                        JOIN ${myuniversity}_${mymodule}.item as it
                          ON it.id = bw.itemid
                        JOIN ${myuniversity}_${mymodule}.holdings_record as hr
                          ON hr.id = bw.holdingsrecordid
                        WHERE hr.instanceId = instance.id LIMIT 1))
	) AS jsonb
  FROM ${myuniversity}_${mymodule}.instance;
