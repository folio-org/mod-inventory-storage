\c database_name

CREATE SCHEMA schema_name AUTHORIZATION user_name;

CREATE TABLE schema_name.item (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON schema_name.item TO user_name;
GRANT ALL ON schema_name.item__id_seq TO user_name;

CREATE TABLE schema_name.instance (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON schema_name.instance TO user_name;
GRANT ALL ON schema_name.instance__id_seq TO user_name;
