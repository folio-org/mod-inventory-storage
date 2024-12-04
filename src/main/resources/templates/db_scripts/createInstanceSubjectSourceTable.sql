
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.instance_subject_source (
  instance_id UUID,
  source_id UUID,
  CONSTRAINT fk_instance_id FOREIGN KEY(instance_id) REFERENCES ${myuniversity}_${mymodule}.instance(id) ON DELETE CASCADE,
  CONSTRAINT fk_source_id FOREIGN KEY(source_id) REFERENCES ${myuniversity}_${mymodule}.subject_source(id)
);

CREATE INDEX IF NOT EXISTS instance_subject_source_source_idx
  ON ${myuniversity}_${mymodule}.instance_subject_source(source_id);
