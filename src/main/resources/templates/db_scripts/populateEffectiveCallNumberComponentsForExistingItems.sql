START TRANSACTION;

UPDATE pg_trigger
  SET tgenabled = 'D'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'item')
  AND tgisinternal IS FALSE
  AND tgenabled = 'O';

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

UPDATE pg_trigger
  SET tgenabled = 'O'
WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relname = 'item')
  AND tgisinternal IS FALSE
  AND tgenabled = 'D';

END TRANSACTION;
