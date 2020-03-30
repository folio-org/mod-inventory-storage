UPDATE ${myuniversity}_${mymodule}.item_log SET effectiveLocationId = (jsonb->>'effectiveLocationId')::uuid;
