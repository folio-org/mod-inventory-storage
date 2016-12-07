DROP DATABASE IF EXISTS folio_shared;
DROP ROLE IF EXISTS demouser;

CREATE ROLE demouser PASSWORD 'demo' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
CREATE DATABASE folio_shared OWNER demouser;

\c folio_shared

CREATE TABLE item (_id SERIAL PRIMARY KEY,jsonb JSONB NOT NULL);

GRANT ALL ON item TO demouser;
GRANT ALL ON item__id_seq TO demouser;