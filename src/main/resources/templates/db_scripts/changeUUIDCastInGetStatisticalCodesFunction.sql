DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.getStatisticalCodes;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.getStatisticalCodes(val jsonb) RETURNS jsonb AS
$$
WITH stat_codes(statCodeId, statCodeJsonb, statCodeTypeJsonb) AS (
SELECT sc.id, sc.jsonb, sct.jsonb
FROM statistical_code sc
JOIN statistical_code_type sct ON sct.id = sc.statisticalcodetypeid
)
SELECT jsonb_agg(DISTINCT jsonb_build_object('id', sc.statCodeJsonb ->> 'id') ||
							  jsonb_build_object('code', sc.statCodeJsonb ->> 'code') ||
							  jsonb_build_object('name', sc.statCodeJsonb ->> 'name') ||
							  jsonb_build_object('statisticalCodeType', sc.statCodeTypeJsonb ->> 'name') ||
							  jsonb_build_object('source', sc.statCodeTypeJsonb ->> 'source'))
FROM jsonb_array_elements( $1 ) AS e,
     stat_codes sc
WHERE sc.statCodeId::text = (e ->> 0)::text
$$ LANGUAGE sql strict;
