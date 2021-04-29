START TRANSACTION;

UPDATE ${myuniversity}_${mymodule}.holdings_record
  SET jsonb = JSONB_SET(holdings_record.jsonb, '{effectiveLocationId}', TO_JSONB(holdings_record.jsonb->>'temporaryLocationId'))
WHERE jsonb->>'temporaryLocationId' IS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.holdings_record
  SET jsonb = JSONB_SET(holdings_record.jsonb, '{effectiveLocationId}', TO_JSONB(holdings_record.jsonb->>'permanentLocationId'))
WHERE jsonb->>'temporaryLocationId' IS NULL;

END TRANSACTION;