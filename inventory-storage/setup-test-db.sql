DROP DATABASE IF EXISTS test_tenant;
DROP ROLE IF EXISTS testuser;

CREATE ROLE testuser PASSWORD 'test' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
CREATE DATABASE test_tenant OWNER testuser;

\c test_tenant

CREATE TABLE item (_id SERIAL PRIMARY KEY,jsonb JSONB NOT NULL);

GRANT ALL ON item TO testuser;
GRANT ALL ON item__id_seq TO testuser;
