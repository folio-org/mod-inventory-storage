START TRANSACTION;
-- Make the array of copy numbers as a single value, take the first value from the array
UPDATE ${myuniversity}_${mymodule}.item AS it
  SET jsonb = jsonb_set(it.jsonb, '{copyNumber}', it.jsonb#>'{copyNumbers, 0}')
WHERE it.jsonb->>'copyNumbers' IS NOT NULL AND jsonb_array_length(it.jsonb->'copyNumbers') > 0;

-- Remove the copyNumbers property even if it has zero length.
UPDATE ${myuniversity}_${mymodule}.item AS it
  SET jsonb = it.jsonb - 'copyNumbers'
WHERE it.jsonb->'copyNumbers' IS NOT NULL;

END TRANSACTION;
