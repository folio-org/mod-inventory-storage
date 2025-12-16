UPDATE ${myuniversity}_${mymodule}.holdings_record
  SET jsonb = JSONB_SET(holdings_record.jsonb, '{effectiveLocationId}',
                        TO_JSONB(COALESCE(temporarylocationid, permanentlocationid)))
WHERE effectivelocationid IS NULL;
