-- MODINVSTOR: instance_statistical_code, holdings_record_statistical_code, item_statistical_code
-- and item_order_tracker are created via raw CREATE TABLE IF NOT EXISTS snippets instead of RMB's
-- declarative schema.json "tables" list. If their owner ever drifts away from the tenant role
-- (e.g. a tenant schema provisioned/cloned by a different DB role), RMB's end-of-upgrade
-- "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA" silently skips them, because Postgres only
-- re-grants on objects the executing role already owns. Reassign ownership explicitly so that
-- grant always succeeds, regardless of how ownership drifted.
ALTER TABLE IF EXISTS ${myuniversity}_${mymodule}.instance_statistical_code OWNER TO ${myuniversity}_${mymodule};
ALTER TABLE IF EXISTS ${myuniversity}_${mymodule}.holdings_record_statistical_code OWNER TO ${myuniversity}_${mymodule};
ALTER TABLE IF EXISTS ${myuniversity}_${mymodule}.item_statistical_code OWNER TO ${myuniversity}_${mymodule};
ALTER TABLE IF EXISTS ${myuniversity}_${mymodule}.item_order_tracker OWNER TO ${myuniversity}_${mymodule};
