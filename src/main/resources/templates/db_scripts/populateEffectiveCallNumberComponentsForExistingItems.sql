UPDATE ${myuniversity}_${mymodule}.item AS it
SET jsonb = jsonb_set(
  it.jsonb,
  '{effectiveCallNumberComponents}',
  jsonb_build_object(
    'callNumber', COALESCE(it.jsonb->'itemLevelCallNumber', hr.jsonb->'callNumber'),
    'callNumberPrefix', COALESCE(it.jsonb->'itemLevelCallNumberPrefix', hr.jsonb->'callNumberPrefix'),
    'callNumberSuffix', COALESCE(it.jsonb->'itemLevelCallNumberSuffix', hr.jsonb->'callNumberSuffix')
  )
)
FROM ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;
