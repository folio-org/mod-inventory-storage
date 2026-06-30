DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.get_instance_summary(uuid, boolean, boolean);
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.get_instance_summary(uuid, boolean);
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.get_instance_summary_multi_tenant(text, uuid);
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.merge_instance_summaries(jsonb, text);

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.get_instance_summary(_instance_id uuid)
  RETURNS jsonb
  LANGUAGE sql
AS $function$
WITH
  target_instance AS (
    SELECT i.id, i.jsonb
    FROM ${myuniversity}_${mymodule}.instance i
    WHERE i.id = _instance_id
  ),
  holdings_all AS (
    SELECT
      hr.id,
      hr.instanceid,
      hr.jsonb,
      COALESCE((hr.jsonb ->> 'discoverySuppress')::boolean, false) AS discovery_suppress
    FROM ${myuniversity}_${mymodule}.holdings_record hr
      JOIN target_instance ti ON hr.instanceid = ti.id
  ),
  holdings_not_suppressed AS (
    SELECT *
    FROM holdings_all
    WHERE NOT discovery_suppress
  ),
  direct_items_all AS (
    SELECT
      item.id,
      item.materialtypeid,
      NULLIF(item.jsonb ->> 'effectiveShelvingOrder', '') AS effective_shelving_order,
      CASE
        WHEN jsonb_typeof(item.jsonb -> 'electronicAccess') = 'array'
          AND item.jsonb -> 'electronicAccess' <> '[]'::jsonb
          THEN item.jsonb -> 'electronicAccess'
        ELSE NULL
      END AS electronic_access,
      COALESCE((hr.jsonb ->> 'discoverySuppress')::boolean, false) AS holdings_discovery_suppress,
      COALESCE((item.jsonb ->> 'discoverySuppress')::boolean, false) AS item_discovery_suppress
    FROM holdings_all hr
      JOIN ${myuniversity}_${mymodule}.item item ON item.holdingsrecordid = hr.id
  ),
  bound_with_items_all AS (
    SELECT
      item.id,
      item.materialtypeid,
      NULLIF(item.jsonb ->> 'effectiveShelvingOrder', '') AS effective_shelving_order,
      CASE
        WHEN jsonb_typeof(item.jsonb -> 'electronicAccess') = 'array'
          AND item.jsonb -> 'electronicAccess' <> '[]'::jsonb
          THEN item.jsonb -> 'electronicAccess'
        ELSE NULL
      END AS electronic_access,
      COALESCE((hr.jsonb ->> 'discoverySuppress')::boolean, false) AS holdings_discovery_suppress,
      COALESCE((item.jsonb ->> 'discoverySuppress')::boolean, false) AS item_discovery_suppress
    FROM holdings_all hr
      JOIN ${myuniversity}_${mymodule}.bound_with_part bwp ON bwp.holdingsrecordid = hr.id
      JOIN ${myuniversity}_${mymodule}.item item ON item.id = bwp.itemid
  ),
  items_all AS (
    SELECT DISTINCT ON (all_items.id)
      all_items.id,
      all_items.materialtypeid,
      all_items.effective_shelving_order,
      all_items.electronic_access,
      all_items.holdings_discovery_suppress,
      all_items.item_discovery_suppress
    FROM (
      SELECT * FROM direct_items_all
      UNION ALL
      SELECT * FROM bound_with_items_all
    ) all_items
    ORDER BY all_items.id
  ),
  items_not_suppressed AS (
    SELECT *
    FROM items_all
    WHERE NOT (holdings_discovery_suppress OR item_discovery_suppress)
  ),
  holdings_counts AS (
    SELECT
      COUNT(*) AS total,
      COUNT(*) FILTER (WHERE discovery_suppress) AS suppressed_from_discovery,
      COUNT(*) FILTER (WHERE NOT discovery_suppress) AS not_suppressed_from_discovery
    FROM holdings_all
  ),
  items_counts AS (
    SELECT
      COUNT(*) AS total,
      COUNT(*) FILTER (WHERE item_discovery_suppress) AS suppressed_from_discovery,
      COUNT(*) FILTER (WHERE holdings_discovery_suppress) AS suppressed_by_holdings,
      COUNT(*) FILTER (WHERE holdings_discovery_suppress OR item_discovery_suppress)
        AS suppressed_from_discovery_or_by_holdings,
      COUNT(*) FILTER (WHERE NOT (holdings_discovery_suppress OR item_discovery_suppress))
        AS not_suppressed_from_discovery
    FROM items_all
  ),
  electronic_access_data AS (
    SELECT 'allRecords' AS scope, 1 AS source_order, electronic_access.ordinal, electronic_access.value
    FROM target_instance ti
      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(ti.jsonb -> 'electronicAccess', '[]'::jsonb))
        WITH ORDINALITY AS electronic_access(value, ordinal)
    UNION ALL
    SELECT 'notSuppressedFromDiscoveryRecords' AS scope, 1 AS source_order, electronic_access.ordinal, electronic_access.value
    FROM target_instance ti
      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(ti.jsonb -> 'electronicAccess', '[]'::jsonb))
        WITH ORDINALITY AS electronic_access(value, ordinal)
    UNION ALL
    SELECT 'allRecords' AS scope, 2 AS source_order, electronic_access.ordinal, electronic_access.value
    FROM (
        SELECT item.electronic_access
        FROM items_all item
        WHERE item.electronic_access IS NOT NULL
      ) item
      CROSS JOIN LATERAL jsonb_array_elements(item.electronic_access)
        WITH ORDINALITY AS electronic_access(value, ordinal)
    UNION ALL
    SELECT 'notSuppressedFromDiscoveryRecords' AS scope, 2 AS source_order, electronic_access.ordinal, electronic_access.value
    FROM (
        SELECT item.electronic_access
        FROM items_not_suppressed item
        WHERE item.electronic_access IS NOT NULL
      ) item
      CROSS JOIN LATERAL jsonb_array_elements(item.electronic_access)
        WITH ORDINALITY AS electronic_access(value, ordinal)
    UNION ALL
    SELECT 'allRecords' AS scope, 3 AS source_order, electronic_access.ordinal, electronic_access.value
    FROM holdings_all hr
      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(hr.jsonb -> 'electronicAccess', '[]'::jsonb))
        WITH ORDINALITY AS electronic_access(value, ordinal)
    UNION ALL
    SELECT 'notSuppressedFromDiscoveryRecords' AS scope, 3 AS source_order, electronic_access.ordinal, electronic_access.value
    FROM holdings_not_suppressed hr
      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(hr.jsonb -> 'electronicAccess', '[]'::jsonb))
        WITH ORDINALITY AS electronic_access(value, ordinal)
  ),
  electronic_access_distinct AS (
    SELECT DISTINCT ON (scope, value ->> 'uri')
      scope,
      value,
      source_order,
      ordinal
    FROM electronic_access_data
    WHERE NULLIF(value ->> 'uri', '') IS NOT NULL
    ORDER BY scope, value ->> 'uri', source_order, ordinal
  ),
  electronic_access_by_scope AS (
    SELECT
      COALESCE(
        jsonb_agg(value ORDER BY source_order, ordinal) FILTER (WHERE scope = 'allRecords'),
        '[]'::jsonb
      ) AS all_records,
      COALESCE(
        jsonb_agg(value ORDER BY source_order, ordinal) FILTER (WHERE scope = 'notSuppressedFromDiscoveryRecords'),
        '[]'::jsonb
      ) AS not_suppressed_from_discovery_records
    FROM electronic_access_distinct
  ),
  material_type_data AS (
    SELECT 'allRecords' AS scope, mt.id::text AS id, mt.jsonb ->> 'name' AS name
    FROM (
        SELECT DISTINCT item.materialtypeid
        FROM items_all item
        WHERE item.materialtypeid IS NOT NULL
      ) item
      JOIN ${myuniversity}_${mymodule}.material_type mt ON item.materialtypeid = mt.id
    UNION
    SELECT 'notSuppressedFromDiscoveryRecords' AS scope, mt.id::text AS id, mt.jsonb ->> 'name' AS name
    FROM (
        SELECT DISTINCT item.materialtypeid
        FROM items_not_suppressed item
        WHERE item.materialtypeid IS NOT NULL
      ) item
      JOIN ${myuniversity}_${mymodule}.material_type mt ON item.materialtypeid = mt.id
  ),
  material_types_by_scope AS (
    SELECT
      COALESCE(
        jsonb_agg(jsonb_build_object('id', id, 'name', name) ORDER BY name) FILTER (WHERE scope = 'allRecords'),
        '[]'::jsonb
      ) AS all_records,
      COALESCE(
        jsonb_agg(jsonb_build_object('id', id, 'name', name) ORDER BY name)
          FILTER (WHERE scope = 'notSuppressedFromDiscoveryRecords'),
        '[]'::jsonb
      ) AS not_suppressed_from_discovery_records
    FROM material_type_data
  ),
  shelving_order_by_scope AS (
    SELECT
      (
        SELECT item.effective_shelving_order
        FROM items_all item
        WHERE item.effective_shelving_order IS NOT NULL
        ORDER BY item.id
        LIMIT 1
      ) AS all_records,
      (
        SELECT item.effective_shelving_order
        FROM items_not_suppressed item
        WHERE item.effective_shelving_order IS NOT NULL
        ORDER BY item.id
        LIMIT 1
      ) AS not_suppressed_from_discovery_records
  ),
  instance_format_candidates AS (
    SELECT COALESCE(jsonb_agg(jsonb_build_object('id', id, 'name', name) ORDER BY name), '[]'::jsonb)
      AS instance_formats
    FROM (
      SELECT DISTINCT instance_format.id::text AS id, instance_format.jsonb ->> 'name' AS name
      FROM target_instance ti
        CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ti.jsonb -> 'instanceFormatIds', '[]'::jsonb))
          AS instance_format_ids(format_id)
        JOIN ${myuniversity}_${mymodule}.instance_format instance_format
          ON instance_format.id = instance_format_ids.format_id::uuid
    ) instance_formats
  ),
  nature_of_content_candidates AS (
    SELECT COALESCE(jsonb_agg(jsonb_build_object('id', id, 'name', name) ORDER BY name), '[]'::jsonb)
      AS nature_of_content_terms
    FROM (
      SELECT DISTINCT nature_of_content_term.id::text AS id, nature_of_content_term.jsonb ->> 'name' AS name
      FROM target_instance ti
        CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ti.jsonb -> 'natureOfContentTermIds', '[]'::jsonb))
          AS nature_of_content_term_ids(term_id)
        JOIN ${myuniversity}_${mymodule}.nature_of_content_term nature_of_content_term
          ON nature_of_content_term.id = nature_of_content_term_ids.term_id::uuid
    ) nature_of_content_terms
  ),
  mode_of_issuance_candidate AS (
    SELECT CASE
      WHEN mode_of_issuance.id IS NULL THEN NULL
      ELSE jsonb_build_object('id', mode_of_issuance.id::text, 'name', mode_of_issuance.jsonb ->> 'name')
    END AS mode_of_issuance
    FROM target_instance ti
      LEFT JOIN ${myuniversity}_${mymodule}.mode_of_issuance mode_of_issuance
        ON mode_of_issuance.id = NULLIF(ti.jsonb ->> 'modeOfIssuanceId', '')::uuid
  ),
  instance_type_candidate AS (
    SELECT CASE
      WHEN instance_type.id IS NULL THEN NULL
      ELSE jsonb_build_object('id', instance_type.id::text, 'name', instance_type.jsonb ->> 'name')
    END AS instance_type
    FROM target_instance ti
      LEFT JOIN ${myuniversity}_${mymodule}.instance_type instance_type
        ON instance_type.id = NULLIF(ti.jsonb ->> 'instanceTypeId', '')::uuid
  )
