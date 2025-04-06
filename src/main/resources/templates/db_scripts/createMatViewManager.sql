CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.mat_view_metadata
(
  view_name           TEXT PRIMARY KEY,
  last_refresh        TIMESTAMPTZ,
  is_refreshing       BOOLEAN DEFAULT FALSE,
  refresh_started_at  TIMESTAMPTZ,
  refresh_instance_id TEXT
);

CREATE MATERIALIZED VIEW ${myuniversity}_${mymodule}.bound_instances_mv AS
SELECT DISTINCT hr.instanceId
FROM ${myuniversity}_${mymodule}.bound_with_part bw
JOIN ${myuniversity}_${mymodule}.holdings_record hr ON hr.id = bw.holdingsrecordid;

-- Add an index for better performance
CREATE UNIQUE INDEX ON ${myuniversity}_${mymodule}.bound_instances_mv (instanceId);

INSERT INTO ${myuniversity}_${mymodule}.mat_view_metadata
           (view_name, last_refresh, is_refreshing)
          VALUES ('bound_instances_mv', NULL, FALSE)
          ON CONFLICT (view_name) DO NOTHING
