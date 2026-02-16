CREATE INDEX IF NOT EXISTS idx_instance_status_id_functional
  ON ${myuniversity}_${mymodule}.instance USING btree (lower(f_unaccent((jsonb ->> 'statusId'::text))));
