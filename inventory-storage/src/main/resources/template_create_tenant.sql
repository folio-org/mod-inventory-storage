CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.item (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON myuniversity_mymodule.item TO myuniversity_mymodule;
GRANT ALL ON myuniversity_mymodule.item__id_seq TO myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.instance (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON myuniversity_mymodule.instance TO myuniversity_mymodule;
GRANT ALL ON myuniversity_mymodule.instance__id_seq TO myuniversity_mymodule;