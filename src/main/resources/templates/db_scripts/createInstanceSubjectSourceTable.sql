
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.instance_subject_source (
  instance_id UUID,
  source_id UUID,
  CONSTRAINT fk_instance_id FOREIGN KEY(instance_id) REFERENCES ${myuniversity}_${mymodule}.instance(id) ON DELETE CASCADE,
  CONSTRAINT fk_source_id FOREIGN KEY(source_id) REFERENCES ${myuniversity}_${mymodule}.subject_source(id)
);
