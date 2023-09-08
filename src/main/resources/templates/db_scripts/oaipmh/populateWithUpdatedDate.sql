UPDATE ${myuniversity}_${mymodule}.instance
SET completeUpdatedDate = (jsonb -> 'metadata' ->> 'updatedDate')::timestamp with time zone;
