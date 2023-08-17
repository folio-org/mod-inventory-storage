WITH item_circnotes as (
	SELECT itemId, ARRAY_AGG(circNote) AS circNotes FROM (
	  SELECT item.id AS itemId, CASE
	  WHEN circulationNote ->> 'id' IS NULL
		  THEN jsonb_build_object(
			  'id', gen_random_uuid(),
			  'noteType' ,circulationNote ->> 'noteType',
			  'note', circulationNote ->> 'note',
			  'source', circulationNote ->> 'source',
			  'date', circulationNote ->> 'date',
			  'staffOnly', circulationNote ->> 'staffOnly')
		  ELSE jsonb_build_object(
			  'id', circulationNote ->> 'id',
			  'noteType' ,circulationNote ->> 'noteType',
			  'note', circulationNote ->> 'note',
			  'source', circulationNote ->> 'source',
			  'date', circulationNote ->> 'date',
			  'staffOnly', circulationNote ->> 'staffOnly')
		  END as circNote
    FROM ${myuniversity}_${mymodule}.item, jsonb_array_elements((jsonb ->> 'circulationNotes')::jsonb) WITH ORDINALITY arr(circulationNote, index)
  ) AS tableA
  GROUP BY itemId
)
UPDATE ${myuniversity}_${mymodule}.item
SET jsonb = jsonb_set(jsonb, '{circulationNotes}', json_build_array(item_circnotes.circNotes)::jsonb)
FROM item_circnotes
WHERE item.id = item_circnotes.itemId;
