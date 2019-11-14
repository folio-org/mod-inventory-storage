UPDATE ${myuniversity}_${mymodule}.item AS it
SET jsonb =
  CASE
    WHEN it.jsonb->'itemLevelCallNumber' IS NOT NULL
      THEN
        CASE
          WHEN it.jsonb->'effectiveCallNumberComponents' IS NULL THEN
            jsonb_set(it.jsonb, '{effectiveCallNumberComponents}',
              jsonb_build_object('callNumber', it.jsonb->'itemLevelCallNumber'))
          ELSE
            jsonb_set(it.jsonb, '{effectiveCallNumberComponents,callNumber}', it.jsonb->'itemLevelCallNumber')
        END
    WHEN hr.jsonb->'callNumber' IS NOT NULL
      THEN
        CASE
          WHEN it.jsonb->'effectiveCallNumberComponents' IS NULL THEN
            jsonb_set(it.jsonb, '{effectiveCallNumberComponents}',
              jsonb_build_object('callNumber', hr.jsonb->'callNumber'))
          ELSE
            jsonb_set(it.jsonb, '{effectiveCallNumberComponents,callNumber}', hr.jsonb->'callNumber')
        END
    ELSE it.jsonb
  END
FROM ${myuniversity}_${mymodule}.holdings_record AS hr
WHERE hr.id = it.holdingsrecordid;
