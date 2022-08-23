-- When deleting an instance automatically delete the connected marc record.
-- Approach: foreign key "ON DELETE CASCADE"

ALTER TABLE ${myuniversity}_${mymodule}.instance_source_marc
  DROP CONSTRAINT IF EXISTS instance_source_marc_id_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.instance_source_marc
  ADD CONSTRAINT instance_source_marc_id_fkey
  FOREIGN KEY (id) REFERENCES instance(id) ON DELETE CASCADE;

