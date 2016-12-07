DROP DATABASE IF EXISTS folio_shared;
DROP ROLE IF EXISTS testuser;

CREATE ROLE testuser PASSWORD 'test' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
CREATE DATABASE folio_shared OWNER testuser;

\c folio_shared

CREATE TABLE item (_id SERIAL PRIMARY KEY,jsonb JSONB NOT NULL);

GRANT ALL ON item TO testuser;
GRANT ALL ON item__id_seq TO testuser;
