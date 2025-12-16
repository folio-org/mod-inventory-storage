CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getElectronicAccessName(val jsonb) RETURNS jsonb AS
$$
SELECT jsonb_agg(e)
FROM ( SELECT e || jsonb_build_object('name', ( SELECT jsonb ->> 'name'
                                                FROM ${myuniversity}_${mymodule}.electronic_access_relationship
                                                WHERE id = nullif(e ->> 'relationshipId','')::uuid )) e
       FROM jsonb_array_elements($1) AS e ) e1
$$ LANGUAGE sql strict;