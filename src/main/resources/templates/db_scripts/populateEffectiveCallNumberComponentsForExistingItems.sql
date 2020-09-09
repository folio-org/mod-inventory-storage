START TRANSACTION;

ALTER TABLE ${myuniversity}_${mymodule}.item DISABLE TRIGGER USER;

UPDATE ${myuniversity}_${mymodule}.item AS it
  SET jsonb = JSONB_SET(
    it.jsonb,
    '{effectiveCallNumberComponents}',
    jsonb_build_object(
      'callNumber', COALESCE(it.jsonb->'itemLevelCallNumber', hr.jsonb->'callNumber'),
      'prefix', COALESCE(it.jsonb->'itemLevelCallNumberPrefix', hr.jsonb->'callNumberPrefix'),
      'suffix', COALESCE(it.jsonb->'itemLevelCallNumberSuffix', hr.jsonb->'callNumberSuffix'),
      'typeId', COALESCE(it.jsonb->'itemLevelCallNumberTypeId', hr.jsonb->'callNumberTypeId')
    )
  )
FROM ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;

ALTER TABLE ${myuniversity}_${mymodule}.item ENABLE TRIGGER USER;

END TRANSACTION;