SELECT jsonb_build_object(
  'instance', ti.jsonb,
  'isBoundWith', EXISTS (
    SELECT 1
    FROM holdings_all hr
      JOIN ${myuniversity}_${mymodule}.bound_with_part bwp ON bwp.holdingsrecordid = hr.id
      JOIN ${myuniversity}_${mymodule}.item item ON item.id = bwp.itemid
  ),
  'recordCounts', jsonb_build_object(
    'instance', jsonb_build_object(
      'suppressedFromDiscovery', COALESCE((ti.jsonb ->> 'discoverySuppress')::boolean, false)
    ),
    'holdings', jsonb_build_object(
      'total', holdings_counts.total,
      'suppressedFromDiscovery', holdings_counts.suppressed_from_discovery,
      'notSuppressedFromDiscovery', holdings_counts.not_suppressed_from_discovery
    ),
    'items', jsonb_build_object(
      'total', items_counts.total,
      'suppressedFromDiscovery', items_counts.suppressed_from_discovery,
      'suppressedByHoldings', items_counts.suppressed_by_holdings,
      'suppressedFromDiscoveryOrByHoldings', items_counts.suppressed_from_discovery_or_by_holdings,
      'notSuppressedFromDiscovery', items_counts.not_suppressed_from_discovery
    )
  ),
  'aggregates', jsonb_build_object(
    'allRecords', jsonb_build_object(
      'itemDerivedFields', jsonb_build_object(
        'effectiveShelvingOrder', shelving_order_by_scope.all_records
      ),
      'electronicAccess', electronic_access_by_scope.all_records,
      'referenceValues', jsonb_build_object(
        'itemMaterialTypes', material_types_by_scope.all_records
      )
    ),
    'notSuppressedFromDiscoveryRecords', jsonb_build_object(
      'itemDerivedFields', jsonb_build_object(
        'effectiveShelvingOrder', shelving_order_by_scope.not_suppressed_from_discovery_records
      ),
      'electronicAccess', electronic_access_by_scope.not_suppressed_from_discovery_records,
      'referenceValues', jsonb_build_object(
        'itemMaterialTypes', material_types_by_scope.not_suppressed_from_discovery_records
      )
    )
  ),
  'referenceValues', jsonb_build_object(
    'instanceFormats', instance_format_candidates.instance_formats,
    'modeOfIssuance', mode_of_issuance_candidate.mode_of_issuance,
    'natureOfContentTerms', nature_of_content_candidates.nature_of_content_terms,
    'instanceType', instance_type_candidate.instance_type
  )
)
FROM target_instance ti
  CROSS JOIN holdings_counts
  CROSS JOIN items_counts
  CROSS JOIN electronic_access_by_scope
  CROSS JOIN material_types_by_scope
  CROSS JOIN shelving_order_by_scope
  CROSS JOIN instance_format_candidates
  CROSS JOIN nature_of_content_candidates
  CROSS JOIN mode_of_issuance_candidate
  CROSS JOIN instance_type_candidate;
