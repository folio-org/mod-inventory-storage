CREATE OR REPLACE FUNCTION strtotimestamp(text)
    RETURNS timestamp with time zone
    LANGUAGE 'sql'
    COST 100
    IMMUTABLE STRICT PARALLEL UNSAFE
AS $BODY$
SELECT $1::timestamptz
$BODY$;

CREATE OR REPLACE FUNCTION dateormin(timestamp with time zone)
    RETURNS timestamp with time zone
    LANGUAGE 'sql'
    COST 100
    IMMUTABLE PARALLEL UNSAFE
AS $BODY$
SELECT COALESCE($1, timestamptz '1970-01-01')
$BODY$;

CREATE OR REPLACE FUNCTION dateormax(timestamp with time zone)
    RETURNS timestamp with time zone
    LANGUAGE 'sql'
    COST 100
    IMMUTABLE PARALLEL UNSAFE
AS $BODY$
SELECT COALESCE($1, timestamptz '2050-01-01')
$BODY$;

CREATE OR REPLACE FUNCTION getelectronicaccessname(val jsonb)
    RETURNS jsonb
    LANGUAGE 'sql'
    COST 100
    VOLATILE STRICT PARALLEL UNSAFE
AS $BODY$
SELECT jsonb_agg(e)
FROM ( SELECT e || jsonb_build_object('name', ( SELECT jsonb ->> 'name'
                                                FROM electronic_access_relationship
                                                WHERE id = nullif(e ->> 'relationshipId','')::uuid )) e
       FROM jsonb_array_elements($1) AS e ) e1
$BODY$;

CREATE OR REPLACE FUNCTION getitemnotetypename(val jsonb)
    RETURNS jsonb
    LANGUAGE 'sql'
    COST 100
    VOLATILE STRICT PARALLEL UNSAFE
AS $BODY$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'itemNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('itemNoteTypeName', ( SELECT jsonb ->> 'name'
                                                       FROM item_note_type
                                                       WHERE id = nullif(e ->> 'itemNoteTypeId','')::uuid )) e
       FROM jsonb_array_elements( $1 ) AS e
       WHERE NOT (e ->> 'staffOnly')::bool ) e1
$BODY$;

CREATE OR REPLACE FUNCTION getholdingnotetypename(val jsonb)
    RETURNS jsonb
    LANGUAGE 'sql'
    COST 100
    VOLATILE STRICT PARALLEL UNSAFE
AS $BODY$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'holdingsNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('holdingsNoteTypeName', ( SELECT jsonb ->> 'name'
                                                           FROM holdings_note_type
                                                           WHERE id = nullif(e ->> 'holdingsNoteTypeId','')::uuid )) e
       FROM jsonb_array_elements( $1 ) AS e
       WHERE NOT (e ->> 'staffOnly')::bool ) e1
$BODY$;

CREATE OR REPLACE FUNCTION getstatisticalcodes(val jsonb)
    RETURNS jsonb
    LANGUAGE 'sql'
    COST 100
    VOLATILE STRICT PARALLEL UNSAFE
AS $BODY$
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
$BODY$;

CREATE OR REPLACE FUNCTION getnatureofcontentname(val jsonb)
    RETURNS jsonb
    LANGUAGE 'sql'
    COST 100
    VOLATILE STRICT PARALLEL UNSAFE
AS $BODY$
SELECT jsonb_agg(DISTINCT e.name)
FROM (
         SELECT (jsonb ->> 'name') AS "name"
         FROM nature_of_content_term
                  JOIN jsonb_array_elements($1) as insNoctIds
                       ON id = nullif(insNoctIds ->> 0,'')::uuid) e
$BODY$;
