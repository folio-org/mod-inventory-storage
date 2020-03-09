START TRANSACTION;

-- update_item_references TRIGGER is left enabled to update the item effectiveLocationId.
ALTER TABLE item DISABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item DISABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item DISABLE TRIGGER update_effective_location;
ALTER TABLE item DISABLE TRIGGER update_item_status_date;

UPDATE ${myuniversity}_${mymodule}.item AS it
  SET jsonb = jsonb_set(
    it.jsonb,
    '{effectiveCallNumberComponents}',
    jsonb_build_object(
      'callNumber', COALESCE(it.jsonb->'itemLevelCallNumber', hr.jsonb->'callNumber'),
      'prefix', COALESCE(it.jsonb->'itemLevelCallNumberPrefix', hr.jsonb->'callNumberPrefix'),
      'suffix', COALESCE(it.jsonb->'itemLevelCallNumberSuffix', hr.jsonb->'callNumberSuffix'),
      'typeId', COALESCE(it.jsonb->'itemLevelCallNumberTypeId', hr.jsonb->'callNumberTypeId')
    )
  ) ||
  jsonb_build_object(
    -- Since holdings_record.permanentLocationId and item.holdingsRecordId are required there can not be a NULL value
    'effectiveLocationId', COALESCE(it.jsonb->'temporaryLocationId', it.jsonb->'permanentLocationId',
    hr.jsonb->'temporaryLocationId', hr.jsonb->'permanentLocationId')
  )
FROM ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;

ALTER TABLE item ENABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item ENABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item ENABLE TRIGGER update_effective_location;
ALTER TABLE item ENABLE TRIGGER update_item_status_date;

END TRANSACTION;
