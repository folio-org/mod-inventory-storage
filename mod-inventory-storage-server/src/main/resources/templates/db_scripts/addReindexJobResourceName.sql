UPDATE ${myuniversity}_${mymodule}.reindex_job
SET jsonb = jsonb ||
            '{
               "resourceName": "Unknown"
             }'
WHERE (jsonb->'resourceName') IS NULL;
