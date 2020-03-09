START TRANSACTION;

ALTER TABLE item DISABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item DISABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item DISABLE TRIGGER update_effective_location;
ALTER TABLE item DISABLE TRIGGER update_item_references;
ALTER TABLE item DISABLE TRIGGER update_item_status_date;

UPDATE ${myuniversity}_${mymodule}.instance
  SET jsonb = JSONB_SET(instance.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;

ALTER TABLE item ENABLE TRIGGER set_id_in_jsonb;
ALTER TABLE item ENABLE TRIGGER set_item_md_json_trigger;
ALTER TABLE item ENABLE TRIGGER update_effective_location;
ALTER TABLE item ENABLE TRIGGER update_item_references;
ALTER TABLE item ENABLE TRIGGER update_item_status_date;

END TRANSACTION;
