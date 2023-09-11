UPDATE ${myuniversity}_${mymodule}.instance
SET complete_updated_date = (jsonb -> 'metadata' ->> 'updatedDate')::timestamp with time zone;
