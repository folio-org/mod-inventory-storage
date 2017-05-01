CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

GRANT myuniversity_mymodule TO CURRENT_USER;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.item (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

GRANT ALL ON myuniversity_mymodule.item TO myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.instance (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

GRANT ALL ON myuniversity_mymodule.instance TO myuniversity_mymodule;

-- *** loan type start *** --
-- loan type table
CREATE TABLE IF NOT EXISTS myuniversity_mymodule.loan_type (
 _id UUID PRIMARY KEY,
 jsonb jsonb NOT NULL,
 creation_date date not null default current_timestamp,
 update_date date not null default current_timestamp
);
-- allow querying jsonb
CREATE INDEX idxgin_loan_type ON myuniversity_mymodule.loan_type USING gin (jsonb jsonb_path_ops);
-- unique constraint
CREATE UNIQUE INDEX loan_type_unique_idx ON myuniversity_mymodule.loan_type((jsonb->>'name'));
-- update the update_date column when record is updated
CREATE OR REPLACE FUNCTION update_modified_column_loan_type()
RETURNS TRIGGER AS $$
BEGIN
  NEW.update_date = current_timestamp;
  RETURN NEW;
END;
$$ language 'plpgsql';
CREATE TRIGGER update_date_loan_type BEFORE UPDATE ON myuniversity_mymodule.loan_type FOR EACH ROW EXECUTE PROCEDURE update_modified_column_loan_type();
GRANT ALL ON myuniversity_mymodule.loan_type TO myuniversity_mymodule;
-- *** loan type end *** --

-- *** material type start *** --
-- material type table
CREATE TABLE IF NOT EXISTS myuniversity_mymodule.material_type (
   _id UUID PRIMARY KEY,
   jsonb jsonb NOT NULL,
   creation_date date not null default current_timestamp,
   update_date date not null default current_timestamp
);
-- allow querying jsonb in material type table
CREATE INDEX idxgin_mtype ON myuniversity_mymodule.material_type USING gin (jsonb jsonb_path_ops);
-- unique constraint on material type name
CREATE UNIQUE INDEX mtype_unique_idx ON myuniversity_mymodule.material_type((jsonb->>'name'));
-- update the update_date column when record is updated
CREATE OR REPLACE FUNCTION update_modified_column_mtype()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_date = current_timestamp;
    RETURN NEW;
END;
$$ language 'plpgsql';
CREATE TRIGGER update_date_mtype BEFORE UPDATE ON myuniversity_mymodule.material_type FOR EACH ROW EXECUTE PROCEDURE  update_modified_column_mtype();
GRANT ALL ON myuniversity_mymodule.material_type TO myuniversity_mymodule;
-- *** material type end *** --
