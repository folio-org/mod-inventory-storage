CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

-- for uuid generator -> gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE myuniversity_mymodule.item (
  _id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  jsonb JSONB NOT NULL
);

GRANT ALL ON myuniversity_mymodule.item TO myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.instance (
  _id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  jsonb JSONB NOT NULL
);

GRANT ALL ON myuniversity_mymodule.instance TO myuniversity_mymodule;

