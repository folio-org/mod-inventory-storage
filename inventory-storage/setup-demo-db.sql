DROP DATABASE IF EXISTS demo_tenant;
DROP ROLE IF EXISTS demouser;

CREATE ROLE demouser PASSWORD 'demo' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
CREATE DATABASE demo_tenant OWNER demouser;

\c demo_tenant

CREATE TABLE item (_id SERIAL PRIMARY KEY,jsonb JSONB NOT NULL);

GRANT ALL ON item TO demouser;
GRANT ALL ON item__id_seq TO demouser;

CREATE TABLE instance (_id SERIAL PRIMARY KEY,jsonb JSONB NOT NULL);

GRANT ALL ON instance TO testuser;
GRANT ALL ON instance__id_seq TO testuser;
