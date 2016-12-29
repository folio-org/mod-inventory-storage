CREATE ROLE myuniversity PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

CREATE SCHEMA myuniversity AUTHORIZATION myuniversity;

CREATE TABLE myuniversity.item (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON myuniversity.item TO myuniversity;
GRANT ALL ON myuniversity.item__id_seq TO myuniversity;

CREATE TABLE myuniversity.instance (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON myuniversity.instance TO myuniversity;
GRANT ALL ON myuniversity.instance__id_seq TO myuniversity;