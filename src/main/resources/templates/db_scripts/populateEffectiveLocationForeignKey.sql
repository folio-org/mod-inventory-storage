UPDATE ${myuniversity}_${mymodule}.item SET effectiveLocationId = (jsonb->>'effectiveLocationId')::uuid;
