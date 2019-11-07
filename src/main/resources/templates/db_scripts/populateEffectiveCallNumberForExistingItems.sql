UPDATE ${myuniversity}_${mymodule}.item AS it
SET jsonb =
  CASE
    WHEN it.jsonb->'itemLevelCallNumber' IS NOT NULL
      THEN jsonb_set(it.jsonb, '{effectiveCallNumber}', it.jsonb->'itemLevelCallNumber')
    WHEN hr.jsonb->'callNumber' IS NOT NULL
      THEN jsonb_set(it.jsonb, '{effectiveCallNumber}', hr.jsonb->'callNumber')
    ELSE it.jsonb
  END
FROM ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;
