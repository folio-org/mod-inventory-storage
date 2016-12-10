CREATE DATABASE database_name OWNER user_name;

\c database_name

CREATE TABLE item (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON item TO user_name;
GRANT ALL ON item__id_seq TO user_name;

CREATE TABLE instance (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL);

GRANT ALL ON instance TO user_name;
GRANT ALL ON instance__id_seq TO user_name;
