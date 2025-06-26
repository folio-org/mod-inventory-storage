CREATE INDEX IF NOT EXISTS idx_source_discoverysuppress_composite
  ON ${myuniversity}_${mymodule}.instance USING btree
  ((jsonb ->> 'source'::text) COLLATE pg_catalog."default" ASC NULLS LAST, (jsonb ->> 'discoverySuppress'::text) COLLATE pg_catalog."default" ASC NULLS LAST)
  TABLESPACE pg_default;
