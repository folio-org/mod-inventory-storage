CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

GRANT myuniversity_mymodule TO CURRENT_USER;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

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

-- *** shelf location start *** --
CREATE TABLE myuniversity_mymodule.shelflocation(
	_id UUID PRIMARY KEY,
	jsonb jsonb NOT NULL,
	creation_date date not null default current_timestamp,
	update_date date not null default current_timestamp
);
CREATE INDEX idxgin_shelflocation ON myuniversity_mymodule.shelflocation USING gin (jsonb jsonb_path_ops);
CREATE UNIQUE INDEX shelflocation_unique_idx ON myuniversity_mymodule.shelflocation((jsonb->>'name'));
CREATE OR REPLACE FUNCTION update_modified_column_shelflocation()
RETURNS TRIGGER AS $$
BEGIN
	NEW.update_date = current_timestamp;
	RETURN NEW;
END;
$$ language 'plpgsql';
CREATE TRIGGER update_date_shelflocation BEFORE UPDATE ON myuniversity_mymodule.shelflocation FOR EACH ROW EXECUTE PROCEDURE update_modified_column_shelflocation();
GRANT ALL ON myuniversity_mymodule.shelflocation TO myuniversity_mymodule;
-- *** shelf location end *** --
CREATE TABLE myuniversity_mymodule.item (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL,
  permanentLoanTypeId UUID REFERENCES myuniversity_mymodule.loan_type,
  temporaryLoanTypeId UUID REFERENCES myuniversity_mymodule.loan_type
);
CREATE OR REPLACE FUNCTION update_item_references()
RETURNS TRIGGER AS $$
BEGIN
  NEW.permanentLoanTypeId = NEW.jsonb->>'permanentLoanTypeId';
  NEW.temporaryLoanTypeId = NEW.jsonb->>'temporaryLoanTypeId';
  RETURN NEW;
END;
$$ language 'plpgsql';
CREATE TRIGGER update_item_references
  BEFORE INSERT OR UPDATE ON myuniversity_mymodule.item
  FOR EACH ROW EXECUTE PROCEDURE update_item_references();
GRANT ALL ON myuniversity_mymodule.item TO myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.instance (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);
GRANT ALL ON myuniversity_mymodule.instance TO myuniversity_mymodule;
