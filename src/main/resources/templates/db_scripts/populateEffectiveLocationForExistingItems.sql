START TRANSACTION;

UPDATE pg_trigger
  SET tgenabled = 'D'
WHERE tgrelid = '${myuniversity}_${mymodule}.item'::regclass::oid
  AND tgisinternal IS FALSE
  AND tgenabled = 'O';

UPDATE ${myuniversity}_${mymodule}.item AS it
  -- Since holdings_record.permanentLocationId and item.holdingsRecordId are required there can not be a NULL value
  SET jsonb = JSONB_SET(it.jsonb, '{effectiveLocationId}', COALESCE(
    it.jsonb->'temporaryLocationId', it.jsonb->'permanentLocationId',
    hr.jsonb->'temporaryLocationId', hr.jsonb->'permanentLocationId'
  )),
  effectivelocationid = COALESCE(
    it.jsonb->>'temporaryLocationId', it.jsonb->>'permanentLocationId',
    hr.jsonb->>'temporaryLocationId', hr.jsonb->>'permanentLocationId'
  )::uuid
FROM ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;

UPDATE pg_trigger
  SET tgenabled = 'O'
WHERE tgrelid = '${myuniversity}_${mymodule}.item'::regclass::oid
  AND tgisinternal IS FALSE
  AND tgenabled = 'D';

END TRANSACTION;
