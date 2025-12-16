-- Update source field to 'folio' for specific loan types
UPDATE ${myuniversity}_${mymodule}.loan_type
SET jsonb = jsonb_set(jsonb, '{source}', '"folio"', true)
WHERE id IN (
  '2b94c631-fca9-4892-a730-03ee529ffe27',
  'e8b311a6-3b21-43f2-a269-dd9310cb2d0e',
  '2e48e713-17f3-4c13-a9f8-23845bb210a4',
  'a1dc1ce3-d56f-4d8a-b498-d5d674ccc845'
);

-- Update source field to 'local' for all other loan types that don't have source set
UPDATE ${myuniversity}_${mymodule}.loan_type
SET jsonb = jsonb_set(jsonb, '{source}', '"local"', true)
WHERE jsonb->>'source' IS NULL;
