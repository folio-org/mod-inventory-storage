CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

--Update circulationNotes of item only if there is at least one circulationNote with id equals null
WITH item_circnotes AS (
  SELECT itemId, ARRAY_AGG(circNote) AS circNotes, BOOL_OR(changed) AS changed
  FROM (
    SELECT
      item.id AS itemId,
      CASE WHEN circulationNote ->> 'id' IS NULL THEN jsonb_set(circulationNote, '{id}', to_jsonb(public.uuid_generate_v4())) ELSE circulationNote END AS circNote,
      CASE WHEN circulationNote ->> 'id' IS NULL THEN true ELSE false END AS changed
    FROM ${myuniversity}_${mymodule}.item, jsonb_array_elements((jsonb ->> 'circulationNotes')::jsonb) WITH ORDINALITY arr(circulationNote, index)
  ) AS tableA
  GROUP BY itemId
)
UPDATE ${myuniversity}_${mymodule}.item
SET jsonb = jsonb_set(jsonb, '{circulationNotes}', to_jsonb(item_circnotes.circNotes))
FROM item_circnotes
WHERE item.id = item_circnotes.itemId AND item_circnotes.changed;
