CREATE INDEX IF NOT EXISTS instance_discoverysuppress_source_idx
ON ${myuniversity}_${mymodule}.instance (((jsonb ->> 'discoverySuppress'::text)), ((jsonb ->> 'source'::text)));