$function$
;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.merge_instance_summaries(
  _summary_wrappers jsonb,
  _preferred_schema text
)
  RETURNS jsonb
  LANGUAGE sql
AS $function$
WITH
  summaries AS MATERIALIZED (
    SELECT
      summary_wrapper ->> 'schemaName' AS schema_name,
      summary_wrapper -> 'summary' AS summary
    FROM jsonb_array_elements(COALESCE(_summary_wrappers, '[]'::jsonb)) AS summary_wrapper
    WHERE summary_wrapper -> 'summary' IS NOT NULL
      AND summary_wrapper -> 'summary' <> 'null'::jsonb
  ),
  selected_summary AS (
    SELECT (
      SELECT summary
      FROM summaries
      ORDER BY CASE WHEN schema_name = _preferred_schema THEN 0 ELSE 1 END, schema_name
      LIMIT 1
    ) AS summary
  ),
  record_counts AS (
    SELECT
      COALESCE(SUM((summary #>> '{recordCounts,holdings,total}')::bigint), 0) AS holdings_total,
      COALESCE(SUM((summary #>> '{recordCounts,holdings,suppressedFromDiscovery}')::bigint), 0)
        AS holdings_suppressed_from_discovery,
      COALESCE(SUM((summary #>> '{recordCounts,holdings,notSuppressedFromDiscovery}')::bigint), 0)
        AS holdings_not_suppressed_from_discovery,
      COALESCE(SUM((summary #>> '{recordCounts,items,total}')::bigint), 0) AS items_total,
      COALESCE(SUM((summary #>> '{recordCounts,items,suppressedFromDiscovery}')::bigint), 0)
        AS items_suppressed_from_discovery,
      COALESCE(SUM((summary #>> '{recordCounts,items,suppressedByHoldings}')::bigint), 0)
        AS items_suppressed_by_holdings,
      COALESCE(SUM((summary #>> '{recordCounts,items,suppressedFromDiscoveryOrByHoldings}')::bigint), 0)
        AS items_suppressed_from_discovery_or_by_holdings,
      COALESCE(SUM((summary #>> '{recordCounts,items,notSuppressedFromDiscovery}')::bigint), 0)
        AS items_not_suppressed_from_discovery
    FROM summaries
  ),
  bound_with_summary AS (
    SELECT COALESCE(bool_or((summary ->> 'isBoundWith')::boolean), false) AS is_bound_with
    FROM summaries
  ),
  electronic_access_data AS (
    SELECT scoped.scope, s.schema_name, electronic_access.ordinal, electronic_access.value
    FROM summaries s
      CROSS JOIN LATERAL (VALUES
        ('allRecords', s.summary #> '{aggregates,allRecords,electronicAccess}'),
        ('notSuppressedFromDiscoveryRecords',
          s.summary #> '{aggregates,notSuppressedFromDiscoveryRecords,electronicAccess}')
      ) AS scoped(scope, values_json)
      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(scoped.values_json, '[]'::jsonb))
        WITH ORDINALITY AS electronic_access(value, ordinal)
    WHERE NULLIF(electronic_access.value ->> 'uri', '') IS NOT NULL
  ),
  electronic_access_distinct AS (
    SELECT DISTINCT ON (scope, value ->> 'uri')
      scope,
      value,
      schema_name,
      ordinal
    FROM electronic_access_data
    ORDER BY
      scope,
      value ->> 'uri',
      CASE WHEN schema_name = _preferred_schema THEN 0 ELSE 1 END,
      schema_name,
      ordinal
  ),
  electronic_access_by_scope AS (
    SELECT
      COALESCE(
        jsonb_agg(value ORDER BY CASE WHEN schema_name = _preferred_schema THEN 0 ELSE 1 END, schema_name, ordinal)
          FILTER (WHERE scope = 'allRecords'),
        '[]'::jsonb
      ) AS all_records,
      COALESCE(
        jsonb_agg(value ORDER BY CASE WHEN schema_name = _preferred_schema THEN 0 ELSE 1 END, schema_name, ordinal)
          FILTER (WHERE scope = 'notSuppressedFromDiscoveryRecords'),
        '[]'::jsonb
      ) AS not_suppressed_from_discovery_records
    FROM electronic_access_distinct
  ),
  material_type_data AS (
    SELECT scoped.scope, s.schema_name, material_type.value
    FROM summaries s
      CROSS JOIN LATERAL (VALUES
        ('allRecords', s.summary #> '{aggregates,allRecords,referenceValues,itemMaterialTypes}'),
        ('notSuppressedFromDiscoveryRecords',
          s.summary #> '{aggregates,notSuppressedFromDiscoveryRecords,referenceValues,itemMaterialTypes}')
      ) AS scoped(scope, values_json)
      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(scoped.values_json, '[]'::jsonb)) AS material_type(value)
    WHERE NULLIF(material_type.value ->> 'id', '') IS NOT NULL
  ),
  material_type_distinct AS (
    SELECT DISTINCT ON (scope, value ->> 'id')
      scope,
      value,
      schema_name
    FROM material_type_data
    ORDER BY
      scope,
      value ->> 'id',
      CASE WHEN schema_name = _preferred_schema THEN 0 ELSE 1 END,
      schema_name,
      value ->> 'name'
  ),
  material_types_by_scope AS (
    SELECT
      COALESCE(
        jsonb_agg(value ORDER BY value ->> 'name') FILTER (WHERE scope = 'allRecords'),
        '[]'::jsonb
      ) AS all_records,
      COALESCE(
        jsonb_agg(value ORDER BY value ->> 'name') FILTER (WHERE scope = 'notSuppressedFromDiscoveryRecords'),
        '[]'::jsonb
      ) AS not_suppressed_from_discovery_records
    FROM material_type_distinct
  ),
  shelving_order_data AS (
    SELECT scoped.scope, s.schema_name, scoped.value
    FROM summaries s
      CROSS JOIN LATERAL (VALUES
        ('allRecords', NULLIF(s.summary #>> '{aggregates,allRecords,itemDerivedFields,effectiveShelvingOrder}', '')),
        ('notSuppressedFromDiscoveryRecords',
          NULLIF(s.summary #>> '{aggregates,notSuppressedFromDiscoveryRecords,itemDerivedFields,effectiveShelvingOrder}',
            ''))
      ) AS scoped(scope, value)
    WHERE scoped.value IS NOT NULL
  ),
  shelving_order_by_scope AS (
    SELECT
      (
        SELECT value
        FROM shelving_order_data
        WHERE scope = 'allRecords'
        ORDER BY CASE WHEN schema_name = _preferred_schema THEN 0 ELSE 1 END, schema_name
        LIMIT 1
      ) AS all_records,
      (
        SELECT value
        FROM shelving_order_data
        WHERE scope = 'notSuppressedFromDiscoveryRecords'
        ORDER BY CASE WHEN schema_name = _preferred_schema THEN 0 ELSE 1 END, schema_name
        LIMIT 1
      ) AS not_suppressed_from_discovery_records
  )
SELECT CASE
  WHEN selected_summary.summary IS NULL THEN NULL
  ELSE selected_summary.summary || jsonb_build_object(
    'isBoundWith', bound_with_summary.is_bound_with,
    'recordCounts', jsonb_build_object(
      'instance', selected_summary.summary #> '{recordCounts,instance}',
      'holdings', jsonb_build_object(
        'total', record_counts.holdings_total,
        'suppressedFromDiscovery', record_counts.holdings_suppressed_from_discovery,
        'notSuppressedFromDiscovery', record_counts.holdings_not_suppressed_from_discovery
      ),
      'items', jsonb_build_object(
        'total', record_counts.items_total,
        'suppressedFromDiscovery', record_counts.items_suppressed_from_discovery,
        'suppressedByHoldings', record_counts.items_suppressed_by_holdings,
        'suppressedFromDiscoveryOrByHoldings', record_counts.items_suppressed_from_discovery_or_by_holdings,
        'notSuppressedFromDiscovery', record_counts.items_not_suppressed_from_discovery
      )
    ),
    'aggregates', jsonb_build_object(
      'allRecords', jsonb_build_object(
        'itemDerivedFields', jsonb_build_object(
          'effectiveShelvingOrder', shelving_order_by_scope.all_records
        ),
        'electronicAccess', electronic_access_by_scope.all_records,
        'referenceValues', jsonb_build_object(
          'itemMaterialTypes', material_types_by_scope.all_records
        )
      ),
      'notSuppressedFromDiscoveryRecords', jsonb_build_object(
        'itemDerivedFields', jsonb_build_object(
          'effectiveShelvingOrder', shelving_order_by_scope.not_suppressed_from_discovery_records
        ),
        'electronicAccess', electronic_access_by_scope.not_suppressed_from_discovery_records,
        'referenceValues', jsonb_build_object(
          'itemMaterialTypes', material_types_by_scope.not_suppressed_from_discovery_records
        )
      )
    )
  )
END
FROM selected_summary
  CROSS JOIN record_counts
  CROSS JOIN bound_with_summary
  CROSS JOIN electronic_access_by_scope
  CROSS JOIN material_types_by_scope
  CROSS JOIN shelving_order_by_scope;
$function$
;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.get_instance_summary_multi_tenant(
  _schemas_str text,
  _instance_id uuid
)
  RETURNS jsonb
  LANGUAGE plpgsql
AS $function$
DECLARE
  summary_query text;
  summary_wrappers jsonb;
BEGIN
  SELECT string_agg(
      format(
        'SELECT jsonb_build_object(''schemaName'', %L, ''summary'', %I.get_instance_summary($1)) AS summary_wrapper',
        schema_name,
        schema_name
      ),
      ' UNION ALL '
      ORDER BY schema_name
    )
    INTO summary_query
  FROM (
    SELECT DISTINCT schema_name
    FROM (
      SELECT NULLIF(trim(schema_name), '') AS schema_name
      FROM unnest(string_to_array(COALESCE(_schemas_str, ''), ',')) AS schema_name
      UNION ALL
      SELECT '${myuniversity}_${mymodule}'
    ) normalized_schema_names
    WHERE schema_name IS NOT NULL
  ) schema_names;

  IF summary_query IS NULL THEN
    RETURN NULL;
  END IF;

  EXECUTE format(
    'SELECT COALESCE(jsonb_agg(summary_wrapper), ''[]''::jsonb)
     FROM (%s) tenant_summaries
     WHERE summary_wrapper -> ''summary'' IS NOT NULL
       AND summary_wrapper -> ''summary'' <> ''null''::jsonb',
    summary_query
  )
  INTO summary_wrappers
  USING _instance_id;

  RETURN ${myuniversity}_${mymodule}.merge_instance_summaries(
    summary_wrappers,
    '${myuniversity}_${mymodule}'
  );
END;
$function$
;
