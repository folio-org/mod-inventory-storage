CREATE OR REPLACE VIEW instance_holdings_item_view
 AS
 SELECT id,
    jsonb_build_object('instanceId', id, 'instance', jsonb, 'holdingsRecords', ( SELECT jsonb_agg(holdings_record.jsonb) AS jsonb_agg
           FROM holdings_record
          WHERE holdings_record.instanceid = instance.id), 'items', ( SELECT jsonb_agg(item.jsonb) AS jsonb_agg
           FROM holdings_record hr
             JOIN item ON item.holdingsrecordid = hr.id AND hr.instanceid = instance.id), 'isBoundWith', ( SELECT (EXISTS ( SELECT 1
                   FROM bound_with_part bw
                     JOIN item it ON it.id = bw.itemid
                     JOIN holdings_record hr ON hr.id = bw.holdingsrecordid
                  WHERE hr.instanceid = instance.id
                 LIMIT 1)) AS "exists")) AS jsonb
   FROM instance;