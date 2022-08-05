CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.instance_set AS
  SELECT
    instance.id,
    (SELECT jsonb_agg(holdings_record.jsonb) FROM holdings_record WHERE holdings_record.instanceid = instance.id)
      AS holdings_records,
    (SELECT jsonb_agg(item.jsonb) FROM holdings_record hr JOIN item ON item.holdingsrecordid = hr.id AND hr.instanceid = instance.id)
      AS items,
    (SELECT jsonb_agg(jsonb) FROM preceding_succeeding_title WHERE preceding_succeeding_title.succeedinginstanceid=instance.id)
      AS preceding_titles,
    (SELECT jsonb_agg(jsonb) FROM preceding_succeeding_title WHERE preceding_succeeding_title.precedinginstanceid=instance.id)
      AS succeeding_titles,
    (SELECT jsonb_agg(jsonb) FROM instance_relationship WHERE instance_relationship.subinstanceid=instance.id)
      AS super_instance_relationships,
    (SELECT jsonb_agg(jsonb) FROM instance_relationship WHERE instance_relationship.superinstanceid=instance.id)
      AS sub_instance_relationships
  FROM instance;
