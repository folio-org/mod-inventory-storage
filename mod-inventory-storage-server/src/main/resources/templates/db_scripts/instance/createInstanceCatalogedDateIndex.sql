CREATE INDEX IF NOT EXISTS instance_cataloged_date_idx ON ${myuniversity}_${mymodule}.instance
  (${myuniversity}_${mymodule}.to_timestamp(jsonb->>'catalogedDate'));
