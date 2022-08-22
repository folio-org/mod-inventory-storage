CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.instance_set AS
  SELECT
    instance.id,
    (SELECT COALESCE(jsonb_agg(holdings_record.jsonb), '[]'::jsonb) FROM holdings_record
      WHERE holdings_record.instanceid = instance.id)
      AS holdings_records,
    (SELECT COALESCE(jsonb_agg(item.jsonb), '[]'::jsonb) FROM holdings_record hr
      JOIN item ON item.holdingsrecordid = hr.id AND hr.instanceid = instance.id)
      AS items,
    (SELECT COALESCE(jsonb_agg(jsonb), '[]'::jsonb) FROM preceding_succeeding_title
      WHERE preceding_succeeding_title.succeedinginstanceid=instance.id)
      AS preceding_titles,
    (SELECT COALESCE(jsonb_agg(jsonb), '[]'::jsonb) FROM preceding_succeeding_title
      WHERE preceding_succeeding_title.precedinginstanceid=instance.id)
      AS succeeding_titles,
    (SELECT COALESCE(jsonb_agg(jsonb), '[]'::jsonb) FROM instance_relationship
      WHERE instance_relationship.subinstanceid=instance.id)
      AS super_instance_relationships,
    (SELECT COALESCE(jsonb_agg(jsonb), '[]'::jsonb) FROM instance_relationship
      WHERE instance_relationship.superinstanceid=instance.id)
      AS sub_instance_relationships
  FROM instance;
