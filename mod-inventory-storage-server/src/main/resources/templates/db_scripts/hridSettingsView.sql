CREATE OR REPLACE VIEW hrid_settings_view AS
  SELECT jsonb_set(jsonb_set(jsonb_set(jsonb,
    '{instances,currentNumber}', to_jsonb(hrid_instances_seq.last_value
                                          - CASE WHEN hrid_instances_seq.is_called THEN 0 ELSE 1 END)),
    '{holdings,currentNumber}',  to_jsonb(hrid_holdings_seq.last_value
                                          - CASE WHEN hrid_holdings_seq.is_called  THEN 0 ELSE 1 END)),
    '{items,currentNumber}',     to_jsonb(hrid_items_seq.last_value
                                          - CASE WHEN hrid_items_seq.is_called     THEN 0 ELSE 1 END))
    AS jsonb
  FROM hrid_settings, hrid_instances_seq, hrid_holdings_seq, hrid_items_seq;

ALTER TABLE    hrid_settings      OWNER TO ${myuniversity}_${mymodule};
ALTER SEQUENCE hrid_instances_seq OWNER TO ${myuniversity}_${mymodule};
ALTER SEQUENCE hrid_holdings_seq  OWNER TO ${myuniversity}_${mymodule};
ALTER SEQUENCE hrid_items_seq     OWNER TO ${myuniversity}_${mymodule};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA ${myuniversity}_${mymodule} TO ${myuniversity}_${mymodule};
