
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.instance_subject_type (
  instance_id UUID,
  type_id UUID,
  CONSTRAINT fk_instance_id FOREIGN KEY(instance_id) REFERENCES ${myuniversity}_${mymodule}.instance(id) ON DELETE CASCADE,
  CONSTRAINT fk_type_id FOREIGN KEY(type_id) REFERENCES ${myuniversity}_${mymodule}.subject_type(id)
);
