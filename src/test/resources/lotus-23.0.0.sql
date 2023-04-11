
CREATE ROLE lotus_mod_inventory_storage PASSWORD 'lotus' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT lotus_mod_inventory_storage TO CURRENT_USER;
ALTER ROLE lotus_mod_inventory_storage SET search_path TO '$user';

-- the following has been dumped using
-- PGPASSWORD="$DB_PASSWORD" pg_dump -U "$DB_USERNAME" -h "$DB_HOST" -p "$DB_PORT" -n lotus_mod_inventory_storage > dump.sql

--
-- PostgreSQL database dump
--

-- Dumped from database version 12.8
-- Dumped by pg_dump version 12.11 (Ubuntu 12.11-0ubuntu0.20.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: lotus_mod_inventory_storage; Type: SCHEMA; Schema: -; Owner: lotus_mod_inventory_storage
--

CREATE SCHEMA lotus_mod_inventory_storage;


ALTER SCHEMA lotus_mod_inventory_storage OWNER TO lotus_mod_inventory_storage;

--
-- Name: alternative_title_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.alternative_title_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.alternative_title_type_set_md() OWNER TO postgres;

--
-- Name: audit_holdings_record_changes(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.audit_holdings_record_changes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  DECLARE
    jsonb JSONB;
    uuidtext TEXT;
    uuid UUID;
  BEGIN
    jsonb = CASE WHEN TG_OP = 'DELETE' THEN OLD.jsonb ELSE NEW.jsonb END;

    -- create uuid based on the jsonb value so that concurrent updates of different records are possible.
    uuidtext = md5(jsonb::text);
    -- UUID version byte
    uuidtext = overlay(uuidtext placing '4' from 13);
    -- UUID variant byte
    uuidtext = overlay(uuidtext placing '8' from 17);
    uuid = uuidtext::uuid;
    -- If uuid is already in use increment until an unused is found. This can only happen if the jsonb content
    -- is exactly the same. This should be very rare when it includes a timestamp.
    WHILE EXISTS (SELECT 1 FROM lotus_mod_inventory_storage.audit_holdings_record WHERE id = uuid) LOOP
      uuid = lotus_mod_inventory_storage.next_uuid(uuid);
    END LOOP;

    jsonb = jsonb_build_object(
      'id', to_jsonb(uuid::text),
      'record', jsonb,
      'operation', to_jsonb(left(TG_OP, 1)),
      'createdDate', to_jsonb(current_timestamp::text));
    IF (TG_OP = 'DELETE') THEN
    ELSIF (TG_OP = 'UPDATE') THEN
    ELSIF (TG_OP = 'INSERT') THEN
    END IF;
    INSERT INTO lotus_mod_inventory_storage.audit_holdings_record VALUES (uuid, jsonb);
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.audit_holdings_record_changes() OWNER TO postgres;

--
-- Name: audit_instance_changes(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.audit_instance_changes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  DECLARE
    jsonb JSONB;
    uuidtext TEXT;
    uuid UUID;
  BEGIN
    jsonb = CASE WHEN TG_OP = 'DELETE' THEN OLD.jsonb ELSE NEW.jsonb END;

    -- create uuid based on the jsonb value so that concurrent updates of different records are possible.
    uuidtext = md5(jsonb::text);
    -- UUID version byte
    uuidtext = overlay(uuidtext placing '4' from 13);
    -- UUID variant byte
    uuidtext = overlay(uuidtext placing '8' from 17);
    uuid = uuidtext::uuid;
    -- If uuid is already in use increment until an unused is found. This can only happen if the jsonb content
    -- is exactly the same. This should be very rare when it includes a timestamp.
    WHILE EXISTS (SELECT 1 FROM lotus_mod_inventory_storage.audit_instance WHERE id = uuid) LOOP
      uuid = lotus_mod_inventory_storage.next_uuid(uuid);
    END LOOP;

    jsonb = jsonb_build_object(
      'id', to_jsonb(uuid::text),
      'record', jsonb,
      'operation', to_jsonb(left(TG_OP, 1)),
      'createdDate', to_jsonb(current_timestamp::text));
    IF (TG_OP = 'DELETE') THEN
    ELSIF (TG_OP = 'UPDATE') THEN
    ELSIF (TG_OP = 'INSERT') THEN
    END IF;
    INSERT INTO lotus_mod_inventory_storage.audit_instance VALUES (uuid, jsonb);
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.audit_instance_changes() OWNER TO postgres;

--
-- Name: audit_item_changes(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.audit_item_changes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  DECLARE
    jsonb JSONB;
    uuidtext TEXT;
    uuid UUID;
  BEGIN
    jsonb = CASE WHEN TG_OP = 'DELETE' THEN OLD.jsonb ELSE NEW.jsonb END;

    -- create uuid based on the jsonb value so that concurrent updates of different records are possible.
    uuidtext = md5(jsonb::text);
    -- UUID version byte
    uuidtext = overlay(uuidtext placing '4' from 13);
    -- UUID variant byte
    uuidtext = overlay(uuidtext placing '8' from 17);
    uuid = uuidtext::uuid;
    -- If uuid is already in use increment until an unused is found. This can only happen if the jsonb content
    -- is exactly the same. This should be very rare when it includes a timestamp.
    WHILE EXISTS (SELECT 1 FROM lotus_mod_inventory_storage.audit_item WHERE id = uuid) LOOP
      uuid = lotus_mod_inventory_storage.next_uuid(uuid);
    END LOOP;

    jsonb = jsonb_build_object(
      'id', to_jsonb(uuid::text),
      'record', jsonb,
      'operation', to_jsonb(left(TG_OP, 1)),
      'createdDate', to_jsonb(current_timestamp::text));
    IF (TG_OP = 'DELETE') THEN
    ELSIF (TG_OP = 'UPDATE') THEN
    ELSIF (TG_OP = 'INSERT') THEN
    END IF;
    INSERT INTO lotus_mod_inventory_storage.audit_item VALUES (uuid, jsonb);
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
  END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.audit_item_changes() OWNER TO postgres;

--
-- Name: authority_note_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.authority_note_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.authority_note_type_set_md() OWNER TO postgres;

--
-- Name: authority_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.authority_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.authority_set_md() OWNER TO postgres;

--
-- Name: authority_set_ol_version(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.authority_set_ol_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  BEGIN
    CASE TG_OP
      WHEN 'INSERT' THEN
          NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}', to_jsonb(1));
      WHEN 'UPDATE' THEN
        IF NEW.jsonb->'_version' IS DISTINCT FROM OLD.jsonb->'_version' THEN
            RAISE 'Cannot update record % because it has been changed (optimistic locking): '
                'Stored _version is %, _version of request is %',
                OLD.id, OLD.jsonb->'_version', NEW.jsonb->'_version'
                USING ERRCODE = '23F09', TABLE = 'authority', SCHEMA = 'lotus_mod_inventory_storage';
        END IF;
        NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}',
            to_jsonb(COALESCE(((OLD.jsonb->>'_version')::numeric + 1) % 2147483648, 1)));
    END CASE;
    RETURN NEW;
  END;
  $$;


ALTER FUNCTION lotus_mod_inventory_storage.authority_set_ol_version() OWNER TO postgres;

--
-- Name: bound_with_part_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.bound_with_part_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.bound_with_part_set_md() OWNER TO postgres;

--
-- Name: call_number_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.call_number_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.call_number_type_set_md() OWNER TO postgres;

--
-- Name: check_statistical_code_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.check_statistical_code_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  invalid text;
BEGIN
  SELECT ref
    INTO invalid
    FROM jsonb_array_elements_text(NEW.jsonb->'statisticalCodeIds') ref
    LEFT JOIN statistical_code ON id=ref::uuid
    WHERE id IS NULL
    LIMIT 1;
  IF FOUND THEN
    RAISE foreign_key_violation USING
      MESSAGE='statistical code doesn''t exist: ' || invalid,
      DETAIL='foreign key violation in statisticalCodeIds array of ' || TG_TABLE_NAME || ' with id=' || NEW.id,
      SCHEMA=TG_TABLE_SCHEMA,
      TABLE=TG_TABLE_NAME;
  END IF;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.check_statistical_code_references() OWNER TO postgres;

--
-- Name: classification_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.classification_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.classification_type_set_md() OWNER TO postgres;

--
-- Name: concat_array_object(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.concat_array_object(jsonb_array jsonb) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT string_agg(value::text, ' ') FROM jsonb_array_elements_text($1);
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.concat_array_object(jsonb_array jsonb) OWNER TO postgres;

--
-- Name: concat_array_object_values(jsonb, text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.concat_array_object_values(jsonb_array jsonb, field text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT string_agg(value->>$2, ' ') FROM jsonb_array_elements($1);
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.concat_array_object_values(jsonb_array jsonb, field text) OWNER TO postgres;

--
-- Name: concat_array_object_values(jsonb, text, text, text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.concat_array_object_values(jsonb_array jsonb, field text, filterkey text, filtervalue text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
SELECT string_agg(value->>$2, ' ') FROM jsonb_array_elements($1) WHERE value->>$3 = $4;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.concat_array_object_values(jsonb_array jsonb, field text, filterkey text, filtervalue text) OWNER TO postgres;

--
-- Name: concat_space_sql(text[]); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.concat_space_sql(VARIADIC text[]) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$ select concat_ws(' ', VARIADIC $1);
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.concat_space_sql(VARIADIC text[]) OWNER TO postgres;

--
-- Name: contributor_name_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.contributor_name_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.contributor_name_type_set_md() OWNER TO postgres;

--
-- Name: count_estimate(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.count_estimate(query text) RETURNS bigint
    LANGUAGE plpgsql STABLE STRICT
    AS $$
DECLARE
  count bigint;
  est_count bigint;
  q text;
BEGIN
  est_count = lotus_mod_inventory_storage.count_estimate_smart2(1000, 1000, query);
  IF est_count > 4*1000 THEN
    RETURN est_count;
  END IF;
  q = 'SELECT COUNT(*) FROM (' || query || ' LIMIT 1000) x';
  EXECUTE q INTO count;
  IF count < 1000 THEN
    RETURN count;
  END IF;
  IF est_count < 1000 THEN
    RETURN 1000;
  END IF;
  RETURN est_count;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.count_estimate(query text) OWNER TO postgres;

--
-- Name: count_estimate_default(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.count_estimate_default(query text) RETURNS bigint
    LANGUAGE plpgsql IMMUTABLE STRICT
    AS $$
DECLARE
  rows bigint;
  q text;
BEGIN
  q = 'SELECT COUNT(*) FROM (' || query || ' LIMIT 1000) x';
  EXECUTE q INTO rows;
  IF rows < 1000 THEN
    return rows;
  END IF;
  rows = lotus_mod_inventory_storage.count_estimate_smart2(1000, 1000, query);
  IF rows < 1000 THEN
    return 1000;
  END IF;
  RETURN rows;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.count_estimate_default(query text) OWNER TO postgres;

--
-- Name: count_estimate_smart2(bigint, bigint, text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.count_estimate_smart2(rows bigint, lim bigint, query text) RETURNS bigint
    LANGUAGE plpgsql STRICT
    AS $$
DECLARE
  rec   record;
  cnt bigint;
BEGIN
  IF rows = lim THEN
      FOR rec IN EXECUTE 'EXPLAIN ' || query LOOP
        cnt := substring(rec."QUERY PLAN" FROM ' rows=([[:digit:]]+)');
        EXIT WHEN cnt IS NOT NULL;
      END LOOP;
      RETURN cnt;
  END IF;
  RETURN rows;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.count_estimate_smart2(rows bigint, lim bigint, query text) OWNER TO postgres;

--
-- Name: dateormax(timestamp with time zone); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.dateormax(timestamp with time zone) RETURNS timestamp with time zone
    LANGUAGE sql IMMUTABLE
    AS $_$
SELECT COALESCE($1, timestamptz '2050-01-01')
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.dateormax(timestamp with time zone) OWNER TO postgres;

--
-- Name: dateormin(timestamp with time zone); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.dateormin(timestamp with time zone) RETURNS timestamp with time zone
    LANGUAGE sql IMMUTABLE
    AS $_$
SELECT COALESCE($1, timestamptz '1970-01-01')
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.dateormin(timestamp with time zone) OWNER TO postgres;

--
-- Name: electronic_access_relationship_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.electronic_access_relationship_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.electronic_access_relationship_set_md() OWNER TO postgres;

--
-- Name: f_unaccent(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
        SELECT public.unaccent('public.unaccent', $1)  -- schema-qualify function and dictionary
      $_$;


ALTER FUNCTION lotus_mod_inventory_storage.f_unaccent(text) OWNER TO postgres;

--
-- Name: first_array_object_value(jsonb, text, text, text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.first_array_object_value(jsonb_array jsonb, field text, filterkey text, filtervalue text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
SELECT value->>$2 FROM jsonb_array_elements($1) WHERE value->>$3 = $4 LIMIT 1;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.first_array_object_value(jsonb_array jsonb, field text, filterkey text, filtervalue text) OWNER TO postgres;

--
-- Name: get_items_and_holdings_view(uuid[], boolean); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.get_items_and_holdings_view(instanceids uuid[], skipsuppressedfromdiscoveryrecords boolean DEFAULT true) RETURNS TABLE("instanceId" uuid, source character varying, "modeOfIssuance" character varying, "natureOfContent" jsonb, holdings jsonb, items jsonb)
    LANGUAGE sql
    AS $_$
WITH
	-- Locations
	viewLocations(locId, locJsonb, locCampJsonb, locLibJsonb, locInstJsonb) AS (
	SELECT loc.id AS locId,
		     loc.jsonb AS locJsonb,
		     locCamp.jsonb AS locCampJsonb,
		     locLib.jsonb AS locLibJsonb,
		     locInst.jsonb AS locInstJsonb
	FROM location loc
		 LEFT JOIN locinstitution locInst
			    ON (loc.jsonb ->> 'institutionId')::uuid = locInst.id
		 LEFT JOIN loccampus locCamp
			    ON (loc.jsonb ->> 'campusId')::uuid = locCamp.id
		 LEFT JOIN loclibrary locLib
			    ON (loc.jsonb ->> 'libraryId')::uuid = locLib.id
	WHERE (loc.jsonb ->> 'isActive')::bool = true
	),
	-- Passed instances ids
	viewInstances(instId, source, modeOfIssuance, natureOfContent) AS (
  SELECT DISTINCT
         instId AS "instanceId",
         i.jsonb ->> 'source' AS source,
         moi.jsonb ->> 'name' AS modeOfIssuance,
         COALESCE(getNatureOfContentName(COALESCE(i.jsonb #> '{natureOfContentTermIds}', '[]'::jsonb)), '[]'::jsonb) AS natureOfContent
    FROM UNNEST( $1 ) instId
		     JOIN instance i
			        ON i.id = instId
			   LEFT JOIN mode_of_issuance moi
			        ON moi.id = nullif(i.jsonb ->> 'modeOfIssuanceId','')::uuid
	),
	-- Prepared items and holdings
	viewItemsAndHoldings(instId, records) AS (
	SELECT itemAndHoldingsAttrs.instanceId, jsonb_strip_nulls(itemAndHoldingsAttrs.itemsAndHoldings)
      FROM (SELECT
              i.id AS instanceId,
              jsonb_build_object('holdings',
                                 COALESCE(jsonb_agg(DISTINCT
                                            jsonb_build_object('id', hr.id,
                                                               'hrId', hr.jsonb ->> 'hrId',
                                                               'suppressFromDiscovery',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                                  COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false)
                                                               ELSE NULL END::bool,
                                                               'holdingsType', ht.jsonb ->> 'name',
                                                               'formerIds', hr.jsonb -> 'formerIds',
                                                               'location',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                   json_build_object('permanentLocation',
                                                                                     jsonb_build_object('name', COALESCE(holdPermLoc.locJsonb ->> 'discoveryDisplayName', holdPermLoc.locJsonb ->> 'name'),
                                                                                                        'code', holdPermLoc.locJsonb ->> 'code',
                                                                                                        'campusName', holdPermLoc.locCampJsonb ->> 'name',
                                                                                                        'libraryName', holdPermLoc.locLibJsonb ->> 'name',
                                                                                                        'institutionName', holdPermLoc.locInstJsonb ->> 'name'),
                                                                                     'temporaryLocation',
                                                                                     jsonb_build_object('name', COALESCE(holdTempLoc.locJsonb ->> 'discoveryDisplayName', holdTempLoc.locJsonb ->> 'name'),
                                                                                                        'code', holdTempLoc.locJsonb ->> 'code',
                                                                                                        'campusName', holdTempLoc.locCampJsonb ->> 'name',
                                                                                                        'libraryName', holdTempLoc.locLibJsonb ->> 'name',
                                                                                                        'institutionName', holdTempLoc.locInstJsonb ->> 'name'),
                                                                                     'effectiveLocation',
                                                                                     jsonb_build_object('name', COALESCE(holdEffLoc.locJsonb ->> 'discoveryDisplayName', holdEffLoc.locJsonb ->> 'name'),
                                                                                                        'code', holdEffLoc.locJsonb ->> 'code',
                                                                                                        'campusName', holdEffLoc.locCampJsonb ->> 'name',
                                                                                                        'libraryName', holdEffLoc.locLibJsonb ->> 'name',
                                                                                                        'institutionName', holdEffLoc.locInstJsonb ->> 'name'))
                                                               ELSE NULL END::jsonb,
                                                               'callNumber', json_build_object('prefix', hr.jsonb ->> 'callNumberPrefix',
                                                                                               'suffix', hr.jsonb ->> 'callNumberSuffix',
                                                                                               'typeId', hr.jsonb ->> 'callNumberTypeId',
                                                                                               'typeName', hrcnt.jsonb ->> 'name',
                                                                                               'callNumber', hr.jsonb ->> 'callNumber'),
                                                               'shelvingTitle', hr.jsonb ->> 'shelvingTitle',
                                                               'acquisitionFormat', hr.jsonb ->> 'acquisitionFormat',
                                                               'acquisitionMethod', hr.jsonb ->> 'acquisitionMethod',
                                                               'receiptStatus', hr.jsonb ->> 'receiptStatus',
                                                               'electronicAccess',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE(getElectronicAccessName(COALESCE(hr.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb)
                                                               ELSE NULL::jsonb END,
                                                               'notes',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE(getHoldingNoteTypeName(hr.jsonb -> 'notes'), '[]'::jsonb)
                                                               ELSE NULL END::jsonb,
                                                               'illPolicy', ilp.jsonb ->> 'name',
                                                               'retentionPolicy', hr.jsonb ->> 'retentionPolicy',
                                                               'digitizationPolicy', hr.jsonb ->> 'digitizationPolicy',
                                                               'holdingsStatements', hr.jsonb -> 'holdingsStatements',
                                                               'holdingsStatementsForIndexes', hr.jsonb -> 'holdingsStatementsForIndexes',
                                                               'holdingsStatementsForSupplements', hr.jsonb -> 'holdingsStatementsForSupplements',
                                                               'copyNumber', hr.jsonb ->> 'copyNumber',
                                                               'numberOfItems', hr.jsonb ->> 'numberOfItems',
                                                               'receivingHistory', hr.jsonb -> 'receivingHistory',
                                                               'tags', hr.jsonb -> 'tags',
                                                               'statisticalCodes',
                                                               CASE WHEN hr.id IS NOT NULL THEN
                                                                  COALESCE(getStatisticalCodes(hr.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
                                                               ELSE NULL END ::jsonb))
                                 FILTER (WHERE hr.id IS NOT NULL), '[]'::jsonb),
                                 'items',
                                 COALESCE(jsonb_agg(DISTINCT
                                      jsonb_build_object('id', item.id,
                                               'hrId', item.jsonb ->> 'hrId',
                                               'holdingsRecordId', (item.jsonb ->> 'holdingsRecordId')::UUID,
                                               'suppressFromDiscovery',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE((i.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                  COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false) OR
                                                  COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false)
                                               ELSE NULL END::bool,
                                               'status', item.jsonb #>> '{status, name}',
                                               'formerIds', item.jsonb -> 'formerIds',
                                               'location',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                   json_build_object('location',
                                                                     jsonb_build_object('name', COALESCE(itemEffLoc.locJsonb ->> 'discoveryDisplayName', itemEffLoc.locJsonb ->> 'name'),
                                                                                        'code', itemEffLoc.locJsonb ->> 'code',
                                                                                        'campusName', itemEffLoc.locCampJsonb ->> 'name',
                                                                                        'libraryName', itemEffLoc.locLibJsonb ->> 'name',
                                                                                        'institutionName', itemEffLoc.locInstJsonb ->> 'name'),
                                                                     'permanentLocation',
                                                                     jsonb_build_object('name', COALESCE(itemPermLoc.locJsonb ->> 'discoveryDisplayName', itemPermLoc.locJsonb ->> 'name'),
                                                                                        'code', itemPermLoc.locJsonb ->> 'code',
                                                                                        'campusName', itemPermLoc.locCampJsonb ->> 'name',
                                                                                        'libraryName', itemPermLoc.locLibJsonb ->> 'name',
                                                                                        'institutionName', itemPermLoc.locInstJsonb ->> 'name'),
                                                                     'temporaryLocation',
                                                                     jsonb_build_object('name', COALESCE(itemTempLoc.locJsonb ->> 'discoveryDisplayName', itemTempLoc.locJsonb ->> 'name'),
                                                                                        'code', itemTempLoc.locJsonb ->> 'code',
                                                                                        'campusName', itemTempLoc.locCampJsonb ->> 'name',
                                                                                        'libraryName', itemTempLoc.locLibJsonb ->> 'name',
                                                                                        'institutionName', itemTempLoc.locInstJsonb ->> 'name'))
                                               ELSE NULL END::jsonb,
                                               'callNumber', item.jsonb -> 'effectiveCallNumberComponents' ||
                                                             jsonb_build_object('typeName', cnt.jsonb ->> 'name'),
                                               'accessionNumber', item.jsonb ->> 'accessionNumber',
                                               'barcode', item.jsonb ->> 'barcode',
                                               'copyNumber', item.jsonb ->> 'copyNumber',
                                               'volume', item.jsonb ->> 'volume',
                                               'enumeration', item.jsonb ->> 'enumeration',
                                               'chronology', item.jsonb ->>'chronology',
                                               'yearCaption', item.jsonb -> 'yearCaption',
                                               'itemIdentifier', item.jsonb ->> 'itemIdentifier',
                                               'numberOfPieces', item.jsonb ->> 'numberOfPieces',
                                               'descriptionOfPieces', item.jsonb ->> 'descriptionOfPieces',
                                               'numberOfMissingPieces', item.jsonb ->> 'numberOfMissingPieces',
                                               'missingPieces', item.jsonb ->> 'missingPieces',
                                               'missingPiecesDate', item.jsonb ->> 'missingPiecesDate',
                                               'itemDamagedStatus', itemDmgStat.jsonb ->> 'name',
                                               'itemDamagedStatusDate', item.jsonb ->> 'itemDamagedStatusDate',
                                               'materialType', mt.jsonb ->> 'name',
                                               'permanentLoanType', plt.jsonb ->> 'name',
                                               'temporaryLoanType', tlt.jsonb ->> 'name',
                                               'electronicAccess',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE(getElectronicAccessName(COALESCE(item.jsonb #> '{electronicAccess}', '[]'::jsonb)), '[]'::jsonb)
                                               ELSE NULL::jsonb END,
                                               'notes',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE(getItemNoteTypeName(item.jsonb -> 'notes'), '[]'::jsonb)
                                               ELSE NULL END::jsonb,
                                               'tags', item.jsonb -> 'tags',
                                               'statisticalCodes',
                                               CASE WHEN item.id IS NOT NULL THEN
                                                  COALESCE(getStatisticalCodes(item.jsonb -> 'statisticalCodeIds'), '[]'::jsonb)
                                               ELSE NULL END ::jsonb))
                                 FILTER (WHERE item.id IS NOT NULL AND NOT ($2 AND COALESCE((item.jsonb ->> 'discoverySuppress')::bool, false))), '[]'::jsonb)
                              ) itemsAndHoldings

            FROM lotus_mod_inventory_storage.holdings_record hr
                  JOIN lotus_mod_inventory_storage.instance i
                       ON i.id = hr.instanceid
                  JOIN viewInstances vi
                       ON vi.instId = i.id
                  LEFT JOIN lotus_mod_inventory_storage.item item
                       ON item.holdingsrecordid = hr.id
                  -- Item's Effective location relation
                  LEFT JOIN viewLocations itemEffLoc
                       ON (item.jsonb ->> 'effectiveLocationId')::uuid = itemEffLoc.locId
                  -- Item's Permanent location relation
                  LEFT JOIN viewLocations itemPermLoc
                       ON (item.jsonb ->> 'permanentLocationId')::uuid = itemPermLoc.locId
                  -- Item's Temporary location relation
                  LEFT JOIN viewLocations itemTempLoc
                       ON (item.jsonb ->> 'temporaryLocationId')::uuid = itemTempLoc.locId
                  -- Item's Material type relation
                  LEFT JOIN lotus_mod_inventory_storage.material_type mt
                       ON item.materialtypeid = mt.id
                  -- Item's Call number type relation
                  LEFT JOIN lotus_mod_inventory_storage.call_number_type cnt
                       ON (item.jsonb #>> '{effectiveCallNumberComponents, typeId}')::uuid = cnt.id
                  -- Item's Damaged status relation
                  LEFT JOIN lotus_mod_inventory_storage.item_damaged_status itemDmgStat
                       ON (item.jsonb ->> 'itemDamagedStatusId')::uuid = itemDmgStat.id
                  -- Item's Permanent loan type relation
                  LEFT JOIN lotus_mod_inventory_storage.loan_type plt
                       ON (item.jsonb ->> 'permanentLoanTypeId')::uuid = plt.id
                  -- Item's Temporary loan type relation
                  LEFT JOIN lotus_mod_inventory_storage.loan_type tlt
                       ON (item.jsonb ->> 'temporaryLoanTypeId')::uuid = tlt.id
                  -- Holdings type relation
                  LEFT JOIN lotus_mod_inventory_storage.holdings_type ht
                       ON ht.id = hr.holdingstypeid
                  -- Holdings Permanent location relation
                  LEFT JOIN viewLocations holdPermLoc
                       ON (hr.jsonb ->> 'permanentLocationId')::uuid = holdPermLoc.locId
                  -- Holdings Temporary location relation
                  LEFT JOIN viewLocations holdTempLoc
                       ON (hr.jsonb ->> 'temporaryLocationId')::uuid = holdTempLoc.locId
                  -- Holdings Effective location relation
                  LEFT JOIN viewLocations holdEffLoc
                       ON (hr.jsonb ->> 'effectiveLocationId')::uuid = holdEffLoc.locId
                  -- Holdings Call number type relation
                  LEFT JOIN lotus_mod_inventory_storage.call_number_type hrcnt
                       ON (hr.jsonb ->> 'callNumberTypeId')::uuid = hrcnt.id
                  -- Holdings Ill policy relation
                  LEFT JOIN lotus_mod_inventory_storage.ill_policy ilp
                       ON hr.illpolicyid = ilp.id
            WHERE true
                  AND NOT ($2 AND COALESCE((hr.jsonb ->> 'discoverySuppress')::bool, false))
            GROUP BY 1
           ) itemAndHoldingsAttrs
                                           )
-- Instances with items and holding records
SELECT
      vi.instId AS "instanceId",
      vi.source AS "source",
  	  vi.modeOfIssuance AS "modeOfIssuance",
  	  vi.natureOfContent AS "natureOfContent",
      COALESCE(viah.records -> 'holdings', '[]'::jsonb) AS "holdings",
      COALESCE(viah.records -> 'items', '[]'::jsonb) AS "items"
FROM viewInstances vi
	   LEFT JOIN viewItemsAndHoldings viah
		      ON viah.instId = vi.instId

$_$;


ALTER FUNCTION lotus_mod_inventory_storage.get_items_and_holdings_view(instanceids uuid[], skipsuppressedfromdiscoveryrecords boolean) OWNER TO postgres;

--
-- Name: get_tsvector(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.get_tsvector(text) RETURNS tsvector
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT to_tsvector('simple', translate($1, '&', ','));
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.get_tsvector(text) OWNER TO postgres;

--
-- Name: get_updated_instance_ids_view(timestamp with time zone, timestamp with time zone, boolean, boolean, boolean); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.get_updated_instance_ids_view(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean DEFAULT true, skipsuppressedfromdiscoveryrecords boolean DEFAULT true, onlyinstanceupdatedate boolean DEFAULT true) RETURNS TABLE("instanceId" uuid, source character varying, "updatedDate" timestamp with time zone, "suppressFromDiscovery" boolean, deleted boolean)
    LANGUAGE sql
    AS $_$
WITH instanceIdsInRange AS ( SELECT inst.id AS instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) AS maxDate
                             FROM lotus_mod_inventory_storage.instance inst
                             WHERE (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)

                             UNION ALL
                             SELECT instanceid, MAX(maxdate) as maxdate
                               FROM (
                                     SELECT instanceid,(strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) as maxdate
                                       FROM lotus_mod_inventory_storage.holdings_record hr
                                      WHERE ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)
                                        AND NOT EXISTS (SELECT NULL WHERE $5))
                                     UNION
                                     SELECT instanceid, (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) AS maxDate
                                       FROM lotus_mod_inventory_storage.holdings_record hr
                                              INNER JOIN lotus_mod_inventory_storage.item item ON item.holdingsrecordid = hr.id
                                      WHERE (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)
                                        AND NOT EXISTS (SELECT NULL WHERE $5)
                                    ) AS related_hr_items
                                    GROUP BY instanceid
                             UNION ALL
                             SELECT (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strToTimestamp(audit_item.jsonb -> 'record' ->> 'updatedDate')),
                                             (strToTimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate'))) AS maxDate
                             FROM lotus_mod_inventory_storage.audit_holdings_record audit_holdings_record
                                      JOIN lotus_mod_inventory_storage.audit_item audit_item
                                           ON (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              audit_holdings_record.id
                             WHERE ((strToTimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2) OR
                                    (strToTimestamp(audit_item.jsonb #>> '{record,updatedDate}')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                    AND NOT EXISTS (SELECT NULL WHERE $5) )
SELECT instanceId,
       instance.jsonb ->> 'source' AS source,
       MAX(instanceIdsInRange.maxDate) AS maxDate,
       (instance.jsonb ->> 'discoverySuppress')::bool AS suppressFromDiscovery,
       false AS deleted
FROM instanceIdsInRange,
    lotus_mod_inventory_storage.instance
WHERE instanceIdsInRange.maxDate BETWEEN dateOrMin($1) AND dateOrMax($2)
      AND instance.id = instanceIdsInRange.instanceId
      AND NOT ($4 AND COALESCE((instance.jsonb ->> 'discoverySuppress')::bool, false))
GROUP BY 1, 2, 4

UNION ALL
SELECT (jsonb #>> '{record,id}')::uuid              AS instanceId,
        jsonb #>> '{record,source}'                 AS source,
        strToTimestamp(jsonb ->> 'createdDate')     AS maxDate,
        false                                       AS suppressFromDiscovery,
        true                                        AS deleted
FROM lotus_mod_inventory_storage.audit_instance
WHERE $3
      AND strToTimestamp(jsonb ->> 'createdDate') BETWEEN dateOrMin($1) AND dateOrMax($2)

$_$;


ALTER FUNCTION lotus_mod_inventory_storage.get_updated_instance_ids_view(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean, skipsuppressedfromdiscoveryrecords boolean, onlyinstanceupdatedate boolean) OWNER TO postgres;

--
-- Name: getelectronicaccessname(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.getelectronicaccessname(val jsonb) RETURNS jsonb
    LANGUAGE sql STRICT
    AS $_$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e || jsonb_build_object('name', ( SELECT jsonb ->> 'name'
                                                FROM lotus_mod_inventory_storage.electronic_access_relationship
                                                WHERE id = nullif(e ->> 'relationshipId','')::uuid )) e
       FROM jsonb_array_elements($1) AS e ) e1
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.getelectronicaccessname(val jsonb) OWNER TO postgres;

--
-- Name: getholdingnotetypename(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.getholdingnotetypename(val jsonb) RETURNS jsonb
    LANGUAGE sql STRICT
    AS $_$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'holdingsNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('holdingsNoteTypeName', ( SELECT jsonb ->> 'name'
                                   FROM holdings_note_type
                                   WHERE id = nullif(e ->> 'holdingsNoteTypeId','')::uuid )) e
       FROM jsonb_array_elements( $1 ) AS e
	   WHERE NOT (e ->> 'staffOnly')::bool ) e1
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.getholdingnotetypename(val jsonb) OWNER TO postgres;

--
-- Name: getitemnotetypename(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.getitemnotetypename(val jsonb) RETURNS jsonb
    LANGUAGE sql STRICT
    AS $_$
SELECT jsonb_agg(DISTINCT e)
FROM ( SELECT e - 'itemNoteTypeId' - 'staffOnly' ||
              jsonb_build_object('itemNoteTypeName', ( SELECT jsonb ->> 'name'
                                 FROM item_note_type
                                 WHERE id = nullif(e ->> 'itemNoteTypeId','')::uuid )) e
       FROM jsonb_array_elements( $1 ) AS e
	   WHERE NOT (e ->> 'staffOnly')::bool ) e1
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.getitemnotetypename(val jsonb) OWNER TO postgres;

--
-- Name: getnatureofcontentname(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.getnatureofcontentname(val jsonb) RETURNS jsonb
    LANGUAGE sql STRICT
    AS $_$
SELECT jsonb_agg(DISTINCT e.name)
FROM (
	SELECT (jsonb ->> 'name') AS "name"
	FROM lotus_mod_inventory_storage.nature_of_content_term
		JOIN jsonb_array_elements($1) as insNoctIds
			ON id = nullif(insNoctIds ->> 0,'')::uuid) e
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.getnatureofcontentname(val jsonb) OWNER TO postgres;

--
-- Name: getstatisticalcodes(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.getstatisticalcodes(val jsonb) RETURNS jsonb
    LANGUAGE sql STRICT
    AS $_$
WITH stat_codes(statCodeId, statCodeJsonb, statCodeTypeJsonb) AS (
SELECT sc.id, sc.jsonb, sct.jsonb
FROM statistical_code sc
JOIN statistical_code_type sct ON sct.id = sc.statisticalcodetypeid
)
SELECT jsonb_agg(DISTINCT jsonb_build_object('id', sc.statCodeJsonb ->> 'id') ||
							  jsonb_build_object('code', sc.statCodeJsonb ->> 'code') ||
							  jsonb_build_object('name', sc.statCodeJsonb ->> 'name') ||
							  jsonb_build_object('statisticalCodeType', sc.statCodeTypeJsonb ->> 'name') ||
							  jsonb_build_object('source', sc.statCodeTypeJsonb ->> 'source'))
FROM jsonb_array_elements( $1 ) AS e,
     stat_codes sc
WHERE sc.statCodeId::text = (e ->> 0)::text
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.getstatisticalcodes(val jsonb) OWNER TO postgres;

--
-- Name: holdings_note_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.holdings_note_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.holdings_note_type_set_md() OWNER TO postgres;

--
-- Name: holdings_record_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.holdings_record_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.holdings_record_set_md() OWNER TO postgres;

--
-- Name: holdings_record_set_ol_version(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.holdings_record_set_ol_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  BEGIN
    CASE TG_OP
      WHEN 'INSERT' THEN
          NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}', to_jsonb(1));
      WHEN 'UPDATE' THEN
        IF NEW.jsonb->'_version' IS DISTINCT FROM OLD.jsonb->'_version' THEN
            RAISE 'Cannot update record % because it has been changed (optimistic locking): '
                'Stored _version is %, _version of request is %',
                OLD.id, OLD.jsonb->'_version', NEW.jsonb->'_version'
                USING ERRCODE = '23F09', TABLE = 'holdings_record', SCHEMA = 'lotus_mod_inventory_storage';
        END IF;
        NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}',
            to_jsonb(COALESCE(((OLD.jsonb->>'_version')::numeric + 1) % 2147483648, 1)));
    END CASE;
    RETURN NEW;
  END;
  $$;


ALTER FUNCTION lotus_mod_inventory_storage.holdings_record_set_ol_version() OWNER TO postgres;

--
-- Name: holdings_records_source_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.holdings_records_source_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.holdings_records_source_set_md() OWNER TO postgres;

--
-- Name: holdings_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.holdings_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.holdings_type_set_md() OWNER TO postgres;

--
-- Name: identifier_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.identifier_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.identifier_type_set_md() OWNER TO postgres;

--
-- Name: ill_policy_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.ill_policy_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.ill_policy_set_md() OWNER TO postgres;

--
-- Name: instance_note_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.instance_note_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.instance_note_type_set_md() OWNER TO postgres;

--
-- Name: instance_relationship_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.instance_relationship_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.instance_relationship_set_md() OWNER TO postgres;

--
-- Name: instance_relationship_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.instance_relationship_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.instance_relationship_type_set_md() OWNER TO postgres;

--
-- Name: instance_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.instance_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.instance_set_md() OWNER TO postgres;

--
-- Name: instance_set_ol_version(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.instance_set_ol_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  BEGIN
    CASE TG_OP
      WHEN 'INSERT' THEN
          NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}', to_jsonb(1));
      WHEN 'UPDATE' THEN
        IF NEW.jsonb->'_version' IS DISTINCT FROM OLD.jsonb->'_version' THEN
            RAISE 'Cannot update record % because it has been changed (optimistic locking): '
                'Stored _version is %, _version of request is %',
                OLD.id, OLD.jsonb->'_version', NEW.jsonb->'_version'
                USING ERRCODE = '23F09', TABLE = 'instance', SCHEMA = 'lotus_mod_inventory_storage';
        END IF;
        NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}',
            to_jsonb(COALESCE(((OLD.jsonb->>'_version')::numeric + 1) % 2147483648, 1)));
    END CASE;
    RETURN NEW;
  END;
  $$;


ALTER FUNCTION lotus_mod_inventory_storage.instance_set_ol_version() OWNER TO postgres;

--
-- Name: instance_source_marc_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.instance_source_marc_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.instance_source_marc_set_md() OWNER TO postgres;

--
-- Name: instance_status_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.instance_status_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.instance_status_set_md() OWNER TO postgres;

--
-- Name: item_damaged_status_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.item_damaged_status_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.item_damaged_status_set_md() OWNER TO postgres;

--
-- Name: item_note_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.item_note_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.item_note_type_set_md() OWNER TO postgres;

--
-- Name: item_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.item_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.item_set_md() OWNER TO postgres;

--
-- Name: item_set_ol_version(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.item_set_ol_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  BEGIN
    CASE TG_OP
      WHEN 'INSERT' THEN
          NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}', to_jsonb(1));
      WHEN 'UPDATE' THEN
        IF NEW.jsonb->'_version' IS DISTINCT FROM OLD.jsonb->'_version' THEN
            RAISE 'Cannot update record % because it has been changed (optimistic locking): '
                'Stored _version is %, _version of request is %',
                OLD.id, OLD.jsonb->'_version', NEW.jsonb->'_version'
                USING ERRCODE = '23F09', TABLE = 'item', SCHEMA = 'lotus_mod_inventory_storage';
        END IF;
        NEW.jsonb = jsonb_set(NEW.jsonb, '{_version}',
            to_jsonb(COALESCE(((OLD.jsonb->>'_version')::numeric + 1) % 2147483648, 1)));
    END CASE;
    RETURN NEW;
  END;
  $$;


ALTER FUNCTION lotus_mod_inventory_storage.item_set_ol_version() OWNER TO postgres;

--
-- Name: loan_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.loan_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.loan_type_set_md() OWNER TO postgres;

--
-- Name: location_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.location_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.location_set_md() OWNER TO postgres;

--
-- Name: loccampus_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.loccampus_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.loccampus_set_md() OWNER TO postgres;

--
-- Name: locinstitution_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.locinstitution_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.locinstitution_set_md() OWNER TO postgres;

--
-- Name: loclibrary_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.loclibrary_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.loclibrary_set_md() OWNER TO postgres;

--
-- Name: material_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.material_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.material_type_set_md() OWNER TO postgres;

--
-- Name: mode_of_issuance_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.mode_of_issuance_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.mode_of_issuance_set_md() OWNER TO postgres;

--
-- Name: nature_of_content_term_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.nature_of_content_term_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.nature_of_content_term_set_md() OWNER TO postgres;

--
-- Name: next_uuid(uuid); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.next_uuid(uuid) RETURNS uuid
    LANGUAGE plpgsql
    AS $_$
DECLARE
  uuid text;
  digit text;
BEGIN
  uuid = $1;
  FOR i IN REVERSE 36..1 LOOP
    digit := substring(uuid from i for 1);
    -- skip minus, version byte M and variant byte N
    CONTINUE WHEN digit = '-' OR i = 15 OR i = 20;
    CASE digit
      WHEN '0' THEN digit := '1';
      WHEN '1' THEN digit := '2';
      WHEN '2' THEN digit := '3';
      WHEN '3' THEN digit := '4';
      WHEN '4' THEN digit := '5';
      WHEN '5' THEN digit := '6';
      WHEN '6' THEN digit := '7';
      WHEN '7' THEN digit := '8';
      WHEN '8' THEN digit := '9';
      WHEN '9' THEN digit := 'a';
      WHEN 'a' THEN digit := 'b';
      WHEN 'b' THEN digit := 'c';
      WHEN 'c' THEN digit := 'd';
      WHEN 'd' THEN digit := 'e';
      WHEN 'e' THEN digit := 'f';
      WHEN 'f' THEN digit := '0';
      ELSE NULL;
    END CASE;
    uuid = overlay(uuid placing digit from i);
    EXIT WHEN digit <> '0';
  END LOOP;
  RETURN uuid;
END;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.next_uuid(uuid) OWNER TO postgres;

--
-- Name: normalize_digits(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.normalize_digits(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT    translate((regexp_match($1, '^([0-9 \t-]*(?:\*[ \t]*)?)(.*)'))[1], E' \t-', '')
         || CASE WHEN (regexp_match($1, '^([0-9 \t-]*(?:\*[ \t]*)?)(.*)'))[1] = '' THEN ''
                 WHEN (regexp_match($1, '^([0-9 \t-]*(?:\*[ \t]*)?)(.*)'))[2] = '' THEN ''
                 ELSE ' '
            END
         || (regexp_match($1, '^([0-9 \t-]*(?:\*[ \t]*)?)(.*)'))[2];
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.normalize_digits(text) OWNER TO postgres;

--
-- Name: normalize_invalid_isbns(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.normalize_invalid_isbns(jsonb_array jsonb) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT string_agg(lotus_mod_inventory_storage.normalize_digits(identifier->>'value'), ' ')
  FROM jsonb_array_elements($1) as identifier
  WHERE identifier->>'identifierTypeId' = 'fcca2643-406a-482a-b760-7a7f8aec640e';
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.normalize_invalid_isbns(jsonb_array jsonb) OWNER TO postgres;

--
-- Name: normalize_isbns(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.normalize_isbns(jsonb_array jsonb) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT string_agg(lotus_mod_inventory_storage.normalize_digits(identifier->>'value'), ' ')
  FROM jsonb_array_elements($1) as identifier
  WHERE identifier->>'identifierTypeId' = '8261054f-be78-422d-bd51-4ed9f33c3422';
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.normalize_isbns(jsonb_array jsonb) OWNER TO postgres;

--
-- Name: parse_end_year(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.parse_end_year(jsonb) RETURNS integer
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT COALESCE(
    (regexp_match($1->'publication'->-1->>'dateOfPublication', '(?:-\s?|and\s|,\s?)\w{0,2}(\d{4})'))[1],
    (regexp_match($1->'publication'->-1->>'dateOfPublication', '\d{4}'))[1],
    (regexp_match($1->'publication'-> 0->>'dateOfPublication', '(?:-\s?|and\s|,\s?)\w{0,2}(\d{4})'))[1],
    (regexp_match($1->'publication'-> 0->>'dateOfPublication', '\d{4}'))[1]
  )::int;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.parse_end_year(jsonb) OWNER TO postgres;

--
-- Name: parse_publication_period(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.parse_publication_period(jsonb) RETURNS jsonb
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT CASE
    WHEN $1->'publication' IS NULL THEN NULL
    WHEN jsonb_array_length($1->'publication') = 0 THEN NULL
    WHEN parse_start_year($1) IS NULL AND parse_end_year($1) IS NULL THEN NULL
    WHEN parse_start_year($1) IS NULL THEN jsonb_build_object('end', parse_end_year($1))
    WHEN parse_end_year($1) IS NULL OR parse_start_year($1) >= parse_end_year($1)
        THEN jsonb_build_object('start', parse_start_year($1))
    ELSE jsonb_build_object('start', parse_start_year($1), 'end', parse_end_year($1))
  END;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.parse_publication_period(jsonb) OWNER TO postgres;

--
-- Name: parse_start_year(jsonb); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.parse_start_year(jsonb) RETURNS integer
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT COALESCE(
    (regexp_match($1->'publication'->0->>'dateOfPublication', '(\d{4})\w{0,2}(?:\s?-|\sand|\s?,)'))[1],
    (regexp_match($1->'publication'->0->>'dateOfPublication', '\d{4}'))[1]
  )::int;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.parse_start_year(jsonb) OWNER TO postgres;

--
-- Name: pmh_get_updated_instances_ids(timestamp with time zone, timestamp with time zone, boolean, boolean); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.pmh_get_updated_instances_ids(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean DEFAULT true, skipsuppressedfromdiscoveryrecords boolean DEFAULT true) RETURNS TABLE(instanceid uuid, updateddate timestamp with time zone, suppressfromdiscovery boolean, deleted boolean)
    LANGUAGE sql
    AS $_$
with instanceIdsInRange as ( select inst.id                                                       as instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) as maxDate
                             from lotus_mod_inventory_storage.instance inst
                             where (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2)

                             union all
                             select instanceid,
                                    greatest((strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')),
                                             (strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate'))) as maxDate
                             from holdings_record hr
                                      join lotus_mod_inventory_storage.item item on item.holdingsrecordid = hr.id
                             where ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2) or
                                    (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2))

                             union all
                             select (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strtotimestamp(audit_item.jsonb -> 'record' ->> 'updatedDate')),
                                             (strtotimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate'))) as maxDate
                             from audit_holdings_record audit_holdings_record
                                      join audit_item audit_item
                                           on (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              audit_holdings_record.id
                             where ((strToTimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2) or
                                    (strToTimestamp(audit_item.jsonb #>> '{record,updatedDate}')) between dateOrMin($1) and dateOrMax($2)) )

select instanceId,
                    max(instanceIdsInRange.maxDate)    as maxDate,
        (instance.jsonb ->> 'discoverySuppress')::bool as suppressFromDiscovery,
                                                 false as deleted
                                     from instanceIdsInRange,
                                          instance
                                     where instanceIdsInRange.maxDate between dateOrMin($1) and dateOrMax($2)
                                       and instance.id = instanceIdsInRange.instanceId
                                       and not ($4 and coalesce((instance.jsonb ->> 'discoverySuppress')::bool, false))
                                     group by 1, 3
union all
select (audit_instance.jsonb #>> '{record,id}')::uuid as instanceId,
       strToTimestamp(jsonb ->> 'createdDate')        as maxDate,
       false                                          as suppressFromDiscovery,
       true                                           as deleted
from lotus_mod_inventory_storage.audit_instance
where $3
  and strToTimestamp(jsonb ->> 'createdDate') between dateOrMin($1) and dateOrMax($2)

$_$;


ALTER FUNCTION lotus_mod_inventory_storage.pmh_get_updated_instances_ids(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean, skipsuppressedfromdiscoveryrecords boolean) OWNER TO postgres;

--
-- Name: pmh_instance_view_function(uuid[], boolean); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.pmh_instance_view_function(instanceids uuid[], skipsuppressedfromdiscoveryrecords boolean DEFAULT true) RETURNS TABLE(instanceid uuid, itemsandholdingsfields jsonb)
    LANGUAGE sql
    AS $_$
select instId,
(select to_jsonb(itemAndHoldingsAttrs) as itemsAndHoldingsFields
         from ( select hr.instanceid,
                       jsonb_agg(jsonb_build_object('id', item.id, 'callNumber',
                                                    item.jsonb -> 'effectiveCallNumberComponents'
                                                        || jsonb_build_object('typeName',cnt.jsonb ->> 'name'),
                                                    'location',
                                                    json_build_object('location', jsonb_build_object('institutionId',
                                                                                                     itemLocInst.id,
                                                                                                     'institutionName',
                                                                                                     itemLocInst.jsonb ->> 'name',
                                                                                                     'campusId',
                                                                                                     itemLocCamp.id,
                                                                                                     'campusName',
                                                                                                     itemLocCamp.jsonb ->> 'name',
                                                                                                     'libraryId',
                                                                                                     itemLocLib.id,
                                                                                                     'libraryName',
                                                                                                     itemLocLib.jsonb ->> 'name'),
                                                                                                      'name',
                                                                                                      coalesce(loc.jsonb ->> 'discoveryDisplayName', loc.jsonb ->> 'name')),
                                                    'volume',
                                                    item.jsonb -> 'volume',
                                                    'enumeration',
                                                    item.jsonb -> 'enumeration',
                                                    'materialType',
                                                    mt.jsonb -> 'name',
                                                    'electronicAccess',
                                                    getElectronicAccessName(
                                                                coalesce(item.jsonb #> '{electronicAccess}', '[]'::jsonb) ||
                                                                coalesce(hr.jsonb #> '{electronicAccess}', '[]'::jsonb)),
                                                    'suppressFromDiscovery',
                                                                coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false) or
                                                                coalesce((item.jsonb ->> 'discoverySuppress')::bool, false),
                                                    'notes',
                                                    getItemNoteTypeName(item.jsonb-> 'notes'),
                                                    'barcode',
                                                    item.jsonb->>'barcode',
                                                    'chronology',
                                                    item.jsonb->>'chronology',
                                                    'copyNumber',
                                                    item.jsonb->>'copyNumber',
                                                    'holdingsRecordId',
                                                    hr.id
                           )) items
                from holdings_record hr
                         join lotus_mod_inventory_storage.item item on item.holdingsrecordid = hr.id
                         join lotus_mod_inventory_storage.location loc
                              on (item.jsonb ->> 'effectiveLocationId')::uuid = loc.id and
                                 (loc.jsonb ->> 'isActive')::bool = true
                         join lotus_mod_inventory_storage.locinstitution itemLocInst
                              on (loc.jsonb ->> 'institutionId')::uuid = itemLocInst.id
                         join lotus_mod_inventory_storage.loccampus itemLocCamp
                              on (loc.jsonb ->> 'campusId')::uuid = itemLocCamp.id
                         join lotus_mod_inventory_storage.loclibrary itemLocLib
                              on (loc.jsonb ->> 'libraryId')::uuid = itemLocLib.id
                         left join lotus_mod_inventory_storage.material_type mt on item.materialtypeid = mt.id
                         left join lotus_mod_inventory_storage.call_number_type cnt on nullif(item.jsonb #>> '{effectiveCallNumberComponents, typeId}','')::uuid = cnt.id
                where instanceId = instId
                  and not ($2 and coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  and not ($2 and coalesce((item.jsonb ->> 'discoverySuppress')::bool, false))
                group by 1) itemAndHoldingsAttrs)
FROM unnest( $1 ) AS instId;

$_$;


ALTER FUNCTION lotus_mod_inventory_storage.pmh_instance_view_function(instanceids uuid[], skipsuppressedfromdiscoveryrecords boolean) OWNER TO postgres;

--
-- Name: pmh_view_function(timestamp with time zone, timestamp with time zone, boolean, boolean); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.pmh_view_function(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean DEFAULT true, skipsuppressedfromdiscoveryrecords boolean DEFAULT true) RETURNS TABLE(instanceid uuid, updateddate timestamp with time zone, deleted boolean, itemsandholdingsfields jsonb)
    LANGUAGE sql
    AS $_$
with instanceIdsInRange as ( select inst.id                                                      as instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) as maxDate
                             from lotus_mod_inventory_storage.instance inst
                             where (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2)

                             union all
                             select instanceid,
                                    greatest((strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')),
                                             (strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate'))) as maxDate
                             from holdings_record hr
                                      join lotus_mod_inventory_storage.item item on item.holdingsrecordid = hr.id
                             where ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2) or
                                    (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2))

                             union all
                             select (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strtotimestamp(audit_item.jsonb -> 'record' ->> 'updatedDate')),
                                             (strtotimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate'))) as maxDate
                             from audit_holdings_record audit_holdings_record
                                      join audit_item audit_item
                                           on (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              audit_holdings_record.id
                             where ((strToTimestamp(audit_holdings_record.jsonb -> 'record' ->> 'updatedDate')) between dateOrMin($1) and dateOrMax($2) or
                                    (strToTimestamp(audit_item.jsonb #>> '{record,updatedDate}')) between dateOrMin($1) and dateOrMax($2)) ),
     instanceIdsAndDatesInRange as ( select instanceId, max(instanceIdsInRange.maxDate) as maxDate,
                                            (instance.jsonb ->> 'discoverySuppress')::bool as suppressFromDiscovery
                                     from instanceIdsInRange,
                                          instance
                                     where instanceIdsInRange.maxDate between dateOrMin($1) and dateOrMax($2)
                                       and instance.id = instanceIdsInRange.instanceId
                                       and not ($4 and coalesce((instance.jsonb ->> 'discoverySuppress')::bool, false))
                                     group by 1, 3)

select instanceIdsAndDatesInRange.instanceId,
       instanceIdsAndDatesInRange.maxDate,
       false as deleted,
       ( select to_jsonb(itemAndHoldingsAttrs) as instanceFields
         from ( select hr.instanceid,
                       instanceIdsAndDatesInRange.suppressFromDiscovery as suppressFromDiscovery,
                       jsonb_agg(jsonb_build_object('id', item.id, 'callNumber',
                                                    item.jsonb -> 'effectiveCallNumberComponents'
                                                        || jsonb_build_object('typeName',cnt.jsonb ->> 'name'),
                                                    'location',
                                                    json_build_object('location', jsonb_build_object('institutionId',
                                                                                                     itemLocInst.id,
                                                                                                     'institutionName',
                                                                                                     itemLocInst.jsonb ->> 'name',
                                                                                                     'campusId',
                                                                                                     itemLocCamp.id,
                                                                                                     'campusName',
                                                                                                     itemLocCamp.jsonb ->> 'name',
                                                                                                     'libraryId',
                                                                                                     itemLocLib.id,
                                                                                                     'libraryName',
                                                                                                     itemLocLib.jsonb ->> 'name'),
                                                                                                      'name',
                                                                                                      coalesce(loc.jsonb ->> 'discoveryDisplayName', loc.jsonb ->> 'name')),
                                                    'volume',
                                                    item.jsonb -> 'volume',
                                                    'enumeration',
                                                    item.jsonb -> 'enumeration',
                                                    'materialType',
                                                    mt.jsonb -> 'name',
                                                    'electronicAccess',
                                                    getElectronicAccessName(
                                                                coalesce(item.jsonb #> '{electronicAccess}', '[]'::jsonb) ||
                                                                coalesce(hr.jsonb #> '{electronicAccess}', '[]'::jsonb)),
                                                    'suppressFromDiscovery',
                                                    case
                                                        when instanceIdsAndDatesInRange.suppressFromDiscovery
                                                            then true
                                                        else
                                                                coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false) or
                                                                coalesce((item.jsonb ->> 'discoverySuppress')::bool, false)
                                                        end,
                                                    'notes',
                                                    getItemNoteTypeName(item.jsonb-> 'notes'),
                                                    'barcode',
                                                    item.jsonb->>'barcode',
                                                    'chronology',
                                                    item.jsonb->>'chronology',
                                                    'copyNumber',
                                                    item.jsonb->>'copyNumber',
                                                    'holdingsRecordId',
                                                    hr.id
                           )) items
                from holdings_record hr
                         join lotus_mod_inventory_storage.item item on item.holdingsrecordid = hr.id
                         join lotus_mod_inventory_storage.location loc
                              on (item.jsonb ->> 'effectiveLocationId')::uuid = loc.id and
                                 (loc.jsonb ->> 'isActive')::bool = true
                         join lotus_mod_inventory_storage.locinstitution itemLocInst
                              on (loc.jsonb ->> 'institutionId')::uuid = itemLocInst.id
                         join lotus_mod_inventory_storage.loccampus itemLocCamp
                              on (loc.jsonb ->> 'campusId')::uuid = itemLocCamp.id
                         join lotus_mod_inventory_storage.loclibrary itemLocLib
                              on (loc.jsonb ->> 'libraryId')::uuid = itemLocLib.id
                         left join lotus_mod_inventory_storage.material_type mt on item.materialtypeid = mt.id
                         left join lotus_mod_inventory_storage.call_number_type cnt on nullif(item.jsonb #>> '{effectiveCallNumberComponents, typeId}','')::uuid = cnt.id
                where instanceId = instanceIdsAndDatesInRange.instanceId
                  and not ($4 and coalesce((hr.jsonb ->> 'discoverySuppress')::bool, false))
                  and not ($4 and coalesce((item.jsonb ->> 'discoverySuppress')::bool, false))
                group by 1) itemAndHoldingsAttrs )
from instanceIdsAndDatesInRange
union all
select (audit_instance.jsonb #>> '{record,id}')::uuid as instanceId,
       strToTimestamp(jsonb ->> 'createdDate')         as maxDate,
       true                                           as deleted,
       null                                           as itemFields
from lotus_mod_inventory_storage.audit_instance
where $3
  and strToTimestamp(jsonb ->> 'createdDate') between dateOrMin($1) and dateOrMax($2)

$_$;


ALTER FUNCTION lotus_mod_inventory_storage.pmh_view_function(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean, skipsuppressedfromdiscoveryrecords boolean) OWNER TO postgres;

--
-- Name: preceding_succeeding_title_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.preceding_succeeding_title_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.preceding_succeeding_title_set_md() OWNER TO postgres;

--
-- Name: process_statistical_code_delete(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.process_statistical_code_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  DECLARE
    item_fk_counter integer := 0;
    holding_fk_counter integer := 0;
    instance_fk_counter integer := 0;
  BEGIN
    IF (TG_OP = 'DELETE') THEN
      SELECT COUNT(*) INTO item_fk_counter FROM item WHERE jsonb->'statisticalCodeIds' ? OLD.id::text;
      IF (item_fk_counter > 0) THEN
        RAISE foreign_key_violation USING DETAIL = format('Key (id)=(%s) is still referenced from table "item".', OLD.id::text);
      END IF;

      SELECT COUNT(*) INTO holding_fk_counter FROM holdings_record WHERE jsonb->'statisticalCodeIds' ? OLD.id::text;
      IF (holding_fk_counter > 0) THEN
        RAISE foreign_key_violation USING DETAIL = format('Key (id)=(%s) is still referenced from table "holdings record".', OLD.id::text);
      END IF;

      SELECT COUNT(*) INTO instance_fk_counter FROM instance WHERE jsonb->'statisticalCodeIds' ? OLD.id::text;
      IF (instance_fk_counter > 0) THEN
        RAISE foreign_key_violation USING DETAIL = format('Key (id)=(%s) is still referenced from table "instance".', OLD.id::text);
      END IF;
    END IF;
    RETURN OLD;
  END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.process_statistical_code_delete() OWNER TO postgres;

--
-- Name: rmb_internal_index(text, text, text, text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.rmb_internal_index(atable text, aname text, tops text, newdef text) RETURNS void
    LANGUAGE plpgsql
    AS $_$
DECLARE
  olddef text;
  namep CONSTANT text = concat(aname, '_p');
  prepareddef text;
BEGIN
  IF tops = 'DELETE' THEN
    -- use case insensitive %s, not case sensitive %I
    -- no SQL injection because the names are hard-coded in schema.json
    EXECUTE format('DROP INDEX IF EXISTS %s', aname);
    EXECUTE 'DELETE FROM lotus_mod_inventory_storage.rmb_internal_index WHERE name = $1' USING aname;
    RETURN;
  END IF;
  SELECT def INTO olddef      FROM lotus_mod_inventory_storage.rmb_internal_index WHERE name = aname;
  SELECT def INTO prepareddef FROM lotus_mod_inventory_storage.rmb_internal_index WHERE name = namep;
  prepareddef = replace(prepareddef, concat(' ', namep, ' ON '), concat(' ', aname, ' ON '));
  IF prepareddef = newdef THEN
    EXECUTE format('DROP INDEX IF EXISTS %s', aname);
    EXECUTE format('ALTER INDEX IF EXISTS %s RENAME TO %s', namep, aname);
    EXECUTE 'DELETE FROM rmb_internal_index WHERE name = $1' USING namep;
    EXECUTE 'INSERT INTO rmb_internal_analyze VALUES ($1)' USING atable;
  ELSIF olddef IS DISTINCT FROM newdef THEN
    EXECUTE format('DROP INDEX IF EXISTS %s', aname);
    EXECUTE newdef;
    EXECUTE 'INSERT INTO rmb_internal_analyze VALUES ($1)' USING atable;
  END IF;
  EXECUTE 'INSERT INTO lotus_mod_inventory_storage.rmb_internal_index VALUES ($1, $2, FALSE) '
          'ON CONFLICT (name) DO UPDATE SET def = EXCLUDED.def, remove = EXCLUDED.remove' USING aname, newdef;
END
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.rmb_internal_index(atable text, aname text, tops text, newdef text) OWNER TO postgres;

--
-- Name: service_point_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.service_point_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.service_point_set_md() OWNER TO postgres;

--
-- Name: service_point_user_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.service_point_user_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.service_point_user_set_md() OWNER TO postgres;

--
-- Name: set_alternative_title_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_alternative_title_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_alternative_title_type_md_json() OWNER TO postgres;

--
-- Name: set_authority_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_authority_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_authority_md_json() OWNER TO postgres;

--
-- Name: set_authority_note_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_authority_note_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_authority_note_type_md_json() OWNER TO postgres;

--
-- Name: set_bound_with_part_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_bound_with_part_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_bound_with_part_md_json() OWNER TO postgres;

--
-- Name: set_call_number_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_call_number_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_call_number_type_md_json() OWNER TO postgres;

--
-- Name: set_classification_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_classification_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_classification_type_md_json() OWNER TO postgres;

--
-- Name: set_contributor_name_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_contributor_name_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_contributor_name_type_md_json() OWNER TO postgres;

--
-- Name: set_electronic_access_relationship_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_electronic_access_relationship_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_electronic_access_relationship_md_json() OWNER TO postgres;

--
-- Name: set_holdings_note_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_holdings_note_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_holdings_note_type_md_json() OWNER TO postgres;

--
-- Name: set_holdings_record_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_holdings_record_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_holdings_record_md_json() OWNER TO postgres;

--
-- Name: set_holdings_records_source_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_holdings_records_source_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_holdings_records_source_md_json() OWNER TO postgres;

--
-- Name: set_holdings_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_holdings_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_holdings_type_md_json() OWNER TO postgres;

--
-- Name: set_id_in_jsonb(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.jsonb = jsonb_set(NEW.jsonb, '{id}', to_jsonb(NEW.id));
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb() OWNER TO postgres;

--
-- Name: set_identifier_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_identifier_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_identifier_type_md_json() OWNER TO postgres;

--
-- Name: set_ill_policy_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_ill_policy_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_ill_policy_md_json() OWNER TO postgres;

--
-- Name: set_instance_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_md_json() OWNER TO postgres;

--
-- Name: set_instance_note_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_note_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_note_type_md_json() OWNER TO postgres;

--
-- Name: set_instance_relationship_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_relationship_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_relationship_md_json() OWNER TO postgres;

--
-- Name: set_instance_relationship_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_relationship_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_relationship_type_md_json() OWNER TO postgres;

--
-- Name: set_instance_source_marc_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_source_marc_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_source_marc_md_json() OWNER TO postgres;

--
-- Name: set_instance_sourcerecordformat(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_sourcerecordformat() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  BEGIN
    CASE TG_OP
    WHEN 'INSERT' THEN
      -- a newly inserted instance cannot have a source record because of foreign key relationship
      NEW.jsonb := NEW.jsonb - 'sourceRecordFormat';
    ELSE
      NEW.jsonb := CASE (SELECT count(*) FROM lotus_mod_inventory_storage.instance_source_marc WHERE id=NEW.id)
                   WHEN 0 THEN NEW.jsonb - 'sourceRecordFormat'
                   ELSE jsonb_set(NEW.jsonb, '{sourceRecordFormat}', '"MARC-JSON"')
                   END;
    END CASE;
    RETURN NEW;
  END;
  $$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_sourcerecordformat() OWNER TO postgres;

--
-- Name: set_instance_status_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_status_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_status_md_json() OWNER TO postgres;

--
-- Name: set_instance_status_updated_date(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_instance_status_updated_date() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
	BEGIN
		IF (OLD.jsonb->'statusId' IS DISTINCT FROM NEW.jsonb->'statusId') THEN
			-- Date time in "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" format at UTC (00:00) time zone
			NEW.jsonb = jsonb_set(
		    NEW.jsonb, '{statusUpdatedDate}',
			  to_jsonb(to_char(CURRENT_TIMESTAMP(3) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.ms"Z"'))
		  );
		END IF;
		RETURN NEW;
	END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_instance_status_updated_date() OWNER TO postgres;

--
-- Name: set_item_damaged_status_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_item_damaged_status_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_item_damaged_status_md_json() OWNER TO postgres;

--
-- Name: set_item_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_item_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_item_md_json() OWNER TO postgres;

--
-- Name: set_item_note_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_item_note_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_item_note_type_md_json() OWNER TO postgres;

--
-- Name: set_loan_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_loan_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_loan_type_md_json() OWNER TO postgres;

--
-- Name: set_location_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_location_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_location_md_json() OWNER TO postgres;

--
-- Name: set_loccampus_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_loccampus_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_loccampus_md_json() OWNER TO postgres;

--
-- Name: set_locinstitution_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_locinstitution_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_locinstitution_md_json() OWNER TO postgres;

--
-- Name: set_loclibrary_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_loclibrary_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_loclibrary_md_json() OWNER TO postgres;

--
-- Name: set_material_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_material_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_material_type_md_json() OWNER TO postgres;

--
-- Name: set_mode_of_issuance_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_mode_of_issuance_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_mode_of_issuance_md_json() OWNER TO postgres;

--
-- Name: set_nature_of_content_term_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_nature_of_content_term_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_nature_of_content_term_md_json() OWNER TO postgres;

--
-- Name: set_preceding_succeeding_title_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_preceding_succeeding_title_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_preceding_succeeding_title_md_json() OWNER TO postgres;

--
-- Name: set_service_point_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_service_point_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_service_point_md_json() OWNER TO postgres;

--
-- Name: set_service_point_user_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_service_point_user_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_service_point_user_md_json() OWNER TO postgres;

--
-- Name: set_statistical_code_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_statistical_code_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_statistical_code_md_json() OWNER TO postgres;

--
-- Name: set_statistical_code_type_md_json(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.set_statistical_code_type_md_json() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  updatedDate timestamp;
BEGIN
  if NEW.creation_date IS NULL then
    RETURN NEW;
  end if;

  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(NEW.creation_date, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  if NEW.created_by IS NULL then
    NEW.jsonb = NEW.jsonb #- '{metadata,createdByUserId}';
  else
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdByUserId}', to_jsonb(NEW.created_by));
  end if;

  input = NEW.jsonb->'metadata'->>'updatedDate';
  if input IS NOT NULL then
    -- time stamp without time zone?
    IF (input::timestamp::timestamptz = input::timestamptz) THEN
      -- updatedDate already has no time zone, normalize using ::timestamp
      updatedDate = input::timestamp;
    ELSE
      -- updatedDate has a time zone string
      -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
      updatedDate = input::timestamptz AT TIME ZONE '+00';
    END IF;
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(updatedDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  end if;
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.set_statistical_code_type_md_json() OWNER TO postgres;

--
-- Name: statistical_code_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.statistical_code_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.statistical_code_set_md() OWNER TO postgres;

--
-- Name: statistical_code_type_set_md(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.statistical_code_type_set_md() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  input text;
  createdDate timestamp;
BEGIN
  input = NEW.jsonb->'metadata'->>'createdDate';
  IF input IS NULL THEN
    RETURN NEW;
  END IF;
  -- time stamp without time zone?
  IF (input::timestamp::timestamptz = input::timestamptz) THEN
    -- createdDate already has no time zone, normalize using ::timestamp
    createdDate = input::timestamp;
  ELSE
    -- createdDate has a time zone string
    -- normalize using ::timestamptz, convert to '+00' time zone and remove time zone string
    createdDate = input::timestamptz AT TIME ZONE '+00';
  END IF;
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,createdDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata,updatedDate}', to_jsonb(to_char(createdDate, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')));
  NEW.creation_date = createdDate;
  NEW.created_by = NEW.jsonb->'metadata'->>'createdByUserId';
  RETURN NEW;
END;
$$;


ALTER FUNCTION lotus_mod_inventory_storage.statistical_code_type_set_md() OWNER TO postgres;

--
-- Name: strtotimestamp(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.strtotimestamp(text) RETURNS timestamp with time zone
    LANGUAGE sql IMMUTABLE STRICT
    AS $_$
SELECT $1::timestamptz
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.strtotimestamp(text) OWNER TO postgres;

--
-- Name: tsquery_and(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.tsquery_and(text) RETURNS tsquery
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT to_tsquery('simple', string_agg(CASE WHEN length(v) = 0 OR v = '*' THEN ''
                                              WHEN right(v, 1) = '*' THEN '''' || left(v, -1) || ''':*'
                                              ELSE '''' || v || '''' END,
                                         '&'))
  FROM (SELECT regexp_split_to_table(translate($1, '&''', ',,'), ' +')) AS x(v);
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.tsquery_and(text) OWNER TO postgres;

--
-- Name: tsquery_or(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.tsquery_or(text) RETURNS tsquery
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT replace(lotus_mod_inventory_storage.tsquery_and($1)::text, '&', '|')::tsquery;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.tsquery_or(text) OWNER TO postgres;

--
-- Name: tsquery_phrase(text); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.tsquery_phrase(text) RETURNS tsquery
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
  SELECT replace(lotus_mod_inventory_storage.tsquery_and($1)::text, '&', '<->')::tsquery;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.tsquery_phrase(text) OWNER TO postgres;

--
-- Name: update_bound_with_part_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_bound_with_part_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.itemId = (NEW.jsonb->>'itemId');
      NEW.holdingsRecordId = (NEW.jsonb->>'holdingsRecordId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_bound_with_part_references() OWNER TO postgres;

--
-- Name: update_holdings_record_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_holdings_record_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.instanceId = (NEW.jsonb->>'instanceId');
      NEW.permanentLocationId = (NEW.jsonb->>'permanentLocationId');
      NEW.temporaryLocationId = (NEW.jsonb->>'temporaryLocationId');
      NEW.effectiveLocationId = (NEW.jsonb->>'effectiveLocationId');
      NEW.holdingsTypeId = (NEW.jsonb->>'holdingsTypeId');
      NEW.callNumberTypeId = (NEW.jsonb->>'callNumberTypeId');
      NEW.illPolicyId = (NEW.jsonb->>'illPolicyId');
      NEW.sourceId = (NEW.jsonb->>'sourceId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_holdings_record_references() OWNER TO postgres;

--
-- Name: update_instance_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_instance_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.instanceStatusId = (NEW.jsonb->>'instanceStatusId');
      NEW.modeOfIssuanceId = (NEW.jsonb->>'modeOfIssuanceId');
      NEW.instanceTypeId = (NEW.jsonb->>'instanceTypeId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_instance_references() OWNER TO postgres;

--
-- Name: update_instance_relationship_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_instance_relationship_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.superInstanceId = (NEW.jsonb->>'superInstanceId');
      NEW.subInstanceId = (NEW.jsonb->>'subInstanceId');
      NEW.instanceRelationshipTypeId = (NEW.jsonb->>'instanceRelationshipTypeId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_instance_relationship_references() OWNER TO postgres;

--
-- Name: update_instance_source_marc(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_instance_source_marc() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  BEGIN
    IF (TG_OP = 'DELETE') THEN
      UPDATE lotus_mod_inventory_storage.instance
        SET jsonb = jsonb - 'sourceRecordFormat'
        WHERE id = OLD.id;
    ELSE
      UPDATE lotus_mod_inventory_storage.instance
        SET jsonb = jsonb_set(jsonb, '{sourceRecordFormat}', '"MARC-JSON"')
        WHERE id = NEW.id;
    END IF;
    RETURN NULL;
  END;
  $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_instance_source_marc() OWNER TO postgres;

--
-- Name: update_item_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_item_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.holdingsRecordId = (NEW.jsonb->>'holdingsRecordId');
      NEW.permanentLoanTypeId = (NEW.jsonb->>'permanentLoanTypeId');
      NEW.temporaryLoanTypeId = (NEW.jsonb->>'temporaryLoanTypeId');
      NEW.materialTypeId = (NEW.jsonb->>'materialTypeId');
      NEW.permanentLocationId = (NEW.jsonb->>'permanentLocationId');
      NEW.temporaryLocationId = (NEW.jsonb->>'temporaryLocationId');
      NEW.effectiveLocationId = (NEW.jsonb->>'effectiveLocationId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_item_references() OWNER TO postgres;

--
-- Name: update_item_status_date(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_item_status_date() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
  DECLARE
	  newStatus text;
  BEGIN
  	newStatus = NEW.jsonb->'status'->>'name';
	  IF (newStatus IS DISTINCT FROM OLD.jsonb->'status'->>'name') THEN
	    -- Date time in "YYYY-MM-DD"T"HH24:MI:SS.ms'Z'" format at UTC (00:00) time zone
      NEW.jsonb = jsonb_set(NEW.jsonb, '{status,date}',
       to_jsonb(to_char(CURRENT_TIMESTAMP(3) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.ms"Z"')), true);
    ELSIF (OLD.jsonb->'status'->'date' IS NOT NULL) THEN
      NEW.jsonb = jsonb_set(NEW.jsonb, '{status,date}', OLD.jsonb->'status'->'date', true);
    ELSE
      NEW.jsonb = NEW.jsonb #- '{status, date}';
	  END IF;
	  RETURN NEW;
  END;
  $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_item_status_date() OWNER TO postgres;

--
-- Name: update_location_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_location_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.institutionId = (NEW.jsonb->>'institutionId');
      NEW.campusId = (NEW.jsonb->>'campusId');
      NEW.libraryId = (NEW.jsonb->>'libraryId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_location_references() OWNER TO postgres;

--
-- Name: update_loccampus_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_loccampus_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.institutionId = (NEW.jsonb->>'institutionId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_loccampus_references() OWNER TO postgres;

--
-- Name: update_loclibrary_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_loclibrary_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.campusId = (NEW.jsonb->>'campusId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_loclibrary_references() OWNER TO postgres;

--
-- Name: update_preceding_succeeding_title_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_preceding_succeeding_title_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.precedingInstanceId = (NEW.jsonb->>'precedingInstanceId');
      NEW.succeedingInstanceId = (NEW.jsonb->>'succeedingInstanceId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_preceding_succeeding_title_references() OWNER TO postgres;

--
-- Name: update_service_point_user_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_service_point_user_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.defaultServicePointId = (NEW.jsonb->>'defaultServicePointId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_service_point_user_references() OWNER TO postgres;

--
-- Name: update_statistical_code_references(); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.update_statistical_code_references() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      NEW.statisticalCodeTypeId = (NEW.jsonb->>'statisticalCodeTypeId');
      RETURN NEW;
    END;
    $$;


ALTER FUNCTION lotus_mod_inventory_storage.update_statistical_code_references() OWNER TO postgres;

--
-- Name: upsert(text, uuid, anyelement); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.upsert(text, uuid, anyelement) RETURNS uuid
    LANGUAGE plpgsql
    AS $_$
DECLARE
  ret uuid;
BEGIN
  EXECUTE format('UPDATE lotus_mod_inventory_storage.%I SET jsonb=$3 WHERE id=$2 RETURNING id', $1)
          USING $1, $2, $3 INTO ret;
  IF ret IS NOT NULL THEN
    RETURN ret;
  END IF;
  EXECUTE format('INSERT INTO lotus_mod_inventory_storage.%I (id, jsonb) VALUES ($2, $3) RETURNING id', $1)
          USING $1, $2, $3 INTO STRICT ret;
  RETURN ret;
END;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.upsert(text, uuid, anyelement) OWNER TO postgres;

--
-- Name: uuid_larger(uuid, uuid); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.uuid_larger(uuid, uuid) RETURNS uuid
    LANGUAGE plpgsql
    AS $_$
BEGIN
  IF $1 IS NULL THEN
    RETURN $2;
  END IF;
  IF $2 IS NULL THEN
    RETURN $1;
  END IF;
  IF $1 > $2 THEN
    RETURN $1;
  ELSE
    RETURN $2;
  END IF;
END;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.uuid_larger(uuid, uuid) OWNER TO postgres;

--
-- Name: uuid_smaller(uuid, uuid); Type: FUNCTION; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE FUNCTION lotus_mod_inventory_storage.uuid_smaller(uuid, uuid) RETURNS uuid
    LANGUAGE plpgsql
    AS $_$
BEGIN
  IF $1 IS NULL THEN
    RETURN $2;
  END IF;
  IF $2 IS NULL THEN
    RETURN $1;
  END IF;
  IF $1 < $2 THEN
    RETURN $1;
  ELSE
    RETURN $2;
  END IF;
END;
$_$;


ALTER FUNCTION lotus_mod_inventory_storage.uuid_smaller(uuid, uuid) OWNER TO postgres;

--
-- Name: max(uuid); Type: AGGREGATE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE AGGREGATE lotus_mod_inventory_storage.max(uuid) (
    SFUNC = lotus_mod_inventory_storage.uuid_larger,
    STYPE = uuid,
    COMBINEFUNC = lotus_mod_inventory_storage.uuid_larger,
    SORTOP = OPERATOR(pg_catalog.>),
    PARALLEL = safe
);


ALTER AGGREGATE lotus_mod_inventory_storage.max(uuid) OWNER TO postgres;

--
-- Name: min(uuid); Type: AGGREGATE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE AGGREGATE lotus_mod_inventory_storage.min(uuid) (
    SFUNC = lotus_mod_inventory_storage.uuid_smaller,
    STYPE = uuid,
    COMBINEFUNC = lotus_mod_inventory_storage.uuid_smaller,
    SORTOP = OPERATOR(pg_catalog.<),
    PARALLEL = safe
);


ALTER AGGREGATE lotus_mod_inventory_storage.min(uuid) OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: alternative_title_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.alternative_title_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.alternative_title_type OWNER TO postgres;

--
-- Name: async_migration_job; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.async_migration_job (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.async_migration_job OWNER TO postgres;

--
-- Name: audit_holdings_record; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.audit_holdings_record (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.audit_holdings_record OWNER TO postgres;

--
-- Name: audit_instance; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.audit_instance (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.audit_instance OWNER TO postgres;

--
-- Name: audit_item; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.audit_item (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.audit_item OWNER TO postgres;

--
-- Name: authority; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.authority (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.authority OWNER TO postgres;

--
-- Name: authority_note_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.authority_note_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.authority_note_type OWNER TO postgres;

--
-- Name: bound_with_part; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.bound_with_part (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    itemid uuid,
    holdingsrecordid uuid
);


ALTER TABLE lotus_mod_inventory_storage.bound_with_part OWNER TO postgres;

--
-- Name: call_number_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.call_number_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.call_number_type OWNER TO postgres;

--
-- Name: classification_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.classification_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.classification_type OWNER TO postgres;

--
-- Name: contributor_name_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.contributor_name_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.contributor_name_type OWNER TO postgres;

--
-- Name: contributor_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.contributor_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.contributor_type OWNER TO postgres;

--
-- Name: electronic_access_relationship; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.electronic_access_relationship (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.electronic_access_relationship OWNER TO postgres;

--
-- Name: holdings_note_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.holdings_note_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.holdings_note_type OWNER TO postgres;

--
-- Name: holdings_record; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.holdings_record (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    instanceid uuid,
    permanentlocationid uuid,
    temporarylocationid uuid,
    effectivelocationid uuid,
    holdingstypeid uuid,
    callnumbertypeid uuid,
    illpolicyid uuid,
    sourceid uuid
);


ALTER TABLE lotus_mod_inventory_storage.holdings_record OWNER TO postgres;

--
-- Name: holdings_records_source; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.holdings_records_source (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.holdings_records_source OWNER TO postgres;

--
-- Name: holdings_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.holdings_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.holdings_type OWNER TO postgres;

--
-- Name: hrid_settings; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.hrid_settings (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    lock boolean DEFAULT true,
    CONSTRAINT hrid_settings_lock_check CHECK ((lock = true))
);


ALTER TABLE lotus_mod_inventory_storage.hrid_settings OWNER TO postgres;

--
-- Name: hrid_holdings_seq; Type: SEQUENCE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE SEQUENCE lotus_mod_inventory_storage.hrid_holdings_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 99999999999
    CACHE 1;


ALTER TABLE lotus_mod_inventory_storage.hrid_holdings_seq OWNER TO postgres;

--
-- Name: hrid_holdings_seq; Type: SEQUENCE OWNED BY; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER SEQUENCE lotus_mod_inventory_storage.hrid_holdings_seq OWNED BY lotus_mod_inventory_storage.hrid_settings.jsonb;


--
-- Name: hrid_instances_seq; Type: SEQUENCE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE SEQUENCE lotus_mod_inventory_storage.hrid_instances_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 99999999999
    CACHE 1;


ALTER TABLE lotus_mod_inventory_storage.hrid_instances_seq OWNER TO postgres;

--
-- Name: hrid_instances_seq; Type: SEQUENCE OWNED BY; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER SEQUENCE lotus_mod_inventory_storage.hrid_instances_seq OWNED BY lotus_mod_inventory_storage.hrid_settings.jsonb;


--
-- Name: hrid_items_seq; Type: SEQUENCE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE SEQUENCE lotus_mod_inventory_storage.hrid_items_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 99999999999
    CACHE 1;


ALTER TABLE lotus_mod_inventory_storage.hrid_items_seq OWNER TO postgres;

--
-- Name: hrid_items_seq; Type: SEQUENCE OWNED BY; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER SEQUENCE lotus_mod_inventory_storage.hrid_items_seq OWNED BY lotus_mod_inventory_storage.hrid_settings.jsonb;


--
-- Name: identifier_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.identifier_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.identifier_type OWNER TO postgres;

--
-- Name: ill_policy; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.ill_policy (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.ill_policy OWNER TO postgres;

--
-- Name: instance; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    instancestatusid uuid,
    modeofissuanceid uuid,
    instancetypeid uuid
);


ALTER TABLE lotus_mod_inventory_storage.instance OWNER TO postgres;

--
-- Name: instance_format; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance_format (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.instance_format OWNER TO postgres;

--
-- Name: item; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.item (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    holdingsrecordid uuid,
    permanentloantypeid uuid,
    temporaryloantypeid uuid,
    materialtypeid uuid,
    permanentlocationid uuid,
    temporarylocationid uuid,
    effectivelocationid uuid
);


ALTER TABLE lotus_mod_inventory_storage.item OWNER TO postgres;

--
-- Name: instance_holdings_item_view; Type: VIEW; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE VIEW lotus_mod_inventory_storage.instance_holdings_item_view AS
 SELECT instance.id,
    jsonb_build_object('instanceId', instance.id, 'instance', instance.jsonb, 'holdingsRecords', ( SELECT jsonb_agg(holdings_record.jsonb) AS jsonb_agg
           FROM lotus_mod_inventory_storage.holdings_record
          WHERE (holdings_record.instanceid = instance.id)), 'items', ( SELECT jsonb_agg(item.jsonb) AS jsonb_agg
           FROM (lotus_mod_inventory_storage.holdings_record hr
             JOIN lotus_mod_inventory_storage.item ON (((item.holdingsrecordid = hr.id) AND (hr.instanceid = instance.id))))), 'isBoundWith', ( SELECT (EXISTS ( SELECT 1
                   FROM ((lotus_mod_inventory_storage.bound_with_part bw
                     JOIN lotus_mod_inventory_storage.item it ON ((it.id = bw.itemid)))
                     JOIN lotus_mod_inventory_storage.holdings_record hr ON ((hr.id = bw.holdingsrecordid)))
                  WHERE (hr.instanceid = instance.id)
                 LIMIT 1)) AS "exists")) AS jsonb
   FROM lotus_mod_inventory_storage.instance;


ALTER TABLE lotus_mod_inventory_storage.instance_holdings_item_view OWNER TO postgres;

--
-- Name: instance_note_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance_note_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.instance_note_type OWNER TO postgres;

--
-- Name: instance_relationship; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance_relationship (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    superinstanceid uuid,
    subinstanceid uuid,
    instancerelationshiptypeid uuid
);


ALTER TABLE lotus_mod_inventory_storage.instance_relationship OWNER TO postgres;

--
-- Name: instance_relationship_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance_relationship_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.instance_relationship_type OWNER TO postgres;

--
-- Name: instance_source_marc; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance_source_marc (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.instance_source_marc OWNER TO postgres;

--
-- Name: instance_status; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance_status (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.instance_status OWNER TO postgres;

--
-- Name: instance_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.instance_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.instance_type OWNER TO postgres;

--
-- Name: item_damaged_status; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.item_damaged_status (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.item_damaged_status OWNER TO postgres;

--
-- Name: item_note_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.item_note_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.item_note_type OWNER TO postgres;

--
-- Name: iteration_job; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.iteration_job (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.iteration_job OWNER TO postgres;

--
-- Name: loan_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.loan_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.loan_type OWNER TO postgres;

--
-- Name: location; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.location (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    institutionid uuid,
    campusid uuid,
    libraryid uuid
);


ALTER TABLE lotus_mod_inventory_storage.location OWNER TO postgres;

--
-- Name: loccampus; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.loccampus (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    institutionid uuid
);


ALTER TABLE lotus_mod_inventory_storage.loccampus OWNER TO postgres;

--
-- Name: locinstitution; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.locinstitution (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.locinstitution OWNER TO postgres;

--
-- Name: loclibrary; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.loclibrary (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    campusid uuid
);


ALTER TABLE lotus_mod_inventory_storage.loclibrary OWNER TO postgres;

--
-- Name: material_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.material_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.material_type OWNER TO postgres;

--
-- Name: mode_of_issuance; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.mode_of_issuance (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.mode_of_issuance OWNER TO postgres;

--
-- Name: nature_of_content_term; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.nature_of_content_term (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.nature_of_content_term OWNER TO postgres;

--
-- Name: notification_sending_error; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.notification_sending_error (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.notification_sending_error OWNER TO postgres;

--
-- Name: preceding_succeeding_title; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.preceding_succeeding_title (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    precedinginstanceid uuid,
    succeedinginstanceid uuid,
    CONSTRAINT preceding_or_succeeding_id_is_set CHECK ((((jsonb -> 'precedingInstanceId'::text) IS NOT NULL) OR ((jsonb -> 'succeedingInstanceId'::text) IS NOT NULL)))
);


ALTER TABLE lotus_mod_inventory_storage.preceding_succeeding_title OWNER TO postgres;

--
-- Name: reindex_job; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.reindex_job (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.reindex_job OWNER TO postgres;

--
-- Name: rmb_internal; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.rmb_internal (
    id integer NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.rmb_internal OWNER TO postgres;

--
-- Name: rmb_internal_analyze; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.rmb_internal_analyze (
    tablename text
);


ALTER TABLE lotus_mod_inventory_storage.rmb_internal_analyze OWNER TO postgres;

--
-- Name: rmb_internal_id_seq; Type: SEQUENCE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE SEQUENCE lotus_mod_inventory_storage.rmb_internal_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE lotus_mod_inventory_storage.rmb_internal_id_seq OWNER TO postgres;

--
-- Name: rmb_internal_id_seq; Type: SEQUENCE OWNED BY; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER SEQUENCE lotus_mod_inventory_storage.rmb_internal_id_seq OWNED BY lotus_mod_inventory_storage.rmb_internal.id;


--
-- Name: rmb_internal_index; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.rmb_internal_index (
    name text NOT NULL,
    def text NOT NULL,
    remove boolean NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.rmb_internal_index OWNER TO postgres;

--
-- Name: rmb_job; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.rmb_job (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL
);


ALTER TABLE lotus_mod_inventory_storage.rmb_job OWNER TO postgres;

--
-- Name: service_point; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.service_point (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.service_point OWNER TO postgres;

--
-- Name: service_point_user; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.service_point_user (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    defaultservicepointid uuid
);


ALTER TABLE lotus_mod_inventory_storage.service_point_user OWNER TO postgres;

--
-- Name: statistical_code; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.statistical_code (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text,
    statisticalcodetypeid uuid
);


ALTER TABLE lotus_mod_inventory_storage.statistical_code OWNER TO postgres;

--
-- Name: statistical_code_type; Type: TABLE; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TABLE lotus_mod_inventory_storage.statistical_code_type (
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text
);


ALTER TABLE lotus_mod_inventory_storage.statistical_code_type OWNER TO postgres;

--
-- Name: rmb_internal id; Type: DEFAULT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.rmb_internal ALTER COLUMN id SET DEFAULT nextval('lotus_mod_inventory_storage.rmb_internal_id_seq'::regclass);


--
-- Data for Name: alternative_title_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.alternative_title_type (id, jsonb, creation_date, created_by) FROM stdin;
781c04a4-f41e-4ab0-9118-6836e93de3c8	{"id": "781c04a4-f41e-4ab0-9118-6836e93de3c8", "name": "Distinctive title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.874Z", "updatedDate": "2022-06-23T15:13:59.874Z"}}	2022-06-23 15:13:59.874	\N
432ca81a-fe4d-4249-bfd3-53388725647d	{"id": "432ca81a-fe4d-4249-bfd3-53388725647d", "name": "Caption title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.884Z", "updatedDate": "2022-06-23T15:13:59.884Z"}}	2022-06-23 15:13:59.884	\N
5c364ce4-c8fd-4891-a28d-bb91c9bcdbfb	{"id": "5c364ce4-c8fd-4891-a28d-bb91c9bcdbfb", "name": "Cover title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.891Z", "updatedDate": "2022-06-23T15:13:59.891Z"}}	2022-06-23 15:13:59.891	\N
09964ad1-7aed-49b8-8223-a4c105e3ef87	{"id": "09964ad1-7aed-49b8-8223-a4c105e3ef87", "name": "Running title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.879Z", "updatedDate": "2022-06-23T15:13:59.879Z"}}	2022-06-23 15:13:59.879	\N
30512027-cdc9-4c79-af75-1565b3bd888d	{"id": "30512027-cdc9-4c79-af75-1565b3bd888d", "name": "Uniform title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.889Z", "updatedDate": "2022-06-23T15:13:59.889Z"}}	2022-06-23 15:13:59.889	\N
a8b45056-2223-43ca-8514-4dd88ece984b	{"id": "a8b45056-2223-43ca-8514-4dd88ece984b", "name": "Portion of title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.895Z", "updatedDate": "2022-06-23T15:13:59.895Z"}}	2022-06-23 15:13:59.895	\N
2ca8538d-a2fd-4e60-b967-1cb220101e22	{"id": "2ca8538d-a2fd-4e60-b967-1cb220101e22", "name": "Added title page title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.880Z", "updatedDate": "2022-06-23T15:13:59.880Z"}}	2022-06-23 15:13:59.88	\N
dae08d04-8c4e-4ab2-b6bb-99edbf252231	{"id": "dae08d04-8c4e-4ab2-b6bb-99edbf252231", "name": "Spine title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.893Z", "updatedDate": "2022-06-23T15:13:59.893Z"}}	2022-06-23 15:13:59.893	\N
2584943f-36ad-4037-a7fa-3bdebb09f452	{"id": "2584943f-36ad-4037-a7fa-3bdebb09f452", "name": "Other title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.898Z", "updatedDate": "2022-06-23T15:13:59.898Z"}}	2022-06-23 15:13:59.898	\N
4bb300a4-04c9-414b-bfbc-9c032f74b7b2	{"id": "4bb300a4-04c9-414b-bfbc-9c032f74b7b2", "name": "Parallel title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.875Z", "updatedDate": "2022-06-23T15:13:59.875Z"}}	2022-06-23 15:13:59.875	\N
0fe58901-183e-4678-a3aa-0b4751174ba8	{"id": "0fe58901-183e-4678-a3aa-0b4751174ba8", "name": "No type specified", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.892Z", "updatedDate": "2022-06-23T15:13:59.892Z"}}	2022-06-23 15:13:59.892	\N
\.


--
-- Data for Name: async_migration_job; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.async_migration_job (id, jsonb) FROM stdin;
\.


--
-- Data for Name: audit_holdings_record; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.audit_holdings_record (id, jsonb) FROM stdin;
\.


--
-- Data for Name: audit_instance; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.audit_instance (id, jsonb) FROM stdin;
\.


--
-- Data for Name: audit_item; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.audit_item (id, jsonb) FROM stdin;
\.


--
-- Data for Name: authority; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.authority (id, jsonb, creation_date, created_by) FROM stdin;
\.


--
-- Data for Name: authority_note_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.authority_note_type (id, jsonb, creation_date, created_by) FROM stdin;
76c74801-afec-45a0-aad7-3ff23591e147	{"id": "76c74801-afec-45a0-aad7-3ff23591e147", "name": "Nonpublic general note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.091Z", "updatedDate": "2022-06-23T15:14:00.091Z"}}	2022-06-23 15:14:00.091	\N
\.


--
-- Data for Name: bound_with_part; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.bound_with_part (id, jsonb, creation_date, created_by, itemid, holdingsrecordid) FROM stdin;
ab048eb7-179e-441f-8403-fcb2094eb329	{"id": "ab048eb7-179e-441f-8403-fcb2094eb329", "itemId": "f4b8c3d1-f461-4551-aa7b-5f45e64f236c", "metadata": {"createdDate": "2022-06-23T15:14:01.356Z", "updatedDate": "2022-06-23T15:14:01.356Z"}, "holdingsRecordId": "247f1832-88be-4a84-9638-605ffde308b3"}	2022-06-23 15:14:01.356	\N	f4b8c3d1-f461-4551-aa7b-5f45e64f236c	247f1832-88be-4a84-9638-605ffde308b3
476a9ce4-72c9-4854-b2b8-8baf949a8db6	{"id": "476a9ce4-72c9-4854-b2b8-8baf949a8db6", "itemId": "f4b8c3d1-f461-4551-aa7b-5f45e64f236c", "metadata": {"createdDate": "2022-06-23T15:14:01.357Z", "updatedDate": "2022-06-23T15:14:01.357Z"}, "holdingsRecordId": "704ea4ec-456c-4740-852b-0814d59f7d21"}	2022-06-23 15:14:01.357	\N	f4b8c3d1-f461-4551-aa7b-5f45e64f236c	704ea4ec-456c-4740-852b-0814d59f7d21
831b1e02-8828-470d-bd80-60d48828ca2c	{"id": "831b1e02-8828-470d-bd80-60d48828ca2c", "itemId": "f4b8c3d1-f461-4551-aa7b-5f45e64f236c", "metadata": {"createdDate": "2022-06-23T15:14:01.360Z", "updatedDate": "2022-06-23T15:14:01.360Z"}, "holdingsRecordId": "9e8dc8ce-68f3-4e75-8479-d548ce521157"}	2022-06-23 15:14:01.36	\N	f4b8c3d1-f461-4551-aa7b-5f45e64f236c	9e8dc8ce-68f3-4e75-8479-d548ce521157
\.


--
-- Data for Name: call_number_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.call_number_type (id, jsonb, creation_date, created_by) FROM stdin;
054d460d-d6b9-4469-9e37-7a78a2266655	{"id": "054d460d-d6b9-4469-9e37-7a78a2266655", "name": "National Library of Medicine classification", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.048Z", "updatedDate": "2022-06-23T15:14:00.048Z"}}	2022-06-23 15:14:00.048	\N
28927d76-e097-4f63-8510-e56f2b7a3ad0	{"id": "28927d76-e097-4f63-8510-e56f2b7a3ad0", "name": "Shelving control number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.048Z", "updatedDate": "2022-06-23T15:14:00.048Z"}}	2022-06-23 15:14:00.048	\N
fc388041-6cd0-4806-8a74-ebe3b9ab4c6e	{"id": "fc388041-6cd0-4806-8a74-ebe3b9ab4c6e", "name": "Superintendent of Documents classification", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.056Z", "updatedDate": "2022-06-23T15:14:00.056Z"}}	2022-06-23 15:14:00.056	\N
828ae637-dfa3-4265-a1af-5279c436edff	{"id": "828ae637-dfa3-4265-a1af-5279c436edff", "name": "MOYS", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.059Z", "updatedDate": "2022-06-23T15:14:00.059Z"}}	2022-06-23 15:14:00.059	\N
d644be8f-deb5-4c4d-8c9e-2291b7c0f46f	{"id": "d644be8f-deb5-4c4d-8c9e-2291b7c0f46f", "name": "UDC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.060Z", "updatedDate": "2022-06-23T15:14:00.060Z"}}	2022-06-23 15:14:00.06	\N
5ba6b62e-6858-490a-8102-5b1369873835	{"id": "5ba6b62e-6858-490a-8102-5b1369873835", "name": "Title", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.064Z", "updatedDate": "2022-06-23T15:14:00.064Z"}}	2022-06-23 15:14:00.064	\N
95467209-6d7b-468b-94df-0f5d7ad2747d	{"id": "95467209-6d7b-468b-94df-0f5d7ad2747d", "name": "Library of Congress classification", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.064Z", "updatedDate": "2022-06-23T15:14:00.064Z"}}	2022-06-23 15:14:00.064	\N
cd70562c-dd0b-42f6-aa80-ce803d24d4a1	{"id": "cd70562c-dd0b-42f6-aa80-ce803d24d4a1", "name": "Shelved separately", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.068Z", "updatedDate": "2022-06-23T15:14:00.068Z"}}	2022-06-23 15:14:00.068	\N
6caca63e-5651-4db6-9247-3205156e9699	{"id": "6caca63e-5651-4db6-9247-3205156e9699", "name": "Other scheme", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.070Z", "updatedDate": "2022-06-23T15:14:00.070Z"}}	2022-06-23 15:14:00.07	\N
512173a7-bd09-490e-b773-17d83f2b63fe	{"id": "512173a7-bd09-490e-b773-17d83f2b63fe", "name": "LC Modified", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.071Z", "updatedDate": "2022-06-23T15:14:00.071Z"}}	2022-06-23 15:14:00.071	\N
827a2b64-cbf5-4296-8545-130876e4dfc0	{"id": "827a2b64-cbf5-4296-8545-130876e4dfc0", "name": "Source specified in subfield $2", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.074Z", "updatedDate": "2022-06-23T15:14:00.074Z"}}	2022-06-23 15:14:00.074	\N
03dd64d0-5626-4ecd-8ece-4531e0069f35	{"id": "03dd64d0-5626-4ecd-8ece-4531e0069f35", "name": "Dewey Decimal classification", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.075Z", "updatedDate": "2022-06-23T15:14:00.075Z"}}	2022-06-23 15:14:00.075	\N
\.


--
-- Data for Name: classification_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.classification_type (id, jsonb, creation_date, created_by) FROM stdin;
42471af9-7d25-4f3a-bf78-60d29dcf463b	{"id": "42471af9-7d25-4f3a-bf78-60d29dcf463b", "name": "Dewey", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.663Z", "updatedDate": "2022-06-23T15:13:59.663Z"}}	2022-06-23 15:13:59.663	\N
e8662436-75a8-4984-bebc-531e38c774a0	{"id": "e8662436-75a8-4984-bebc-531e38c774a0", "name": "UDC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.666Z", "updatedDate": "2022-06-23T15:13:59.666Z"}}	2022-06-23 15:13:59.666	\N
9a60012a-0fcf-4da9-a1d1-148e818c27ad	{"id": "9a60012a-0fcf-4da9-a1d1-148e818c27ad", "name": "National Agricultural Library", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.669Z", "updatedDate": "2022-06-23T15:13:59.669Z"}}	2022-06-23 15:13:59.669	\N
ad615f6e-e28c-4343-b4a0-457397c5be3e	{"id": "ad615f6e-e28c-4343-b4a0-457397c5be3e", "name": "Canadian Classification", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.668Z", "updatedDate": "2022-06-23T15:13:59.668Z"}}	2022-06-23 15:13:59.668	\N
74c08086-81a4-4466-93d8-d117ce8646db	{"id": "74c08086-81a4-4466-93d8-d117ce8646db", "name": "Additional Dewey", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.666Z", "updatedDate": "2022-06-23T15:13:59.666Z"}}	2022-06-23 15:13:59.666	\N
a83699eb-cc23-4307-8043-5a38a8dce335	{"id": "a83699eb-cc23-4307-8043-5a38a8dce335", "name": "LC (local)", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.670Z", "updatedDate": "2022-06-23T15:13:59.670Z"}}	2022-06-23 15:13:59.67	\N
a7f4d03f-b0d8-496c-aebf-4e9cdb678200	{"id": "a7f4d03f-b0d8-496c-aebf-4e9cdb678200", "name": "NLM", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.677Z", "updatedDate": "2022-06-23T15:13:59.677Z"}}	2022-06-23 15:13:59.677	\N
9075b5f8-7d97-49e1-a431-73fdd468d476	{"id": "9075b5f8-7d97-49e1-a431-73fdd468d476", "name": "SUDOC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.681Z", "updatedDate": "2022-06-23T15:13:59.681Z"}}	2022-06-23 15:13:59.681	\N
fb12264c-ff3b-47e0-8e09-b0aa074361f1	{"id": "fb12264c-ff3b-47e0-8e09-b0aa074361f1", "name": "GDC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.682Z", "updatedDate": "2022-06-23T15:13:59.682Z"}}	2022-06-23 15:13:59.682	\N
ce176ace-a53e-4b4d-aa89-725ed7b2edac	{"id": "ce176ace-a53e-4b4d-aa89-725ed7b2edac", "name": "LC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.682Z", "updatedDate": "2022-06-23T15:13:59.682Z"}}	2022-06-23 15:13:59.682	\N
\.


--
-- Data for Name: contributor_name_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.contributor_name_type (id, jsonb, creation_date, created_by) FROM stdin;
e8b311a6-3b21-43f2-a269-dd9310cb2d0a	{"id": "e8b311a6-3b21-43f2-a269-dd9310cb2d0a", "name": "Meeting name", "metadata": {"createdDate": "2022-06-23T15:13:59.263Z", "updatedDate": "2022-06-23T15:13:59.263Z"}, "ordering": "3"}	2022-06-23 15:13:59.263	\N
2e48e713-17f3-4c13-a9f8-23845bb210aa	{"id": "2e48e713-17f3-4c13-a9f8-23845bb210aa", "name": "Corporate name", "metadata": {"createdDate": "2022-06-23T15:13:59.263Z", "updatedDate": "2022-06-23T15:13:59.263Z"}, "ordering": "2"}	2022-06-23 15:13:59.263	\N
2b94c631-fca9-4892-a730-03ee529ffe2a	{"id": "2b94c631-fca9-4892-a730-03ee529ffe2a", "name": "Personal name", "metadata": {"createdDate": "2022-06-23T15:13:59.265Z", "updatedDate": "2022-06-23T15:13:59.265Z"}, "ordering": "1"}	2022-06-23 15:13:59.265	\N
\.


--
-- Data for Name: contributor_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.contributor_type (id, jsonb) FROM stdin;
12a73179-1283-4828-8fd9-065e18dc2e78	{"id": "12a73179-1283-4828-8fd9-065e18dc2e78", "code": "sgn", "name": "Signer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.602+00:00", "updatedDate": "2022-06-23T15:13:58.602+00:00"}}
9e99e803-c73d-4250-8605-403be57f83f9	{"id": "9e99e803-c73d-4250-8605-403be57f83f9", "code": "bpd", "name": "Bookplate designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.603+00:00", "updatedDate": "2022-06-23T15:13:58.603+00:00"}}
41a0378d-5362-4c1a-b103-592ff354be1c	{"id": "41a0378d-5362-4c1a-b103-592ff354be1c", "code": "jud", "name": "Judge", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.604+00:00", "updatedDate": "2022-06-23T15:13:58.604+00:00"}}
2665431e-aad4-44d1-9218-04053d1cfd53	{"id": "2665431e-aad4-44d1-9218-04053d1cfd53", "code": "fmp", "name": "Film producer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.604+00:00", "updatedDate": "2022-06-23T15:13:58.604+00:00"}}
00311f78-e990-4d8b-907e-c67a3664fe15	{"id": "00311f78-e990-4d8b-907e-c67a3664fe15", "code": "dtc", "name": "Data contributor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.606+00:00", "updatedDate": "2022-06-23T15:13:58.606+00:00"}}
3c3ab522-2600-4b93-a121-8832146d5cdf	{"id": "3c3ab522-2600-4b93-a121-8832146d5cdf", "code": "rsp", "name": "Respondent", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.608+00:00", "updatedDate": "2022-06-23T15:13:58.608+00:00"}}
246858e3-4022-4991-9f1c-50901ccc1438	{"id": "246858e3-4022-4991-9f1c-50901ccc1438", "code": "prf", "name": "Performer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.609+00:00", "updatedDate": "2022-06-23T15:13:58.609+00:00"}}
6ccd61f4-c408-46ec-b359-a761b4781477	{"id": "6ccd61f4-c408-46ec-b359-a761b4781477", "code": "etr", "name": "Etcher", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.612+00:00", "updatedDate": "2022-06-23T15:13:58.612+00:00"}}
61afcb8a-8c53-445b-93b9-38e799721f82	{"id": "61afcb8a-8c53-445b-93b9-38e799721f82", "code": "enj", "name": "Enacting jurisdiction", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.613+00:00", "updatedDate": "2022-06-23T15:13:58.613+00:00"}}
f9395f3d-cd46-413e-9504-8756c54f38a2	{"id": "f9395f3d-cd46-413e-9504-8756c54f38a2", "code": "pfr", "name": "Proofreader", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.614+00:00", "updatedDate": "2022-06-23T15:13:58.614+00:00"}}
5c3abceb-6bd8-43aa-b08d-1187ae78b15b	{"id": "5c3abceb-6bd8-43aa-b08d-1187ae78b15b", "code": "fmo", "name": "Former owner", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.614+00:00", "updatedDate": "2022-06-23T15:13:58.614+00:00"}}
e2b5ceaf-663b-4cc0-91ba-bf036943ece8	{"id": "e2b5ceaf-663b-4cc0-91ba-bf036943ece8", "code": "prp", "name": "Production place", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.615+00:00", "updatedDate": "2022-06-23T15:13:58.615+00:00"}}
ec56cc25-e470-46f7-a429-72f438c0513b	{"id": "ec56cc25-e470-46f7-a429-72f438c0513b", "code": "wit", "name": "Witness", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.617+00:00", "updatedDate": "2022-06-23T15:13:58.617+00:00"}}
06fef928-bd00-4c7f-bd3c-5bc93973f8e8	{"id": "06fef928-bd00-4c7f-bd3c-5bc93973f8e8", "code": "frg", "name": "Forger", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.618+00:00", "updatedDate": "2022-06-23T15:13:58.618+00:00"}}
c8050073-f62b-4606-9688-02caa98bdc60	{"id": "c8050073-f62b-4606-9688-02caa98bdc60", "code": "crr", "name": "Corrector", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.620+00:00", "updatedDate": "2022-06-23T15:13:58.620+00:00"}}
a8d59132-aa1e-4a62-b5bd-b26b7d7a16b9	{"id": "a8d59132-aa1e-4a62-b5bd-b26b7d7a16b9", "code": "lse", "name": "Licensee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.620+00:00", "updatedDate": "2022-06-23T15:13:58.620+00:00"}}
c6005151-7005-4ee7-8d6d-a6b72d25377a	{"id": "c6005151-7005-4ee7-8d6d-a6b72d25377a", "code": "vdg", "name": "Videographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.621+00:00", "updatedDate": "2022-06-23T15:13:58.621+00:00"}}
a2231628-6a5a-48f4-8eac-7e6b0328f6fe	{"id": "a2231628-6a5a-48f4-8eac-7e6b0328f6fe", "code": "mfp", "name": "Manufacture place", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.623+00:00", "updatedDate": "2022-06-23T15:13:58.623+00:00"}}
cd06cefa-acfe-48cb-a5a3-4c48be4a79ad	{"id": "cd06cefa-acfe-48cb-a5a3-4c48be4a79ad", "code": "rpy", "name": "Responsible party", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.623+00:00", "updatedDate": "2022-06-23T15:13:58.623+00:00"}}
d669122b-c021-46f5-a911-1e9df10b6542	{"id": "d669122b-c021-46f5-a911-1e9df10b6542", "code": "mfr", "name": "Manufacturer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.625+00:00", "updatedDate": "2022-06-23T15:13:58.625+00:00"}}
168b6ff3-7482-4fd0-bf07-48172b47876c	{"id": "168b6ff3-7482-4fd0-bf07-48172b47876c", "code": "mrk", "name": "Markup editor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.626+00:00", "updatedDate": "2022-06-23T15:13:58.626+00:00"}}
94e6a5a8-b84f-44f7-b900-71cd10ea954e	{"id": "94e6a5a8-b84f-44f7-b900-71cd10ea954e", "code": "rcp", "name": "Addressee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.627+00:00", "updatedDate": "2022-06-23T15:13:58.627+00:00"}}
115fa75c-385b-4a8e-9a2b-b13de9f21bcf	{"id": "115fa75c-385b-4a8e-9a2b-b13de9f21bcf", "code": "wpr", "name": "Writer of preface", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.628+00:00", "updatedDate": "2022-06-23T15:13:58.628+00:00"}}
af9a58fa-95df-4139-a06d-ecdab0b2317e	{"id": "af9a58fa-95df-4139-a06d-ecdab0b2317e", "code": "egr", "name": "Engraver", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.629+00:00", "updatedDate": "2022-06-23T15:13:58.629+00:00"}}
1c623f6e-25bf-41ec-8110-6bde712dfa79	{"id": "1c623f6e-25bf-41ec-8110-6bde712dfa79", "code": "sds", "name": "Sound designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.630+00:00", "updatedDate": "2022-06-23T15:13:58.630+00:00"}}
81bbe282-dca7-4763-bf5a-fe28c8939988	{"id": "81bbe282-dca7-4763-bf5a-fe28c8939988", "code": "pro", "name": "Producer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.631+00:00", "updatedDate": "2022-06-23T15:13:58.631+00:00"}}
d517010e-908f-49d6-b1e8-8c1a5f9a7f1c	{"id": "d517010e-908f-49d6-b1e8-8c1a5f9a7f1c", "code": "aft", "name": "Author of afterword, colophon, etc.", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.633+00:00", "updatedDate": "2022-06-23T15:13:58.633+00:00"}}
7131e7b8-84fa-48bd-a725-14050be38f9f	{"id": "7131e7b8-84fa-48bd-a725-14050be38f9f", "code": "act", "name": "Actor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.633+00:00", "updatedDate": "2022-06-23T15:13:58.633+00:00"}}
c9d28351-c862-433e-8957-c4721f30631f	{"id": "c9d28351-c862-433e-8957-c4721f30631f", "code": "acp", "name": "Art copyist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.634+00:00", "updatedDate": "2022-06-23T15:13:58.634+00:00"}}
e8b5040d-a5c7-47c1-96ca-6313c8b9c849	{"id": "e8b5040d-a5c7-47c1-96ca-6313c8b9c849", "code": "ato", "name": "Autographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.635+00:00", "updatedDate": "2022-06-23T15:13:58.635+00:00"}}
57247637-c41b-498d-9c46-935469335485	{"id": "57247637-c41b-498d-9c46-935469335485", "code": "aqt", "name": "Author in quotations or text abstracts", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.642+00:00", "updatedDate": "2022-06-23T15:13:58.642+00:00"}}
3665d2dd-24cc-4fb4-922a-699811daa41c	{"id": "3665d2dd-24cc-4fb4-922a-699811daa41c", "code": "dsr", "name": "Designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.647+00:00", "updatedDate": "2022-06-23T15:13:58.647+00:00"}}
2a3e2d58-3a21-4e35-b7e4-cffb197750e3	{"id": "2a3e2d58-3a21-4e35-b7e4-cffb197750e3", "code": "cng", "name": "Cinematographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.657+00:00", "updatedDate": "2022-06-23T15:13:58.657+00:00"}}
d6a6d28c-1bfc-46df-b2ba-6cb377a6151e	{"id": "d6a6d28c-1bfc-46df-b2ba-6cb377a6151e", "code": "prm", "name": "Printmaker", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.663+00:00", "updatedDate": "2022-06-23T15:13:58.663+00:00"}}
08cb225a-302c-4d5a-a6a3-fa90850babcd	{"id": "08cb225a-302c-4d5a-a6a3-fa90850babcd", "code": "pra", "name": "Praeses", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.675+00:00", "updatedDate": "2022-06-23T15:13:58.675+00:00"}}
7c5c2fd5-3283-4f96-be89-3bb3e8fa6942	{"id": "7c5c2fd5-3283-4f96-be89-3bb3e8fa6942", "code": "wst", "name": "Writer of supplementary textual content", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.682+00:00", "updatedDate": "2022-06-23T15:13:58.682+00:00"}}
863e41e3-b9c5-44fb-abeb-a8ab536bb432	{"id": "863e41e3-b9c5-44fb-abeb-a8ab536bb432", "code": "edc", "name": "Editor of compilation", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.692+00:00", "updatedDate": "2022-06-23T15:13:58.692+00:00"}}
5e9333a6-bc92-43c0-a306-30811bb71e61	{"id": "5e9333a6-bc92-43c0-a306-30811bb71e61", "code": "lgd", "name": "Lighting designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.702+00:00", "updatedDate": "2022-06-23T15:13:58.702+00:00"}}
05875ac5-a509-4a51-a6ee-b8051e37c7b0	{"id": "05875ac5-a509-4a51-a6ee-b8051e37c7b0", "code": "sce", "name": "Scenarist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.716+00:00", "updatedDate": "2022-06-23T15:13:58.716+00:00"}}
81b2174a-06b9-48f5-8c49-6cbaf7b869fe	{"id": "81b2174a-06b9-48f5-8c49-6cbaf7b869fe", "code": "his", "name": "Host institution", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.727+00:00", "updatedDate": "2022-06-23T15:13:58.727+00:00"}}
ced7cdfc-a3e0-47c8-861b-3f558094b02e	{"id": "ced7cdfc-a3e0-47c8-861b-3f558094b02e", "code": "ant", "name": "Bibliographic antecedent", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.739+00:00", "updatedDate": "2022-06-23T15:13:58.739+00:00"}}
38c09577-6652-4281-a391-4caabe4c09b6	{"id": "38c09577-6652-4281-a391-4caabe4c09b6", "code": "spn", "name": "Sponsor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.754+00:00", "updatedDate": "2022-06-23T15:13:58.754+00:00"}}
50a6d58a-cea2-42a1-8c57-0c6fde225c93	{"id": "50a6d58a-cea2-42a1-8c57-0c6fde225c93", "code": "bsl", "name": "Bookseller", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.768+00:00", "updatedDate": "2022-06-23T15:13:58.768+00:00"}}
66bfc19c-eeb0-4167-bd8d-448311aab929	{"id": "66bfc19c-eeb0-4167-bd8d-448311aab929", "code": "mcp", "name": "Music copyist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.827+00:00", "updatedDate": "2022-06-23T15:13:58.827+00:00"}}
2129a478-c55c-4f71-9cd1-584cbbb381d4	{"id": "2129a478-c55c-4f71-9cd1-584cbbb381d4", "code": "fmk", "name": "Filmmaker", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.835+00:00", "updatedDate": "2022-06-23T15:13:58.835+00:00"}}
1ce93f32-3e10-46e2-943f-77f3c8a41d7d	{"id": "1ce93f32-3e10-46e2-943f-77f3c8a41d7d", "code": "vac", "name": "Voice actor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.842+00:00", "updatedDate": "2022-06-23T15:13:58.842+00:00"}}
a986c8f2-b36a-400d-b09f-9250a753563c	{"id": "a986c8f2-b36a-400d-b09f-9250a753563c", "code": "brl", "name": "Braille embosser", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.848+00:00", "updatedDate": "2022-06-23T15:13:58.848+00:00"}}
e38a0c64-f1d3-4b03-a364-34d6b402841c	{"id": "e38a0c64-f1d3-4b03-a364-34d6b402841c", "code": "ppm", "name": "Papermaker", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.854+00:00", "updatedDate": "2022-06-23T15:13:58.854+00:00"}}
c5988fb2-cd21-469c-b35e-37e443c01adc	{"id": "c5988fb2-cd21-469c-b35e-37e443c01adc", "code": "sad", "name": "Scientific advisor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.867+00:00", "updatedDate": "2022-06-23T15:13:58.867+00:00"}}
eecb30c5-a061-4790-8fa5-cf24d0fa472b	{"id": "eecb30c5-a061-4790-8fa5-cf24d0fa472b", "code": "ivr", "name": "Interviewer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.871+00:00", "updatedDate": "2022-06-23T15:13:58.871+00:00"}}
e8423d78-7b08-4f81-8f34-4871d5e2b7af	{"id": "e8423d78-7b08-4f81-8f34-4871d5e2b7af", "code": "ctt", "name": "Contestee-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.877+00:00", "updatedDate": "2022-06-23T15:13:58.877+00:00"}}
d67decd7-3dbe-4ac7-8072-ef18f5cd3e09	{"id": "d67decd7-3dbe-4ac7-8072-ef18f5cd3e09", "code": "cur", "name": "Curator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.889+00:00", "updatedDate": "2022-06-23T15:13:58.889+00:00"}}
7156fd73-b8ca-4e09-a002-bb2afaaf259a	{"id": "7156fd73-b8ca-4e09-a002-bb2afaaf259a", "code": "rse", "name": "Respondent-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.894+00:00", "updatedDate": "2022-06-23T15:13:58.894+00:00"}}
206246b1-8e17-4588-bad8-78c82e3e6d54	{"id": "206246b1-8e17-4588-bad8-78c82e3e6d54", "code": "sht", "name": "Supporting host", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.900+00:00", "updatedDate": "2022-06-23T15:13:58.900+00:00"}}
e79ca231-af4c-4724-8fe1-eabafd2e0bec	{"id": "e79ca231-af4c-4724-8fe1-eabafd2e0bec", "code": "mod", "name": "Moderator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.906+00:00", "updatedDate": "2022-06-23T15:13:58.906+00:00"}}
cbceda25-1f4d-43b7-96a5-f2911026a154	{"id": "cbceda25-1f4d-43b7-96a5-f2911026a154", "code": "clt", "name": "Collotyper", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.910+00:00", "updatedDate": "2022-06-23T15:13:58.910+00:00"}}
7aac64ab-7f2a-4019-9705-e07133e3ad1a	{"id": "7aac64ab-7f2a-4019-9705-e07133e3ad1a", "code": "cre", "name": "Creator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.914+00:00", "updatedDate": "2022-06-23T15:13:58.914+00:00"}}
88a66ebf-0b18-4ed7-91e5-01bc7e8de441	{"id": "88a66ebf-0b18-4ed7-91e5-01bc7e8de441", "code": "lee", "name": "Libelee-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.636+00:00", "updatedDate": "2022-06-23T15:13:58.636+00:00"}}
0d022d0d-902d-4273-8013-0a2a753d9d76	{"id": "0d022d0d-902d-4273-8013-0a2a753d9d76", "code": "rbr", "name": "Rubricator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.639+00:00", "updatedDate": "2022-06-23T15:13:58.639+00:00"}}
28de45ae-f0ca-46fe-9f89-283313b3255b	{"id": "28de45ae-f0ca-46fe-9f89-283313b3255b", "code": "abr", "name": "Abridger", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.643+00:00", "updatedDate": "2022-06-23T15:13:58.643+00:00"}}
fd0a47ec-58ce-43f6-8ecc-696ec17a98ab	{"id": "fd0a47ec-58ce-43f6-8ecc-696ec17a98ab", "code": "pop", "name": "Printer of plates", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.655+00:00", "updatedDate": "2022-06-23T15:13:58.655+00:00"}}
0d8dc4be-e87b-43df-90d4-1ed60c4e08c5	{"id": "0d8dc4be-e87b-43df-90d4-1ed60c4e08c5", "code": "dte", "name": "Dedicatee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.661+00:00", "updatedDate": "2022-06-23T15:13:58.661+00:00"}}
a7a25290-226d-4f81-b780-2efc1f7dfd26	{"id": "a7a25290-226d-4f81-b780-2efc1f7dfd26", "code": "med", "name": "Medium", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.673+00:00", "updatedDate": "2022-06-23T15:13:58.673+00:00"}}
a3642006-14ab-4816-b5ac-533e4971417a	{"id": "a3642006-14ab-4816-b5ac-533e4971417a", "code": "stl", "name": "Storyteller", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.680+00:00", "updatedDate": "2022-06-23T15:13:58.680+00:00"}}
22286157-3058-434c-9009-8f8d100fc74a	{"id": "22286157-3058-434c-9009-8f8d100fc74a", "code": "ctg", "name": "Cartographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.687+00:00", "updatedDate": "2022-06-23T15:13:58.687+00:00"}}
94d131ef-2814-49a0-a59c-49b6e7584b3d	{"id": "94d131ef-2814-49a0-a59c-49b6e7584b3d", "code": "stn", "name": "Standards body", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.695+00:00", "updatedDate": "2022-06-23T15:13:58.695+00:00"}}
e1510ac5-a9e9-4195-b762-7cb82c5357c4	{"id": "e1510ac5-a9e9-4195-b762-7cb82c5357c4", "code": "cst", "name": "Costume designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.702+00:00", "updatedDate": "2022-06-23T15:13:58.702+00:00"}}
dd44e44e-a153-4ab6-9a7c-f3d23b6c4676	{"id": "dd44e44e-a153-4ab6-9a7c-f3d23b6c4676", "code": "col", "name": "Collector", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.707+00:00", "updatedDate": "2022-06-23T15:13:58.707+00:00"}}
846ac49c-749d-49fd-a05f-e7f2885d9eaf	{"id": "846ac49c-749d-49fd-a05f-e7f2885d9eaf", "code": "bkd", "name": "Book designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.714+00:00", "updatedDate": "2022-06-23T15:13:58.714+00:00"}}
9945290f-bcd7-4515-81fd-09e23567b75d	{"id": "9945290f-bcd7-4515-81fd-09e23567b75d", "code": "coe", "name": "Contestant-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.723+00:00", "updatedDate": "2022-06-23T15:13:58.723+00:00"}}
f9e5b41b-8d5b-47d3-91d0-ca9004796337	{"id": "f9e5b41b-8d5b-47d3-91d0-ca9004796337", "code": "art", "name": "Artist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.730+00:00", "updatedDate": "2022-06-23T15:13:58.730+00:00"}}
5aa6e3d1-283c-4f6d-8694-3bdc52137b07	{"id": "5aa6e3d1-283c-4f6d-8694-3bdc52137b07", "code": "cos", "name": "Contestant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.738+00:00", "updatedDate": "2022-06-23T15:13:58.738+00:00"}}
9593efce-a42d-4991-9aad-3a4dc07abb1e	{"id": "9593efce-a42d-4991-9aad-3a4dc07abb1e", "code": "asn", "name": "Associated name", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.751+00:00", "updatedDate": "2022-06-23T15:13:58.751+00:00"}}
5d92d9de-adf3-4dea-93b5-580e9a88e696	{"id": "5d92d9de-adf3-4dea-93b5-580e9a88e696", "code": "cpc", "name": "Copyright claimant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.757+00:00", "updatedDate": "2022-06-23T15:13:58.757+00:00"}}
7c62ecb4-544c-4c26-8765-f6f6d34031a0	{"id": "7c62ecb4-544c-4c26-8765-f6f6d34031a0", "code": "dpt", "name": "Depositor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.765+00:00", "updatedDate": "2022-06-23T15:13:58.765+00:00"}}
fcfc0b86-b083-4ab8-8a75-75a66638ed2e	{"id": "fcfc0b86-b083-4ab8-8a75-75a66638ed2e", "code": "rdd", "name": "Radio director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.826+00:00", "updatedDate": "2022-06-23T15:13:58.826+00:00"}}
5c132335-8ad0-47bf-a4d1-6dda0a3a2654	{"id": "5c132335-8ad0-47bf-a4d1-6dda0a3a2654", "code": "auc", "name": "Auctioneer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.830+00:00", "updatedDate": "2022-06-23T15:13:58.830+00:00"}}
245cfa8e-8709-4f1f-969b-894b94bc029f	{"id": "245cfa8e-8709-4f1f-969b-894b94bc029f", "code": "blw", "name": "Blurb writer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.835+00:00", "updatedDate": "2022-06-23T15:13:58.835+00:00"}}
7e5b0859-80c1-4e78-a5e7-61979862c1fa	{"id": "7e5b0859-80c1-4e78-a5e7-61979862c1fa", "code": "str", "name": "Stereotyper", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.838+00:00", "updatedDate": "2022-06-23T15:13:58.838+00:00"}}
593862b4-a655-47c3-92b9-2b305b14cce7	{"id": "593862b4-a655-47c3-92b9-2b305b14cce7", "code": "chr", "name": "Choreographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.843+00:00", "updatedDate": "2022-06-23T15:13:58.843+00:00"}}
f90c67e8-d1fa-4fe9-b98b-cbc3f019c65f	{"id": "f90c67e8-d1fa-4fe9-b98b-cbc3f019c65f", "code": "bnd", "name": "Binder", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.845+00:00", "updatedDate": "2022-06-23T15:13:58.845+00:00"}}
21430354-f17a-4ac1-8545-1a5907cd15e5	{"id": "21430354-f17a-4ac1-8545-1a5907cd15e5", "code": "inv", "name": "Inventor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.850+00:00", "updatedDate": "2022-06-23T15:13:58.850+00:00"}}
9e7651f8-a4f0-4d02-81b4-578ef9303d1b	{"id": "9e7651f8-a4f0-4d02-81b4-578ef9303d1b", "code": "std", "name": "Set designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.856+00:00", "updatedDate": "2022-06-23T15:13:58.856+00:00"}}
94b839e8-cabe-4d58-8918-8a5058fe5501	{"id": "94b839e8-cabe-4d58-8918-8a5058fe5501", "code": "rst", "name": "Respondent-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.866+00:00", "updatedDate": "2022-06-23T15:13:58.866+00:00"}}
319cb290-a549-4ae8-a0ed-a65fe155cac8	{"id": "319cb290-a549-4ae8-a0ed-a65fe155cac8", "code": "crp", "name": "Correspondent", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.873+00:00", "updatedDate": "2022-06-23T15:13:58.873+00:00"}}
ae8bc401-47da-4853-9b0b-c7c2c3ec324d	{"id": "ae8bc401-47da-4853-9b0b-c7c2c3ec324d", "code": "lil", "name": "Libelant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.879+00:00", "updatedDate": "2022-06-23T15:13:58.879+00:00"}}
2b7080f7-d03d-46af-86f0-40ea02867362	{"id": "2b7080f7-d03d-46af-86f0-40ea02867362", "code": "cph", "name": "Copyright holder", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.638+00:00", "updatedDate": "2022-06-23T15:13:58.638+00:00"}}
94bb3440-591f-41af-80fa-e124006faa49	{"id": "94bb3440-591f-41af-80fa-e124006faa49", "code": "con", "name": "Conservator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.641+00:00", "updatedDate": "2022-06-23T15:13:58.641+00:00"}}
825a7d9f-7596-4007-9684-9bee72625cfc	{"id": "825a7d9f-7596-4007-9684-9bee72625cfc", "code": "dgs", "name": "Degree supervisor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.645+00:00", "updatedDate": "2022-06-23T15:13:58.645+00:00"}}
88370fc3-bf69-45b6-b518-daf9a3877385	{"id": "88370fc3-bf69-45b6-b518-daf9a3877385", "code": "dub", "name": "Dubious author", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.653+00:00", "updatedDate": "2022-06-23T15:13:58.653+00:00"}}
6b566426-f325-4182-ac31-e1c4e0b2aa19	{"id": "6b566426-f325-4182-ac31-e1c4e0b2aa19", "code": "ren", "name": "Renderer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.659+00:00", "updatedDate": "2022-06-23T15:13:58.659+00:00"}}
453e4f4a-cda9-4cfa-b93d-3faeb18a85db	{"id": "453e4f4a-cda9-4cfa-b93d-3faeb18a85db", "code": "rsg", "name": "Restager", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.671+00:00", "updatedDate": "2022-06-23T15:13:58.671+00:00"}}
c9c3bbe8-d305-48ef-ab2a-5eff941550e3	{"id": "c9c3bbe8-d305-48ef-ab2a-5eff941550e3", "code": "bkp", "name": "Book producer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.677+00:00", "updatedDate": "2022-06-23T15:13:58.677+00:00"}}
08553068-8495-49c2-9c18-d29ab656fef0	{"id": "08553068-8495-49c2-9c18-d29ab656fef0", "code": "mus", "name": "Musician", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.684+00:00", "updatedDate": "2022-06-23T15:13:58.684+00:00"}}
867f3d13-779a-454e-8a06-a1b9fb37ba2a	{"id": "867f3d13-779a-454e-8a06-a1b9fb37ba2a", "code": "scr", "name": "Scribe", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.691+00:00", "updatedDate": "2022-06-23T15:13:58.691+00:00"}}
45747710-39dc-47ec-b2b3-024d757f997e	{"id": "45747710-39dc-47ec-b2b3-024d757f997e", "code": "pte", "name": "Plaintiff-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.697+00:00", "updatedDate": "2022-06-23T15:13:58.697+00:00"}}
58461dca-efd4-4fd4-b380-d033e3540be5	{"id": "58461dca-efd4-4fd4-b380-d033e3540be5", "code": "tyg", "name": "Typographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.706+00:00", "updatedDate": "2022-06-23T15:13:58.706+00:00"}}
55e4a59b-2dfd-478d-9fe9-110fc24f0752	{"id": "55e4a59b-2dfd-478d-9fe9-110fc24f0752", "code": "brd", "name": "Broadcaster", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.712+00:00", "updatedDate": "2022-06-23T15:13:58.712+00:00"}}
7bebb5a2-9332-4ba7-a258-875143b5d754	{"id": "7bebb5a2-9332-4ba7-a258-875143b5d754", "code": "csp", "name": "Consultant to a project", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.720+00:00", "updatedDate": "2022-06-23T15:13:58.720+00:00"}}
b02cbeb7-8ca7-4bf4-8d58-ce943b4d5ea3	{"id": "b02cbeb7-8ca7-4bf4-8d58-ce943b4d5ea3", "code": "stm", "name": "Stage manager", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.727+00:00", "updatedDate": "2022-06-23T15:13:58.727+00:00"}}
cce475f7-ccfa-4e15-adf8-39f907788515	{"id": "cce475f7-ccfa-4e15-adf8-39f907788515", "code": "ths", "name": "Thesis advisor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.735+00:00", "updatedDate": "2022-06-23T15:13:58.735+00:00"}}
f72a24d1-f404-4275-9350-158fe3a20b21	{"id": "f72a24d1-f404-4275-9350-158fe3a20b21", "code": "tch", "name": "Teacher", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.741+00:00", "updatedDate": "2022-06-23T15:13:58.741+00:00"}}
9f0a2cf0-7a9b-45a2-a403-f68d2850d07c	{"id": "9f0a2cf0-7a9b-45a2-a403-f68d2850d07c", "code": "ctb", "name": "Contributor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.748+00:00", "updatedDate": "2022-06-23T15:13:58.748+00:00"}}
913233b3-b2a0-4635-8dad-49b6fc515fc5	{"id": "913233b3-b2a0-4635-8dad-49b6fc515fc5", "code": "wam", "name": "Writer of accompanying material", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.754+00:00", "updatedDate": "2022-06-23T15:13:58.754+00:00"}}
3db02638-598e-44a3-aafa-cbae77533ee1	{"id": "3db02638-598e-44a3-aafa-cbae77533ee1", "code": "ccp", "name": "Conceptor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.764+00:00", "updatedDate": "2022-06-23T15:13:58.764+00:00"}}
398a0a2f-752d-4496-8737-e6df7c29aaa7	{"id": "398a0a2f-752d-4496-8737-e6df7c29aaa7", "code": "lyr", "name": "Lyricist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.776+00:00", "updatedDate": "2022-06-23T15:13:58.776+00:00"}}
754edaff-07bb-45eb-88bf-10a8b6842c38	{"id": "754edaff-07bb-45eb-88bf-10a8b6842c38", "code": "arc", "name": "Architect", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.829+00:00", "updatedDate": "2022-06-23T15:13:58.829+00:00"}}
361f4bfd-a87d-463c-84d8-69346c3082f6	{"id": "361f4bfd-a87d-463c-84d8-69346c3082f6", "code": "oth", "name": "Other", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.834+00:00", "updatedDate": "2022-06-23T15:13:58.834+00:00"}}
44eaf0db-85dd-4888-ac8d-a5976dd483a6	{"id": "44eaf0db-85dd-4888-ac8d-a5976dd483a6", "code": "rth", "name": "Research team head", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.842+00:00", "updatedDate": "2022-06-23T15:13:58.842+00:00"}}
3c1508ab-fbcc-4500-b319-10885570fe2f	{"id": "3c1508ab-fbcc-4500-b319-10885570fe2f", "code": "lsa", "name": "Landscape architect", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.850+00:00", "updatedDate": "2022-06-23T15:13:58.850+00:00"}}
901d01e5-66b1-48f0-99f9-b5e92e3d2d15	{"id": "901d01e5-66b1-48f0-99f9-b5e92e3d2d15", "code": "cmp", "name": "Composer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.854+00:00", "updatedDate": "2022-06-23T15:13:58.854+00:00"}}
af09f37e-12f5-46db-a532-ccd6a8877f2d	{"id": "af09f37e-12f5-46db-a532-ccd6a8877f2d", "code": "tld", "name": "Television director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.865+00:00", "updatedDate": "2022-06-23T15:13:58.865+00:00"}}
b7000ced-c847-4b43-8f29-c5325e6279a8	{"id": "b7000ced-c847-4b43-8f29-c5325e6279a8", "code": "cov", "name": "Cover designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.869+00:00", "updatedDate": "2022-06-23T15:13:58.869+00:00"}}
99f6b0b7-c22f-460d-afe0-ee0877bc66d1	{"id": "99f6b0b7-c22f-460d-afe0-ee0877bc66d1", "code": "lso", "name": "Licensor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.876+00:00", "updatedDate": "2022-06-23T15:13:58.876+00:00"}}
18ba15a9-0502-4fa2-ad41-daab9d5ab7bb	{"id": "18ba15a9-0502-4fa2-ad41-daab9d5ab7bb", "code": "itr", "name": "Instrumentalist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.638+00:00", "updatedDate": "2022-06-23T15:13:58.638+00:00"}}
26ad4833-5d49-4999-97fc-44bc86a9fae0	{"id": "26ad4833-5d49-4999-97fc-44bc86a9fae0", "code": "fpy", "name": "First party", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.645+00:00", "updatedDate": "2022-06-23T15:13:58.645+00:00"}}
cb8fdd3f-7193-4096-934c-3efea46b1138	{"id": "cb8fdd3f-7193-4096-934c-3efea46b1138", "code": "wal", "name": "Writer of added lyrics", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.656+00:00", "updatedDate": "2022-06-23T15:13:58.656+00:00"}}
468ac852-339e-43b7-8e94-7e2ce475cb00	{"id": "468ac852-339e-43b7-8e94-7e2ce475cb00", "code": "cas", "name": "Caster", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.667+00:00", "updatedDate": "2022-06-23T15:13:58.667+00:00"}}
366821b5-5319-4888-8867-0ffb2d7649d1	{"id": "366821b5-5319-4888-8867-0ffb2d7649d1", "code": "eng", "name": "Engineer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.674+00:00", "updatedDate": "2022-06-23T15:13:58.674+00:00"}}
e46bdfe3-5923-4585-bca4-d9d930d41148	{"id": "e46bdfe3-5923-4585-bca4-d9d930d41148", "code": "dfd", "name": "Defendant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.689+00:00", "updatedDate": "2022-06-23T15:13:58.689+00:00"}}
7d0a897c-4f83-493a-a0c5-5e040cdce75b	{"id": "7d0a897c-4f83-493a-a0c5-5e040cdce75b", "code": "apl", "name": "Appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.700+00:00", "updatedDate": "2022-06-23T15:13:58.700+00:00"}}
5f27fcc6-4134-4916-afb8-fcbcfb6793d4	{"id": "5f27fcc6-4134-4916-afb8-fcbcfb6793d4", "code": "bdd", "name": "Binding designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.711+00:00", "updatedDate": "2022-06-23T15:13:58.711+00:00"}}
3e86cb67-5407-4622-a540-71a978899404	{"id": "3e86cb67-5407-4622-a540-71a978899404", "code": "stg", "name": "Setting", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.717+00:00", "updatedDate": "2022-06-23T15:13:58.717+00:00"}}
e603ffa2-8999-4091-b10d-96248c283c04	{"id": "e603ffa2-8999-4091-b10d-96248c283c04", "code": "lbr", "name": "Laboratory", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.724+00:00", "updatedDate": "2022-06-23T15:13:58.724+00:00"}}
12101b05-afcb-4159-9ee4-c207378ef910	{"id": "12101b05-afcb-4159-9ee4-c207378ef910", "code": "drt", "name": "Director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.738+00:00", "updatedDate": "2022-06-23T15:13:58.738+00:00"}}
f5f9108a-9afc-4ea9-9b99-4f83dcf51204	{"id": "f5f9108a-9afc-4ea9-9b99-4f83dcf51204", "code": "fmd", "name": "Film director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.743+00:00", "updatedDate": "2022-06-23T15:13:58.743+00:00"}}
0eef1c70-bd77-429c-a790-48a8d82b4d8f	{"id": "0eef1c70-bd77-429c-a790-48a8d82b4d8f", "code": "trc", "name": "Transcriber", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.753+00:00", "updatedDate": "2022-06-23T15:13:58.753+00:00"}}
54f69767-5712-47aa-bdb7-39c31aa8295e	{"id": "54f69767-5712-47aa-bdb7-39c31aa8295e", "code": "evp", "name": "Event place", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.761+00:00", "updatedDate": "2022-06-23T15:13:58.761+00:00"}}
e04bea27-813b-4765-9ba1-e98e0fca7101	{"id": "e04bea27-813b-4765-9ba1-e98e0fca7101", "code": "dln", "name": "Delineator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.768+00:00", "updatedDate": "2022-06-23T15:13:58.768+00:00"}}
8fbe6e92-87c9-4eff-b736-88cd02571465	{"id": "8fbe6e92-87c9-4eff-b736-88cd02571465", "code": "dnr", "name": "Donor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.770+00:00", "updatedDate": "2022-06-23T15:13:58.770+00:00"}}
1f20d444-79f6-497a-ae0d-98a92e504c58	{"id": "1f20d444-79f6-497a-ae0d-98a92e504c58", "code": "aui", "name": "Author of introduction, etc.", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.827+00:00", "updatedDate": "2022-06-23T15:13:58.827+00:00"}}
0ad74d5d-03b9-49bb-b9df-d692945ca66e	{"id": "0ad74d5d-03b9-49bb-b9df-d692945ca66e", "code": "cot", "name": "Contestant-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.832+00:00", "updatedDate": "2022-06-23T15:13:58.832+00:00"}}
c96df2ce-7b00-498a-bf37-3011f3ef1229	{"id": "c96df2ce-7b00-498a-bf37-3011f3ef1229", "code": "rpc", "name": "Radio producer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.837+00:00", "updatedDate": "2022-06-23T15:13:58.837+00:00"}}
c04ff362-c80a-4543-88cf-fc6e49e7d201	{"id": "c04ff362-c80a-4543-88cf-fc6e49e7d201", "code": "csl", "name": "Consultant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.840+00:00", "updatedDate": "2022-06-23T15:13:58.840+00:00"}}
1b51068c-506a-4b85-a815-175c17932448	{"id": "1b51068c-506a-4b85-a815-175c17932448", "code": "pat", "name": "Patron", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.845+00:00", "updatedDate": "2022-06-23T15:13:58.845+00:00"}}
d30f5556-6d79-4980-9528-c48ef60f3b31	{"id": "d30f5556-6d79-4980-9528-c48ef60f3b31", "code": "plt", "name": "Platemaker", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.853+00:00", "updatedDate": "2022-06-23T15:13:58.853+00:00"}}
003e8b5e-426c-4d33-b940-233b1b89dfbd	{"id": "003e8b5e-426c-4d33-b940-233b1b89dfbd", "code": "pan", "name": "Panelist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.857+00:00", "updatedDate": "2022-06-23T15:13:58.857+00:00"}}
36b921fe-6c34-45c8-908b-5701f0763e1b	{"id": "36b921fe-6c34-45c8-908b-5701f0763e1b", "code": "cou", "name": "Court governed", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.868+00:00", "updatedDate": "2022-06-23T15:13:58.868+00:00"}}
b76cb226-50f9-4d34-a3d0-48b475f83c80	{"id": "b76cb226-50f9-4d34-a3d0-48b475f83c80", "code": "jug", "name": "Jurisdiction governed", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.874+00:00", "updatedDate": "2022-06-23T15:13:58.874+00:00"}}
3add6049-0b63-4fec-9892-e3867e7358e2	{"id": "3add6049-0b63-4fec-9892-e3867e7358e2", "code": "ill", "name": "Illustrator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.878+00:00", "updatedDate": "2022-06-23T15:13:58.878+00:00"}}
fec9ae68-6b55-4dd6-9637-3a694fb6a82b	{"id": "fec9ae68-6b55-4dd6-9637-3a694fb6a82b", "code": "uvp", "name": "University place", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.884+00:00", "updatedDate": "2022-06-23T15:13:58.884+00:00"}}
2d046e17-742b-4d99-8e25-836cc141fee9	{"id": "2d046e17-742b-4d99-8e25-836cc141fee9", "code": "pbd", "name": "Publishing director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.891+00:00", "updatedDate": "2022-06-23T15:13:58.891+00:00"}}
8ddb69bb-cd69-4898-a62d-b71649089e4a	{"id": "8ddb69bb-cd69-4898-a62d-b71649089e4a", "code": "cor", "name": "Collection registrar", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.880+00:00", "updatedDate": "2022-06-23T15:13:58.880+00:00"}}
515caf91-3dde-4769-b784-50c9e23400d5	{"id": "515caf91-3dde-4769-b784-50c9e23400d5", "code": "mrb", "name": "Marbler", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.887+00:00", "updatedDate": "2022-06-23T15:13:58.887+00:00"}}
2cb49b06-5aeb-4e84-8160-79d13c6357ed	{"id": "2cb49b06-5aeb-4e84-8160-79d13c6357ed", "code": "pth", "name": "Patent holder", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.896+00:00", "updatedDate": "2022-06-23T15:13:58.896+00:00"}}
bf1a8165-54bf-411c-a5ea-b6bbbb9c55df	{"id": "bf1a8165-54bf-411c-a5ea-b6bbbb9c55df", "code": "wac", "name": "Writer of added commentary", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.903+00:00", "updatedDate": "2022-06-23T15:13:58.903+00:00"}}
85962960-ef07-499d-bf49-63f137204f9a	{"id": "85962960-ef07-499d-bf49-63f137204f9a", "code": "rev", "name": "Reviewer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.911+00:00", "updatedDate": "2022-06-23T15:13:58.911+00:00"}}
ee04a129-f2e4-4fd7-8342-7a73a0700665	{"id": "ee04a129-f2e4-4fd7-8342-7a73a0700665", "code": "mdc", "name": "Metadata contact", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.916+00:00", "updatedDate": "2022-06-23T15:13:58.916+00:00"}}
369783f6-78c8-4cd7-97ab-5029444e0c85	{"id": "369783f6-78c8-4cd7-97ab-5029444e0c85", "code": "gis", "name": "Geographic information specialist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.921+00:00", "updatedDate": "2022-06-23T15:13:58.921+00:00"}}
9deb29d1-3e71-4951-9413-a80adac703d0	{"id": "9deb29d1-3e71-4951-9413-a80adac703d0", "code": "edt", "name": "Editor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.926+00:00", "updatedDate": "2022-06-23T15:13:58.926+00:00"}}
036b6349-27c8-4b68-8875-79cb8e0fd459	{"id": "036b6349-27c8-4b68-8875-79cb8e0fd459", "code": "fac", "name": "Facsimilist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.933+00:00", "updatedDate": "2022-06-23T15:13:58.933+00:00"}}
c7345998-fd17-406b-bce0-e08cb7b2671f	{"id": "c7345998-fd17-406b-bce0-e08cb7b2671f", "code": "cmt", "name": "Compositor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.947+00:00", "updatedDate": "2022-06-23T15:13:58.947+00:00"}}
b388c02a-19dc-4948-916d-3688007b9a2c	{"id": "b388c02a-19dc-4948-916d-3688007b9a2c", "code": "rcd", "name": "Recordist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.953+00:00", "updatedDate": "2022-06-23T15:13:58.953+00:00"}}
8af7e981-65f9-4407-80ae-1bacd11315d5	{"id": "8af7e981-65f9-4407-80ae-1bacd11315d5", "code": "mte", "name": "Metal-engraver", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.958+00:00", "updatedDate": "2022-06-23T15:13:58.958+00:00"}}
3322b734-ce38-4cd4-815d-8983352837cc	{"id": "3322b734-ce38-4cd4-815d-8983352837cc", "code": "trl", "name": "Translator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.964+00:00", "updatedDate": "2022-06-23T15:13:58.964+00:00"}}
2c9cd812-7b00-47e8-81e5-1711f3b6fe38	{"id": "2c9cd812-7b00-47e8-81e5-1711f3b6fe38", "code": "pup", "name": "Publication place", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.967+00:00", "updatedDate": "2022-06-23T15:13:58.967+00:00"}}
b1e95783-5308-46b2-9853-bd7015c1774b	{"id": "b1e95783-5308-46b2-9853-bd7015c1774b", "code": "edm", "name": "Editor of moving image work", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.973+00:00", "updatedDate": "2022-06-23T15:13:58.973+00:00"}}
9d81737c-ec6c-49d8-9771-50e1ab4d7ad7	{"id": "9d81737c-ec6c-49d8-9771-50e1ab4d7ad7", "code": "dtm", "name": "Data manager", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.994+00:00", "updatedDate": "2022-06-23T15:13:58.994+00:00"}}
3bd0b539-4440-4971-988c-5330daa14e3a	{"id": "3bd0b539-4440-4971-988c-5330daa14e3a", "code": "dnc", "name": "Dancer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.006+00:00", "updatedDate": "2022-06-23T15:13:59.006+00:00"}}
bd13d6d3-e604-4b80-9c5f-4d68115ba616	{"id": "bd13d6d3-e604-4b80-9c5f-4d68115ba616", "code": "crt", "name": "Court reporter", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.013+00:00", "updatedDate": "2022-06-23T15:13:59.013+00:00"}}
e4f2fd1c-ee79-4cf7-bc1a-fbaac616f804	{"id": "e4f2fd1c-ee79-4cf7-bc1a-fbaac616f804", "code": "len", "name": "Lender", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.026+00:00", "updatedDate": "2022-06-23T15:13:59.026+00:00"}}
097adac4-6576-4152-ace8-08fc59cb0218	{"id": "097adac4-6576-4152-ace8-08fc59cb0218", "code": "pdr", "name": "Project director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.035+00:00", "updatedDate": "2022-06-23T15:13:59.035+00:00"}}
acad26a9-e288-4385-bea1-0560bb884b7a	{"id": "acad26a9-e288-4385-bea1-0560bb884b7a", "code": "bjd", "name": "Bookjacket designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.041+00:00", "updatedDate": "2022-06-23T15:13:59.041+00:00"}}
27aeee86-4099-466d-ba10-6d876e6f293b	{"id": "27aeee86-4099-466d-ba10-6d876e6f293b", "code": "com", "name": "Compiler", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.048+00:00", "updatedDate": "2022-06-23T15:13:59.048+00:00"}}
e7e8fc17-7c97-4a37-8c12-f832ddca7a71	{"id": "e7e8fc17-7c97-4a37-8c12-f832ddca7a71", "code": "ive", "name": "Interviewee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.055+00:00", "updatedDate": "2022-06-23T15:13:59.055+00:00"}}
8210b9d7-8fe7-41b7-8c5f-6e0485b50725	{"id": "8210b9d7-8fe7-41b7-8c5f-6e0485b50725", "code": "prs", "name": "Production designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.063+00:00", "updatedDate": "2022-06-23T15:13:59.063+00:00"}}
e1edbaae-5365-4fcb-bb6a-7aae38bbed9c	{"id": "e1edbaae-5365-4fcb-bb6a-7aae38bbed9c", "code": "msd", "name": "Musical director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.070+00:00", "updatedDate": "2022-06-23T15:13:59.070+00:00"}}
ec0959b3-becc-4abd-87b0-3e02cf2665cc	{"id": "ec0959b3-becc-4abd-87b0-3e02cf2665cc", "code": "cli", "name": "Client", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.076+00:00", "updatedDate": "2022-06-23T15:13:59.076+00:00"}}
4b41e752-3646-4097-ae80-21fd02e913f7	{"id": "4b41e752-3646-4097-ae80-21fd02e913f7", "code": "aud", "name": "Author of dialog", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.082+00:00", "updatedDate": "2022-06-23T15:13:59.082+00:00"}}
86b9292d-4dce-401d-861e-2df2cfaacb83	{"id": "86b9292d-4dce-401d-861e-2df2cfaacb83", "code": "rpt", "name": "Reporter", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.089+00:00", "updatedDate": "2022-06-23T15:13:59.089+00:00"}}
e0dc043c-0a4d-499b-a8a8-4cc9b0869cf3	{"id": "e0dc043c-0a4d-499b-a8a8-4cc9b0869cf3", "code": "cmm", "name": "Commentator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.888+00:00", "updatedDate": "2022-06-23T15:13:58.888+00:00"}}
d791c3b9-993a-4203-ac81-3fb3f14793ae	{"id": "d791c3b9-993a-4203-ac81-3fb3f14793ae", "code": "led", "name": "Lead", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.892+00:00", "updatedDate": "2022-06-23T15:13:58.892+00:00"}}
8f9d96f5-32ad-43d7-8122-18063a617fc8	{"id": "8f9d96f5-32ad-43d7-8122-18063a617fc8", "code": "cpl", "name": "Complainant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.897+00:00", "updatedDate": "2022-06-23T15:13:58.897+00:00"}}
a79f874f-319e-4bc8-a2e1-f8b15fa186fe	{"id": "a79f874f-319e-4bc8-a2e1-f8b15fa186fe", "code": "cnd", "name": "Conductor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.903+00:00", "updatedDate": "2022-06-23T15:13:58.903+00:00"}}
b13f6a89-d2e3-4264-8418-07ad4de6a626	{"id": "b13f6a89-d2e3-4264-8418-07ad4de6a626", "code": "prd", "name": "Production personnel", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.913+00:00", "updatedDate": "2022-06-23T15:13:58.913+00:00"}}
396f4b4d-5b0a-4fb4-941b-993ebf63db2e	{"id": "396f4b4d-5b0a-4fb4-941b-993ebf63db2e", "code": "anl", "name": "Analyst", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.917+00:00", "updatedDate": "2022-06-23T15:13:58.917+00:00"}}
81c01802-f61b-4548-954a-22aab027f6e5	{"id": "81c01802-f61b-4548-954a-22aab027f6e5", "code": "clr", "name": "Colorist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.922+00:00", "updatedDate": "2022-06-23T15:13:58.922+00:00"}}
756fcbfc-ef95-4bd0-99cc-1cc364c7b0cd	{"id": "756fcbfc-ef95-4bd0-99cc-1cc364c7b0cd", "code": "cns", "name": "Censor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.930+00:00", "updatedDate": "2022-06-23T15:13:58.930+00:00"}}
3ed655b0-505b-43fe-a4c6-397789449a5b	{"id": "3ed655b0-505b-43fe-a4c6-397789449a5b", "code": "tlp", "name": "Television producer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.937+00:00", "updatedDate": "2022-06-23T15:13:58.937+00:00"}}
22f8ea20-b4f0-4498-8125-7962f0037c2d	{"id": "22f8ea20-b4f0-4498-8125-7962f0037c2d", "code": "flm", "name": "Film editor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.949+00:00", "updatedDate": "2022-06-23T15:13:58.949+00:00"}}
ac0baeb5-71e2-435f-aaf1-14b64e2ba700	{"id": "ac0baeb5-71e2-435f-aaf1-14b64e2ba700", "code": "spk", "name": "Speaker", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.957+00:00", "updatedDate": "2022-06-23T15:13:58.957+00:00"}}
201a378e-23dd-4aab-bfe0-e5bc3c855f9c	{"id": "201a378e-23dd-4aab-bfe0-e5bc3c855f9c", "code": "elt", "name": "Electrotyper", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.964+00:00", "updatedDate": "2022-06-23T15:13:58.964+00:00"}}
8999f7cb-6d9a-4be7-aeed-4cc6aae35a8c	{"id": "8999f7cb-6d9a-4be7-aeed-4cc6aae35a8c", "code": "cll", "name": "Calligrapher", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.972+00:00", "updatedDate": "2022-06-23T15:13:58.972+00:00"}}
3cbd0832-328e-48f5-96c4-6f7bcf341461	{"id": "3cbd0832-328e-48f5-96c4-6f7bcf341461", "code": "pmn", "name": "Production manager", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.993+00:00", "updatedDate": "2022-06-23T15:13:58.993+00:00"}}
d2df2901-fac7-45e1-a9ad-7a67b70ea65b	{"id": "d2df2901-fac7-45e1-a9ad-7a67b70ea65b", "code": "mon", "name": "Monitor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.009+00:00", "updatedDate": "2022-06-23T15:13:59.009+00:00"}}
563bcaa7-7fe1-4206-8fc9-5ef8c7fbf998	{"id": "563bcaa7-7fe1-4206-8fc9-5ef8c7fbf998", "code": "osp", "name": "Onscreen presenter", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.018+00:00", "updatedDate": "2022-06-23T15:13:59.018+00:00"}}
d04782ec-b969-4eac-9428-0eb52d97c644	{"id": "d04782ec-b969-4eac-9428-0eb52d97c644", "code": "pre", "name": "Presenter", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.031+00:00", "updatedDate": "2022-06-23T15:13:59.031+00:00"}}
97082157-5900-4c4c-a6d8-2e6c13f22ef1	{"id": "97082157-5900-4c4c-a6d8-2e6c13f22ef1", "code": "isb", "name": "Issuing body", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.043+00:00", "updatedDate": "2022-06-23T15:13:59.043+00:00"}}
2fba7b2e-26bc-4ac5-93cb-73e31e554377	{"id": "2fba7b2e-26bc-4ac5-93cb-73e31e554377", "code": "spy", "name": "Second party", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.053+00:00", "updatedDate": "2022-06-23T15:13:59.053+00:00"}}
316cd382-a4fe-4939-b06e-e7199bfdbc7a	{"id": "316cd382-a4fe-4939-b06e-e7199bfdbc7a", "code": "cwt", "name": "Commentator for written text", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.060+00:00", "updatedDate": "2022-06-23T15:13:59.060+00:00"}}
35a3feaf-1c13-4221-8cfa-d6879faf714c	{"id": "35a3feaf-1c13-4221-8cfa-d6879faf714c", "code": "adp", "name": "Adapter", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.074+00:00", "updatedDate": "2022-06-23T15:13:59.074+00:00"}}
86890f8f-2273-44e2-aa86-927c7f649b32	{"id": "86890f8f-2273-44e2-aa86-927c7f649b32", "code": "cpt", "name": "Complainant-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.085+00:00", "updatedDate": "2022-06-23T15:13:59.085+00:00"}}
9fc0bffb-6dd9-4218-9a44-81be4a5059d4	{"id": "9fc0bffb-6dd9-4218-9a44-81be4a5059d4", "code": "cts", "name": "Contestee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.095+00:00", "updatedDate": "2022-06-23T15:13:59.095+00:00"}}
e2a1a9dc-4aec-4bb5-ae43-99bb0383516a	{"id": "e2a1a9dc-4aec-4bb5-ae43-99bb0383516a", "code": "adi", "name": "Art director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.103+00:00", "updatedDate": "2022-06-23T15:13:59.103+00:00"}}
6901fbf1-c038-42eb-a03e-cd65bf91f660	{"id": "6901fbf1-c038-42eb-a03e-cd65bf91f660", "code": "dgg", "name": "Degree granting institution", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.112+00:00", "updatedDate": "2022-06-23T15:13:59.112+00:00"}}
0efdaf72-6126-430a-8256-69c42ff6866f	{"id": "0efdaf72-6126-430a-8256-69c42ff6866f", "code": "tcd", "name": "Technical director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.122+00:00", "updatedDate": "2022-06-23T15:13:59.122+00:00"}}
3b4709f1-5286-4c42-9423-4620fff78141	{"id": "3b4709f1-5286-4c42-9423-4620fff78141", "code": "prv", "name": "Provider", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.131+00:00", "updatedDate": "2022-06-23T15:13:59.131+00:00"}}
300171aa-95e1-45b0-86c6-2855fcaf9ef4	{"id": "300171aa-95e1-45b0-86c6-2855fcaf9ef4", "code": "opn", "name": "Opponent", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.146+00:00", "updatedDate": "2022-06-23T15:13:59.146+00:00"}}
6e09d47d-95e2-4d8a-831b-f777b8ef6d81	{"id": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "code": "aut", "name": "Author", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.899+00:00", "updatedDate": "2022-06-23T15:13:58.899+00:00"}}
539872f1-f4a1-4e83-9d87-da235f64c520	{"id": "539872f1-f4a1-4e83-9d87-da235f64c520", "code": "org", "name": "Originator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.904+00:00", "updatedDate": "2022-06-23T15:13:58.904+00:00"}}
7d60c4bf-5ddc-483a-b179-af6f1a76efbe	{"id": "7d60c4bf-5ddc-483a-b179-af6f1a76efbe", "code": "lie", "name": "Libelant-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.908+00:00", "updatedDate": "2022-06-23T15:13:58.908+00:00"}}
32021771-311e-497b-9bf2-672492f322c7	{"id": "32021771-311e-497b-9bf2-672492f322c7", "code": "wdc", "name": "Woodcutter", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.914+00:00", "updatedDate": "2022-06-23T15:13:58.914+00:00"}}
e038262b-25f8-471b-93ea-2afe287b00a3	{"id": "e038262b-25f8-471b-93ea-2afe287b00a3", "code": "ilu", "name": "Illuminator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.923+00:00", "updatedDate": "2022-06-23T15:13:58.923+00:00"}}
d5e6972c-9e2f-4788-8dd6-10e859e20945	{"id": "d5e6972c-9e2f-4788-8dd6-10e859e20945", "code": "dbp", "name": "Distribution place", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.934+00:00", "updatedDate": "2022-06-23T15:13:58.934+00:00"}}
c86fc16d-61d8-4471-8089-76550daa04f0	{"id": "c86fc16d-61d8-4471-8089-76550daa04f0", "code": "dft", "name": "Defendant-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.949+00:00", "updatedDate": "2022-06-23T15:13:58.949+00:00"}}
0683aecf-42a8-432d-adb2-a8abaf2f15d5	{"id": "0683aecf-42a8-432d-adb2-a8abaf2f15d5", "code": "pma", "name": "Permitting agency", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.958+00:00", "updatedDate": "2022-06-23T15:13:58.958+00:00"}}
6a983219-b6cd-4dd7-bfa4-bcb0b43590d4	{"id": "6a983219-b6cd-4dd7-bfa4-bcb0b43590d4", "code": "wat", "name": "Writer of added text", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.961+00:00", "updatedDate": "2022-06-23T15:13:58.961+00:00"}}
df7daf2f-7ab4-4c7b-a24d-d46695fa9072	{"id": "df7daf2f-7ab4-4c7b-a24d-d46695fa9072", "code": "orm", "name": "Organizer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.966+00:00", "updatedDate": "2022-06-23T15:13:58.966+00:00"}}
b318e49c-f2ad-498c-8106-57b5544f9bb0	{"id": "b318e49c-f2ad-498c-8106-57b5544f9bb0", "code": "prn", "name": "Production company", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.973+00:00", "updatedDate": "2022-06-23T15:13:58.973+00:00"}}
b38c4e20-9aa0-43f4-a1a0-f547e54873f7	{"id": "b38c4e20-9aa0-43f4-a1a0-f547e54873f7", "code": "red", "name": "Redaktor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.979+00:00", "updatedDate": "2022-06-23T15:13:58.979+00:00"}}
5b2de939-879c-45b4-817d-c29fd16b78a0	{"id": "5b2de939-879c-45b4-817d-c29fd16b78a0", "code": "elg", "name": "Electrician", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.004+00:00", "updatedDate": "2022-06-23T15:13:59.004+00:00"}}
f3aa0070-71bd-4c39-9a9b-ec2fd03ac26d	{"id": "f3aa0070-71bd-4c39-9a9b-ec2fd03ac26d", "code": "cte", "name": "Contestee-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.010+00:00", "updatedDate": "2022-06-23T15:13:59.010+00:00"}}
2576c328-61f1-4684-83cf-4376a66f7731	{"id": "2576c328-61f1-4684-83cf-4376a66f7731", "code": "fld", "name": "Field director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.028+00:00", "updatedDate": "2022-06-23T15:13:59.028+00:00"}}
a21a56ea-5136-439a-a513-0bffa53402de	{"id": "a21a56ea-5136-439a-a513-0bffa53402de", "code": "srv", "name": "Surveyor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.043+00:00", "updatedDate": "2022-06-23T15:13:59.043+00:00"}}
b998a229-68e7-4a3d-8cfd-b73c10844e96	{"id": "b998a229-68e7-4a3d-8cfd-b73c10844e96", "code": "anm", "name": "Animator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.056+00:00", "updatedDate": "2022-06-23T15:13:59.056+00:00"}}
255be0dd-54d0-4161-9c6c-4d1f58310303	{"id": "255be0dd-54d0-4161-9c6c-4d1f58310303", "code": "ard", "name": "Artistic director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.067+00:00", "updatedDate": "2022-06-23T15:13:59.067+00:00"}}
13361ce8-7664-46c0-860d-ffbcc01414e0	{"id": "13361ce8-7664-46c0-860d-ffbcc01414e0", "code": "rps", "name": "Repository", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.086+00:00", "updatedDate": "2022-06-23T15:13:59.086+00:00"}}
33aa4117-95d1-4eb5-986b-dfba809871f6	{"id": "33aa4117-95d1-4eb5-986b-dfba809871f6", "code": "drm", "name": "Draftsman", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.097+00:00", "updatedDate": "2022-06-23T15:13:59.097+00:00"}}
5c1e0a9e-1fdc-47a5-8d06-c12af63cbc5a	{"id": "5c1e0a9e-1fdc-47a5-8d06-c12af63cbc5a", "code": "hnr", "name": "Honoree", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.104+00:00", "updatedDate": "2022-06-23T15:13:59.104+00:00"}}
40fe62fb-4319-4313-ac88-ac4912b1e1fa	{"id": "40fe62fb-4319-4313-ac88-ac4912b1e1fa", "code": "aus", "name": "Screenwriter", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.115+00:00", "updatedDate": "2022-06-23T15:13:59.115+00:00"}}
06b2cbd8-66bf-4956-9d90-97c9776365a4	{"id": "06b2cbd8-66bf-4956-9d90-97c9776365a4", "code": "ann", "name": "Annotator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.124+00:00", "updatedDate": "2022-06-23T15:13:59.124+00:00"}}
f74dfba3-ea20-471b-8c4f-5d9b7895d3b5	{"id": "f74dfba3-ea20-471b-8c4f-5d9b7895d3b5", "code": "ldr", "name": "Laboratory director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.134+00:00", "updatedDate": "2022-06-23T15:13:59.134+00:00"}}
ca3b9559-f178-41e8-aa88-6b2c367025f9	{"id": "ca3b9559-f178-41e8-aa88-6b2c367025f9", "code": "app", "name": "Applicant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.147+00:00", "updatedDate": "2022-06-23T15:13:59.147+00:00"}}
a2c9e8b5-edb4-49dc-98ba-27f0b8b5cebf	{"id": "a2c9e8b5-edb4-49dc-98ba-27f0b8b5cebf", "code": "tyd", "name": "Type designer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.156+00:00", "updatedDate": "2022-06-23T15:13:59.156+00:00"}}
002c0eef-eb77-4c0b-a38e-117a09773d59	{"id": "002c0eef-eb77-4c0b-a38e-117a09773d59", "code": "mtk", "name": "Minute taker", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.164+00:00", "updatedDate": "2022-06-23T15:13:59.164+00:00"}}
7b21bffb-91e1-45bf-980a-40dd89cc26e4	{"id": "7b21bffb-91e1-45bf-980a-40dd89cc26e4", "code": "dst", "name": "Distributor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.169+00:00", "updatedDate": "2022-06-23T15:13:59.169+00:00"}}
1aae8ca3-4ddd-4549-a769-116b75f3c773	{"id": "1aae8ca3-4ddd-4549-a769-116b75f3c773", "code": "pht", "name": "Photographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.179+00:00", "updatedDate": "2022-06-23T15:13:59.179+00:00"}}
6847c9ab-e2f8-4c9e-8dc6-1a97c6836c1c	{"id": "6847c9ab-e2f8-4c9e-8dc6-1a97c6836c1c", "code": "sng", "name": "Singer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.924+00:00", "updatedDate": "2022-06-23T15:13:58.924+00:00"}}
b47d8841-112e-43be-b992-eccb5747eb50	{"id": "b47d8841-112e-43be-b992-eccb5747eb50", "code": "prg", "name": "Programmer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.935+00:00", "updatedDate": "2022-06-23T15:13:58.935+00:00"}}
c0c46b4f-fd18-4d8a-96ac-aff91662206c	{"id": "c0c46b4f-fd18-4d8a-96ac-aff91662206c", "code": "sgd", "name": "Stage director", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.941+00:00", "updatedDate": "2022-06-23T15:13:58.941+00:00"}}
630142eb-6b68-4cf7-8296-bdaba03b5760	{"id": "630142eb-6b68-4cf7-8296-bdaba03b5760", "code": "pta", "name": "Patent applicant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.951+00:00", "updatedDate": "2022-06-23T15:13:58.951+00:00"}}
3179eb17-275e-44f8-8cad-3a9514799bd0	{"id": "3179eb17-275e-44f8-8cad-3a9514799bd0", "code": "sll", "name": "Seller", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.955+00:00", "updatedDate": "2022-06-23T15:13:58.955+00:00"}}
ac64c865-4f29-4d51-8b43-7816a5217f04	{"id": "ac64c865-4f29-4d51-8b43-7816a5217f04", "code": "arr", "name": "Arranger", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.965+00:00", "updatedDate": "2022-06-23T15:13:58.965+00:00"}}
61c9f06f-620a-4423-8c78-c698b9bb555f	{"id": "61c9f06f-620a-4423-8c78-c698b9bb555f", "code": "lel", "name": "Libelee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.969+00:00", "updatedDate": "2022-06-23T15:13:58.969+00:00"}}
abfa3014-7349-444b-aace-9d28efa5ede4	{"id": "abfa3014-7349-444b-aace-9d28efa5ede4", "code": "hst", "name": "Host", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:58.994+00:00", "updatedDate": "2022-06-23T15:13:58.994+00:00"}}
f26858bc-4468-47be-8e30-d5db4c0b1e88	{"id": "f26858bc-4468-47be-8e30-d5db4c0b1e88", "code": "dis", "name": "Dissertant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.012+00:00", "updatedDate": "2022-06-23T15:13:59.012+00:00"}}
3555bf7f-a6cc-4890-b050-9c428eabf579	{"id": "3555bf7f-a6cc-4890-b050-9c428eabf579", "code": "fnd", "name": "Funder", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.028+00:00", "updatedDate": "2022-06-23T15:13:59.028+00:00"}}
cf04404a-d628-432b-b190-6694c5a3dc4b	{"id": "cf04404a-d628-432b-b190-6694c5a3dc4b", "code": "rsr", "name": "Restorationist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.046+00:00", "updatedDate": "2022-06-23T15:13:59.046+00:00"}}
a5c024f1-3c81-492c-ab5e-73d2bc5dcad7	{"id": "a5c024f1-3c81-492c-ab5e-73d2bc5dcad7", "code": "let", "name": "Libelee-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.077+00:00", "updatedDate": "2022-06-23T15:13:59.077+00:00"}}
3ebe73f4-0895-4979-a5e3-2b3e9c63acd6	{"id": "3ebe73f4-0895-4979-a5e3-2b3e9c63acd6", "code": "dfe", "name": "Defendant-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.090+00:00", "updatedDate": "2022-06-23T15:13:59.090+00:00"}}
de1ea2dc-8d9d-4dfa-b86e-8ce9d8b0c2f2	{"id": "de1ea2dc-8d9d-4dfa-b86e-8ce9d8b0c2f2", "code": "wde", "name": "Wood engraver", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.106+00:00", "updatedDate": "2022-06-23T15:13:59.106+00:00"}}
52c08141-307f-4997-9799-db97076a2eb3	{"id": "52c08141-307f-4997-9799-db97076a2eb3", "code": "lit", "name": "Libelant-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.119+00:00", "updatedDate": "2022-06-23T15:13:59.119+00:00"}}
28f7eb9e-f923-4a77-9755-7571381b2a47	{"id": "28f7eb9e-f923-4a77-9755-7571381b2a47", "code": "ctr", "name": "Contractor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.125+00:00", "updatedDate": "2022-06-23T15:13:59.125+00:00"}}
60d3f16f-958a-45c2-bb39-69cc9eb3835e	{"id": "60d3f16f-958a-45c2-bb39-69cc9eb3835e", "code": "fds", "name": "Film distributor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.136+00:00", "updatedDate": "2022-06-23T15:13:59.136+00:00"}}
12b7418a-0c90-4337-90b7-16d2d3157b68	{"id": "12b7418a-0c90-4337-90b7-16d2d3157b68", "code": "sec", "name": "Secretary", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.140+00:00", "updatedDate": "2022-06-23T15:13:59.140+00:00"}}
0d2580f5-fe16-4d64-a5eb-f0247cccb129	{"id": "0d2580f5-fe16-4d64-a5eb-f0247cccb129", "code": "dto", "name": "Dedicator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.149+00:00", "updatedDate": "2022-06-23T15:13:59.149+00:00"}}
5ee1e598-72b8-44d5-8edd-173e7bc4cf8c	{"id": "5ee1e598-72b8-44d5-8edd-173e7bc4cf8c", "code": "prc", "name": "Process contact", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.167+00:00", "updatedDate": "2022-06-23T15:13:59.167+00:00"}}
fec4d84b-0421-4d15-b53f-d5104f39b3ca	{"id": "fec4d84b-0421-4d15-b53f-d5104f39b3ca", "code": "res", "name": "Researcher", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.181+00:00", "updatedDate": "2022-06-23T15:13:59.181+00:00"}}
54fd209c-d552-43eb-850f-d31f557170b9	{"id": "54fd209c-d552-43eb-850f-d31f557170b9", "code": "rtm", "name": "Research team member", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.198+00:00", "updatedDate": "2022-06-23T15:13:59.198+00:00"}}
4f7c335d-a9d9-4f38-87ef-9a5846b63e7f	{"id": "4f7c335d-a9d9-4f38-87ef-9a5846b63e7f", "code": "ppt", "name": "Puppeteer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.102+00:00", "updatedDate": "2022-06-23T15:13:59.102+00:00"}}
2230246a-1fdb-4f06-a08a-004fd4b929bf	{"id": "2230246a-1fdb-4f06-a08a-004fd4b929bf", "code": "ptf", "name": "Plaintiff", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.108+00:00", "updatedDate": "2022-06-23T15:13:59.108+00:00"}}
ab7a95da-590c-4955-b03b-9d8fbc6c1fe6	{"id": "ab7a95da-590c-4955-b03b-9d8fbc6c1fe6", "code": "rce", "name": "Recording engineer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.118+00:00", "updatedDate": "2022-06-23T15:13:59.118+00:00"}}
2b45c004-805d-4e7f-864d-8664a23488dc	{"id": "2b45c004-805d-4e7f-864d-8664a23488dc", "code": "ltg", "name": "Lithographer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.128+00:00", "updatedDate": "2022-06-23T15:13:59.128+00:00"}}
223da16e-5a03-4f5c-b8c3-0eb79f662bcb	{"id": "223da16e-5a03-4f5c-b8c3-0eb79f662bcb", "code": "scl", "name": "Sculptor", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.133+00:00", "updatedDate": "2022-06-23T15:13:59.133+00:00"}}
02c1c664-1d71-4f7b-a656-1abf1209848f	{"id": "02c1c664-1d71-4f7b-a656-1abf1209848f", "code": "prt", "name": "Printer", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.137+00:00", "updatedDate": "2022-06-23T15:13:59.137+00:00"}}
d32885eb-b82c-4391-abb2-4582c8ee02b3	{"id": "d32885eb-b82c-4391-abb2-4582c8ee02b3", "code": "dpc", "name": "Depicted", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.145+00:00", "updatedDate": "2022-06-23T15:13:59.145+00:00"}}
2c345cb7-0420-4a7d-93ce-b51fb636cce6	{"id": "2c345cb7-0420-4a7d-93ce-b51fb636cce6", "code": "nrt", "name": "Narrator", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.163+00:00", "updatedDate": "2022-06-23T15:13:59.163+00:00"}}
f0061c4b-df42-432f-9d1a-3873bb27c8e6	{"id": "f0061c4b-df42-432f-9d1a-3873bb27c8e6", "code": "ape", "name": "Appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.176+00:00", "updatedDate": "2022-06-23T15:13:59.176+00:00"}}
d836488a-8d0e-42ad-9091-b63fe885fe03	{"id": "d836488a-8d0e-42ad-9091-b63fe885fe03", "code": "att", "name": "Attributed name", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.182+00:00", "updatedDate": "2022-06-23T15:13:59.182+00:00"}}
53f075e1-53c0-423f-95ae-676df3d8c7a2	{"id": "53f075e1-53c0-423f-95ae-676df3d8c7a2", "code": "win", "name": "Writer of introduction", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.193+00:00", "updatedDate": "2022-06-23T15:13:59.193+00:00"}}
f6bd4f15-4715-4b0e-9258-61dac047f106	{"id": "f6bd4f15-4715-4b0e-9258-61dac047f106", "code": "ins", "name": "Inscriber", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.199+00:00", "updatedDate": "2022-06-23T15:13:59.199+00:00"}}
764c208a-493f-43af-8db7-3dd48efca45c	{"id": "764c208a-493f-43af-8db7-3dd48efca45c", "code": "exp", "name": "Expert", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.151+00:00", "updatedDate": "2022-06-23T15:13:59.151+00:00"}}
ad9b7785-53a2-4bf4-8a01-572858e82941	{"id": "ad9b7785-53a2-4bf4-8a01-572858e82941", "code": "asg", "name": "Assignee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.164+00:00", "updatedDate": "2022-06-23T15:13:59.164+00:00"}}
6358626f-aa02-4c40-8e73-fb202fa5fb4d	{"id": "6358626f-aa02-4c40-8e73-fb202fa5fb4d", "code": "cpe", "name": "Complainant-appellee", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.178+00:00", "updatedDate": "2022-06-23T15:13:59.178+00:00"}}
6d5779a3-e692-4a24-a5ee-d1ce8a6eae47	{"id": "6d5779a3-e692-4a24-a5ee-d1ce8a6eae47", "code": "lbt", "name": "Librettist", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.187+00:00", "updatedDate": "2022-06-23T15:13:59.187+00:00"}}
68dcc037-901e-46a9-9b4e-028548cd750f	{"id": "68dcc037-901e-46a9-9b4e-028548cd750f", "code": "ptt", "name": "Plaintiff-appellant", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.196+00:00", "updatedDate": "2022-06-23T15:13:59.196+00:00"}}
21dda3dc-cebd-4018-8db2-4f6d50ce3d02	{"id": "21dda3dc-cebd-4018-8db2-4f6d50ce3d02", "code": "own", "name": "Owner", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.204+00:00", "updatedDate": "2022-06-23T15:13:59.204+00:00"}}
a60314d4-c3c6-4e29-92fa-86cc6ace4d56	{"id": "a60314d4-c3c6-4e29-92fa-86cc6ace4d56", "code": "pbl", "name": "Publisher", "source": "marcrelator", "metadata": {"createdDate": "2022-06-23T15:13:59.197+00:00", "updatedDate": "2022-06-23T15:13:59.197+00:00"}}
\.


--
-- Data for Name: electronic_access_relationship; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.electronic_access_relationship (id, jsonb, creation_date, created_by) FROM stdin;
f50c90c9-bae0-4add-9cd0-db9092dbc9dd	{"id": "f50c90c9-bae0-4add-9cd0-db9092dbc9dd", "name": "No information provided", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.921Z", "updatedDate": "2022-06-23T15:13:59.921Z"}}	2022-06-23 15:13:59.921	\N
f5d0068e-6272-458e-8a81-b85e7b9a14aa	{"id": "f5d0068e-6272-458e-8a81-b85e7b9a14aa", "name": "Resource", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.921Z", "updatedDate": "2022-06-23T15:13:59.921Z"}}	2022-06-23 15:13:59.921	\N
5bfe1b7b-f151-4501-8cfa-23b321d5cd1e	{"id": "5bfe1b7b-f151-4501-8cfa-23b321d5cd1e", "name": "Related resource", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.923Z", "updatedDate": "2022-06-23T15:13:59.923Z"}}	2022-06-23 15:13:59.923	\N
3b430592-2e09-4b48-9a0c-0636d66b9fb3	{"id": "3b430592-2e09-4b48-9a0c-0636d66b9fb3", "name": "Version of resource", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.924Z", "updatedDate": "2022-06-23T15:13:59.924Z"}}	2022-06-23 15:13:59.924	\N
ef03d582-219c-4221-8635-bc92f1107021	{"id": "ef03d582-219c-4221-8635-bc92f1107021", "name": "No display constant generated", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.926Z", "updatedDate": "2022-06-23T15:13:59.926Z"}}	2022-06-23 15:13:59.926	\N
\.


--
-- Data for Name: holdings_note_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.holdings_note_type (id, jsonb, creation_date, created_by) FROM stdin;
88914775-f677-4759-b57b-1a33b90b24e0	{"id": "88914775-f677-4759-b57b-1a33b90b24e0", "name": "Electronic bookplate", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.353Z", "updatedDate": "2022-06-23T15:14:00.353Z"}}	2022-06-23 15:14:00.353	\N
e19eabab-a85c-4aef-a7b2-33bd9acef24e	{"id": "e19eabab-a85c-4aef-a7b2-33bd9acef24e", "name": "Binding", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.355Z", "updatedDate": "2022-06-23T15:14:00.355Z"}}	2022-06-23 15:14:00.355	\N
6a41b714-8574-4084-8d64-a9373c3fbb59	{"id": "6a41b714-8574-4084-8d64-a9373c3fbb59", "name": "Reproduction", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.360Z", "updatedDate": "2022-06-23T15:14:00.360Z"}}	2022-06-23 15:14:00.36	\N
c4407cc7-d79f-4609-95bd-1cefb2e2b5c5	{"id": "c4407cc7-d79f-4609-95bd-1cefb2e2b5c5", "name": "Copy note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.365Z", "updatedDate": "2022-06-23T15:14:00.365Z"}}	2022-06-23 15:14:00.365	\N
db9b4787-95f0-4e78-becf-26748ce6bdeb	{"id": "db9b4787-95f0-4e78-becf-26748ce6bdeb", "name": "Provenance", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.364Z", "updatedDate": "2022-06-23T15:14:00.364Z"}}	2022-06-23 15:14:00.364	\N
b160f13a-ddba-4053-b9c4-60ec5ea45d56	{"id": "b160f13a-ddba-4053-b9c4-60ec5ea45d56", "name": "Note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.369Z", "updatedDate": "2022-06-23T15:14:00.369Z"}}	2022-06-23 15:14:00.369	\N
d6510242-5ec3-42ed-b593-3585d2e48fd6	{"id": "d6510242-5ec3-42ed-b593-3585d2e48fd6", "name": "Action note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.371Z", "updatedDate": "2022-06-23T15:14:00.371Z"}}	2022-06-23 15:14:00.371	\N
\.


--
-- Data for Name: holdings_record; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.holdings_record (id, jsonb, creation_date, created_by, instanceid, permanentlocationid, temporarylocationid, effectivelocationid, holdingstypeid, callnumbertypeid, illpolicyid, sourceid) FROM stdin;
55f48dc6-efa7-4cfe-bc7c-4786efe493e3	{"id": "55f48dc6-efa7-4cfe-bc7c-4786efe493e3", "hrid": "hold000000000012", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:00.963Z", "updatedDate": "2022-06-23T15:14:00.963Z"}, "formerIds": [], "instanceId": "bbd4a5e1-c9f3-44b9-bfdf-d184e04f0ba0", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "f34d27c6-a8eb-461b-acd6-5dea81771e70", "permanentLocationId": "f34d27c6-a8eb-461b-acd6-5dea81771e70", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:00.963	\N	bbd4a5e1-c9f3-44b9-bfdf-d184e04f0ba0	f34d27c6-a8eb-461b-acd6-5dea81771e70	\N	f34d27c6-a8eb-461b-acd6-5dea81771e70	\N	\N	\N	\N
0c45bb50-7c9b-48b0-86eb-178a494e25fe	{"id": "0c45bb50-7c9b-48b0-86eb-178a494e25fe", "hrid": "hold000000000002", "notes": [{"note": " Subscription cancelled per Evans Current Periodicals Selector Review. acq", "staffOnly": true, "holdingsNoteTypeId": "b160f13a-ddba-4053-b9c4-60ec5ea45d56"}, {"note": "Asked Ebsco to check with publisher and ask what years were paid since we are missing (2001:Oct.-Dec.), (All of 2002), & (2003:Jan.-Feb.). 20030305. evaldez", "staffOnly": false, "holdingsNoteTypeId": "b160f13a-ddba-4053-b9c4-60ec5ea45d56"}, {"note": "Backorder:v.87(2001:Oct.-Dec)-v.88(2002). eluza", "staffOnly": false, "holdingsNoteTypeId": "b160f13a-ddba-4053-b9c4-60ec5ea45d56"}, {"note": "WITH 2010 TREAT ISSUE S AS DISCARDS. dgill", "staffOnly": false, "holdingsNoteTypeId": "b160f13a-ddba-4053-b9c4-60ec5ea45d56"}], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:00.966Z", "updatedDate": "2022-06-23T15:14:00.966Z"}, "formerIds": ["ABW4508", "442882"], "callNumber": "K1 .M44", "copyNumber": "1", "instanceId": "69640328-788e-43fc-9c3c-af39e243f3b7", "holdingsItems": [], "receiptStatus": "Not currently received", "retentionPolicy": "Permanently retained.", "electronicAccess": [{"uri": "http://www.ebscohost.com", "relationshipId": "3b430592-2e09-4b48-9a0c-0636d66b9fb3", "materialsSpecification": "1984-"}, {"uri": "http://www.jstor.com", "publicNote": "Most recent 4 years not available.", "relationshipId": "3b430592-2e09-4b48-9a0c-0636d66b9fb3", "materialsSpecification": "1984-"}], "acquisitionMethod": "Purchase", "bareHoldingsItems": [], "holdingsStatements": [{"statement": "v.70-84 (1984-1998)"}, {"statement": "v.85:no. 1-11 (1999:Jan.-Nov.)"}, {"statement": "v.87:no.1-9 (2001:Jan.-Sept.)"}, {"statement": "v.89:no.2-12 (2003:Feb.-Dec.)"}, {"statement": "v.90-95 (2004-2009)"}], "statisticalCodeIds": ["775b6ad4-9c35-4d29-bf78-8775a9b42226"], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": [{"statement": "no.1-23 "}]}	2022-06-23 15:14:00.966	\N	69640328-788e-43fc-9c3c-af39e243f3b7	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	\N	\N	\N
67cd0046-e4f1-4e4f-9024-adf0b0039d09	{"id": "67cd0046-e4f1-4e4f-9024-adf0b0039d09", "hrid": "hold000000000007", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:00.967Z", "updatedDate": "2022-06-23T15:14:00.967Z"}, "formerIds": [], "callNumber": "D15.H63 A3 2002", "instanceId": "a89eccf0-57a6-495e-898d-32b9b2210f2f", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [{"statement": "Line 1b"}, {"statement": "Line 2b"}], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "f34d27c6-a8eb-461b-acd6-5dea81771e70", "permanentLocationId": "f34d27c6-a8eb-461b-acd6-5dea81771e70", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:00.967	\N	a89eccf0-57a6-495e-898d-32b9b2210f2f	f34d27c6-a8eb-461b-acd6-5dea81771e70	\N	f34d27c6-a8eb-461b-acd6-5dea81771e70	\N	\N	\N	\N
e9285a1c-1dfc-4380-868c-e74073003f43	{"id": "e9285a1c-1dfc-4380-868c-e74073003f43", "hrid": "hold000000000011", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:00.984Z", "updatedDate": "2022-06-23T15:14:00.984Z"}, "formerIds": [], "callNumber": "M1366.S67 T73 2017", "instanceId": "e54b1f4d-7d05-4b1a-9368-3c36b75d8ac6", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [{"statement": "Line 1b"}, {"statement": "Line 2b"}], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:00.984	\N	e54b1f4d-7d05-4b1a-9368-3c36b75d8ac6	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	\N	\N	\N
133a7916-f05e-4df4-8f7f-09eb2a7076d1	{"id": "133a7916-f05e-4df4-8f7f-09eb2a7076d1", "hrid": "hold000000000003", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:00.985Z", "updatedDate": "2022-06-23T15:14:00.985Z"}, "formerIds": [], "callNumber": "R11.A38", "instanceId": "30fcc8e7-a019-43f4-b642-2edc389f4501", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [{"statement": "v1-128, July 1946-December 2016"}], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:00.985	\N	30fcc8e7-a019-43f4-b642-2edc389f4501	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	\N	\N	\N
e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19	{"id": "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19", "hrid": "hold000000000009", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.016Z", "updatedDate": "2022-06-23T15:14:01.016Z"}, "formerIds": [], "callNumber": "TK5105.88815 . A58 2004 FT MEADE", "instanceId": "5bf370e0-8cca-4d9c-82e4-5170ab2a0a39", "illPolicyId": "46970b40-918e-47a4-a45d-b1677a2d3d46", "holdingsItems": [], "shelvingTitle": " TK5105.88815", "holdingsTypeId": "03c9c400-b9e3-4a07-ac0e-05ab470233ed", "callNumberTypeId": "512173a7-bd09-490e-b773-17d83f2b63fe", "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": ["b5968c9e-cddc-4576-99e3-8e60aed8b0dd"], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.016	\N	5bf370e0-8cca-4d9c-82e4-5170ab2a0a39	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	03c9c400-b9e3-4a07-ac0e-05ab470233ed	512173a7-bd09-490e-b773-17d83f2b63fe	46970b40-918e-47a4-a45d-b1677a2d3d46	\N
c4a15834-0184-4a6f-9c0c-0ca5bad8286d	{"id": "c4a15834-0184-4a6f-9c0c-0ca5bad8286d", "hrid": "hold000000000001", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.019Z", "updatedDate": "2022-06-23T15:14:01.019Z"}, "formerIds": [], "callNumber": "K1 .M44", "instanceId": "69640328-788e-43fc-9c3c-af39e243f3b7", "holdingsItems": [], "electronicAccess": [{"uri": "https://search.proquest.com/publication/1396348", "publicNote": "via ProQuest, the last 12 months are not available due to an embargo", "relationshipId": "f5d0068e-6272-458e-8a81-b85e7b9a14aa", "materialsSpecification": "1.2012 -"}, {"uri": "https://www.emeraldinsight.com/loi/jepp", "publicNote": "via Emerald", "relationshipId": "f5d0068e-6272-458e-8a81-b85e7b9a14aa", "materialsSpecification": "1.2012 -"}, {"uri": "https://www.emeraldinsight.com/journal/jepp", "publicNote": "via Emerald, national license", "relationshipId": "f5d0068e-6272-458e-8a81-b85e7b9a14aa", "materialsSpecification": "1.2012 - 5.2016"}], "bareHoldingsItems": [], "holdingsStatements": [{"statement": "1.2012 -"}], "statisticalCodeIds": [], "administrativeNotes": ["cataloging note"], "effectiveLocationId": "53cf956f-c1df-410b-8bea-27f712cca7c0", "permanentLocationId": "53cf956f-c1df-410b-8bea-27f712cca7c0", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.019	\N	69640328-788e-43fc-9c3c-af39e243f3b7	53cf956f-c1df-410b-8bea-27f712cca7c0	\N	53cf956f-c1df-410b-8bea-27f712cca7c0	\N	\N	\N	\N
65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61	{"id": "65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61", "hrid": "hold000000000004", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.022Z", "updatedDate": "2022-06-23T15:14:01.022Z"}, "formerIds": [], "callNumber": "PR6056.I4588 B749 2016", "instanceId": "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.022	\N	7fbd5d84-62d1-44c6-9c45-6cb173998bbd	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	\N	\N	\N
9e8dc8ce-68f3-4e75-8479-d548ce521157	{"id": "9e8dc8ce-68f3-4e75-8479-d548ce521157", "hrid": "BW-1", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.306Z", "updatedDate": "2022-06-23T15:14:01.306Z"}, "sourceId": "f32d531e-df79-46b3-8932-cdd35f7a2264", "formerIds": [], "callNumber": "1958 A 8050", "instanceId": "ce9dd893-c812-49d5-8973-d55d018894c4", "holdingsItems": [], "holdingsTypeId": "0c422f92-0f4d-4d32-8cbe-390ebc33a3e5", "callNumberPrefix": "A", "callNumberTypeId": "6caca63e-5651-4db6-9247-3205156e9699", "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.306	\N	ce9dd893-c812-49d5-8973-d55d018894c4	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	0c422f92-0f4d-4d32-8cbe-390ebc33a3e5	6caca63e-5651-4db6-9247-3205156e9699	\N	f32d531e-df79-46b3-8932-cdd35f7a2264
68872d8a-bf16-420b-829f-206da38f6c10	{"id": "68872d8a-bf16-420b-829f-206da38f6c10", "hrid": "hold000000000008", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.021Z", "updatedDate": "2022-06-23T15:14:01.021Z"}, "formerIds": [], "callNumber": "some-callnumber", "instanceId": "6506b79b-7702-48b2-9774-a1c538fdd34e", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [{"statement": "Line 1b"}, {"statement": "Line 2b"}], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.021	\N	6506b79b-7702-48b2-9774-a1c538fdd34e	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	\N	\N	\N
247f1832-88be-4a84-9638-605ffde308b3	{"id": "247f1832-88be-4a84-9638-605ffde308b3", "hrid": "BW-2", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.304Z", "updatedDate": "2022-06-23T15:14:01.304Z"}, "sourceId": "f32d531e-df79-46b3-8932-cdd35f7a2264", "formerIds": [], "callNumber": "1958 A 8050", "instanceId": "85010f04-b914-4ac7-ba30-be2b52f79708", "holdingsItems": [], "holdingsTypeId": "0c422f92-0f4d-4d32-8cbe-390ebc33a3e5", "callNumberPrefix": "A", "callNumberTypeId": "6caca63e-5651-4db6-9247-3205156e9699", "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.304	\N	85010f04-b914-4ac7-ba30-be2b52f79708	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	0c422f92-0f4d-4d32-8cbe-390ebc33a3e5	6caca63e-5651-4db6-9247-3205156e9699	\N	f32d531e-df79-46b3-8932-cdd35f7a2264
13767c78-f8d0-425e-801d-cc5bd475856a	{"id": "13767c78-f8d0-425e-801d-cc5bd475856a", "hrid": "bwho000000001", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.303Z", "updatedDate": "2022-06-23T15:14:01.303Z"}, "sourceId": "f32d531e-df79-46b3-8932-cdd35f7a2264", "formerIds": [], "callNumber": "DE3", "instanceId": "ce9dd893-c812-49d5-8973-d55d018894c4", "holdingsItems": [], "holdingsTypeId": "03c9c400-b9e3-4a07-ac0e-05ab470233ed", "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.303	\N	ce9dd893-c812-49d5-8973-d55d018894c4	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	03c9c400-b9e3-4a07-ac0e-05ab470233ed	\N	\N	f32d531e-df79-46b3-8932-cdd35f7a2264
fb7b70f1-b898-4924-a991-0e4b6312bb5f	{"id": "fb7b70f1-b898-4924-a991-0e4b6312bb5f", "hrid": "hold000000000005", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.023Z", "updatedDate": "2022-06-23T15:14:01.023Z"}, "formerIds": [], "callNumber": "PR6056.I4588 B749 2016", "instanceId": "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "53cf956f-c1df-410b-8bea-27f712cca7c0", "permanentLocationId": "53cf956f-c1df-410b-8bea-27f712cca7c0", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.023	\N	7fbd5d84-62d1-44c6-9c45-6cb173998bbd	53cf956f-c1df-410b-8bea-27f712cca7c0	\N	53cf956f-c1df-410b-8bea-27f712cca7c0	\N	\N	\N	\N
e6d7e91a-4dbc-4a70-9b38-e000d2fbdc79	{"id": "e6d7e91a-4dbc-4a70-9b38-e000d2fbdc79", "hrid": "hold000000000010", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.032Z", "updatedDate": "2022-06-23T15:14:01.032Z"}, "formerIds": [], "callNumber": "some-callnumber", "instanceId": "cf23adf0-61ba-4887-bf82-956c4aae2260", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [{"statement": "Line 1b"}, {"statement": "Line 2b"}], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.032	\N	cf23adf0-61ba-4887-bf82-956c4aae2260	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	\N	\N	\N
65032151-39a5-4cef-8810-5350eb316300	{"id": "65032151-39a5-4cef-8810-5350eb316300", "hrid": "hold000000000006", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.043Z", "updatedDate": "2022-06-23T15:14:01.043Z"}, "formerIds": [], "callNumber": "MCN FICTION", "instanceId": "f31a36de-fcf8-44f9-87ef-a55d06ad21ae", "holdingsItems": [], "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [{"statement": "Line 1b"}, {"statement": "Line 2b"}], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "b241764c-1466-4e1d-a028-1a3684a5da87", "permanentLocationId": "b241764c-1466-4e1d-a028-1a3684a5da87", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.043	\N	f31a36de-fcf8-44f9-87ef-a55d06ad21ae	b241764c-1466-4e1d-a028-1a3684a5da87	\N	b241764c-1466-4e1d-a028-1a3684a5da87	\N	\N	\N	\N
704ea4ec-456c-4740-852b-0814d59f7d21	{"id": "704ea4ec-456c-4740-852b-0814d59f7d21", "hrid": "BW-3", "notes": [], "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.302Z", "updatedDate": "2022-06-23T15:14:01.302Z"}, "sourceId": "f32d531e-df79-46b3-8932-cdd35f7a2264", "formerIds": [], "callNumber": "1958 A 8050", "instanceId": "cd3288a4-898c-4347-a003-2d810ef70f03", "holdingsItems": [], "holdingsTypeId": "0c422f92-0f4d-4d32-8cbe-390ebc33a3e5", "callNumberPrefix": "A", "callNumberTypeId": "6caca63e-5651-4db6-9247-3205156e9699", "electronicAccess": [], "bareHoldingsItems": [], "holdingsStatements": [], "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "holdingsStatementsForIndexes": [], "holdingsStatementsForSupplements": []}	2022-06-23 15:14:01.302	\N	cd3288a4-898c-4347-a003-2d810ef70f03	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371	0c422f92-0f4d-4d32-8cbe-390ebc33a3e5	6caca63e-5651-4db6-9247-3205156e9699	\N	f32d531e-df79-46b3-8932-cdd35f7a2264
\.


--
-- Data for Name: holdings_records_source; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.holdings_records_source (id, jsonb, creation_date, created_by) FROM stdin;
036ee84a-6afd-4c3c-9ad3-4a12ab875f59	{"id": "036ee84a-6afd-4c3c-9ad3-4a12ab875f59", "name": "MARC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.450Z", "updatedDate": "2022-06-23T15:14:00.450Z"}}	2022-06-23 15:14:00.45	\N
f32d531e-df79-46b3-8932-cdd35f7a2264	{"id": "f32d531e-df79-46b3-8932-cdd35f7a2264", "name": "FOLIO", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.452Z", "updatedDate": "2022-06-23T15:14:00.452Z"}}	2022-06-23 15:14:00.452	\N
\.


--
-- Data for Name: holdings_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.holdings_type (id, jsonb, creation_date, created_by) FROM stdin;
03c9c400-b9e3-4a07-ac0e-05ab470233ed	{"id": "03c9c400-b9e3-4a07-ac0e-05ab470233ed", "name": "Monograph", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.001Z", "updatedDate": "2022-06-23T15:14:00.001Z"}}	2022-06-23 15:14:00.001	\N
dc35d0ae-e877-488b-8e97-6e41444e6d0a	{"id": "dc35d0ae-e877-488b-8e97-6e41444e6d0a", "name": "Multi-part monograph", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.003Z", "updatedDate": "2022-06-23T15:14:00.003Z"}}	2022-06-23 15:14:00.003	\N
e6da6c98-6dd0-41bc-8b4b-cfd4bbd9c3ae	{"id": "e6da6c98-6dd0-41bc-8b4b-cfd4bbd9c3ae", "name": "Serial", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.002Z", "updatedDate": "2022-06-23T15:14:00.002Z"}}	2022-06-23 15:14:00.002	\N
0c422f92-0f4d-4d32-8cbe-390ebc33a3e5	{"id": "0c422f92-0f4d-4d32-8cbe-390ebc33a3e5", "name": "Physical", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.004Z", "updatedDate": "2022-06-23T15:14:00.004Z"}}	2022-06-23 15:14:00.004	\N
996f93e2-5b5e-4cf2-9168-33ced1f95eed	{"id": "996f93e2-5b5e-4cf2-9168-33ced1f95eed", "name": "Electronic", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.010Z", "updatedDate": "2022-06-23T15:14:00.010Z"}}	2022-06-23 15:14:00.01	\N
\.


--
-- Data for Name: hrid_settings; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.hrid_settings (id, jsonb, lock) FROM stdin;
a501f2a8-5b31-48b2-874d-2191e48db8cd	{"id": "a501f2a8-5b31-48b2-874d-2191e48db8cd", "items": {"prefix": "it", "startNumber": 1}, "holdings": {"prefix": "ho", "startNumber": 1}, "instances": {"prefix": "in", "startNumber": 1}, "commonRetainLeadingZeroes": true}	t
\.


--
-- Data for Name: identifier_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.identifier_type (id, jsonb, creation_date, created_by) FROM stdin;
913300b2-03ed-469a-8179-c1092c991227	{"id": "913300b2-03ed-469a-8179-c1092c991227", "name": "ISSN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.207Z", "updatedDate": "2022-06-23T15:13:58.207Z"}}	2022-06-23 15:13:58.207	\N
37b65e79-0392-450d-adc6-e2a1f47de452	{"id": "37b65e79-0392-450d-adc6-e2a1f47de452", "name": "Report number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.213Z", "updatedDate": "2022-06-23T15:13:58.213Z"}}	2022-06-23 15:13:58.213	\N
650ef996-35e3-48ec-bf3a-a0d078a0ca37	{"id": "650ef996-35e3-48ec-bf3a-a0d078a0ca37", "name": "UkMac", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.213Z", "updatedDate": "2022-06-23T15:13:58.213Z"}}	2022-06-23 15:13:58.213	\N
c858e4f2-2b6b-4385-842b-60732ee14abb	{"id": "c858e4f2-2b6b-4385-842b-60732ee14abb", "name": "LCCN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.214Z", "updatedDate": "2022-06-23T15:13:58.214Z"}}	2022-06-23 15:13:58.214	\N
439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef	{"id": "439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef", "name": "OCLC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.215Z", "updatedDate": "2022-06-23T15:13:58.215Z"}}	2022-06-23 15:13:58.215	\N
7f907515-a1bf-4513-8a38-92e1a07c539d	{"id": "7f907515-a1bf-4513-8a38-92e1a07c539d", "name": "ASIN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.227Z", "updatedDate": "2022-06-23T15:13:58.227Z"}}	2022-06-23 15:13:58.227	\N
4f07ea37-6c7f-4836-add2-14249e628ed1	{"id": "4f07ea37-6c7f-4836-add2-14249e628ed1", "name": "Invalid ISMN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.232+00:00", "updatedDate": "2022-06-23T15:13:58.232+00:00"}}	\N	\N
2e8b3b6c-0e7d-4e48-bca2-b0b23b376af5	{"id": "2e8b3b6c-0e7d-4e48-bca2-b0b23b376af5", "name": "Other standard identifier", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.233Z", "updatedDate": "2022-06-23T15:13:58.233Z"}}	2022-06-23 15:13:58.233	\N
1795ea23-6856-48a5-a772-f356e16a8a6c	{"id": "1795ea23-6856-48a5-a772-f356e16a8a6c", "name": "UPC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.233+00:00", "updatedDate": "2022-06-23T15:13:58.233+00:00"}}	\N	\N
351ebc1c-3aae-4825-8765-c6d50dbf011f	{"id": "351ebc1c-3aae-4825-8765-c6d50dbf011f", "name": "GPO item number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.236Z", "updatedDate": "2022-06-23T15:13:58.236Z"}}	2022-06-23 15:13:58.236	\N
ebfd00b6-61d3-4d87-a6d8-810c941176d5	{"id": "ebfd00b6-61d3-4d87-a6d8-810c941176d5", "name": "ISMN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.238+00:00", "updatedDate": "2022-06-23T15:13:58.238+00:00"}}	\N	\N
39554f54-d0bb-4f0a-89a4-e422f6136316	{"id": "39554f54-d0bb-4f0a-89a4-e422f6136316", "name": "DOI", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.241Z", "updatedDate": "2022-06-23T15:13:58.241Z"}}	2022-06-23 15:13:58.241	\N
8e3dd25e-db82-4b06-8311-90d41998c109	{"id": "8e3dd25e-db82-4b06-8311-90d41998c109", "name": "Standard technical report number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.242Z", "updatedDate": "2022-06-23T15:13:58.242Z"}}	2022-06-23 15:13:58.242	\N
8261054f-be78-422d-bd51-4ed9f33c3422	{"id": "8261054f-be78-422d-bd51-4ed9f33c3422", "name": "ISBN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.242Z", "updatedDate": "2022-06-23T15:13:58.242Z"}}	2022-06-23 15:13:58.242	\N
5130aed5-1095-4fb6-8f6f-caa3d6cc7aae	{"id": "5130aed5-1095-4fb6-8f6f-caa3d6cc7aae", "name": "Local identifier", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.246Z", "updatedDate": "2022-06-23T15:13:58.246Z"}}	2022-06-23 15:13:58.246	\N
b3ea81fb-3324-4c64-9efc-7c0c93d5943c	{"id": "b3ea81fb-3324-4c64-9efc-7c0c93d5943c", "name": "Invalid UPC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.247+00:00", "updatedDate": "2022-06-23T15:13:58.247+00:00"}}	\N	\N
3fbacad6-0240-4823-bce8-bb122cfdf229	{"id": "3fbacad6-0240-4823-bce8-bb122cfdf229", "name": "StEdNL", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.249Z", "updatedDate": "2022-06-23T15:13:58.249Z"}}	2022-06-23 15:13:58.249	\N
5860f255-a27f-4916-a830-262aa900a6b9	{"id": "5860f255-a27f-4916-a830-262aa900a6b9", "name": "Linking ISSN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.251Z", "updatedDate": "2022-06-23T15:13:58.251Z"}}	2022-06-23 15:13:58.251	\N
27fd35a6-b8f6-41f2-aa0e-9c663ceb250c	{"id": "27fd35a6-b8f6-41f2-aa0e-9c663ceb250c", "name": "Invalid ISSN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.253Z", "updatedDate": "2022-06-23T15:13:58.253Z"}}	2022-06-23 15:13:58.253	\N
5d164f4b-0b15-4e42-ae75-cfcf85318ad9	{"id": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9", "name": "Control number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.255Z", "updatedDate": "2022-06-23T15:13:58.255Z"}}	2022-06-23 15:13:58.255	\N
3187432f-9434-40a8-8782-35a111a1491e	{"id": "3187432f-9434-40a8-8782-35a111a1491e", "name": "BNB", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.256Z", "updatedDate": "2022-06-23T15:13:58.256Z"}}	2022-06-23 15:13:58.256	\N
7e591197-f335-4afb-bc6d-a6d76ca3bace	{"id": "7e591197-f335-4afb-bc6d-a6d76ca3bace", "name": "System control number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.258Z", "updatedDate": "2022-06-23T15:13:58.258Z"}}	2022-06-23 15:13:58.258	\N
fcca2643-406a-482a-b760-7a7f8aec640e	{"id": "fcca2643-406a-482a-b760-7a7f8aec640e", "name": "Invalid ISBN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.260Z", "updatedDate": "2022-06-23T15:13:58.260Z"}}	2022-06-23 15:13:58.26	\N
fc4e3f2a-887a-46e5-8057-aeeb271a4e56	{"id": "fc4e3f2a-887a-46e5-8057-aeeb271a4e56", "name": "Cancelled system control number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.262+00:00", "updatedDate": "2022-06-23T15:13:58.262+00:00"}}	\N	\N
5069054d-bc3a-4212-a4e8-e2013a02386f	{"id": "5069054d-bc3a-4212-a4e8-e2013a02386f", "name": "Cancelled GPO item number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.264Z", "updatedDate": "2022-06-23T15:13:58.264Z"}}	2022-06-23 15:13:58.264	\N
b5d8cdc4-9441-487c-90cf-0c7ec97728eb	{"id": "b5d8cdc4-9441-487c-90cf-0c7ec97728eb", "name": "Publisher or distributor number", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.265Z", "updatedDate": "2022-06-23T15:13:58.265Z"}}	2022-06-23 15:13:58.265	\N
593b78cb-32f3-44d1-ba8c-63fd5e6989e6	{"id": "593b78cb-32f3-44d1-ba8c-63fd5e6989e6", "name": "CODEN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.265Z", "updatedDate": "2022-06-23T15:13:58.265Z"}}	2022-06-23 15:13:58.265	\N
eb7b2717-f149-4fec-81a3-deefb8f5ee6b	{"id": "eb7b2717-f149-4fec-81a3-deefb8f5ee6b", "name": "URN", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.267Z", "updatedDate": "2022-06-23T15:13:58.267Z"}}	2022-06-23 15:13:58.267	\N
216b156b-215e-4839-a53e-ade35cb5702a	{"id": "216b156b-215e-4839-a53e-ade35cb5702a", "name": "Handle", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:58.269Z", "updatedDate": "2022-06-23T15:13:58.269Z"}}	2022-06-23 15:13:58.269	\N
\.


--
-- Data for Name: ill_policy; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.ill_policy (id, jsonb, creation_date, created_by) FROM stdin;
9e49924b-f649-4b36-ab57-e66e639a9b0e	{"id": "9e49924b-f649-4b36-ab57-e66e639a9b0e", "name": "Limited lending policy", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.951Z", "updatedDate": "2022-06-23T15:13:59.951Z"}}	2022-06-23 15:13:59.951	\N
37fc2702-7ec9-482a-a4e3-5ed9a122ece1	{"id": "37fc2702-7ec9-482a-a4e3-5ed9a122ece1", "name": "Unknown lending policy", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.954Z", "updatedDate": "2022-06-23T15:13:59.954Z"}}	2022-06-23 15:13:59.954	\N
46970b40-918e-47a4-a45d-b1677a2d3d46	{"id": "46970b40-918e-47a4-a45d-b1677a2d3d46", "name": "Will lend", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.957Z", "updatedDate": "2022-06-23T15:13:59.957Z"}}	2022-06-23 15:13:59.957	\N
c51f7aa9-9997-45e6-94d6-b502445aae9d	{"id": "c51f7aa9-9997-45e6-94d6-b502445aae9d", "name": "Unknown reproduction policy", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.956Z", "updatedDate": "2022-06-23T15:13:59.956Z"}}	2022-06-23 15:13:59.956	\N
6bc6a71f-d6e2-4693-87f1-f495afddff00	{"id": "6bc6a71f-d6e2-4693-87f1-f495afddff00", "name": "Will not reproduce", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.959Z", "updatedDate": "2022-06-23T15:13:59.959Z"}}	2022-06-23 15:13:59.959	\N
b0f97013-87f5-4bab-87f2-ac4a5191b489	{"id": "b0f97013-87f5-4bab-87f2-ac4a5191b489", "name": "Will not lend", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.969Z", "updatedDate": "2022-06-23T15:13:59.969Z"}}	2022-06-23 15:13:59.969	\N
2b870182-a23d-48e8-917d-9421e5c3ce13	{"id": "2b870182-a23d-48e8-917d-9421e5c3ce13", "name": "Will lend hard copy only", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.971Z", "updatedDate": "2022-06-23T15:13:59.971Z"}}	2022-06-23 15:13:59.971	\N
2a572e7b-dfe5-4dee-8a62-b98d26a802e6	{"id": "2a572e7b-dfe5-4dee-8a62-b98d26a802e6", "name": "Will reproduce", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.976Z", "updatedDate": "2022-06-23T15:13:59.976Z"}}	2022-06-23 15:13:59.976	\N
\.


--
-- Data for Name: instance; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance (id, jsonb, creation_date, created_by, instancestatusid, modeofissuanceid, instancetypeid) FROM stdin;
f31a36de-fcf8-44f9-87ef-a55d06ad21ae	{"id": "f31a36de-fcf8-44f9-87ef-a55d06ad21ae", "hrid": "inst000000000012", "notes": [], "title": "The Girl on the Train", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.648Z", "updatedDate": "2022-06-23T15:14:00.648Z"}, "subjects": [], "languages": [], "identifiers": [{"value": "B01LO7PJOE", "identifierTypeId": "7f907515-a1bf-4513-8a38-92e1a07c539d"}], "publication": [], "contributors": [{"name": "Creator A", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}, {"name": "Creator B", "contributorNameTypeId": "e8b311a6-3b21-43f2-a269-dd9310cb2d0a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [{"alternativeTitle": "First alternative title"}, {"alternativeTitle": "Second alternative title"}], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.650+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": ["44cd89f3-2e76-469f-a955-cc57cb9e0395"]}	2022-06-23 15:14:00.648	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
69640328-788e-43fc-9c3c-af39e243f3b7	{"id": "69640328-788e-43fc-9c3c-af39e243f3b7", "hrid": "inst000000000001", "notes": [], "title": "ABA Journal", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.660Z", "updatedDate": "2022-06-23T15:14:00.660Z"}, "subjects": [], "languages": [], "identifiers": [{"value": "0747-0088", "identifierTypeId": "913300b2-03ed-469a-8179-c1092c991227"}, {"value": "84641839", "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"}], "publication": [{"place": "Chicago, Ill.", "publisher": "American Bar Association", "dateOfPublication": "1915-1983"}], "contributors": [], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"end": 1983, "start": 1915}, "statusUpdatedDate": "2022-06-23T15:14:00.660+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": ["0abeee3d-8ad2-4b04-92ff-221b4fce1075"]}	2022-06-23 15:14:00.66	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
6eee8eb9-db1a-46e2-a8ad-780f19974efa	{"id": "6eee8eb9-db1a-46e2-a8ad-780f19974efa", "hrid": "inst000000000011", "notes": [], "title": "DC Motor Control Experiment Objekt for introduction to control systems Herbert Werner", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.658Z", "updatedDate": "2022-06-23T15:14:00.658Z"}, "subjects": [], "languages": ["ger"], "identifiers": [{"value": "727867881", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "(DE-599)GBV727867881", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Hamburg", "publisher": "Techn. Univ., Inst. für Regelungstechnik", "dateOfPublication": "[2016]"}], "contributors": [{"name": "Werner, Herbert", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Technische Universität Hamburg-Harburg Institut für Regelungstechnik", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}], "instanceTypeId": "c1e95c2b-4efc-48cf-9e71-edb622cf0c22", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2016}, "statusUpdatedDate": "2022-06-23T15:14:00.658+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["1 Koffer (Inhalt: 1 Motor mit Platine auf Plattform, 1 Netzteil, 1 USB-Kabel)"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.658	\N	\N	\N	c1e95c2b-4efc-48cf-9e71-edb622cf0c22
bbd4a5e1-c9f3-44b9-bfdf-d184e04f0ba0	{"id": "bbd4a5e1-c9f3-44b9-bfdf-d184e04f0ba0", "hrid": "inst000000000029", "notes": [], "title": "Water resources of East Feliciana Parish, Louisiana", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.664Z", "updatedDate": "2022-06-23T15:14:00.664Z"}, "subjects": [], "languages": [], "identifiers": [], "publication": [], "contributors": [{"name": "White, Vincent E.", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": ["f5e8210f-7640-459b-a71f-552567f92369"], "statusUpdatedDate": "2022-06-23T15:14:00.664+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.664	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
6506b79b-7702-48b2-9774-a1c538fdd34e	{"id": "6506b79b-7702-48b2-9774-a1c538fdd34e", "hrid": "inst000000000021", "notes": [], "title": "Nod", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.666Z", "updatedDate": "2022-06-23T15:14:00.666Z"}, "subjects": [], "languages": [], "identifiers": [{"value": "0956687695", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9780956687695", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}], "publication": [], "contributors": [{"name": "Barnes, Adrian", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.666+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.666	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
7ab22f0a-c9cd-449a-9137-c76e5055ca37	{"id": "7ab22f0a-c9cd-449a-9137-c76e5055ca37", "hrid": "inst000000000016", "notes": [{"note": "Enthält 16 Beiträge", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "Includes bibliographical references and index", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "Introduction / Mike Odugbo Odey and Toyin Falola -- Part I: Dimensions and assessments of poverty reduction policies and programs in Sub-Saharan Africa -- Poverty in post-colonial Africa: the legacy of contested perspectives / Sati U. Fwatshak -- Scaling up power infrastructure investment in Sub-Saharan Africa for poverty alleviation / Aori R. Nyambati -- The impact of anti-corruption conventions in Sub-Saharan Africa / Daniel Barkley and Claire Maduka -- The besieged continent: interrogating contemporary issues of corruption and poverty in Africa / Idris S. Jimada -- PEPFAR and preventing HIV transmission: evidence from Sub-Saharan Africa / Daniel Barkley and Opeyemi Adeniyi -- Reflections on the current challenges of poverty reduction in Africa / Loveday N. Gbara -- A critical analysis of poverty reduction strategies in post-colonial Africa / Okokhere O. Felix -- Part II: Problems of good governance and institutional failures in West-Africa -- Weaknesses and failures of poverty reduction policies and programs in Nigeria since 1960 / Mike O. Odey -- In the web of neo-liberalism and deepening contradictions? Assessing poverty reform strategies in West Africa since the mid-1980s / Okpeh O. Okpeh, Jr. -- An assessment of abuse of the elderly as an aspect of poverty in Akwa-Ibom State, Nigeria / Ekot O. Mildred -- Reflections on the interface between poverty and food insecurity in Nigeria / Funso A. Adesola -- An appraisal of poverty reduction program in Bayelsa State of Nigeria: \\\\\\"In-Care of the People\\\\\\" (COPE) / Ezi Beedie -- A comparative analysis of incidence of poverty in three urban centers in Ghana from 1945-1990 / Wilhelmina J. Donkoh -- Part III: Dimensions of poverty in east and southern Africa -- Landlessness, national politics, and the future of land reforms in Kenya / Shanguhyia S. Martin -- Extra-version and development in northwestern Ethiopia: the case of the Humera Agricultural Project (1967-1975) / Luca Pudu -- Affirmative action as a theological-pastoral challenge in the south-African democratic context / Elijah M. Baloyi.", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "Poverty reduction strategies in Africa edited by Toyin Falola and Mike Odugbo Odey", "series": ["Global Africa 3"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.708Z", "updatedDate": "2022-06-23T15:14:00.708Z"}, "subjects": ["Poverty--Government policy--Africa, Sub-Saharan", "Armut", "Bekämpfung", "Armutsbekämpfung", "Subsahara-Afrika", "Westafrika", "Africa, Sub-Saharan--Economic conditions--21st century", "Subsaharisches Afrika", "Africa, Sub-Saharan Economic conditions 21st century", "Poverty Government policy Africa, Sub-Saharan"], "languages": ["eng"], "identifiers": [{"value": "2017004333", "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"}, {"value": "9781138240667", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9781315282978", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "(DE-599)GBV880159367", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "London", "publisher": "Routledge", "dateOfPublication": "2018"}], "contributors": [{"name": "Falola, Toyin", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Odey, Mike Odugbo", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "HV438.A357", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}, {"classificationNumber": "362.50967", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [{"uri": "http://www.gbv.de/dms/zbw/880159367.pdf", "linkText": "Electronic resource (PDF)", "publicNote": "Address for accessing the table of content. PDF file"}], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2018}, "statusUpdatedDate": "2022-06-23T15:14:00.708+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["xiv, 300 Seiten"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.708	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
7fbd5d84-62d1-44c6-9c45-6cb173998bbd	{"id": "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", "hrid": "inst000000000006", "notes": [{"note": "Bridget Jones finds herself unexpectedly pregnant at the eleventh hour. However, her joyful pregnancy is dominated by one crucial but awkward question --who is the father? Could it be honorable, decent, notable human rights lawyer, Mark Darcy? Or, is it charming, witty, and totally despicable, Daniel Cleaver?", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "Bridget Jones's Baby: the diaries", "series": [], "source": "FOLIO", "_version": 1, "editions": ["First American Edition"], "metadata": {"createdDate": "2022-06-23T15:14:00.746Z", "updatedDate": "2022-06-23T15:14:00.746Z"}, "subjects": ["Jones, Bridget", "Pregnant women", "England", "Humorous fiction", "Diary fiction"], "languages": ["eng"], "identifiers": [{"value": "ocn956625961", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}], "publication": [{"place": "New York", "publisher": "Alfred A. Knopf", "dateOfPublication": "2016"}], "contributors": [{"name": "Fielding, Helen", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "staffSuppress": true, "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": true, "classifications": [{"classificationNumber": "PR6056.I4588", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}], "instanceFormats": [], "electronicAccess": [{"uri": "http://www.folio.org/", "linkText": "Electronic resource (audio streaming)", "publicNote": "Access to audio file", "materialsSpecification": "Novel"}], "holdingsRecords2": [], "publicationRange": ["A publication range"], "alternativeTitles": [], "discoverySuppress": true, "instanceFormatIds": [], "publicationPeriod": {"start": 2016}, "statusUpdatedDate": "2022-06-23T15:14:00.748+0000", "statisticalCodeIds": [], "administrativeNotes": ["Cataloging data"], "physicalDescriptions": ["219 pages ; 20 cm."], "publicationFrequency": ["A frequency description"], "natureOfContentTermIds": []}	2022-06-23 15:14:00.746	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
5b1eb450-ff9f-412d-a9e7-887f6eaeb5b4	{"id": "5b1eb450-ff9f-412d-a9e7-887f6eaeb5b4", "hrid": "inst000000000010", "notes": [{"note": "Dissertation New York University 1993", "staffOnly": false}, {"note": "Mikrofiche-Ausgabe", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "Concepts of fashion 1921 - 1987 microform a study of garments worn by selected winners of the Miss America Pageant Marian Ann J. Matwiejczyk-Montgomery", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.717Z", "updatedDate": "2022-06-23T15:14:00.717Z"}, "subjects": ["Hochschulschrift"], "languages": ["eng"], "identifiers": [{"value": "1008673218", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "(DE-599)GBV1008673218", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Ann Arbor, MI", "publisher": "University Microfims International", "dateOfPublication": "1993"}], "contributors": [{"name": "Matwiejczyk-Montgomery, Marian Ann J", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 1993}, "statusUpdatedDate": "2022-06-23T15:14:00.717+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.717	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
04489a01-f3cd-4f9e-9be4-d9c198703f45	{"id": "04489a01-f3cd-4f9e-9be4-d9c198703f45", "hrid": "inst000000000015", "notes": [{"note": "Literaturangaben", "staffOnly": false}, {"note": "Introduction: The environment in colonial Africa -- British Cameroon grasslands of Bamenda : geography and history -- Heterogeneous societies and ethnic identity : Fulani and cattle migrations -- Resource conflicts : farmers, pastoralists, cattle taxes and disputes over grazing and land -- Towards a resolution : the land settlement question -- Transforming British Bamenda : cattle wealth and development -- Semi-autonomy for pastoralists : native authority and court for the Fulani -- Modernizing the minds : introduction Western education to the pastoral Fulani -- Managing development : grazing innovations -- Continuity and change : the limits of colonial modernization", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "Environment and identity politics in colonial Africa Fulani migrations and land conflict by Emmanuel M. Mbah", "series": ["Global Africa 2"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.796Z", "updatedDate": "2022-06-23T15:14:00.796Z"}, "subjects": ["Fula (African people)--Cameroon--Bamenda Highlands", "Fula (African people)--Land tenure", "Land settlement patterns--Cameroon--Bamenda Highlands", "Cattle--Environmental aspects--Cameroon--Bamenda Highlands", "Grazing--Environmental aspects--Cameroon--Bamenda Highlands", "Kolonie", "Fulbe", "Regionale Mobilität", "Weidewirtschaft", "Ethnische Beziehungen", "Konflikt", "Grundeigentum", "Natürliche Ressourcen", "Cameroon--Ethnic relations", "Great Britain--Colonies--Africa--Administration", "Großbritannien", "Kamerun--Nordwest", "Cameroon Ethnic relations", "Great Britain Colonies Africa Administration", "Cattle Environmental aspects Cameroon Bamenda Highlands", "Fula (African people) Land tenure", "Fula (African people) Cameroon Bamenda Highlands", "Grazing Environmental aspects Cameroon Bamenda Highlands", "Land settlement patterns Cameroon Bamenda Highlands", "Geschichte 1916-1960"], "languages": ["eng"], "identifiers": [{"value": "2016030844", "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"}, {"value": "9781138239555", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9781315294179", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "(OCoLC)961183745", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-599)GBV869303589", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "London New York, NY", "publisher": "Routledge Taylor & Francis Group", "dateOfPublication": "2017"}], "contributors": [{"name": "Mbah, Emmanuel M", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "DT571.F84", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}, {"classificationNumber": "967.1100496322", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2017}, "statusUpdatedDate": "2022-06-23T15:14:00.796+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["175 Seiten Karten"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.796	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
a317b304-528c-424f-961c-39174933b454	{"id": "a317b304-528c-424f-961c-39174933b454", "hrid": "inst000000000026", "notes": [], "title": "Umsetzung der DIN EN ISO 9001:2015 Harald Augustin (Hrsg.)", "series": ["Berichte aus der Betriebswirtschaft"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.823Z", "updatedDate": "2022-06-23T15:14:00.823Z"}, "subjects": [], "languages": ["ger"], "identifiers": [{"value": "(DE-599)GBV101484262X", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Aachen", "publisher": "Shaker Verlag", "dateOfPublication": "2017"}], "contributors": [{"name": "Augustin, Harald", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2017}, "statusUpdatedDate": "2022-06-23T15:14:00.823+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.823	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
30fcc8e7-a019-43f4-b642-2edc389f4501	{"id": "30fcc8e7-a019-43f4-b642-2edc389f4501", "hrid": "inst000000000003", "notes": [{"note": "Print subscription cancelled by Dec. 2016.", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "May 1988-: A Yorke medical. Also known as the Green journal", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "Publisher: Excerpta Medica, 2008-; New York, NY : Elsevier Inc. 2013-", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "Supplements issued irregularly, 1982.", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "Official journal of the Association of Professors of Medicine 2005-", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "Indexed quinquennially in: American journal of medicine 5 year cumulative index", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "The American Journal of Medicine", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.862Z", "updatedDate": "2022-06-23T15:14:00.862Z"}, "subjects": ["Clinical medicine-Periodicals", "Medicine", "Geneeskunde"], "languages": ["eng"], "identifiers": [{"value": "AJMEAZ", "identifierTypeId": "593b78cb-32f3-44d1-ba8c-63fd5e6989e6"}, {"value": "0002-9343", "identifierTypeId": "913300b2-03ed-469a-8179-c1092c991227"}, {"value": "med49002270", "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"}], "publication": [{"place": "New York", "publisher": "Dun-Donnelley Pub. Co. ", "dateOfPublication": "1946-"}], "contributors": [], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "RC60 .A5", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}, {"classificationNumber": "W1 AM493", "classificationTypeId": "a7f4d03f-b0d8-496c-aebf-4e9cdb678200"}], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [{"alternativeTitle": "The American journal of medicine (online)"}, {"alternativeTitle": "Am. J. med"}, {"alternativeTitle": "Green journal"}], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 1946}, "statusUpdatedDate": "2022-06-23T15:14:00.862+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["v., ill. 27 cm."], "publicationFrequency": [], "natureOfContentTermIds": ["0abeee3d-8ad2-4b04-92ff-221b4fce1075"]}	2022-06-23 15:14:00.862	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
8be05cf5-fb4f-4752-8094-8e179d08fb99	{"id": "8be05cf5-fb4f-4752-8094-8e179d08fb99", "hrid": "inst000000000004", "notes": [{"note": "Titel und Angaben zu beteiligter Person vom Begleitheft", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "In English with segments in Anglo-Saxon and Latin", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "Anglo-Saxon manuscripts in microfiche facsimile Volume 25 Corpus Christi College, Cambridge II, MSS 12, 144, 162, 178, 188, 198, 265, 285, 322, 326, 449 microform A. N. Doane (editor and director), Matthew T. Hussey (associate editor), Phillip Pulsiano (founding editor)", "series": ["Medieval and Renaissance Texts and Studies volume 497", "volume 497"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.869Z", "updatedDate": "2022-06-23T15:14:00.869Z"}, "subjects": [], "languages": ["eng", "ang", "lat"], "identifiers": [{"value": "880391235", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "9780866989732", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "0866989730", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9780866985529", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "0866985522", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "(OCoLC)962073864", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(OCoLC)ocn962073864", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(OCoLC)962073864", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-599)GBV880391235", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Tempe, Arizona", "publisher": "ACMRS, Arizona Center for Medieval and Renaissance Studies", "dateOfPublication": "2016"}], "contributors": [{"name": "Lucas, Peter J", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Arizona Center for Medieval and Renaissance Studies", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2016}, "statusUpdatedDate": "2022-06-23T15:14:00.870+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["69 Mikrofiches 1 Begleitbuch (XII, 167 Seiten)"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.869	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
e6bc03c6-c137-4221-b679-a7c5c31f986c	{"id": "e6bc03c6-c137-4221-b679-a7c5c31f986c", "hrid": "inst000000000027", "notes": [], "title": "Organisations- und Prozessentwicklung Harald Augustin (Hrsg.)", "series": ["Umsetzung der DIN EN ISO 9001:2015  / Harald Augustin (Hrsg.) ; Band 1", "Berichte aus der Betriebswirtschaft", "Umsetzung der DIN EN ISO 9001:2015 Band 1"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.723Z", "updatedDate": "2022-06-23T15:14:00.723Z"}, "subjects": ["DIN EN ISO 9001:2015", "Standard", "Organisatorischer Wandel", "Prozessmanagement", "Einführung", "Industrie", "Deutschland"], "languages": ["ger"], "identifiers": [{"value": "3844057420", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9783844057423", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9783844057423", "identifierTypeId": "2e8b3b6c-0e7d-4e48-bca2-b0b23b376af5"}, {"value": "(OCoLC)1024095011", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-101)1150175923", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-599)DNB1150175923", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Aachen", "publisher": "Shaker Verlag", "dateOfPublication": "2017"}], "contributors": [{"name": "Augustin, Harald", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Shaker Verlag GmbH", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "658.4013", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}, {"classificationNumber": "650", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [{"uri": "http://d-nb.info/1150175923/04", "linkText": "Electronic resource (PDF)", "publicNote": "Address for accessing the table of content. PDF file"}], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2017}, "statusUpdatedDate": "2022-06-23T15:14:00.723+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["x, 113 Seiten Illustrationen 21 cm, 223 g"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.723	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
e54b1f4d-7d05-4b1a-9368-3c36b75d8ac6	{"id": "e54b1f4d-7d05-4b1a-9368-3c36b75d8ac6", "hrid": "inst000000000025", "notes": [{"note": "Title from disc label.", "staffOnly": false}, {"note": "All compositions written by Omar Sosa and Seckou Keita, except tracks 6, 8 and 10 written by Omar Sosa.", "staffOnly": false}, {"note": "Produced by Steve Argüelles and Omar Sosa.", "staffOnly": false}, {"note": "Omar Sosa, grand piano, Fender Rhodes, sampler, microKorg, vocals ; Seckou Keita, kora, talking drum, djembe, sabar, vocals ; Wu Tong, sheng, bawu ; Mieko Miyazaki, koto ; Gustavo Ovalles, bata drums, culo'e puya, maracas, guataca, calabaza, clave ; E'Joung-Ju, geojungo ; Mosin Khan Kawa, nagadi ; Dominique Huchet, bird effects.", "staffOnly": false}], "title": "Transparent water", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.787Z", "updatedDate": "2022-06-23T15:14:00.787Z"}, "subjects": ["World music.", "Jazz"], "languages": ["und"], "identifiers": [{"value": "ocn968777846", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "9786316800312", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "6316800312", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "OTA-1031 Otá Records", "identifierTypeId": "b5d8cdc4-9441-487c-90cf-0c7ec97728eb"}, {"value": "(OCoLC)968777846", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "[Place of publication not identified]: ", "publisher": "Otá Records, ", "dateOfPublication": "[2017]"}], "contributors": [{"name": "Sosa, Omar", "contributorTypeId": "9f0a2cf0-7a9b-45a2-a403-f68d2850d07c", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Keita, Seckou, 1977-", "contributorTypeId": "9f0a2cf0-7a9b-45a2-a403-f68d2850d07c", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "3be24c14-3551-4180-9292-26a786649c8b", "previouslyHeld": false, "classifications": [{"classificationNumber": "M1366.S67", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": ["5cb91d15-96b1-4b8a-bf60-ec310538da66"], "publicationPeriod": {"start": 2017}, "statusUpdatedDate": "2022-06-23T15:14:00.787+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["1 audio disc: digital; 4 3/4 in."], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.787	\N	\N	\N	3be24c14-3551-4180-9292-26a786649c8b
62ca5b43-0f11-40af-a6b4-1a9ee2db33cb	{"id": "62ca5b43-0f11-40af-a6b4-1a9ee2db33cb", "hrid": "inst000000000020", "notes": [{"note": "Mikrofilm-Ausg. 1957 1 Mikrofilm", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "The Neurotic Heroine in Tennessee Williams microform C.N. Stavrou", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.824Z", "updatedDate": "2022-06-23T15:14:00.824Z"}, "subjects": [], "languages": ["eng"], "identifiers": [{"value": "53957015X", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "(DE-599)GBV53957015X", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "New York", "publisher": "Columbia University", "dateOfPublication": "1955"}], "contributors": [{"name": "Stavrou, C.N", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 1955}, "statusUpdatedDate": "2022-06-23T15:14:00.827+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["1 Mikrofilm 26-34 S."], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.824	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
5bf370e0-8cca-4d9c-82e4-5170ab2a0a39	{"id": "5bf370e0-8cca-4d9c-82e4-5170ab2a0a39", "hrid": "inst000000000022", "notes": [{"note": "Includes bibliographical references and index.", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}, {"note": "The development of the Semantic Web, with machine-readable content, has the potential to revolutionize the World Wide Web and its uses. A Semantic Web Primer provides an introduction and guide to this continuously evolving field, describing its key ideas, languages, and technologies. Suitable for use as a textbook or for independent study by professionals, it concentrates on undergraduate-level fundamental concepts and techniques that will enable readers to proceed with building applications on their own and includes exercises, project descriptions, and annotated references to relevant online materials. The third edition of this widely used text has been thoroughly updated, with significant new material that reflects a rapidly developing field. Treatment of the different languages (OWL2, rules) expands the coverage of RDF and OWL, defining the data model independently of XML and including coverage of N3/Turtle and RDFa. A chapter is devoted to OWL2, the new W3C standard. This edition also features additional coverage of the query language SPARQL, the rule language RIF and the possibility of interaction between rules and ontology languages and applications. The chapter on Semantic Web applications reflects the rapid developments of the past few years. A new chapter offers ideas for term projects", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "A semantic web primer", "series": ["Cooperative information systems"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.857Z", "updatedDate": "2022-06-23T15:14:00.857Z"}, "statusId": "9634a5ab-9228-4703-baf2-4d12ebc77d56", "subjects": ["Semantic Web"], "languages": ["eng"], "indexTitle": "Semantic web primer", "identifiers": [{"value": "0262012103", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9780262012102", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "2003065165", "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"}], "publication": [{"role": "Publisher", "place": "Cambridge, Mass. ", "publisher": "MIT Press", "dateOfPublication": "c2004"}], "contributors": [{"name": "Antoniou, Grigoris", "contributorTypeId": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "contributorTypeText": "", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Van Harmelen, Frank", "contributorTypeId": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "contributorTypeText": "", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "catalogedDate": "2019-04-05", "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "025.04", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}, {"classificationNumber": "TK5105.88815 .A58 2004", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "modeOfIssuanceId": "9d18a02f-5897-4c31-9106-c9abb5c7ae8b", "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2004}, "statusUpdatedDate": "2022-06-23T15:14:00.859+0000", "statisticalCodeIds": ["b5968c9e-cddc-4576-99e3-8e60aed8b0dd"], "administrativeNotes": [], "physicalDescriptions": ["xx, 238 p. : ill. ; 24 cm."], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.857	\N	\N	9d18a02f-5897-4c31-9106-c9abb5c7ae8b	6312d172-f0cf-40f6-b27d-9fa8feaf332f
3c4ae3f3-b460-4a89-a2f9-78ce3145e4fc	{"id": "3c4ae3f3-b460-4a89-a2f9-78ce3145e4fc", "hrid": "inst000000000008", "notes": [], "title": "The chess player’s mating guide Computer Datei Robert Ris", "series": ["Fritztrainer Tactics"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.900Z", "updatedDate": "2022-06-23T15:14:00.900Z"}, "subjects": ["DVD-ROM"], "languages": ["eng"], "identifiers": [{"value": "858092093", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "(DE-599)GBV858092093", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Hamburg", "publisher": "Chessbase GmbH", "dateOfPublication": "[2016]-"}], "contributors": [{"name": "Ris, Robert", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "ChessBase GmbH Hamburg", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}], "instanceTypeId": "c208544b-9e28-44fa-a13c-f4093d72f798", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2016}, "statusUpdatedDate": "2022-06-23T15:14:00.900+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.9	\N	\N	\N	c208544b-9e28-44fa-a13c-f4093d72f798
ce9dd893-c812-49d5-8973-d55d018894c4	{"id": "ce9dd893-c812-49d5-8973-d55d018894c4", "hrid": "bwinst0001", "tags": {"tagList": []}, "notes": [], "title": "Rapport från inspektionsresa till svenska betongdammar i augusti 1939, med särskild hänsyn till sprickbildningsfrågan och användandet av specialcement / av S. Giertz-Hedström", "series": ["Svenska Vattenkraftforeningens Publikationer ; 354 (1942:16)"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:01.270Z", "updatedDate": "2022-06-23T15:14:01.270Z"}, "statusId": "9634a5ab-9228-4703-baf2-4d12ebc77d56", "subjects": [], "languages": ["swe"], "identifiers": [], "publication": [{"place": "Stockholm", "publisher": "Svenska Vattenkraftföreningen", "dateOfPublication": "1942"}], "contributors": [{"name": "Giertz-Hedström, S.", "primary": true, "contributorTypeId": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "contributorTypeText": "", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "modeOfIssuanceId": "9d18a02f-5897-4c31-9106-c9abb5c7ae8b", "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 1942}, "statusUpdatedDate": "2022-06-23T15:14:01.270+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["16 p."], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:01.27	\N	\N	9d18a02f-5897-4c31-9106-c9abb5c7ae8b	6312d172-f0cf-40f6-b27d-9fa8feaf332f
ce00bca2-9270-4c6b-b096-b83a2e56e8e9	{"id": "ce00bca2-9270-4c6b-b096-b83a2e56e8e9", "hrid": "inst000000000007", "notes": [], "title": "Cantatas for bass 4 Ich habe genug : BWV 82 / Johann Sebastian Bach ; Matthias Goerne, baritone ; Freiburger Barockorchester, Gottfried von der Goltz, violin and conductor", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.719Z", "updatedDate": "2022-06-23T15:14:00.719Z"}, "subjects": [], "languages": ["ger"], "identifiers": [{"value": "1011162431", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "(DE-599)GBV1011162431", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [], "contributors": [{"name": "Bach, Johann Sebastian", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Arfken, Katharina", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Goltz, Gottfried von der", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Freiburger Barockorchester", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}], "instanceTypeId": "3be24c14-3551-4180-9292-26a786649c8b", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [{"alternativeTitle": "Ich habe genung"}, {"alternativeTitle": "Abweichender Titel Ich habe genung"}], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.720+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["Track 10-14"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.719	\N	\N	\N	3be24c14-3551-4180-9292-26a786649c8b
81825729-e824-4d52-9d15-1695e9bf1831	{"id": "81825729-e824-4d52-9d15-1695e9bf1831", "hrid": "inst000000000014", "notes": [{"note": "Literaturangaben", "staffOnly": false}, {"note": "Introduction: Dissent, protest and dispute Africa / Emmanuel M. Mbah and Toyin Falola -- The music of heaven, the music of Earth, and the music of brats: Tuareg Islam, the Devil, and musical performance / Susan J. Rasmussen -- Finding social change backstage and behind the scenes in South African theatre / Nathanael Vlachos -- Soccer and political (ex)pression in Africa: the case of Cameroon / Alain Lawo-Sukam -- Child labor resistance in southern Nigeria, 1916-1938 / Adam Paddock -- M'Fain goes home: African soldiers in the Gabon campaign of 1940 / Mark Reeves -- \\\\\\"Disgraceful disturbances\\\\\\": TANU, the Tanganyikan Rifles, and the 1964 mutiny / Charles Thomas -- The role of ethnicity in political formation in Kenya: 1963-2007 / Tade O. Okediji and Wahutu J. Siguru -- Land, boundaries, chiefs and wars / Toyin Falola -- Borders and boundaries within Ethiopia: dilemmas of group identity, representation and agency / Alexander Meckelburg -- Rural agrarian land conflicts in postcolonial Nigeria's central region / Sati Fwatshak -- The evolution of the Mungiki militia in Kenya, 1990 to 2010 / Felix Kiruthu -- Refugee-warriors and other people's wars in post-colonial Africa: the experience of Rwandese and South African military exiles (1960-94) / Tim Stapleton -- Oiling the guns and gunning for oil: the youth and Niger Delta oil conflicts in Nigeria / Christian C. Madubuko.", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "Dissent, protest and dispute in Africa edited by Emmanuel M. Mbah and Toyin Falola", "series": ["Global Africa 1"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.798Z", "updatedDate": "2022-06-23T15:14:00.798Z"}, "subjects": ["Social conflict--Africa--History--20th century", "Social conflict--Africa--History--21st century", "Political participation--Africa", "Land tenure--Africa", "Africa--Social conditions--20th century", "Africa--Social conditions--21st century", "Africa--Politics and government--20th century", "Africa--Politics and government--21st century", "Africa Politics and government 20th century", "Africa Politics and government 21st century", "Africa Social conditions 20th century", "Africa Social conditions 21st century", "Land tenure Africa", "Political participation Africa", "Social conflict Africa History 20th century", "Social conflict Africa History 21st century"], "languages": ["eng"], "identifiers": [{"value": "2016022536", "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"}, {"value": "9781138220034", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9781315413099", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "(DE-599)GBV86011306X", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "London New York", "publisher": "Routledge, Taylor & Francis Group", "dateOfPublication": "2017"}], "contributors": [{"name": "Mbah, Emmanuel M", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Falola, Toyin", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "HN773", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}, {"classificationNumber": "303.6/9096", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [{"uri": "https://external.dandelon.com/download/attachments/dandelon/ids/DE0069AE502CCFE91E537C1258123001D0DCA.pdf", "linkText": "Electronic resource (PDF)", "publicNote": "Address for accessing the table of content. PDF file"}], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2017}, "statusUpdatedDate": "2022-06-23T15:14:00.798+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["xi, 293 Seiten"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.798	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
549fad9e-7f8e-4d8e-9a71-00d251817866	{"id": "549fad9e-7f8e-4d8e-9a71-00d251817866", "hrid": "inst000000000028", "notes": [], "title": "Agile Organisation, Risiko- und Change Management Harald Augustin (Hrsg.)", "series": ["Umsetzung der DIN EN ISO 9001:2015  / Harald Augustin (Hrsg.) ; Band 2", "Berichte aus der Betriebswirtschaft", "Umsetzung der DIN EN ISO 9001:2015 Band 2"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.833Z", "updatedDate": "2022-06-23T15:14:00.833Z"}, "subjects": ["DIN EN ISO 9001:2015", "Standard", "Risikomanagement", "Einführung", "Organisatorischer Wandel", "Industrie", "Deutschland"], "languages": ["ger"], "identifiers": [{"value": "3844057439", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9783844057430", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9783844057430", "identifierTypeId": "2e8b3b6c-0e7d-4e48-bca2-b0b23b376af5"}, {"value": "(OCoLC)1024128245", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-101)1150176652", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-599)DNB1150176652", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Aachen", "publisher": "Shaker Verlag", "dateOfPublication": "2017"}], "contributors": [{"name": "Augustin, Harald", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Shaker Verlag GmbH", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "658.4013", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}, {"classificationNumber": "650", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [{"uri": "http://d-nb.info/1150176652/04", "linkText": "Electronic resource (PDF)", "publicNote": "Address for accessing the table of content. PDF file"}], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2017}, "statusUpdatedDate": "2022-06-23T15:14:00.836+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["x, 148 Seiten Illustrationen 21 cm, 188 g"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.833	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
c1d3be12-ecec-4fab-9237-baf728575185	{"id": "c1d3be12-ecec-4fab-9237-baf728575185", "hrid": "inst000000000009", "notes": [{"note": "Cities are sites of great wealth and poverty, of hope and despair, of social and economic dynamism, as well as tradition and established power. Social scientists and humanities scholars have over the past three decades generated an impressive range of perspectives for making sense of the vast complexities of cities. These perspectives tell both of the economic, social and political dynamism cities generate, and point to possible lines of future development. The four volumes, The City: Post-Modernity, will focus more exclusively on the contemporary city, looking at the subject through the lenses of globalization and post-colonialism, amongst others", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "The city post-modernity edited by Alan Latham", "series": ["SAGE benchmarks in culture and society"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.863Z", "updatedDate": "2022-06-23T15:14:00.863Z"}, "subjects": ["Stadt", "Postmoderne", "Aufsatzsammlung"], "languages": ["eng"], "identifiers": [{"value": "1010770160", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "9781473937703", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "(DE-599)GBV1010770160", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Los Angeles London New Delhi Singapore Washington DC Melbourne", "publisher": "SAGE", "dateOfPublication": "2018"}], "contributors": [{"name": "Latham, Alan", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "H", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}, {"classificationNumber": "300", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2018}, "statusUpdatedDate": "2022-06-23T15:14:00.863+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.863	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
cf23adf0-61ba-4887-bf82-956c4aae2260	{"id": "cf23adf0-61ba-4887-bf82-956c4aae2260", "hrid": "inst000000000024", "notes": [], "title": "Temeraire", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.902Z", "updatedDate": "2022-06-23T15:14:00.902Z"}, "subjects": [], "languages": [], "identifiers": [{"value": "1447294130", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9781447294130", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}], "publication": [], "contributors": [{"name": "Novik, Naomi", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.902+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.902	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
a89eccf0-57a6-495e-898d-32b9b2210f2f	{"id": "a89eccf0-57a6-495e-898d-32b9b2210f2f", "hrid": "inst000000000017", "notes": [], "title": "Interesting Times", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.913Z", "updatedDate": "2022-06-23T15:14:00.913Z"}, "subjects": [], "languages": [], "identifiers": [{"value": "0552142352", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9780552142352", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}], "publication": [], "contributors": [{"name": "Pratchett, Terry", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.913+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.913	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
1640f178-f243-4e4a-bf1c-9e1e62b3171d	{"id": "1640f178-f243-4e4a-bf1c-9e1e62b3171d", "hrid": "inst000000000005", "notes": [{"note": "Enthält 9 Beiträge", "staffOnly": false, "instanceNoteTypeId": "6a2533a7-4de2-4e64-8466-074c2fa9308c"}], "title": "Futures, biometrics and neuroscience research Luiz Moutinho, Mladen Sokele, editors", "series": ["Innovative research methodologies in management  / Luiz Moutinho, Mladen Sokele ; Volume 2", "Innovative research methodologies in management Volume 2"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.778Z", "updatedDate": "2022-06-23T15:14:00.778Z"}, "subjects": ["Betriebswirtschaftslehre", "Management", "Wissenschaftliche Methode"], "languages": ["eng"], "identifiers": [{"value": "101073931X", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "3319643991", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9783319643991", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "9783319644004", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "(OCoLC)ocn992783736", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(OCoLC)992783736", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-599)GBV101073931X", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Cham", "publisher": "Palgrave Macmillan", "dateOfPublication": "[2018]"}], "contributors": [{"name": "Moutinho, Luiz", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Sokele, Mladen", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [{"uri": "http://www.gbv.de/dms/zbw/101073931X.pdf", "linkText": "Electronic resource (PDF)", "publicNote": "Address for accessing the table of content. PDF file"}], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2018}, "statusUpdatedDate": "2022-06-23T15:14:00.779+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["xxix, 224 Seiten Illustrationen"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.778	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
f7e82a1e-fc06-4b82-bb1d-da326cb378ce	{"id": "f7e82a1e-fc06-4b82-bb1d-da326cb378ce", "hrid": "inst000000000013", "notes": [], "title": "Global Africa", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.810Z", "updatedDate": "2022-06-23T15:14:00.810Z"}, "subjects": ["Monografische Reihe"], "languages": ["eng"], "identifiers": [{"value": "(OCoLC)981117973", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(DE-599)ZDB2905315-8", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "London New York", "publisher": "Routledge, Taylor & Francis Group", "dateOfPublication": "2017-"}], "contributors": [], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "300", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 2017}, "statusUpdatedDate": "2022-06-23T15:14:00.811+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["Bände"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.81	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
1b74ab75-9f41-4837-8662-a1d99118008d	{"id": "1b74ab75-9f41-4837-8662-a1d99118008d", "hrid": "inst000000000018", "notes": [], "title": "A journey through Europe Bildtontraeger high-speed lines European Commission, Directorate-General for Mobility and Transport", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.848Z", "updatedDate": "2022-06-23T15:14:00.848Z"}, "subjects": ["Europäische Union", "Hochgeschwindigkeitszug", "Verkehrsnetz", "Hochgeschwindigkeitsverkehr", "Schienenverkehr", "EU-Verkehrspolitik", "EU-Staaten"], "languages": ["ger", "eng", "spa", "fre", "ita", "dut", "por"], "identifiers": [{"value": "643935371", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "9789279164316", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "10.2768/21035", "identifierTypeId": "2e8b3b6c-0e7d-4e48-bca2-b0b23b376af5"}, {"value": "MI-32-10-386-57-Z", "identifierTypeId": "b5d8cdc4-9441-487c-90cf-0c7ec97728eb"}, {"value": "(DE-599)GBV643935371", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [], "contributors": [{"name": "Europäische Kommission Generaldirektion Mobilität und Verkehr", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}], "instanceTypeId": "225faa14-f9bf-4ecd-990d-69433c912434", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.849+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["1 DVD-Video (14 Min.) farb. 12 cm"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.848	\N	\N	\N	225faa14-f9bf-4ecd-990d-69433c912434
54cc0262-76df-4cac-acca-b10e9bc5c79a	{"id": "54cc0262-76df-4cac-acca-b10e9bc5c79a", "hrid": "inst000000000023", "notes": [], "title": "On the signature of complex system a decomposed approach Gaofeng Da, Ping Shing Chan, Maochao Xu", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.898Z", "updatedDate": "2022-06-23T15:14:00.898Z"}, "subjects": [], "languages": ["eng"], "identifiers": [{"value": "1011184508", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "(DE-599)GBV1011184508", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [], "contributors": [{"name": "Da, Gaofeng", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Chan, Ping Shing", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Xu, Maochao", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.899+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.898	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
00f10ab9-d845-4334-92d2-ff55862bf4f9	{"id": "00f10ab9-d845-4334-92d2-ff55862bf4f9", "hrid": "inst000000000002", "notes": [], "title": "American Bar Association journal.", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.901Z", "updatedDate": "2022-06-23T15:14:00.901Z"}, "subjects": ["Law--United States--Periodicals", "Advocatuur.--gtt", "Droit--Périodiques", "LAW--unbist", "LAWYERS--unbist", "UNITED STATES--unbist", "Law.--fast", "United States.--fast"], "languages": ["eng"], "indexTitle": "American Bar Association journal.", "identifiers": [{"value": "15017355", "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"}, {"value": "1964851", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "236213576", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "0002-7596", "identifierTypeId": "913300b2-03ed-469a-8179-c1092c991227"}, {"value": "0002-7596", "identifierTypeId": "913300b2-03ed-469a-8179-c1092c991227"}, {"value": "(ICU)BID9651294", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(OCoLC)1479565", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(OCoLC)1964851", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "(OCoLC)236213576", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}, {"value": "2363771", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [{"place": "Chicago, Ill.", "publisher": "American Bar Association"}], "contributors": [{"name": "American Bar Association", "contributorTypeId": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "contributorTypeText": "", "contributorNameTypeId": "d376e36c-b759-4fed-8502-7130d1eeff39"}, {"name": "American Bar Association. Journal", "contributorTypeId": "06b2cbd8-66bf-4956-9d90-97c9776365a4", "contributorTypeText": "", "contributorNameTypeId": "d376e36c-b759-4fed-8502-7130d1eeff39"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [{"classificationNumber": "K1 .M385", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}, {"classificationNumber": "KB1 .A437", "classificationTypeId": "ce176ace-a53e-4b4d-aa89-725ed7b2edac"}, {"classificationNumber": "347.05 A512", "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b"}], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": ["Began with vol. 1, no. 1 (Jan. 1915); ceased with v. 69, [no.12] (Dec. 1983)"], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": ["volume nc rdacarrier"], "statusUpdatedDate": "2022-06-23T15:14:00.901+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["69 v. : ill. ; 23-30 cm."], "publicationFrequency": ["Monthly, 1921-83", "Quarterly, 1915-20"], "natureOfContentTermIds": []}	2022-06-23 15:14:00.901	\N	\N	\N	6312d172-f0cf-40f6-b27d-9fa8feaf332f
85010f04-b914-4ac7-ba30-be2b52f79708	{"id": "85010f04-b914-4ac7-ba30-be2b52f79708", "hrid": "bwinst0002", "tags": {"tagList": []}, "notes": [], "title": "Metod att beräkna en index för landets vattenkrafttillgång / av Åke Rusck och Gösta Nilsson", "series": ["Svenska Vattenkraftforeningens Publikationer ; 352 (1942:11)"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:01.271Z", "updatedDate": "2022-06-23T15:14:01.271Z"}, "statusId": "9634a5ab-9228-4703-baf2-4d12ebc77d56", "subjects": [], "languages": ["swe"], "identifiers": [{"value": "836918598", "identifierTypeId": "439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef"}], "publication": [{"place": "Stockholm", "publisher": "Svenska Vattenkraftföreningen", "dateOfPublication": "1942"}], "contributors": [{"name": "Rusck, Åke", "primary": true, "contributorTypeId": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "contributorTypeText": "", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Nilsson, Gösta", "primary": false, "contributorTypeId": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "contributorTypeText": "", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "modeOfIssuanceId": "9d18a02f-5897-4c31-9106-c9abb5c7ae8b", "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 1942}, "statusUpdatedDate": "2022-06-23T15:14:01.272+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["17 p. ; illustrations"], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:01.271	\N	\N	9d18a02f-5897-4c31-9106-c9abb5c7ae8b	6312d172-f0cf-40f6-b27d-9fa8feaf332f
6b4ae089-e1ee-431f-af83-e1133f8e3da0	{"id": "6b4ae089-e1ee-431f-af83-e1133f8e3da0", "hrid": "inst000000000019", "notes": [], "title": "MobiCom'17 5 mmNets'17, October 16, 2017, Snowbird, UT, USA / general chairs: Haitham Hassanieh (University of Illinois at Urbana Champaign, USA), Xinyu Zhang (University of California San Diego, USA)", "series": [], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:00.910Z", "updatedDate": "2022-06-23T15:14:00.910Z"}, "subjects": [], "languages": ["eng"], "identifiers": [{"value": "1011273942", "identifierTypeId": "5d164f4b-0b15-4e42-ae75-cfcf85318ad9"}, {"value": "9781450351430", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}, {"value": "(DE-599)GBV1011273942", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"}], "publication": [], "contributors": [{"name": "ACM Workshop on Millimeter Wave Networks and Sensing Systems 1. 2017 Snowbird, Utah", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}, {"name": "Hassanieh, Haitham", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "Zhang, Xinyu", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}, {"name": "MobiCom 23. 2017 Snowbird, Utah", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}, {"name": "Association for Computing Machinery Special Interest Group on Mobility of Systems Users, Data, and Computing", "contributorNameTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210aa"}, {"name": "ACM Workshop on Millimeter Wave Networks and Sensing Systems 1 2017.10.16 Snowbird, Utah", "contributorNameTypeId": "e8b311a6-3b21-43f2-a269-dd9310cb2d0a"}, {"name": "mmNets 1 2017.10.16 Snowbird, Utah", "contributorNameTypeId": "e8b311a6-3b21-43f2-a269-dd9310cb2d0a"}, {"name": "Annual International Conference on Mobile Computing and Networking (ACM MobiCom) 23 2017.10.16-20 Snowbird, Utah", "contributorNameTypeId": "e8b311a6-3b21-43f2-a269-dd9310cb2d0a"}], "instanceTypeId": "a2c91e87-6bab-44d6-8adb-1fd02481fc4f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "publicationRange": [], "alternativeTitles": [{"alternativeTitle": "1st First ACM Workshop Millimeter Wave Networks Sensing Systems"}], "discoverySuppress": false, "instanceFormatIds": [], "statusUpdatedDate": "2022-06-23T15:14:00.910+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": [], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:00.91	\N	\N	\N	a2c91e87-6bab-44d6-8adb-1fd02481fc4f
cd3288a4-898c-4347-a003-2d810ef70f03	{"id": "cd3288a4-898c-4347-a003-2d810ef70f03", "hrid": "bwinst0003", "tags": {"tagList": []}, "notes": [], "title": "Elpannan och dess ekonomiska förutsättningar / av Hakon Wærn", "series": ["Svenska Vattenkraftforeningens Publikationer ; 351 (1942:10)"], "source": "FOLIO", "_version": 1, "editions": [], "metadata": {"createdDate": "2022-06-23T15:14:01.268Z", "updatedDate": "2022-06-23T15:14:01.268Z"}, "statusId": "9634a5ab-9228-4703-baf2-4d12ebc77d56", "subjects": [], "languages": ["swe"], "identifiers": [{"value": "255752480", "identifierTypeId": "439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef"}], "publication": [{"place": "Stockholm", "publisher": "Svenska Vattenkraftföreningen", "dateOfPublication": "1942"}], "contributors": [{"name": "Wærn, Hakon", "primary": true, "contributorTypeId": "6e09d47d-95e2-4d8a-831b-f777b8ef6d81", "contributorTypeText": "", "contributorNameTypeId": "2b94c631-fca9-4892-a730-03ee529ffe2a"}], "instanceTypeId": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "previouslyHeld": false, "classifications": [], "instanceFormats": [], "electronicAccess": [], "holdingsRecords2": [], "modeOfIssuanceId": "9d18a02f-5897-4c31-9106-c9abb5c7ae8b", "publicationRange": [], "alternativeTitles": [], "discoverySuppress": false, "instanceFormatIds": [], "publicationPeriod": {"start": 1942}, "statusUpdatedDate": "2022-06-23T15:14:01.268+0000", "statisticalCodeIds": [], "administrativeNotes": [], "physicalDescriptions": ["23 p."], "publicationFrequency": [], "natureOfContentTermIds": []}	2022-06-23 15:14:01.268	\N	\N	9d18a02f-5897-4c31-9106-c9abb5c7ae8b	6312d172-f0cf-40f6-b27d-9fa8feaf332f
\.


--
-- Data for Name: instance_format; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance_format (id, jsonb) FROM stdin;
affd5809-2897-42ca-b958-b311f3e0dcfb	{"id": "affd5809-2897-42ca-b958-b311f3e0dcfb", "code": "nn", "name": "unmediated -- flipchart", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.438+00:00", "updatedDate": "2022-06-23T15:13:59.438+00:00"}}
68e7e339-f35c-4be2-b161-0b94d7569b7b	{"id": "68e7e339-f35c-4be2-b161-0b94d7569b7b", "code": "na", "name": "unmediated -- roll", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.439+00:00", "updatedDate": "2022-06-23T15:13:59.439+00:00"}}
5cb91d15-96b1-4b8a-bf60-ec310538da66	{"id": "5cb91d15-96b1-4b8a-bf60-ec310538da66", "code": "sd", "name": "audio -- audio disc", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.439+00:00", "updatedDate": "2022-06-23T15:13:59.439+00:00"}}
6bf2154b-df6e-4f11-97d0-6541231ac2be	{"id": "6bf2154b-df6e-4f11-97d0-6541231ac2be", "code": "mc", "name": "projected image -- film cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.443+00:00", "updatedDate": "2022-06-23T15:13:59.443+00:00"}}
b71e5ec6-a15d-4261-baf9-aea6be7af15b	{"id": "b71e5ec6-a15d-4261-baf9-aea6be7af15b", "code": "hc", "name": "microform -- microfilm cassette", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.444+00:00", "updatedDate": "2022-06-23T15:13:59.444+00:00"}}
8e04d356-2645-4f97-8de8-9721cf11ccef	{"id": "8e04d356-2645-4f97-8de8-9721cf11ccef", "code": "gf", "name": "projected image -- filmstrip", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.451+00:00", "updatedDate": "2022-06-23T15:13:59.451+00:00"}}
0d9b1c3d-2d13-4f18-9472-cc1b91bf1752	{"id": "0d9b1c3d-2d13-4f18-9472-cc1b91bf1752", "code": "sb", "name": "audio -- audio belt", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.454+00:00", "updatedDate": "2022-06-23T15:13:59.454+00:00"}}
7fde4e21-00b5-4de4-a90a-08a84a601aeb	{"id": "7fde4e21-00b5-4de4-a90a-08a84a601aeb", "code": "sq", "name": "audio -- audio roll", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.454+00:00", "updatedDate": "2022-06-23T15:13:59.454+00:00"}}
a3549b8c-3282-4a14-9ec3-c1cf294043b9	{"id": "a3549b8c-3282-4a14-9ec3-c1cf294043b9", "code": "sz", "name": "audio -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.455+00:00", "updatedDate": "2022-06-23T15:13:59.455+00:00"}}
c3f41d5e-e192-4828-805c-6df3270c1910	{"id": "c3f41d5e-e192-4828-805c-6df3270c1910", "code": "es", "name": "stereographic -- stereograph disc", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.459+00:00", "updatedDate": "2022-06-23T15:13:59.459+00:00"}}
2802b285-9f27-4c86-a9d7-d2ac08b26a79	{"id": "2802b285-9f27-4c86-a9d7-d2ac08b26a79", "code": "nz", "name": "unmediated -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.466+00:00", "updatedDate": "2022-06-23T15:13:59.466+00:00"}}
e05f2613-05df-4b4d-9292-2ee9aa778ecc	{"id": "e05f2613-05df-4b4d-9292-2ee9aa778ecc", "code": "ce", "name": "computer -- computer disc cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.468+00:00", "updatedDate": "2022-06-23T15:13:59.468+00:00"}}
7612aa96-61a6-41bd-8ed2-ff1688e794e1	{"id": "7612aa96-61a6-41bd-8ed2-ff1688e794e1", "code": "st", "name": "audio -- audiotape reel", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.471+00:00", "updatedDate": "2022-06-23T15:13:59.471+00:00"}}
e62f4860-b3b0-462e-92b6-e032336ab663	{"id": "e62f4860-b3b0-462e-92b6-e032336ab663", "code": "eh", "name": "stereographic -- stereograph card", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.473+00:00", "updatedDate": "2022-06-23T15:13:59.473+00:00"}}
788aa9a6-5f0b-4c52-957b-998266ee3bd3	{"id": "788aa9a6-5f0b-4c52-957b-998266ee3bd3", "code": "hg", "name": "microform -- microopaque", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.472+00:00", "updatedDate": "2022-06-23T15:13:59.472+00:00"}}
132d70db-53b3-4999-bd79-0fac3b8b9b98	{"id": "132d70db-53b3-4999-bd79-0fac3b8b9b98", "code": "vc", "name": "video -- video cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.478+00:00", "updatedDate": "2022-06-23T15:13:59.478+00:00"}}
ba0d7429-7ccf-419d-8bfb-e6a1200a8d20	{"id": "ba0d7429-7ccf-419d-8bfb-e6a1200a8d20", "code": "vr", "name": "video -- videotape reel", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.480+00:00", "updatedDate": "2022-06-23T15:13:59.480+00:00"}}
926662e9-2486-4bb9-ba3b-59bd2e7f2a0c	{"id": "926662e9-2486-4bb9-ba3b-59bd2e7f2a0c", "code": "nr", "name": "unmediated -- object", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.484+00:00", "updatedDate": "2022-06-23T15:13:59.484+00:00"}}
5642320a-2ab9-475c-8ca2-4af7551cf296	{"id": "5642320a-2ab9-475c-8ca2-4af7551cf296", "code": "sg", "name": "audio -- audio cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.488+00:00", "updatedDate": "2022-06-23T15:13:59.488+00:00"}, "readOnly": true}
7c9b361d-66b6-4e4c-ae4b-2c01f655612c	{"id": "7c9b361d-66b6-4e4c-ae4b-2c01f655612c", "code": "ez", "name": "stereographic -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.492+00:00", "updatedDate": "2022-06-23T15:13:59.492+00:00"}}
98f0caa9-d38e-427b-9ec4-454de81a94d7	{"id": "98f0caa9-d38e-427b-9ec4-454de81a94d7", "code": "zu", "name": "unspecified -- unspecified", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.495+00:00", "updatedDate": "2022-06-23T15:13:59.495+00:00"}}
55d3b8aa-304e-4967-8b78-55926d7809ac	{"id": "55d3b8aa-304e-4967-8b78-55926d7809ac", "code": "pz", "name": "microscopic -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.496+00:00", "updatedDate": "2022-06-23T15:13:59.496+00:00"}}
fe1b9adb-e0cf-4e05-905f-ce9986279404	{"id": "fe1b9adb-e0cf-4e05-905f-ce9986279404", "code": "cz", "name": "computer -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.500+00:00", "updatedDate": "2022-06-23T15:13:59.500+00:00"}}
f0e689e8-e62d-4aac-b1c1-198ac9114aca	{"id": "f0e689e8-e62d-4aac-b1c1-198ac9114aca", "code": "mo", "name": "projected image -- film roll", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.501+00:00", "updatedDate": "2022-06-23T15:13:59.501+00:00"}}
b72e66e2-d946-4b01-a696-8fab07051ff8	{"id": "b72e66e2-d946-4b01-a696-8fab07051ff8", "code": "hf", "name": "microform -- microfiche cassette", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.505+00:00", "updatedDate": "2022-06-23T15:13:59.505+00:00"}}
549e3381-7d49-44f6-8232-37af1cb5ecf3	{"id": "549e3381-7d49-44f6-8232-37af1cb5ecf3", "code": "ck", "name": "computer -- computer card", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.507+00:00", "updatedDate": "2022-06-23T15:13:59.507+00:00"}}
431cc9a0-4572-4613-b267-befb0f3d457f	{"id": "431cc9a0-4572-4613-b267-befb0f3d457f", "code": "vf", "name": "video -- videocassette", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.506+00:00", "updatedDate": "2022-06-23T15:13:59.506+00:00"}}
88f58dc0-4243-4c6b-8321-70244ff34a83	{"id": "88f58dc0-4243-4c6b-8321-70244ff34a83", "code": "cb", "name": "computer -- computer chip cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.512+00:00", "updatedDate": "2022-06-23T15:13:59.512+00:00"}}
33009ba2-b742-4aab-b592-68b27451e94f	{"id": "33009ba2-b742-4aab-b592-68b27451e94f", "code": "hh", "name": "microform -- microfilm slip", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.521+00:00", "updatedDate": "2022-06-23T15:13:59.521+00:00"}}
cb3004a3-2a85-4ed4-8084-409f93d6d8ba	{"id": "cb3004a3-2a85-4ed4-8084-409f93d6d8ba", "code": "ha", "name": "microform -- aperture card", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.531+00:00", "updatedDate": "2022-06-23T15:13:59.531+00:00"}}
55a66581-3921-4b50-9981-4fe53bf35e7f	{"id": "55a66581-3921-4b50-9981-4fe53bf35e7f", "code": "mr", "name": "projected image -- film reel", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.538+00:00", "updatedDate": "2022-06-23T15:13:59.538+00:00"}}
f5e8210f-7640-459b-a71f-552567f92369	{"id": "f5e8210f-7640-459b-a71f-552567f92369", "code": "cr", "name": "computer -- online resource", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.547+00:00", "updatedDate": "2022-06-23T15:13:59.547+00:00"}}
eb860cea-b842-4a8b-ab8d-0739856f0c2c	{"id": "eb860cea-b842-4a8b-ab8d-0739856f0c2c", "code": "gt", "name": "projected image -- overhead transparency", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.559+00:00", "updatedDate": "2022-06-23T15:13:59.559+00:00"}}
8d511d33-5e85-4c5d-9bce-6e3c9cd0c324	{"id": "8d511d33-5e85-4c5d-9bce-6e3c9cd0c324", "code": "nc", "name": "unmediated -- volume", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.572+00:00", "updatedDate": "2022-06-23T15:13:59.572+00:00"}}
f4f30334-568b-4dd2-88b5-db8401607daf	{"id": "f4f30334-568b-4dd2-88b5-db8401607daf", "code": "ca", "name": "computer -- computer tape cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.580+00:00", "updatedDate": "2022-06-23T15:13:59.580+00:00"}}
f7107ab3-9c09-4bcb-a637-368f39e0b140	{"id": "f7107ab3-9c09-4bcb-a637-368f39e0b140", "code": "gc", "name": "projected image -- filmstrip cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.513+00:00", "updatedDate": "2022-06-23T15:13:59.513+00:00"}}
b2b39d2f-856b-4419-93d3-ed1851f91b9f	{"id": "b2b39d2f-856b-4419-93d3-ed1851f91b9f", "code": "gs", "name": "projected image -- slide", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.527+00:00", "updatedDate": "2022-06-23T15:13:59.527+00:00"}}
e3179f91-3032-43ee-be97-f0464f359d9c	{"id": "e3179f91-3032-43ee-be97-f0464f359d9c", "code": "vz", "name": "video -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.534+00:00", "updatedDate": "2022-06-23T15:13:59.534+00:00"}}
7f857834-b2e2-48b1-8528-6a1fe89bf979	{"id": "7f857834-b2e2-48b1-8528-6a1fe89bf979", "code": "vd", "name": "video -- videodisc", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.542+00:00", "updatedDate": "2022-06-23T15:13:59.542+00:00"}}
5fa3e09f-2192-41a9-b4bf-9eb8aef0af0a	{"id": "5fa3e09f-2192-41a9-b4bf-9eb8aef0af0a", "code": "no", "name": "unmediated -- card", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.549+00:00", "updatedDate": "2022-06-23T15:13:59.549+00:00"}}
47b226c0-853c-40f4-ba2e-2bd5ba82b665	{"id": "47b226c0-853c-40f4-ba2e-2bd5ba82b665", "code": "mf", "name": "projected image -- film cassette", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.556+00:00", "updatedDate": "2022-06-23T15:13:59.556+00:00"}}
b1c69d78-4afb-4d8b-9624-8b3cfa5288ad	{"id": "b1c69d78-4afb-4d8b-9624-8b3cfa5288ad", "code": "pp", "name": "microscopic -- microscope slide", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.566+00:00", "updatedDate": "2022-06-23T15:13:59.566+00:00"}}
cb96199a-21fb-4f11-b003-99291d8c9752	{"id": "cb96199a-21fb-4f11-b003-99291d8c9752", "code": "hj", "name": "microform -- microfilm roll", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.575+00:00", "updatedDate": "2022-06-23T15:13:59.575+00:00"}}
fc3e32a0-9c85-4454-a42e-39fca788a7dc	{"id": "fc3e32a0-9c85-4454-a42e-39fca788a7dc", "code": "he", "name": "microform -- microfiche", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.586+00:00", "updatedDate": "2022-06-23T15:13:59.586+00:00"}}
9166e7c9-7edb-4180-b57e-e495f551297f	{"id": "9166e7c9-7edb-4180-b57e-e495f551297f", "code": "mz", "name": "projected image -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.516+00:00", "updatedDate": "2022-06-23T15:13:59.516+00:00"}}
a0f2612b-f24f-4dc8-a139-89c3da5a38f1	{"id": "a0f2612b-f24f-4dc8-a139-89c3da5a38f1", "code": "hz", "name": "microform -- other", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.532+00:00", "updatedDate": "2022-06-23T15:13:59.532+00:00"}}
d16b19d1-507f-4a22-bb8a-b3f713a73221	{"id": "d16b19d1-507f-4a22-bb8a-b3f713a73221", "code": "ch", "name": "computer -- computer tape reel", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.545+00:00", "updatedDate": "2022-06-23T15:13:59.545+00:00"}}
485e3e1d-9f46-42b6-8c65-6bb7bd4b37f8	{"id": "485e3e1d-9f46-42b6-8c65-6bb7bd4b37f8", "code": "se", "name": "audio -- audio cylinder", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.562+00:00", "updatedDate": "2022-06-23T15:13:59.562+00:00"}}
53f44ae4-167b-4cc2-9a63-4375c0ad9f58	{"id": "53f44ae4-167b-4cc2-9a63-4375c0ad9f58", "code": "gd", "name": "projected image -- filmslip", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.579+00:00", "updatedDate": "2022-06-23T15:13:59.579+00:00"}}
6a679992-b37e-4b57-b6ea-96be6b51d2b4	{"id": "6a679992-b37e-4b57-b6ea-96be6b51d2b4", "code": "sw", "name": "audio -- audio wire reel", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.520+00:00", "updatedDate": "2022-06-23T15:13:59.520+00:00"}}
6d749f00-97bd-4eab-9828-57167558f514	{"id": "6d749f00-97bd-4eab-9828-57167558f514", "code": "ss", "name": "audio -- audiocassette", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.528+00:00", "updatedDate": "2022-06-23T15:13:59.528+00:00"}}
ac9de2b9-0914-4a54-8805-463686a5489e	{"id": "ac9de2b9-0914-4a54-8805-463686a5489e", "code": "cd", "name": "computer -- computer disc", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.544+00:00", "updatedDate": "2022-06-23T15:13:59.544+00:00"}}
5913bb96-e881-4087-9e71-33a43f68e12e	{"id": "5913bb96-e881-4087-9e71-33a43f68e12e", "code": "nb", "name": "unmediated -- sheet", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.553+00:00", "updatedDate": "2022-06-23T15:13:59.553+00:00"}}
e5aeb29a-cf0a-4d97-8c39-7756c10d423c	{"id": "e5aeb29a-cf0a-4d97-8c39-7756c10d423c", "code": "cf", "name": "computer -- computer tape cassette", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.561+00:00", "updatedDate": "2022-06-23T15:13:59.561+00:00"}}
fc9bfed9-2cb0-465f-8758-33af5bba750b	{"id": "fc9bfed9-2cb0-465f-8758-33af5bba750b", "code": "hb", "name": "microform -- microfilm cartridge", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.567+00:00", "updatedDate": "2022-06-23T15:13:59.567+00:00"}}
7bfe7e83-d4aa-46d1-b2a9-f612b18d11f4	{"id": "7bfe7e83-d4aa-46d1-b2a9-f612b18d11f4", "code": "hd", "name": "microform -- microfilm reel", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.576+00:00", "updatedDate": "2022-06-23T15:13:59.576+00:00"}}
5bfb7b4f-9cd5-4577-a364-f95352146a56	{"id": "5bfb7b4f-9cd5-4577-a364-f95352146a56", "code": "si", "name": "audio -- sound track reel", "source": "rdacarrier", "metadata": {"createdDate": "2022-06-23T15:13:59.583+00:00", "updatedDate": "2022-06-23T15:13:59.583+00:00"}}
\.


--
-- Data for Name: instance_note_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance_note_type (id, jsonb, creation_date, created_by) FROM stdin;
e8cdc2fe-c53c-478a-a7f3-47f2fc79c6d4	{"id": "e8cdc2fe-c53c-478a-a7f3-47f2fc79c6d4", "name": "Awards note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.196Z", "updatedDate": "2022-06-23T15:14:00.196Z"}}	2022-06-23 15:14:00.196	\N
5ba8e385-0e27-462e-a571-ffa1fa34ea54	{"id": "5ba8e385-0e27-462e-a571-ffa1fa34ea54", "name": "Formatted Contents Note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.199Z", "updatedDate": "2022-06-23T15:14:00.199Z"}}	2022-06-23 15:14:00.199	\N
7356cde5-ec6b-4961-9cb0-961c48a37af4	{"id": "7356cde5-ec6b-4961-9cb0-961c48a37af4", "name": "Language note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.202Z", "updatedDate": "2022-06-23T15:14:00.202Z"}}	2022-06-23 15:14:00.202	\N
b73cc9c2-c9fa-49aa-964f-5ae1aa754ecd	{"id": "b73cc9c2-c9fa-49aa-964f-5ae1aa754ecd", "name": "Dissertation note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.200Z", "updatedDate": "2022-06-23T15:14:00.200Z"}}	2022-06-23 15:14:00.2	\N
f939b820-4a23-43d1-84ba-101add6e1456	{"id": "f939b820-4a23-43d1-84ba-101add6e1456", "name": "Type of report and period covered note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.200Z", "updatedDate": "2022-06-23T15:14:00.200Z"}}	2022-06-23 15:14:00.2	\N
806cb024-80d2-47c2-8bbf-b91091c85f68	{"id": "806cb024-80d2-47c2-8bbf-b91091c85f68", "name": "Former Title Complexity note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.204Z", "updatedDate": "2022-06-23T15:14:00.204Z"}}	2022-06-23 15:14:00.204	\N
86b6e817-e1bc-42fb-bab0-70e7547de6c1	{"id": "86b6e817-e1bc-42fb-bab0-70e7547de6c1", "name": "Bibliography note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.210Z", "updatedDate": "2022-06-23T15:14:00.210Z"}}	2022-06-23 15:14:00.21	\N
d548fdff-b71c-4359-8055-f1c008c30f01	{"id": "d548fdff-b71c-4359-8055-f1c008c30f01", "name": "Reproduction note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.212Z", "updatedDate": "2022-06-23T15:14:00.212Z"}}	2022-06-23 15:14:00.212	\N
6a2533a7-4de2-4e64-8466-074c2fa9308c	{"id": "6a2533a7-4de2-4e64-8466-074c2fa9308c", "name": "General note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.213Z", "updatedDate": "2022-06-23T15:14:00.213Z"}}	2022-06-23 15:14:00.213	\N
9b56b954-7f3b-4e4b-8ed0-cf40aef13975	{"id": "9b56b954-7f3b-4e4b-8ed0-cf40aef13975", "name": "Participant or Performer note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.216Z", "updatedDate": "2022-06-23T15:14:00.216Z"}}	2022-06-23 15:14:00.216	\N
1d51e8b2-dee7-43f5-983c-a40757b9cdfa	{"id": "1d51e8b2-dee7-43f5-983c-a40757b9cdfa", "name": "Additional Physical Form Available note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.214Z", "updatedDate": "2022-06-23T15:14:00.214Z"}}	2022-06-23 15:14:00.214	\N
66ea8f28-d5da-426a-a7c9-739a5d676347	{"id": "66ea8f28-d5da-426a-a7c9-739a5d676347", "name": "Source of Description note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.218Z", "updatedDate": "2022-06-23T15:14:00.218Z"}}	2022-06-23 15:14:00.218	\N
e814a32e-02da-4773-8f3a-6629cdb7ecdf	{"id": "e814a32e-02da-4773-8f3a-6629cdb7ecdf", "name": "Restrictions on Access note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.220Z", "updatedDate": "2022-06-23T15:14:00.220Z"}}	2022-06-23 15:14:00.22	\N
43295b78-3bfa-4c28-bc7f-8d924f63493f	{"id": "43295b78-3bfa-4c28-bc7f-8d924f63493f", "name": "Date / time and place of an event note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.225Z", "updatedDate": "2022-06-23T15:14:00.225Z"}}	2022-06-23 15:14:00.225	\N
265c4910-3997-4242-9269-6a4a2e91392b	{"id": "265c4910-3997-4242-9269-6a4a2e91392b", "name": "Local notes", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.223Z", "updatedDate": "2022-06-23T15:14:00.223Z"}}	2022-06-23 15:14:00.223	\N
06489647-c7b7-4b6c-878a-cb7c1178e9ca	{"id": "06489647-c7b7-4b6c-878a-cb7c1178e9ca", "name": "Study Program Information note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.225Z", "updatedDate": "2022-06-23T15:14:00.225Z"}}	2022-06-23 15:14:00.225	\N
49475f04-35ef-4f8a-aa7f-92773594ca76	{"id": "49475f04-35ef-4f8a-aa7f-92773594ca76", "name": "Issuing Body note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.229Z", "updatedDate": "2022-06-23T15:14:00.229Z"}}	2022-06-23 15:14:00.229	\N
ec9f8285-6bf9-4e6c-a3cb-38ef17f0317f	{"id": "ec9f8285-6bf9-4e6c-a3cb-38ef17f0317f", "name": "Copy and Version Identification note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.228Z", "updatedDate": "2022-06-23T15:14:00.228Z"}}	2022-06-23 15:14:00.228	\N
95f62ca7-5df5-4a51-9890-d0ec3a34665f	{"id": "95f62ca7-5df5-4a51-9890-d0ec3a34665f", "name": "System Details note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.232Z", "updatedDate": "2022-06-23T15:14:00.232Z"}}	2022-06-23 15:14:00.232	\N
cf635f41-29e7-4dd0-8598-33f230157074	{"id": "cf635f41-29e7-4dd0-8598-33f230157074", "name": "Numbering peculiarities note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.234Z", "updatedDate": "2022-06-23T15:14:00.234Z"}}	2022-06-23 15:14:00.234	\N
6f76f4e7-9c0b-4138-9371-09b36136372d	{"id": "6f76f4e7-9c0b-4138-9371-09b36136372d", "name": "Case File Characteristics note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.233Z", "updatedDate": "2022-06-23T15:14:00.233Z"}}	2022-06-23 15:14:00.233	\N
06b44741-888e-4b15-a75e-cb29e27752d1	{"id": "06b44741-888e-4b15-a75e-cb29e27752d1", "name": "With note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.243Z", "updatedDate": "2022-06-23T15:14:00.243Z"}}	2022-06-23 15:14:00.243	\N
13047c94-7d2c-4c41-9658-abacfa97a5c8	{"id": "13047c94-7d2c-4c41-9658-abacfa97a5c8", "name": "Information About Documentation note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.250Z", "updatedDate": "2022-06-23T15:14:00.250Z"}}	2022-06-23 15:14:00.25	\N
922fdcde-952d-45c2-b9ea-5fc8959ad116	{"id": "922fdcde-952d-45c2-b9ea-5fc8959ad116", "name": "Target Audience note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.247Z", "updatedDate": "2022-06-23T15:14:00.247Z"}}	2022-06-23 15:14:00.247	\N
f677d908-69c6-4450-94a6-abbcf94a1ee5	{"id": "f677d908-69c6-4450-94a6-abbcf94a1ee5", "name": "Terms Governing Use and Reproduction note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.251Z", "updatedDate": "2022-06-23T15:14:00.251Z"}}	2022-06-23 15:14:00.251	\N
42be8949-6f69-4c55-874b-60b744ac1103	{"id": "42be8949-6f69-4c55-874b-60b744ac1103", "name": "Original Version note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.256Z", "updatedDate": "2022-06-23T15:14:00.256Z"}}	2022-06-23 15:14:00.256	\N
7929eee7-6822-4199-8df4-bb2ae773e4cd	{"id": "7929eee7-6822-4199-8df4-bb2ae773e4cd", "name": "Data quality note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.254Z", "updatedDate": "2022-06-23T15:14:00.254Z"}}	2022-06-23 15:14:00.254	\N
654be0fd-bba2-4791-afa3-ae60300d7043	{"id": "654be0fd-bba2-4791-afa3-ae60300d7043", "name": "Information related to Copyright Status", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.263Z", "updatedDate": "2022-06-23T15:14:00.263Z"}}	2022-06-23 15:14:00.263	\N
fda2f2e3-965f-4220-8a2b-93d35ce6d582	{"id": "fda2f2e3-965f-4220-8a2b-93d35ce6d582", "name": "Cumulative Index / Finding Aides notes", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.261Z", "updatedDate": "2022-06-23T15:14:00.261Z"}}	2022-06-23 15:14:00.261	\N
1c017b8d-c783-4f63-b620-079f7a5b9c07	{"id": "1c017b8d-c783-4f63-b620-079f7a5b9c07", "name": "Action note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.269Z", "updatedDate": "2022-06-23T15:14:00.269Z"}}	2022-06-23 15:14:00.269	\N
02b5b0c6-3375-4912-ac75-ad9f552362b2	{"id": "02b5b0c6-3375-4912-ac75-ad9f552362b2", "name": "Methodology note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.275Z", "updatedDate": "2022-06-23T15:14:00.275Z"}}	2022-06-23 15:14:00.275	\N
72c611ab-f353-4c09-a0cc-33ff96cc3bef	{"id": "72c611ab-f353-4c09-a0cc-33ff96cc3bef", "name": "Scale note for graphic material", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.281Z", "updatedDate": "2022-06-23T15:14:00.281Z"}}	2022-06-23 15:14:00.281	\N
794f19f1-d00b-4b4b-97e9-0de5a34495a0	{"id": "794f19f1-d00b-4b4b-97e9-0de5a34495a0", "name": "Cartographic Mathematical Data", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.288Z", "updatedDate": "2022-06-23T15:14:00.288Z"}}	2022-06-23 15:14:00.288	\N
3d931c23-6ae8-4e5a-8802-dc8c2e21ea19	{"id": "3d931c23-6ae8-4e5a-8802-dc8c2e21ea19", "name": "Type of computer file or data note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.293Z", "updatedDate": "2022-06-23T15:14:00.293Z"}}	2022-06-23 15:14:00.293	\N
aecfda7a-e8aa-46d6-9046-9b0b8c231b85	{"id": "aecfda7a-e8aa-46d6-9046-9b0b8c231b85", "name": "Supplement note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.305Z", "updatedDate": "2022-06-23T15:14:00.305Z"}}	2022-06-23 15:14:00.305	\N
6ca9df3f-454d-4b5b-9d41-feb5d5030b99	{"id": "6ca9df3f-454d-4b5b-9d41-feb5d5030b99", "name": "Citation / References note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.310Z", "updatedDate": "2022-06-23T15:14:00.310Z"}}	2022-06-23 15:14:00.31	\N
10e2e11b-450f-45c8-b09b-0f819999966e	{"id": "10e2e11b-450f-45c8-b09b-0f819999966e", "name": "Summary", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.317Z", "updatedDate": "2022-06-23T15:14:00.317Z"}}	2022-06-23 15:14:00.317	\N
0ed2da88-3f81-42f5-b688-91b70919d9bb	{"id": "0ed2da88-3f81-42f5-b688-91b70919d9bb", "name": "Exhibitions note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.323Z", "updatedDate": "2022-06-23T15:14:00.323Z"}}	2022-06-23 15:14:00.323	\N
56cf513e-a738-40c5-a3ab-b0c60ba07e15	{"id": "56cf513e-a738-40c5-a3ab-b0c60ba07e15", "name": "Ownership and Custodial History note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.266Z", "updatedDate": "2022-06-23T15:14:00.266Z"}}	2022-06-23 15:14:00.266	\N
1cb8ac76-01fa-49be-8b9c-fcdaf17458a5	{"id": "1cb8ac76-01fa-49be-8b9c-fcdaf17458a5", "name": "Entity and Attribute Information note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.279Z", "updatedDate": "2022-06-23T15:14:00.279Z"}}	2022-06-23 15:14:00.279	\N
28e12ad3-4a8d-48cc-b56c-a5ded22fc844	{"id": "28e12ad3-4a8d-48cc-b56c-a5ded22fc844", "name": "Geographic Coverage note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.291Z", "updatedDate": "2022-06-23T15:14:00.291Z"}}	2022-06-23 15:14:00.291	\N
779c22a2-311c-4ebb-b71e-b246c7ee574d	{"id": "779c22a2-311c-4ebb-b71e-b246c7ee574d", "name": "Linking Entry Complexity note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.300Z", "updatedDate": "2022-06-23T15:14:00.300Z"}}	2022-06-23 15:14:00.3	\N
f289c02b-9515-4c3f-b242-ffd071e82135	{"id": "f289c02b-9515-4c3f-b242-ffd071e82135", "name": "Funding Information Note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.308Z", "updatedDate": "2022-06-23T15:14:00.308Z"}}	2022-06-23 15:14:00.308	\N
9a4b39f4-a7d5-4c4d-abc6-5ccf1fc1d78c	{"id": "9a4b39f4-a7d5-4c4d-abc6-5ccf1fc1d78c", "name": "Location of Other Archival Materials note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.315Z", "updatedDate": "2022-06-23T15:14:00.315Z"}}	2022-06-23 15:14:00.315	\N
09812302-92f7-497e-9120-ed25de458ea5	{"id": "09812302-92f7-497e-9120-ed25de458ea5", "name": "Preferred Citation of Described Materials note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.274Z", "updatedDate": "2022-06-23T15:14:00.274Z"}}	2022-06-23 15:14:00.274	\N
c6340b85-d048-426a-89aa-163cfb801a56	{"id": "c6340b85-d048-426a-89aa-163cfb801a56", "name": "Location of Originals / Duplicates note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.285Z", "updatedDate": "2022-06-23T15:14:00.285Z"}}	2022-06-23 15:14:00.285	\N
9f08c9b7-500a-43e0-b00f-ba02396b198f	{"id": "9f08c9b7-500a-43e0-b00f-ba02396b198f", "name": "Creation / Production Credits note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.295Z", "updatedDate": "2022-06-23T15:14:00.295Z"}}	2022-06-23 15:14:00.295	\N
86c4bd09-16de-45ee-89d3-b6d32fae6de9	{"id": "86c4bd09-16de-45ee-89d3-b6d32fae6de9", "name": "Immediate Source of Acquisition note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.306Z", "updatedDate": "2022-06-23T15:14:00.306Z"}}	2022-06-23 15:14:00.306	\N
c636881b-8927-4480-ad1b-8d7b27b4bbfe	{"id": "c636881b-8927-4480-ad1b-8d7b27b4bbfe", "name": "Biographical or Historical Data", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.313Z", "updatedDate": "2022-06-23T15:14:00.313Z"}}	2022-06-23 15:14:00.313	\N
0dc69a30-6d2b-40df-a50e-e4982bda86f4	{"id": "0dc69a30-6d2b-40df-a50e-e4982bda86f4", "name": "Binding Information note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.326Z", "updatedDate": "2022-06-23T15:14:00.326Z"}}	2022-06-23 15:14:00.326	\N
1c7acba3-523d-4237-acd2-e88549bfc660	{"id": "1c7acba3-523d-4237-acd2-e88549bfc660", "name": "Accumulation and Frequency of Use note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.297Z", "updatedDate": "2022-06-23T15:14:00.297Z"}}	2022-06-23 15:14:00.297	\N
a6a5550f-4981-4b48-b821-a57d5c8ca3b3	{"id": "a6a5550f-4981-4b48-b821-a57d5c8ca3b3", "name": "Accessibility note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.309Z", "updatedDate": "2022-06-23T15:14:00.309Z"}}	2022-06-23 15:14:00.309	\N
e0ea861c-959f-4912-8579-5e9ea8a69454	{"id": "e0ea861c-959f-4912-8579-5e9ea8a69454", "name": "Publications About Described Materials note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.318Z", "updatedDate": "2022-06-23T15:14:00.318Z"}}	2022-06-23 15:14:00.318	\N
\.


--
-- Data for Name: instance_relationship; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance_relationship (id, jsonb, creation_date, created_by, superinstanceid, subinstanceid, instancerelationshiptypeid) FROM stdin;
e5cea7b1-3c48-428c-bc5e-2efc9ead1924	{"id": "e5cea7b1-3c48-428c-bc5e-2efc9ead1924", "metadata": {"createdDate": "2022-06-23T15:14:01.227Z", "updatedDate": "2022-06-23T15:14:01.227Z"}, "subInstanceId": "04489a01-f3cd-4f9e-9be4-d9c198703f45", "superInstanceId": "f7e82a1e-fc06-4b82-bb1d-da326cb378ce", "instanceRelationshipTypeId": "30773a27-b485-4dab-aeb6-b8c04fa3cb17"}	2022-06-23 15:14:01.227	\N	f7e82a1e-fc06-4b82-bb1d-da326cb378ce	04489a01-f3cd-4f9e-9be4-d9c198703f45	30773a27-b485-4dab-aeb6-b8c04fa3cb17
1b449f40-5ae8-47df-9113-3c0a958b5ce8	{"id": "1b449f40-5ae8-47df-9113-3c0a958b5ce8", "metadata": {"createdDate": "2022-06-23T15:14:01.233Z", "updatedDate": "2022-06-23T15:14:01.233Z"}, "subInstanceId": "549fad9e-7f8e-4d8e-9a71-00d251817866", "superInstanceId": "a317b304-528c-424f-961c-39174933b454", "instanceRelationshipTypeId": "a17daf0a-f057-43b3-9997-13d0724cdf51"}	2022-06-23 15:14:01.233	\N	a317b304-528c-424f-961c-39174933b454	549fad9e-7f8e-4d8e-9a71-00d251817866	a17daf0a-f057-43b3-9997-13d0724cdf51
e95b3807-ef1a-4588-b685-50ec38b4973a	{"id": "e95b3807-ef1a-4588-b685-50ec38b4973a", "metadata": {"createdDate": "2022-06-23T15:14:01.229Z", "updatedDate": "2022-06-23T15:14:01.229Z"}, "subInstanceId": "81825729-e824-4d52-9d15-1695e9bf1831", "superInstanceId": "f7e82a1e-fc06-4b82-bb1d-da326cb378ce", "instanceRelationshipTypeId": "30773a27-b485-4dab-aeb6-b8c04fa3cb17"}	2022-06-23 15:14:01.229	\N	f7e82a1e-fc06-4b82-bb1d-da326cb378ce	81825729-e824-4d52-9d15-1695e9bf1831	30773a27-b485-4dab-aeb6-b8c04fa3cb17
34ec984a-4384-4088-bc58-5d5721c7b9d6	{"id": "34ec984a-4384-4088-bc58-5d5721c7b9d6", "metadata": {"createdDate": "2022-06-23T15:14:01.228Z", "updatedDate": "2022-06-23T15:14:01.228Z"}, "subInstanceId": "e6bc03c6-c137-4221-b679-a7c5c31f986c", "superInstanceId": "a317b304-528c-424f-961c-39174933b454", "instanceRelationshipTypeId": "a17daf0a-f057-43b3-9997-13d0724cdf51"}	2022-06-23 15:14:01.228	\N	a317b304-528c-424f-961c-39174933b454	e6bc03c6-c137-4221-b679-a7c5c31f986c	a17daf0a-f057-43b3-9997-13d0724cdf51
6789438f-754e-4fa6-8a4b-66949b68c2bb	{"id": "6789438f-754e-4fa6-8a4b-66949b68c2bb", "metadata": {"createdDate": "2022-06-23T15:14:01.232Z", "updatedDate": "2022-06-23T15:14:01.232Z"}, "subInstanceId": "7ab22f0a-c9cd-449a-9137-c76e5055ca37", "superInstanceId": "f7e82a1e-fc06-4b82-bb1d-da326cb378ce", "instanceRelationshipTypeId": "30773a27-b485-4dab-aeb6-b8c04fa3cb17"}	2022-06-23 15:14:01.232	\N	f7e82a1e-fc06-4b82-bb1d-da326cb378ce	7ab22f0a-c9cd-449a-9137-c76e5055ca37	30773a27-b485-4dab-aeb6-b8c04fa3cb17
\.


--
-- Data for Name: instance_relationship_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance_relationship_type (id, jsonb, creation_date, created_by) FROM stdin;
758f13db-ffb4-440e-bb10-8a364aa6cb4a	{"id": "758f13db-ffb4-440e-bb10-8a364aa6cb4a", "name": "bound-with", "metadata": {"createdDate": "2022-06-23T15:13:59.247Z", "updatedDate": "2022-06-23T15:13:59.247Z"}}	2022-06-23 15:13:59.247	\N
a17daf0a-f057-43b3-9997-13d0724cdf51	{"id": "a17daf0a-f057-43b3-9997-13d0724cdf51", "name": "multipart monograph", "metadata": {"createdDate": "2022-06-23T15:13:59.248Z", "updatedDate": "2022-06-23T15:13:59.248Z"}}	2022-06-23 15:13:59.248	\N
30773a27-b485-4dab-aeb6-b8c04fa3cb17	{"id": "30773a27-b485-4dab-aeb6-b8c04fa3cb17", "name": "monographic series", "metadata": {"createdDate": "2022-06-23T15:13:59.250Z", "updatedDate": "2022-06-23T15:13:59.250Z"}}	2022-06-23 15:13:59.25	\N
\.


--
-- Data for Name: instance_source_marc; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance_source_marc (id, jsonb, creation_date, created_by) FROM stdin;
\.


--
-- Data for Name: instance_status; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance_status (id, jsonb, creation_date, created_by) FROM stdin;
26f5208e-110a-4394-be29-1569a8c84a65	{"id": "26f5208e-110a-4394-be29-1569a8c84a65", "code": "uncat", "name": "Uncataloged", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.712Z", "updatedDate": "2022-06-23T15:13:59.712Z"}}	2022-06-23 15:13:59.712	\N
52a2ff34-2a12-420d-8539-21aa8d3cf5d8	{"id": "52a2ff34-2a12-420d-8539-21aa8d3cf5d8", "code": "batch", "name": "Batch Loaded", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.713Z", "updatedDate": "2022-06-23T15:13:59.713Z"}}	2022-06-23 15:13:59.713	\N
f5cc2ab6-bb92-4cab-b83f-5a3d09261a41	{"id": "f5cc2ab6-bb92-4cab-b83f-5a3d09261a41", "code": "none", "name": "Not yet assigned", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.711Z", "updatedDate": "2022-06-23T15:13:59.711Z"}}	2022-06-23 15:13:59.711	\N
daf2681c-25af-4202-a3fa-e58fdf806183	{"id": "daf2681c-25af-4202-a3fa-e58fdf806183", "code": "temp", "name": "Temporary", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.718Z", "updatedDate": "2022-06-23T15:13:59.718Z"}}	2022-06-23 15:13:59.718	\N
2a340d34-6b70-443a-bb1b-1b8d1c65d862	{"id": "2a340d34-6b70-443a-bb1b-1b8d1c65d862", "code": "other", "name": "Other", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.724Z", "updatedDate": "2022-06-23T15:13:59.724Z"}}	2022-06-23 15:13:59.724	\N
9634a5ab-9228-4703-baf2-4d12ebc77d56	{"id": "9634a5ab-9228-4703-baf2-4d12ebc77d56", "code": "cat", "name": "Cataloged", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.725Z", "updatedDate": "2022-06-23T15:13:59.725Z"}}	2022-06-23 15:13:59.725	\N
\.


--
-- Data for Name: instance_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.instance_type (id, jsonb) FROM stdin;
6312d172-f0cf-40f6-b27d-9fa8feaf332f	{"id": "6312d172-f0cf-40f6-b27d-9fa8feaf332f", "code": "txt", "name": "text", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.298+00:00", "updatedDate": "2022-06-23T15:13:59.298+00:00"}}
e5136fa2-1f19-4581-b005-6e007a940ca8	{"id": "e5136fa2-1f19-4581-b005-6e007a940ca8", "code": "crn", "name": "cartographic tactile three-dimensional form", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.301+00:00", "updatedDate": "2022-06-23T15:13:59.301+00:00"}}
497b5090-3da2-486c-b57f-de5bb3c2e26d	{"id": "497b5090-3da2-486c-b57f-de5bb3c2e26d", "code": "ntm", "name": "notated music", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.302+00:00", "updatedDate": "2022-06-23T15:13:59.302+00:00"}}
c208544b-9e28-44fa-a13c-f4093d72f798	{"id": "c208544b-9e28-44fa-a13c-f4093d72f798", "code": "cop", "name": "computer program", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.301+00:00", "updatedDate": "2022-06-23T15:13:59.301+00:00"}}
535e3160-763a-42f9-b0c0-d8ed7df6e2a2	{"id": "535e3160-763a-42f9-b0c0-d8ed7df6e2a2", "code": "sti", "name": "still image", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.303+00:00", "updatedDate": "2022-06-23T15:13:59.303+00:00"}}
a67e00fd-dcce-42a9-9e75-fd654ec31e89	{"id": "a67e00fd-dcce-42a9-9e75-fd654ec31e89", "code": "tcm", "name": "tactile notated music", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.304+00:00", "updatedDate": "2022-06-23T15:13:59.304+00:00"}}
e6a278fb-565a-4296-a7c5-8eb63d259522	{"id": "e6a278fb-565a-4296-a7c5-8eb63d259522", "code": "tcn", "name": "tactile notated movement", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.306+00:00", "updatedDate": "2022-06-23T15:13:59.306+00:00"}}
225faa14-f9bf-4ecd-990d-69433c912434	{"id": "225faa14-f9bf-4ecd-990d-69433c912434", "code": "tdi", "name": "two-dimensional moving image", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.309+00:00", "updatedDate": "2022-06-23T15:13:59.309+00:00"}}
df5dddff-9c30-4507-8b82-119ff972d4d7	{"id": "df5dddff-9c30-4507-8b82-119ff972d4d7", "code": "cod", "name": "computer dataset", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.310+00:00", "updatedDate": "2022-06-23T15:13:59.310+00:00"}}
3be24c14-3551-4180-9292-26a786649c8b	{"id": "3be24c14-3551-4180-9292-26a786649c8b", "code": "prm", "name": "performed music", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.310+00:00", "updatedDate": "2022-06-23T15:13:59.310+00:00"}}
2022aa2e-bdde-4dc4-90bc-115e8894b8b3	{"id": "2022aa2e-bdde-4dc4-90bc-115e8894b8b3", "code": "crf", "name": "cartographic three-dimensional form", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.312+00:00", "updatedDate": "2022-06-23T15:13:59.312+00:00"}}
c1e95c2b-4efc-48cf-9e71-edb622cf0c22	{"id": "c1e95c2b-4efc-48cf-9e71-edb622cf0c22", "code": "tdf", "name": "three-dimensional form", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.313+00:00", "updatedDate": "2022-06-23T15:13:59.313+00:00"}}
526aa04d-9289-4511-8866-349299592c18	{"id": "526aa04d-9289-4511-8866-349299592c18", "code": "cri", "name": "cartographic image", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.314+00:00", "updatedDate": "2022-06-23T15:13:59.314+00:00"}}
8105bd44-e7bd-487e-a8f2-b804a361d92f	{"id": "8105bd44-e7bd-487e-a8f2-b804a361d92f", "code": "tct", "name": "tactile text", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.317+00:00", "updatedDate": "2022-06-23T15:13:59.317+00:00"}}
efe2e89b-0525-4535-aa9b-3ff1a131189e	{"id": "efe2e89b-0525-4535-aa9b-3ff1a131189e", "code": "tci", "name": "tactile image", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.316+00:00", "updatedDate": "2022-06-23T15:13:59.316+00:00"}}
30fffe0e-e985-4144-b2e2-1e8179bdb41f	{"id": "30fffe0e-e985-4144-b2e2-1e8179bdb41f", "code": "zzz", "name": "unspecified", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.318+00:00", "updatedDate": "2022-06-23T15:13:59.318+00:00"}}
82689e16-629d-47f7-94b5-d89736cf11f2	{"id": "82689e16-629d-47f7-94b5-d89736cf11f2", "code": "tcf", "name": "tactile three-dimensional form", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.320+00:00", "updatedDate": "2022-06-23T15:13:59.320+00:00"}}
80c0c134-0240-4b63-99d0-6ca755d5f433	{"id": "80c0c134-0240-4b63-99d0-6ca755d5f433", "code": "crm", "name": "cartographic moving image", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.320+00:00", "updatedDate": "2022-06-23T15:13:59.320+00:00"}}
3363cdb1-e644-446c-82a4-dc3a1d4395b9	{"id": "3363cdb1-e644-446c-82a4-dc3a1d4395b9", "code": "crd", "name": "cartographic dataset", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.322+00:00", "updatedDate": "2022-06-23T15:13:59.322+00:00"}}
408f82f0-e612-4977-96a1-02076229e312	{"id": "408f82f0-e612-4977-96a1-02076229e312", "code": "crt", "name": "cartographic tactile image", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.322+00:00", "updatedDate": "2022-06-23T15:13:59.322+00:00"}}
fbe264b5-69aa-4b7c-a230-3b53337f6440	{"id": "fbe264b5-69aa-4b7c-a230-3b53337f6440", "code": "ntv", "name": "notated movement", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.324+00:00", "updatedDate": "2022-06-23T15:13:59.324+00:00"}}
c7f7446f-4642-4d97-88c9-55bae2ad6c7f	{"id": "c7f7446f-4642-4d97-88c9-55bae2ad6c7f", "code": "spw", "name": "spoken word", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.326+00:00", "updatedDate": "2022-06-23T15:13:59.326+00:00"}}
9bce18bd-45bf-4949-8fa8-63163e4b7d7f	{"id": "9bce18bd-45bf-4949-8fa8-63163e4b7d7f", "code": "snd", "name": "sounds", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.325+00:00", "updatedDate": "2022-06-23T15:13:59.325+00:00"}}
3e3039b7-fda0-4ac4-885a-022d457cb99c	{"id": "3e3039b7-fda0-4ac4-885a-022d457cb99c", "code": "tdm", "name": "three-dimensional moving image", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.327+00:00", "updatedDate": "2022-06-23T15:13:59.327+00:00"}}
a2c91e87-6bab-44d6-8adb-1fd02481fc4f	{"id": "a2c91e87-6bab-44d6-8adb-1fd02481fc4f", "code": "xxx", "name": "other", "source": "rdacontent", "metadata": {"createdDate": "2022-06-23T15:13:59.329+00:00", "updatedDate": "2022-06-23T15:13:59.329+00:00"}}
\.


--
-- Data for Name: item; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.item (id, jsonb, creation_date, created_by, holdingsrecordid, permanentloantypeid, temporaryloantypeid, materialtypeid, permanentlocationid, temporarylocationid, effectivelocationid) FROM stdin;
1b6d3338-186e-4e35-9e75-1b886b0da53e	{"id": "1b6d3338-186e-4e35-9e75-1b886b0da53e", "hrid": "item000000000008", "notes": [{"note": "Signed by the author", "staffOnly": false, "itemNoteTypeId": "8d0a5eca-25de-4391-81a9-236eeefdd20b"}], "status": {"date": "2022-06-23T15:14:01.090+00:00", "name": "Checked out"}, "barcode": "453987605438", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.089Z", "updatedDate": "2022-06-23T15:14:01.089Z"}, "formerIds": [], "copyNumber": "Copy 1", "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "numberOfPieces": "1", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "temporaryLoanTypeId": "e8b311a6-3b21-43f2-a269-dd9310cb2d0e", "effectiveShelvingOrder": "PR 46056 I4588 B749 42016 COPY 11", "effectiveCallNumberComponents": {"callNumber": "PR6056.I4588 B749 2016"}}	2022-06-23 15:14:01.089	\N	65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61	2b94c631-fca9-4892-a730-03ee529ffe27	e8b311a6-3b21-43f2-a269-dd9310cb2d0e	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
459afaba-5b39-468d-9072-eb1685e0ddf4	{"id": "459afaba-5b39-468d-9072-eb1685e0ddf4", "hrid": "item000000000011", "notes": [], "status": {"date": "2022-06-23T15:14:01.090+00:00", "name": "Available"}, "barcode": "765475420716", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.090Z", "updatedDate": "2022-06-23T15:14:01.090Z"}, "formerIds": [], "yearCaption": [], "materialTypeId": "5ee11d91-f7e8-481d-b079-65d708582ccc", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "65032151-39a5-4cef-8810-5350eb316300", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "53cf956f-c1df-410b-8bea-27f712cca7c0", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "temporaryLocationId": "53cf956f-c1df-410b-8bea-27f712cca7c0", "effectiveShelvingOrder": "MCN FICTION", "effectiveCallNumberComponents": {"callNumber": "MCN FICTION"}}	2022-06-23 15:14:01.09	\N	65032151-39a5-4cef-8810-5350eb316300	2b94c631-fca9-4892-a730-03ee529ffe27	\N	5ee11d91-f7e8-481d-b079-65d708582ccc	\N	53cf956f-c1df-410b-8bea-27f712cca7c0	53cf956f-c1df-410b-8bea-27f712cca7c0
100d10bf-2f06-4aa0-be15-0b95b2d9f9e3	{"id": "100d10bf-2f06-4aa0-be15-0b95b2d9f9e3", "hrid": "item000000000015", "notes": [], "status": {"date": "2022-06-23T15:14:01.094+00:00", "name": "Available"}, "barcode": "90000", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.094Z", "updatedDate": "2022-06-23T15:14:01.094Z"}, "formerIds": [], "chronology": "", "enumeration": "", "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "circulationNotes": [], "electronicAccess": [{"uri": "http://www.loc.gov/catdir/toc/ecip0718/2007020429.html", "linkText": "Links available", "publicNote": "Table of contents only", "relationshipId": "3b430592-2e09-4b48-9a0c-0636d66b9fb3", "materialsSpecification": "Table of contents"}], "holdingsRecordId": "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19", "statisticalCodeIds": ["b5968c9e-cddc-4576-99e3-8e60aed8b0dd"], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "itemLevelCallNumber": "TK5105.88815 . A58 2004 FT MEADE", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "effectiveShelvingOrder": "TK 45105.88815 A58 42004 FT MEADE", "effectiveCallNumberComponents": {"typeId": "512173a7-bd09-490e-b773-17d83f2b63fe", "callNumber": "TK5105.88815 . A58 2004 FT MEADE"}}	2022-06-23 15:14:01.094	\N	e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19	2b94c631-fca9-4892-a730-03ee529ffe27	\N	1a54b431-2e4f-452d-9cae-9cee66c9a892	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
0b96a642-5e7f-452d-9cae-9cee66c9a892	{"id": "0b96a642-5e7f-452d-9cae-9cee66c9a892", "hrid": "item000000000017", "notes": [], "status": {"date": "2022-06-23T15:14:01.103+00:00", "name": "Available"}, "barcode": "645398607547", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.102Z", "updatedDate": "2022-06-23T15:14:01.102Z"}, "formerIds": [], "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "e6d7e91a-4dbc-4a70-9b38-e000d2fbdc79", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "some-callnumber", "effectiveCallNumberComponents": {"callNumber": "some-callnumber"}}	2022-06-23 15:14:01.102	\N	e6d7e91a-4dbc-4a70-9b38-e000d2fbdc79	2b94c631-fca9-4892-a730-03ee529ffe27	\N	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
9ea1fd0b-0259-4edb-95a3-eb2f9a063e20	{"id": "9ea1fd0b-0259-4edb-95a3-eb2f9a063e20", "hrid": "item000000000006", "notes": [], "status": {"date": "2022-06-23T15:14:01.095+00:00", "name": "Available"}, "barcode": "A14837334306", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.095Z", "updatedDate": "2022-06-23T15:14:01.095Z"}, "formerIds": ["3806267"], "chronology": "1984:Jan.-June", "copyNumber": "1", "enumeration": "v.70:no.1-6", "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "0c45bb50-7c9b-48b0-86eb-178a494e25fe", "statisticalCodeIds": ["775b6ad4-9c35-4d29-bf78-8775a9b42226"], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "K 11 M44 V 270 NO 11 16 41984 JAN JUNE 11", "effectiveCallNumberComponents": {"callNumber": "K1 .M44"}}	2022-06-23 15:14:01.095	\N	0c45bb50-7c9b-48b0-86eb-178a494e25fe	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
23f2c8e1-bd5d-4f27-9398-a688c998808a	{"id": "23f2c8e1-bd5d-4f27-9398-a688c998808a", "hrid": "item000000000013", "notes": [], "status": {"date": "2022-06-23T15:14:01.108+00:00", "name": "Checked out"}, "barcode": "697685458679", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.108Z", "updatedDate": "2022-06-23T15:14:01.108Z"}, "formerIds": [], "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "68872d8a-bf16-420b-829f-206da38f6c10", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210a4", "effectiveShelvingOrder": "some-callnumber", "effectiveCallNumberComponents": {"callNumber": "some-callnumber"}}	2022-06-23 15:14:01.108	\N	68872d8a-bf16-420b-829f-206da38f6c10	2e48e713-17f3-4c13-a9f8-23845bb210a4	\N	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
645549b1-2a73-4251-b8bb-39598f773a93	{"id": "645549b1-2a73-4251-b8bb-39598f773a93", "hrid": "item000000000004", "notes": [], "status": {"date": "2022-06-23T15:14:01.113+00:00", "name": "Available"}, "barcode": "A14813848587", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.113Z", "updatedDate": "2022-06-23T15:14:01.113Z"}, "formerIds": [], "chronology": "1985:July-Dec.", "enumeration": "v.71:no.6-2", "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "0c45bb50-7c9b-48b0-86eb-178a494e25fe", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "K 11 M44 V 271 NO 16 12 41985 JULY DEC", "effectiveCallNumberComponents": {"callNumber": "K1 .M44"}}	2022-06-23 15:14:01.113	\N	0c45bb50-7c9b-48b0-86eb-178a494e25fe	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
9428231b-dd31-4f70-8406-fe22fbdeabc2	{"id": "9428231b-dd31-4f70-8406-fe22fbdeabc2", "hrid": "item000000000005", "notes": [], "status": {"date": "2022-06-23T15:14:01.133+00:00", "name": "Available"}, "barcode": "A14837334314", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.132Z", "updatedDate": "2022-06-23T15:14:01.132Z"}, "formerIds": [], "chronology": "1984:July-Dec.", "enumeration": "v.70:no.7-12", "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "0c45bb50-7c9b-48b0-86eb-178a494e25fe", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "K 11 M44 V 270 NO 17 212 41984 JULY DEC", "effectiveCallNumberComponents": {"callNumber": "K1 .M44"}}	2022-06-23 15:14:01.132	\N	0c45bb50-7c9b-48b0-86eb-178a494e25fe	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
d6f7c1ba-a237-465e-94ed-f37e91bc64bd	{"id": "d6f7c1ba-a237-465e-94ed-f37e91bc64bd", "hrid": "item000000000010", "notes": [], "status": {"date": "2022-06-23T15:14:01.126+00:00", "name": "Available"}, "barcode": "4539876054383", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.124Z", "updatedDate": "2022-06-23T15:14:01.124Z"}, "formerIds": [], "copyNumber": "Copy 3", "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "numberOfPieces": "1", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "fb7b70f1-b898-4924-a991-0e4b6312bb5f", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "53cf956f-c1df-410b-8bea-27f712cca7c0", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "temporaryLoanTypeId": "e8b311a6-3b21-43f2-a269-dd9310cb2d0e", "effectiveShelvingOrder": "PR 46056 I4588 B749 42016 COPY 13", "effectiveCallNumberComponents": {"callNumber": "PR6056.I4588 B749 2016"}}	2022-06-23 15:14:01.124	\N	fb7b70f1-b898-4924-a991-0e4b6312bb5f	2b94c631-fca9-4892-a730-03ee529ffe27	e8b311a6-3b21-43f2-a269-dd9310cb2d0e	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	53cf956f-c1df-410b-8bea-27f712cca7c0
bc90a3c9-26c9-4519-96bc-d9d44995afef	{"id": "bc90a3c9-26c9-4519-96bc-d9d44995afef", "hrid": "item000000000001", "notes": [], "status": {"date": "2022-06-23T15:14:01.150+00:00", "name": "Available"}, "barcode": "A14811392695", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.148Z", "updatedDate": "2022-06-23T15:14:01.148Z"}, "formerIds": [], "chronology": "1987:Jan.-June", "enumeration": "v.73:no.1-6", "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "0c45bb50-7c9b-48b0-86eb-178a494e25fe", "statisticalCodeIds": [], "administrativeNotes": ["an administrative note"], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "K 11 M44 V 273 NO 11 16 41987 JAN JUNE", "effectiveCallNumberComponents": {"callNumber": "K1 .M44"}}	2022-06-23 15:14:01.148	\N	0c45bb50-7c9b-48b0-86eb-178a494e25fe	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
f8b6d973-60d4-41ce-a57b-a3884471a6d6	{"id": "f8b6d973-60d4-41ce-a57b-a3884471a6d6", "hrid": "item000000000003", "notes": [], "status": {"date": "2022-06-23T15:14:01.169+00:00", "name": "Available"}, "barcode": "A14811392645", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.169Z", "updatedDate": "2022-06-23T15:14:01.169Z"}, "formerIds": [], "chronology": "1986:Jan.-June", "enumeration": "v.72:no.1-6", "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "0c45bb50-7c9b-48b0-86eb-178a494e25fe", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "K 11 M44 V 272 NO 11 16 41986 JAN JUNE", "effectiveCallNumberComponents": {"callNumber": "K1 .M44"}}	2022-06-23 15:14:01.169	\N	0c45bb50-7c9b-48b0-86eb-178a494e25fe	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
eedd13c4-7d40-4b1e-8f77-b0b9d19a896b	{"id": "eedd13c4-7d40-4b1e-8f77-b0b9d19a896b", "hrid": "item000000000002", "notes": [], "status": {"date": "2022-06-23T15:14:01.174+00:00", "name": "Available"}, "barcode": "A1429864347", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.174Z", "updatedDate": "2022-06-23T15:14:01.174Z"}, "formerIds": [], "chronology": "1986:July-Aug.,Oct.-Dec.", "enumeration": "v.72:no.6-7,10-12", "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "0c45bb50-7c9b-48b0-86eb-178a494e25fe", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "K 11 M44 V 272 NO 16 17 210 212 41986 JULY AUG OCT DEC", "effectiveCallNumberComponents": {"callNumber": "K1 .M44"}}	2022-06-23 15:14:01.174	\N	0c45bb50-7c9b-48b0-86eb-178a494e25fe	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
23fdb0bc-ab58-442a-b326-577a96204487	{"id": "23fdb0bc-ab58-442a-b326-577a96204487", "hrid": "item000000000016", "notes": [], "status": {"date": "2022-06-23T15:14:01.135+00:00", "name": "Available"}, "barcode": "653285216743", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.135Z", "updatedDate": "2022-06-23T15:14:01.135Z"}, "formerIds": [], "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "e6d7e91a-4dbc-4a70-9b38-e000d2fbdc79", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "some-callnumber", "effectiveCallNumberComponents": {"callNumber": "some-callnumber"}}	2022-06-23 15:14:01.135	\N	e6d7e91a-4dbc-4a70-9b38-e000d2fbdc79	2b94c631-fca9-4892-a730-03ee529ffe27	\N	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
7212ba6a-8dcf-45a1-be9a-ffaa847c4423	{"id": "7212ba6a-8dcf-45a1-be9a-ffaa847c4423", "hrid": "item000000000014", "notes": [], "status": {"date": "2022-06-23T15:14:01.151+00:00", "name": "Available"}, "barcode": "10101", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.150Z", "updatedDate": "2022-06-23T15:14:01.150Z"}, "formerIds": [], "chronology": "", "copyNumber": "Copy 2", "enumeration": "", "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "circulationNotes": [], "electronicAccess": [{"uri": "http://www.loc.gov/catdir/toc/ecip0718/2007020429.html", "linkText": "Links available", "publicNote": "Table of contents only", "relationshipId": "3b430592-2e09-4b48-9a0c-0636d66b9fb3", "materialsSpecification": "Table of contents"}], "holdingsRecordId": "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19", "statisticalCodeIds": ["b5968c9e-cddc-4576-99e3-8e60aed8b0dd"], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "itemLevelCallNumber": "TK5105.88815 . A58 2004 FT MEADE", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "effectiveShelvingOrder": "TK 45105.88815 A58 42004 FT MEADE COPY 12", "effectiveCallNumberComponents": {"typeId": "512173a7-bd09-490e-b773-17d83f2b63fe", "callNumber": "TK5105.88815 . A58 2004 FT MEADE"}}	2022-06-23 15:14:01.15	\N	e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19	2b94c631-fca9-4892-a730-03ee529ffe27	\N	1a54b431-2e4f-452d-9cae-9cee66c9a892	fcd64ce1-6995-48f0-840e-89ffa2288371	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
1714f71f-b845-444b-a79e-a577487a6f7d	{"id": "1714f71f-b845-444b-a79e-a577487a6f7d", "hrid": "item000000000007", "notes": [], "status": {"date": "2022-06-23T15:14:01.168+00:00", "name": "Available"}, "barcode": "000111222333444", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.168Z", "updatedDate": "2022-06-23T15:14:01.168Z"}, "formerIds": [], "copyNumber": "c.1", "enumeration": "v. 30 1961", "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "133a7916-f05e-4df4-8f7f-09eb2a7076d1", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2e48e713-17f3-4c13-a9f8-23845bb210a4", "temporaryLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "R 211 A38 V 230 41961 C 11", "effectiveCallNumberComponents": {"callNumber": "R11.A38"}}	2022-06-23 15:14:01.168	\N	133a7916-f05e-4df4-8f7f-09eb2a7076d1	2e48e713-17f3-4c13-a9f8-23845bb210a4	2b94c631-fca9-4892-a730-03ee529ffe27	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
6c7196d2-0c2a-4707-a196-ff6b9e84a75e	{"id": "6c7196d2-0c2a-4707-a196-ff6b9e84a75e", "hrid": "bwit000000001", "tags": {"tagList": []}, "notes": [], "status": {"date": "2022-06-23T15:14:01.335+00:00", "name": "Available"}, "barcode": "12", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.335Z", "updatedDate": "2022-06-23T15:14:01.335Z"}, "formerIds": [], "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "13767c78-f8d0-425e-801d-cc5bd475856a", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "DE 13", "effectiveCallNumberComponents": {"callNumber": "DE3"}}	2022-06-23 15:14:01.335	\N	13767c78-f8d0-425e-801d-cc5bd475856a	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
4428a37c-8bae-4f0d-865d-970d83d5ad55	{"id": "4428a37c-8bae-4f0d-865d-970d83d5ad55", "hrid": "item000000000009", "notes": [{"note": "Missing pages; p 10-13", "staffOnly": false, "itemNoteTypeId": "8d0a5eca-25de-4391-81a9-236eeefdd20b"}, {"note": "My action note", "staffOnly": false, "itemNoteTypeId": "0e40884c-3523-4c6d-8187-d578e3d2794e"}, {"note": "My copy note", "staffOnly": false, "itemNoteTypeId": "1dde7141-ec8a-4dae-9825-49ce14c728e7"}, {"note": "My provenance", "staffOnly": false, "itemNoteTypeId": "c3a539b9-9576-4e3a-b6de-d910200b2919"}, {"note": "My reproduction", "staffOnly": false, "itemNoteTypeId": "acb3a58f-1d72-461d-97c3-0e7119e8d544"}], "status": {"date": "2022-06-23T15:14:01.165+00:00", "name": "Available"}, "barcode": "4539876054382", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.164Z", "updatedDate": "2022-06-23T15:14:01.164Z"}, "formerIds": [], "copyNumber": "Copy 2", "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "numberOfPieces": "1", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "temporaryLoanTypeId": "e8b311a6-3b21-43f2-a269-dd9310cb2d0e", "effectiveShelvingOrder": "PR 46056 I4588 B749 42016 COPY 12", "effectiveCallNumberComponents": {"callNumber": "PR6056.I4588 B749 2016"}}	2022-06-23 15:14:01.164	\N	65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61	2b94c631-fca9-4892-a730-03ee529ffe27	e8b311a6-3b21-43f2-a269-dd9310cb2d0e	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
bb5a6689-c008-4c96-8f8f-b666850ee12d	{"id": "bb5a6689-c008-4c96-8f8f-b666850ee12d", "hrid": "item000000000012", "notes": [], "status": {"date": "2022-06-23T15:14:01.171+00:00", "name": "Checked out"}, "barcode": "326547658598", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.170Z", "updatedDate": "2022-06-23T15:14:01.170Z"}, "formerIds": [], "yearCaption": [], "materialTypeId": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "67cd0046-e4f1-4e4f-9024-adf0b0039d09", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "f34d27c6-a8eb-461b-acd6-5dea81771e70", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "D 215 H63 A3 42002", "effectiveCallNumberComponents": {"callNumber": "D15.H63 A3 2002"}}	2022-06-23 15:14:01.17	\N	67cd0046-e4f1-4e4f-9024-adf0b0039d09	2b94c631-fca9-4892-a730-03ee529ffe27	\N	1a54b431-2e4f-452d-9cae-9cee66c9a892	\N	\N	f34d27c6-a8eb-461b-acd6-5dea81771e70
f4b8c3d1-f461-4551-aa7b-5f45e64f236c	{"id": "f4b8c3d1-f461-4551-aa7b-5f45e64f236c", "hrid": "BW-ITEM-1", "tags": {"tagList": ["important"]}, "notes": [], "status": {"date": "2022-06-23T15:14:01.334+00:00", "name": "Available"}, "barcode": "X575181", "_version": 1, "metadata": {"createdDate": "2022-06-23T15:14:01.334Z", "updatedDate": "2022-06-23T15:14:01.334Z"}, "formerIds": [], "yearCaption": [], "materialTypeId": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "circulationNotes": [], "electronicAccess": [], "holdingsRecordId": "9e8dc8ce-68f3-4e75-8479-d548ce521157", "statisticalCodeIds": [], "administrativeNotes": [], "effectiveLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371", "permanentLoanTypeId": "2b94c631-fca9-4892-a730-03ee529ffe27", "effectiveShelvingOrder": "41958 A 48050", "effectiveCallNumberComponents": {"prefix": "A", "typeId": "6caca63e-5651-4db6-9247-3205156e9699", "callNumber": "1958 A 8050"}}	2022-06-23 15:14:01.334	\N	9e8dc8ce-68f3-4e75-8479-d548ce521157	2b94c631-fca9-4892-a730-03ee529ffe27	\N	d9acad2f-2aac-4b48-9097-e6ab85906b25	\N	\N	fcd64ce1-6995-48f0-840e-89ffa2288371
\.


--
-- Data for Name: item_damaged_status; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.item_damaged_status (id, jsonb, creation_date, created_by) FROM stdin;
54d1dd76-ea33-4bcb-955b-6b29df4f7930	{"id": "54d1dd76-ea33-4bcb-955b-6b29df4f7930", "name": "Damaged", "source": "local", "metadata": {"createdDate": "2022-06-23T15:14:00.431Z", "updatedDate": "2022-06-23T15:14:00.431Z"}}	2022-06-23 15:14:00.431	\N
516b82eb-1f19-4a63-8c48-8f1a3e9ff311	{"id": "516b82eb-1f19-4a63-8c48-8f1a3e9ff311", "name": "Not Damaged", "source": "local", "metadata": {"createdDate": "2022-06-23T15:14:00.434Z", "updatedDate": "2022-06-23T15:14:00.434Z"}}	2022-06-23 15:14:00.434	\N
\.


--
-- Data for Name: item_note_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.item_note_type (id, jsonb, creation_date, created_by) FROM stdin;
c3a539b9-9576-4e3a-b6de-d910200b2919	{"id": "c3a539b9-9576-4e3a-b6de-d910200b2919", "name": "Provenance", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.400Z", "updatedDate": "2022-06-23T15:14:00.400Z"}}	2022-06-23 15:14:00.4	\N
87c450be-2033-41fb-80ba-dd2409883681	{"id": "87c450be-2033-41fb-80ba-dd2409883681", "name": "Binding", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.404Z", "updatedDate": "2022-06-23T15:14:00.404Z"}}	2022-06-23 15:14:00.404	\N
f3ae3823-d096-4c65-8734-0c1efd2ffea8	{"id": "f3ae3823-d096-4c65-8734-0c1efd2ffea8", "name": "Electronic bookplate", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.401Z", "updatedDate": "2022-06-23T15:14:00.401Z"}}	2022-06-23 15:14:00.401	\N
1dde7141-ec8a-4dae-9825-49ce14c728e7	{"id": "1dde7141-ec8a-4dae-9825-49ce14c728e7", "name": "Copy note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.410Z", "updatedDate": "2022-06-23T15:14:00.410Z"}}	2022-06-23 15:14:00.41	\N
acb3a58f-1d72-461d-97c3-0e7119e8d544	{"id": "acb3a58f-1d72-461d-97c3-0e7119e8d544", "name": "Reproduction", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.409Z", "updatedDate": "2022-06-23T15:14:00.409Z"}}	2022-06-23 15:14:00.409	\N
0e40884c-3523-4c6d-8187-d578e3d2794e	{"id": "0e40884c-3523-4c6d-8187-d578e3d2794e", "name": "Action note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.412Z", "updatedDate": "2022-06-23T15:14:00.412Z"}}	2022-06-23 15:14:00.412	\N
8d0a5eca-25de-4391-81a9-236eeefdd20b	{"id": "8d0a5eca-25de-4391-81a9-236eeefdd20b", "name": "Note", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:14:00.414Z", "updatedDate": "2022-06-23T15:14:00.414Z"}}	2022-06-23 15:14:00.414	\N
\.


--
-- Data for Name: iteration_job; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.iteration_job (id, jsonb) FROM stdin;
\.


--
-- Data for Name: loan_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.loan_type (id, jsonb, creation_date, created_by) FROM stdin;
2e48e713-17f3-4c13-a9f8-23845bb210a4	{"id": "2e48e713-17f3-4c13-a9f8-23845bb210a4", "name": "Reading room", "metadata": {"createdDate": "2022-06-23T15:13:57.823Z", "updatedDate": "2022-06-23T15:13:57.823Z"}}	2022-06-23 15:13:57.823	\N
2b94c631-fca9-4892-a730-03ee529ffe27	{"id": "2b94c631-fca9-4892-a730-03ee529ffe27", "name": "Can circulate", "metadata": {"createdDate": "2022-06-23T15:13:57.826Z", "updatedDate": "2022-06-23T15:13:57.826Z"}}	2022-06-23 15:13:57.826	\N
e8b311a6-3b21-43f2-a269-dd9310cb2d0e	{"id": "e8b311a6-3b21-43f2-a269-dd9310cb2d0e", "name": "Course reserves", "metadata": {"createdDate": "2022-06-23T15:13:57.824Z", "updatedDate": "2022-06-23T15:13:57.824Z"}}	2022-06-23 15:13:57.824	\N
a1dc1ce3-d56f-4d8a-b498-d5d674ccc845	{"id": "a1dc1ce3-d56f-4d8a-b498-d5d674ccc845", "name": "Selected", "metadata": {"createdDate": "2022-06-23T15:13:57.827Z", "updatedDate": "2022-06-23T15:13:57.827Z"}}	2022-06-23 15:13:57.827	\N
\.


--
-- Data for Name: location; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.location (id, jsonb, creation_date, created_by, institutionid, campusid, libraryid) FROM stdin;
fcd64ce1-6995-48f0-840e-89ffa2288371	{"id": "fcd64ce1-6995-48f0-840e-89ffa2288371", "code": "KU/CC/DI/M", "name": "Main Library", "campusId": "62cf76b7-cca5-4d33-9217-edf42ce1a848", "isActive": true, "metadata": {"createdDate": "2022-06-23T15:13:58.080Z", "updatedDate": "2022-06-23T15:13:58.080Z"}, "libraryId": "5d78803e-ca04-4b4a-aeae-2c63b924518b", "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57", "servicePoints": [], "servicePointIds": ["3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "primaryServicePoint": "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"}	2022-06-23 15:13:58.08	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57	62cf76b7-cca5-4d33-9217-edf42ce1a848	5d78803e-ca04-4b4a-aeae-2c63b924518b
b241764c-1466-4e1d-a028-1a3684a5da87	{"id": "b241764c-1466-4e1d-a028-1a3684a5da87", "code": "KU/CC/DI/P", "name": "Popular Reading Collection", "campusId": "62cf76b7-cca5-4d33-9217-edf42ce1a848", "isActive": true, "metadata": {"createdDate": "2022-06-23T15:13:58.079Z", "updatedDate": "2022-06-23T15:13:58.079Z"}, "libraryId": "5d78803e-ca04-4b4a-aeae-2c63b924518b", "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57", "servicePoints": [], "servicePointIds": ["3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "primaryServicePoint": "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"}	2022-06-23 15:13:58.079	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57	62cf76b7-cca5-4d33-9217-edf42ce1a848	5d78803e-ca04-4b4a-aeae-2c63b924518b
758258bc-ecc1-41b8-abca-f7b610822ffd	{"id": "758258bc-ecc1-41b8-abca-f7b610822ffd", "code": "KU/CC/DI/O", "name": "ORWIG ETHNO CD", "campusId": "62cf76b7-cca5-4d33-9217-edf42ce1a848", "isActive": true, "metadata": {"createdDate": "2022-06-23T15:13:58.099Z", "updatedDate": "2022-06-23T15:13:58.099Z"}, "libraryId": "5d78803e-ca04-4b4a-aeae-2c63b924518b", "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57", "servicePoints": [], "servicePointIds": ["3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "primaryServicePoint": "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"}	2022-06-23 15:13:58.099	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57	62cf76b7-cca5-4d33-9217-edf42ce1a848	5d78803e-ca04-4b4a-aeae-2c63b924518b
f34d27c6-a8eb-461b-acd6-5dea81771e70	{"id": "f34d27c6-a8eb-461b-acd6-5dea81771e70", "code": "KU/CC/DI/2", "name": "SECOND FLOOR", "campusId": "62cf76b7-cca5-4d33-9217-edf42ce1a848", "isActive": true, "metadata": {"createdDate": "2022-06-23T15:13:58.089Z", "updatedDate": "2022-06-23T15:13:58.089Z"}, "libraryId": "5d78803e-ca04-4b4a-aeae-2c63b924518b", "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57", "servicePoints": [], "servicePointIds": ["3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "primaryServicePoint": "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"}	2022-06-23 15:13:58.089	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57	62cf76b7-cca5-4d33-9217-edf42ce1a848	5d78803e-ca04-4b4a-aeae-2c63b924518b
53cf956f-c1df-410b-8bea-27f712cca7c0	{"id": "53cf956f-c1df-410b-8bea-27f712cca7c0", "code": "KU/CC/DI/A", "name": "Annex", "campusId": "62cf76b7-cca5-4d33-9217-edf42ce1a848", "isActive": true, "metadata": {"createdDate": "2022-06-23T15:13:58.108Z", "updatedDate": "2022-06-23T15:13:58.108Z"}, "libraryId": "5d78803e-ca04-4b4a-aeae-2c63b924518b", "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57", "servicePoints": [], "servicePointIds": ["3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "primaryServicePoint": "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"}	2022-06-23 15:13:58.108	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57	62cf76b7-cca5-4d33-9217-edf42ce1a848	5d78803e-ca04-4b4a-aeae-2c63b924518b
184aae84-a5bf-4c6a-85ba-4a7c73026cd5	{"id": "184aae84-a5bf-4c6a-85ba-4a7c73026cd5", "code": "E", "name": "Online", "campusId": "470ff1dd-937a-4195-bf9e-06bcfcd135df", "isActive": true, "metadata": {"createdDate": "2022-06-23T15:13:58.106Z", "updatedDate": "2022-06-23T15:13:58.106Z"}, "libraryId": "c2549bb4-19c7-4fcc-8b52-39e612fb7dbe", "description": "Use for online resources", "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57", "servicePoints": [], "servicePointIds": ["bba36e5d-d567-45fa-81cd-b25874472e30"], "primaryServicePoint": "bba36e5d-d567-45fa-81cd-b25874472e30", "discoveryDisplayName": "Online"}	2022-06-23 15:13:58.106	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57	470ff1dd-937a-4195-bf9e-06bcfcd135df	c2549bb4-19c7-4fcc-8b52-39e612fb7dbe
\.


--
-- Data for Name: loccampus; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.loccampus (id, jsonb, creation_date, created_by, institutionid) FROM stdin;
470ff1dd-937a-4195-bf9e-06bcfcd135df	{"id": "470ff1dd-937a-4195-bf9e-06bcfcd135df", "code": "E", "name": "Online", "metadata": {"createdDate": "2022-06-23T15:13:57.878Z", "updatedDate": "2022-06-23T15:13:57.878Z"}, "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57"}	2022-06-23 15:13:57.878	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57
62cf76b7-cca5-4d33-9217-edf42ce1a848	{"id": "62cf76b7-cca5-4d33-9217-edf42ce1a848", "code": "CC", "name": "City Campus", "metadata": {"createdDate": "2022-06-23T15:13:57.880Z", "updatedDate": "2022-06-23T15:13:57.880Z"}, "institutionId": "40ee00ca-a518-4b49-be01-0638d0a4ac57"}	2022-06-23 15:13:57.88	\N	40ee00ca-a518-4b49-be01-0638d0a4ac57
\.


--
-- Data for Name: locinstitution; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.locinstitution (id, jsonb, creation_date, created_by) FROM stdin;
40ee00ca-a518-4b49-be01-0638d0a4ac57	{"id": "40ee00ca-a518-4b49-be01-0638d0a4ac57", "code": "KU", "name": "Københavns Universitet", "metadata": {"createdDate": "2022-06-23T15:13:57.860Z", "updatedDate": "2022-06-23T15:13:57.860Z"}}	2022-06-23 15:13:57.86	\N
\.


--
-- Data for Name: loclibrary; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.loclibrary (id, jsonb, creation_date, created_by, campusid) FROM stdin;
5d78803e-ca04-4b4a-aeae-2c63b924518b	{"id": "5d78803e-ca04-4b4a-aeae-2c63b924518b", "code": "DI", "name": "Datalogisk Institut", "campusId": "62cf76b7-cca5-4d33-9217-edf42ce1a848", "metadata": {"createdDate": "2022-06-23T15:13:57.896Z", "updatedDate": "2022-06-23T15:13:57.896Z"}}	2022-06-23 15:13:57.896	\N	62cf76b7-cca5-4d33-9217-edf42ce1a848
c2549bb4-19c7-4fcc-8b52-39e612fb7dbe	{"id": "c2549bb4-19c7-4fcc-8b52-39e612fb7dbe", "code": "E", "name": "Online", "campusId": "470ff1dd-937a-4195-bf9e-06bcfcd135df", "metadata": {"createdDate": "2022-06-23T15:13:57.898Z", "updatedDate": "2022-06-23T15:13:57.898Z"}}	2022-06-23 15:13:57.898	\N	470ff1dd-937a-4195-bf9e-06bcfcd135df
\.


--
-- Data for Name: material_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.material_type (id, jsonb, creation_date, created_by) FROM stdin;
d9acad2f-2aac-4b48-9097-e6ab85906b25	{"id": "d9acad2f-2aac-4b48-9097-e6ab85906b25", "name": "text", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.759Z", "updatedDate": "2022-06-23T15:13:57.759Z"}}	2022-06-23 15:13:57.759	\N
1a54b431-2e4f-452d-9cae-9cee66c9a892	{"id": "1a54b431-2e4f-452d-9cae-9cee66c9a892", "name": "book", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.762Z", "updatedDate": "2022-06-23T15:13:57.762Z"}}	2022-06-23 15:13:57.762	\N
fd6c6515-d470-4561-9c32-3e3290d4ca98	{"id": "fd6c6515-d470-4561-9c32-3e3290d4ca98", "name": "microform", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.764Z", "updatedDate": "2022-06-23T15:13:57.764Z"}}	2022-06-23 15:13:57.764	\N
71fbd940-1027-40a6-8a48-49b44d795e46	{"id": "71fbd940-1027-40a6-8a48-49b44d795e46", "name": "unspecified", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.768Z", "updatedDate": "2022-06-23T15:13:57.768Z"}}	2022-06-23 15:13:57.768	\N
615b8413-82d5-4203-aa6e-e37984cb5ac3	{"id": "615b8413-82d5-4203-aa6e-e37984cb5ac3", "name": "electronic resource", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.768Z", "updatedDate": "2022-06-23T15:13:57.768Z"}}	2022-06-23 15:13:57.768	\N
dd0bf600-dbd9-44ab-9ff2-e2a61a6539f1	{"id": "dd0bf600-dbd9-44ab-9ff2-e2a61a6539f1", "name": "sound recording", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.780Z", "updatedDate": "2022-06-23T15:13:57.780Z"}}	2022-06-23 15:13:57.78	\N
5ee11d91-f7e8-481d-b079-65d708582ccc	{"id": "5ee11d91-f7e8-481d-b079-65d708582ccc", "name": "dvd", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.787Z", "updatedDate": "2022-06-23T15:13:57.787Z"}}	2022-06-23 15:13:57.787	\N
30b3e36a-d3b2-415e-98c2-47fbdf878862	{"id": "30b3e36a-d3b2-415e-98c2-47fbdf878862", "name": "video recording", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:57.789Z", "updatedDate": "2022-06-23T15:13:57.789Z"}}	2022-06-23 15:13:57.789	\N
\.


--
-- Data for Name: mode_of_issuance; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.mode_of_issuance (id, jsonb, creation_date, created_by) FROM stdin;
4fc0f4fe-06fd-490a-a078-c4da1754e03a	{"id": "4fc0f4fe-06fd-490a-a078-c4da1754e03a", "name": "integrating resource", "source": "rdamodeissue", "metadata": {"createdDate": "2022-06-23T15:13:59.831Z", "updatedDate": "2022-06-23T15:13:59.831Z"}}	2022-06-23 15:13:59.831	\N
f5cc2ab6-bb92-4cab-b83f-5a3d09261a41	{"id": "f5cc2ab6-bb92-4cab-b83f-5a3d09261a41", "name": "multipart monograph", "source": "rdamodeissue", "metadata": {"createdDate": "2022-06-23T15:13:59.831Z", "updatedDate": "2022-06-23T15:13:59.831Z"}}	2022-06-23 15:13:59.831	\N
9d18a02f-5897-4c31-9106-c9abb5c7ae8b	{"id": "9d18a02f-5897-4c31-9106-c9abb5c7ae8b", "name": "single unit", "source": "rdamodeissue", "metadata": {"createdDate": "2022-06-23T15:13:59.832Z", "updatedDate": "2022-06-23T15:13:59.832Z"}}	2022-06-23 15:13:59.832	\N
612bbd3d-c16b-4bfb-8517-2afafc60204a	{"id": "612bbd3d-c16b-4bfb-8517-2afafc60204a", "name": "unspecified", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.835Z", "updatedDate": "2022-06-23T15:13:59.835Z"}}	2022-06-23 15:13:59.835	\N
068b5344-e2a6-40df-9186-1829e13cd344	{"id": "068b5344-e2a6-40df-9186-1829e13cd344", "name": "serial", "source": "rdamodeissue", "metadata": {"createdDate": "2022-06-23T15:13:59.833Z", "updatedDate": "2022-06-23T15:13:59.833Z"}}	2022-06-23 15:13:59.833	\N
\.


--
-- Data for Name: nature_of_content_term; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.nature_of_content_term (id, jsonb, creation_date, created_by) FROM stdin;
b29d4dc1-f78b-48fe-b3e5-df6c37cdc58d	{"id": "b29d4dc1-f78b-48fe-b3e5-df6c37cdc58d", "name": "festschrift", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.622Z", "updatedDate": "2022-06-23T15:13:59.622Z"}}	2022-06-23 15:13:59.622	\N
0abeee3d-8ad2-4b04-92ff-221b4fce1075	{"id": "0abeee3d-8ad2-4b04-92ff-221b4fce1075", "name": "journal", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.622Z", "updatedDate": "2022-06-23T15:13:59.622Z"}}	2022-06-23 15:13:59.622	\N
073f7f2f-9212-4395-b039-6f9825b11d54	{"id": "073f7f2f-9212-4395-b039-6f9825b11d54", "name": "proceedings", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.621Z", "updatedDate": "2022-06-23T15:13:59.621Z"}}	2022-06-23 15:13:59.621	\N
44cd89f3-2e76-469f-a955-cc57cb9e0395	{"id": "44cd89f3-2e76-469f-a955-cc57cb9e0395", "name": "textbook", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.623Z", "updatedDate": "2022-06-23T15:13:59.623Z"}}	2022-06-23 15:13:59.623	\N
04a6a8d2-f902-4774-b15f-d8bd885dc804	{"id": "04a6a8d2-f902-4774-b15f-d8bd885dc804", "name": "autobiography", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.624Z", "updatedDate": "2022-06-23T15:13:59.624Z"}}	2022-06-23 15:13:59.624	\N
4570a93e-ddb6-4200-8e8b-283c8f5c9bfa	{"id": "4570a93e-ddb6-4200-8e8b-283c8f5c9bfa", "name": "research report", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.628Z", "updatedDate": "2022-06-23T15:13:59.628Z"}}	2022-06-23 15:13:59.628	\N
ebbbdef1-00e1-428b-bc11-314dc0705074	{"id": "ebbbdef1-00e1-428b-bc11-314dc0705074", "name": "newspaper", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.631Z", "updatedDate": "2022-06-23T15:13:59.631Z"}}	2022-06-23 15:13:59.631	\N
2fbc8a7b-b432-45df-ba37-46031b1f6545	{"id": "2fbc8a7b-b432-45df-ba37-46031b1f6545", "name": "website", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.630Z", "updatedDate": "2022-06-23T15:13:59.630Z"}}	2022-06-23 15:13:59.63	\N
c0d52f31-aabb-4c55-bf81-fea7fdda94a4	{"id": "c0d52f31-aabb-4c55-bf81-fea7fdda94a4", "name": "experience report", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.632Z", "updatedDate": "2022-06-23T15:13:59.632Z"}}	2022-06-23 15:13:59.632	\N
96879b60-098b-453b-bf9a-c47866f1ab2a	{"id": "96879b60-098b-453b-bf9a-c47866f1ab2a", "name": "audiobook", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.634Z", "updatedDate": "2022-06-23T15:13:59.634Z"}}	2022-06-23 15:13:59.634	\N
536da7c1-9c35-45df-8ea1-c3545448df92	{"id": "536da7c1-9c35-45df-8ea1-c3545448df92", "name": "monographic series", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.633Z", "updatedDate": "2022-06-23T15:13:59.633Z"}}	2022-06-23 15:13:59.633	\N
b6e214bd-82f5-467f-af5b-4592456dc4ab	{"id": "b6e214bd-82f5-467f-af5b-4592456dc4ab", "name": "biography", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.635Z", "updatedDate": "2022-06-23T15:13:59.635Z"}}	2022-06-23 15:13:59.635	\N
b82b3a0d-00fa-4811-96da-04f531da8ea8	{"id": "b82b3a0d-00fa-4811-96da-04f531da8ea8", "name": "exhibition catalogue", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.636Z", "updatedDate": "2022-06-23T15:13:59.636Z"}}	2022-06-23 15:13:59.636	\N
acceb2d6-4f05-408f-9a88-a92de26441ce	{"id": "acceb2d6-4f05-408f-9a88-a92de26441ce", "name": "comic (book)", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.637Z", "updatedDate": "2022-06-23T15:13:59.637Z"}}	2022-06-23 15:13:59.637	\N
9419a20e-6c8f-4ae1-85a7-8c184a1f4762	{"id": "9419a20e-6c8f-4ae1-85a7-8c184a1f4762", "name": "travel report", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.639Z", "updatedDate": "2022-06-23T15:13:59.639Z"}}	2022-06-23 15:13:59.639	\N
631893b6-5d8a-4e1a-9e6b-5344e2945c74	{"id": "631893b6-5d8a-4e1a-9e6b-5344e2945c74", "name": "illustrated book / picture book", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.640Z", "updatedDate": "2022-06-23T15:13:59.640Z"}}	2022-06-23 15:13:59.64	\N
94f6d06a-61e0-47c1-bbcb-6186989e6040	{"id": "94f6d06a-61e0-47c1-bbcb-6186989e6040", "name": "thesis", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.642Z", "updatedDate": "2022-06-23T15:13:59.642Z"}}	2022-06-23 15:13:59.642	\N
f5908d05-b16a-49cf-b192-96d55a94a0d1	{"id": "f5908d05-b16a-49cf-b192-96d55a94a0d1", "name": "bibliography", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.642Z", "updatedDate": "2022-06-23T15:13:59.642Z"}}	2022-06-23 15:13:59.642	\N
71b43e3a-8cdd-4d22-9751-020f34fb6ef8	{"id": "71b43e3a-8cdd-4d22-9751-020f34fb6ef8", "name": "report", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.643Z", "updatedDate": "2022-06-23T15:13:59.643Z"}}	2022-06-23 15:13:59.643	\N
31572023-f4c9-4cf3-80a2-0543c9eda884	{"id": "31572023-f4c9-4cf3-80a2-0543c9eda884", "name": "literature report", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.645Z", "updatedDate": "2022-06-23T15:13:59.645Z"}}	2022-06-23 15:13:59.645	\N
85657646-6b6f-4e71-b54c-d47f3b95a5ed	{"id": "85657646-6b6f-4e71-b54c-d47f3b95a5ed", "name": "school program", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.647Z", "updatedDate": "2022-06-23T15:13:59.647Z"}}	2022-06-23 15:13:59.647	\N
\.


--
-- Data for Name: notification_sending_error; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.notification_sending_error (id, jsonb) FROM stdin;
\.


--
-- Data for Name: preceding_succeeding_title; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.preceding_succeeding_title (id, jsonb, creation_date, created_by, precedinginstanceid, succeedinginstanceid) FROM stdin;
\.


--
-- Data for Name: reindex_job; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.reindex_job (id, jsonb) FROM stdin;
\.


--
-- Data for Name: rmb_internal; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.rmb_internal (id, jsonb) FROM stdin;
1	{"rmbVersion": "34.0.0", "schemaJson": "{\\n  \\"tables\\": [\\n    {\\n      \\"tableName\\": \\"authority\\",\\n      \\"fromModuleVersion\\": \\"22.1.0\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"withOptimisticLocking\\": \\"failOnConflict\\"\\n    },\\n    {\\n      \\"tableName\\": \\"loan_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"material_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"locinstitution\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"loccampus\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"institutionId\\",\\n          \\"targetTable\\": \\"locinstitution\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"loclibrary\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"campusId\\",\\n          \\"targetTable\\": \\"loccampus\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"location\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"code\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"institutionId\\",\\n          \\"targetTable\\": \\"locinstitution\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"campusId\\",\\n          \\"targetTable\\": \\"loccampus\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"libraryId\\",\\n          \\"targetTable\\": \\"loclibrary\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"index\\": [\\n        {\\n          \\"fieldName\\": \\"primaryServicePoint\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"service_point\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"code\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"index\\": [\\n        {\\n          \\"fieldName\\": \\"pickupLocation\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"service_point_user\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"userId\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"defaultServicePointId\\",\\n          \\"targetTable\\": \\"service_point\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"identifier_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance_relationship_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"contributor_type\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"code\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"contributor_name_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance_type\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"code\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance_format\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"code\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"nature_of_content_term\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"classification_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"electronic_access_relationship\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"statistical_code_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"code\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"statistical_code\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"code, statisticalCodeTypeId\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"statisticalCodeTypeId\\",\\n          \\"targetTable\\": \\"statistical_code_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance_status\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"code\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"mode_of_issuance\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"alternative_title_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance\\",\\n      \\"fromModuleVersion\\": \\"19.2.0\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": true,\\n      \\"withOptimisticLocking\\": \\"failOnConflict\\",\\n      \\"auditingTableName\\": \\"audit_instance\\",\\n      \\"auditingFieldName\\": \\"record\\",\\n      \\"customSnippetPath\\": \\"audit-delete-trigger.sql\\",\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"instanceStatusId\\",\\n          \\"targetTable\\": \\"instance_status\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"modeOfIssuanceId\\",\\n          \\"targetTable\\": \\"mode_of_issuance\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"instanceTypeId\\",\\n          \\"targetTable\\": \\"instance_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"hrid\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"matchKey\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"ginIndex\\": [\\n        {\\n          \\"fieldName\\": \\"identifiers\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": true\\n        }\\n      ],\\n      \\"index\\": [\\n        {\\n          \\"fieldName\\": \\"source\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"indexTitle\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": true\\n        },\\n        {\\n          \\"fieldName\\": \\"title\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": true\\n        },\\n        {\\n          \\"fieldName\\": \\"statisticalCodeIds\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"contributors\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": true\\n        },\\n        {\\n          \\"fieldName\\": \\"publication\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": true\\n        },\\n        {\\n          \\"fieldName\\": \\"staffSuppress\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"discoverySuppress\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"metadata.updatedDate\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        }\\n      ],\\n      \\"fullTextIndex\\": [\\n        {\\n          \\"fieldName\\": \\"identifiers\\",\\n          \\"arraySubfield\\" : \\"value\\",\\n          \\"arrayModifiers\\": [\\"identifierTypeId\\"]\\n        },\\n        {\\n          \\"fieldName\\": \\"invalidIsbn\\",\\n          \\"sqlExpression\\" : \\"normalize_invalid_isbns(jsonb->'identifiers')\\",\\n          \\"sqlExpressionQuery\\": \\"normalize_digits($)\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"isbn\\",\\n          \\"sqlExpression\\" : \\"normalize_isbns(jsonb->'identifiers')\\",\\n          \\"sqlExpressionQuery\\": \\"normalize_digits($)\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance_relationship\\",\\n      \\"withMetadata\\": true,\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"superInstanceId\\",\\n          \\"targetTable\\": \\"instance\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"subInstanceId\\",\\n          \\"targetTable\\": \\"instance\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"instanceRelationshipTypeId\\",\\n          \\"targetTable\\": \\"instance_relationship_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance_source_marc\\",\\n      \\"withMetadata\\": true,\\n      \\"customSnippetPath\\": \\"instanceSourceMarc.sql\\"\\n    },\\n    {\\n      \\"tableName\\": \\"ill_policy\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"call_number_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"holdings_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"authority_note_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"instance_note_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"holdings_note_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"item_note_type\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"item_damaged_status\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"holdings_records_source\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": false,\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"name\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"holdings_record\\",\\n      \\"fromModuleVersion\\": \\"19.2.0\\",\\n      \\"withMetadata\\": true,\\n      \\"withAuditing\\": true,\\n      \\"withOptimisticLocking\\": \\"failOnConflict\\",\\n      \\"auditingTableName\\": \\"audit_holdings_record\\",\\n      \\"auditingFieldName\\": \\"record\\",\\n      \\"customSnippetPath\\": \\"audit-delete-trigger.sql\\",\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"instanceId\\",\\n          \\"targetTable\\": \\"instance\\",\\n          \\"tableAlias\\" : \\"holdingsRecords\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"permanentLocationId\\",\\n          \\"targetTable\\": \\"location\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"temporaryLocationId\\",\\n          \\"targetTable\\": \\"location\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"effectiveLocationId\\",\\n          \\"targetTable\\": \\"location\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"holdingsTypeId\\",\\n          \\"targetTable\\": \\"holdings_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"callNumberTypeId\\",\\n          \\"targetTable\\": \\"call_number_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"illPolicyId\\",\\n          \\"targetTable\\": \\"ill_policy\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"sourceId\\",\\n          \\"targetTable\\": \\"holdings_records_source\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"hrid\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"ginIndex\\": [\\n      ],\\n      \\"index\\": [\\n        {\\n          \\"fieldName\\": \\"callNumber\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"callNumberAndSuffix\\",\\n          \\"multiFieldNames\\": \\"callNumber, callNumberSuffix\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"fullCallNumber\\",\\n          \\"multiFieldNames\\": \\"callNumberPrefix, callNumber, callNumberSuffix\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"discoverySuppress\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        }\\n      ],\\n      \\"fullTextIndex\\": [\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"item\\",\\n      \\"withMetadata\\": true,\\n      \\"fromModuleVersion\\": \\"19.2.0\\",\\n      \\"withAuditing\\": true,\\n      \\"withOptimisticLocking\\": \\"failOnConflict\\",\\n      \\"auditingTableName\\": \\"audit_item\\",\\n      \\"auditingFieldName\\": \\"record\\",\\n      \\"customSnippetPath\\": \\"audit-delete-trigger.sql\\",\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"holdingsRecordId\\",\\n          \\"targetTable\\": \\"holdings_record\\",\\n          \\"targetTableAlias\\" : \\"holdingsRecords\\",\\n          \\"tableAlias\\": \\"item\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"targetPath\\": [\\"holdingsRecordId\\", \\"instanceId\\"],\\n          \\"targetTable\\":      \\"instance\\",\\n          \\"targetTableAlias\\": \\"instance\\",\\n          \\"tableAlias\\": \\"item\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"permanentLoanTypeId\\",\\n          \\"targetTable\\": \\"loan_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"temporaryLoanTypeId\\",\\n          \\"targetTable\\": \\"loan_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"materialTypeId\\",\\n          \\"targetTable\\": \\"material_type\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"permanentLocationId\\",\\n          \\"targetTable\\": \\"location\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"temporaryLocationId\\",\\n          \\"targetTable\\": \\"location\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"effectiveLocationId\\",\\n          \\"targetTable\\": \\"location\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n\\n      ],\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"barcode\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"hrid\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"index\\": [\\n        {\\n          \\"fieldName\\": \\"accessionNumber\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"status.name\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": true\\n        },\\n        {\\n          \\"fieldName\\": \\"callNumberAndSuffix\\",\\n          \\"multiFieldNames\\": \\"effectiveCallNumberComponents.callNumber, effectiveCallNumberComponents.suffix\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"fullCallNumber\\",\\n          \\"multiFieldNames\\": \\"effectiveCallNumberComponents.prefix, effectiveCallNumberComponents.callNumber, effectiveCallNumberComponents.suffix\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"discoverySuppress\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"purchaseOrderLineIdentifier\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        },\\n        {\\n          \\"fieldName\\": \\"effectiveCallNumberComponents.callNumber\\",\\n          \\"tOps\\": \\"ADD\\",\\n          \\"caseSensitive\\": false,\\n          \\"removeAccents\\": false\\n        }\\n      ],\\n      \\"ginIndex\\": [\\n      ],\\n      \\"fullTextIndex\\": [\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"hrid_settings\\",\\n      \\"fromModuleVersion\\": \\"17.1.0\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false,\\n      \\"customSnippetPath\\": \\"hridSettings.sql\\"\\n    },\\n    {\\n      \\"tableName\\": \\"preceding_succeeding_title\\",\\n      \\"fromModuleVersion\\": \\"19.0.0\\",\\n      \\"withMetadata\\": true,\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"precedingInstanceId\\",\\n          \\"targetTable\\": \\"instance\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"succeedingInstanceId\\",\\n          \\"targetTable\\": \\"instance\\"\\n        }\\n      ],\\n      \\"customSnippetPath\\": \\"alterPrecedingSucceedingTitle.sql\\"\\n    },\\n    {\\n      \\"tableName\\": \\"reindex_job\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false\\n    },\\n    {\\n      \\"tableName\\": \\"bound_with_part\\",\\n      \\"withMetadata\\": true,\\n      \\"foreignKeys\\": [\\n        {\\n          \\"fieldName\\": \\"itemId\\",\\n          \\"targetTable\\": \\"item\\",\\n          \\"tOps\\": \\"ADD\\"\\n        },\\n        {\\n          \\"fieldName\\": \\"holdingsRecordId\\",\\n          \\"targetTable\\": \\"holdings_record\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ],\\n      \\"uniqueIndex\\": [\\n        {\\n          \\"fieldName\\": \\"itemId, holdingsRecordId\\",\\n          \\"tOps\\": \\"ADD\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"tableName\\": \\"notification_sending_error\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false\\n    },\\n    {\\n      \\"tableName\\": \\"iteration_job\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false\\n    },\\n    {\\n      \\"tableName\\": \\"async_migration_job\\",\\n      \\"withMetadata\\": false,\\n      \\"withAuditing\\": false\\n    }\\n  ],\\n  \\"scripts\\": [\\n    {\\n        \\"run\\":\\"after\\",\\n        \\"snippetPath\\":\\"setPreviouslyHeldDefault.sql\\",\\n        \\"fromModuleVersion\\":\\"20.2.0\\"\\n    },\\n    {\\n      \\"run\\": \\"before\\",\\n      \\"snippetPath\\": \\"populateRmbInternalIndex.sql\\",\\n      \\"fromModuleVersion\\": \\"19.1.1\\"\\n    },\\n    {\\n      \\"run\\": \\"before\\",\\n      \\"snippetPath\\": \\"createIsbnFunctions.sql\\",\\n      \\"fromModuleVersion\\": \\"19.2.0\\"\\n    },\\n    {\\n      \\"run\\":\\"after\\",\\n      \\"snippetPath\\":\\"setEffectiveHoldingsLocation.sql\\",\\n      \\"fromModuleVersion\\":\\"20.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"dropLegacyItemEffectiveLocationTriggers.sql\\",\\n      \\"fromModuleVersion\\": \\"19.5.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"populateRetainLeadingZeroesSetting.sql\\",\\n      \\"fromModuleVersion\\": \\"19.5.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"populateEffectiveLocationForExistingItems.sql\\",\\n      \\"fromModuleVersion\\": \\"17.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"populateEffectiveLocationForeignKey.sql\\",\\n      \\"fromModuleVersion\\": \\"18.2.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"populateEffectiveCallNumberComponentsForExistingItems.sql\\",\\n      \\"fromModuleVersion\\": \\"18.3.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"removeOldHridOperations.sql\\",\\n      \\"fromModuleVersion\\": \\"17.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"instanceStatusUpdatedDateTrigger.sql\\",\\n      \\"fromModuleVersion\\": \\"17.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"updateItemStatusDate.sql\\",\\n      \\"fromModuleVersion\\": \\"19.2.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"alterHridSequences.sql\\",\\n      \\"fromModuleVersion\\": \\"18.2.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"migrateItemCopyNumberToSingleValue.sql\\",\\n      \\"fromModuleVersion\\": \\"19.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"renameModesOfIssuance.sql\\",\\n      \\"fromModuleVersion\\": \\"19.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"migratePrecedingSucceedingTitles.sql\\",\\n      \\"fromModuleVersion\\": \\"19.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"populateDiscoverySuppressIfNotSet.sql\\",\\n      \\"fromModuleVersion\\": \\"19.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"oaipmh/createOaiPmhViewFunction.sql\\",\\n      \\"fromModuleVersion\\": \\"19.3.1\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"removeOldPrecedingSucceedingTitles.sql\\",\\n      \\"fromModuleVersion\\": \\"19.2.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"inventory-hierarchy/createRecordsViewFunction.sql\\",\\n      \\"fromModuleVersion\\": \\"19.4.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"updateIllPolicyWillNotLend.sql\\",\\n      \\"fromModuleVersion\\": \\"19.4.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"addIdentifierTypeCancelledSystemControlNumber.sql\\",\\n      \\"fromModuleVersion\\": \\"19.4.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"inventory-hierarchy/addNullChecksToRecordsViewFunctions.sql\\",\\n      \\"fromModuleVersion\\": \\"19.5.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"publication-period/publication-period-functions.sql\\",\\n      \\"fromModuleVersion\\": \\"23.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"instance-hr-item/instance-hr-item-view.sql\\",\\n      \\"fromModuleVersion\\": \\"23.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"inventory-hierarchy/addEffectiveHoldingsToItemsAndHoldingsView.sql\\",\\n      \\"fromModuleVersion\\": \\"20.3.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"addInstanceFormatsAudioBelt.sql\\",\\n      \\"fromModuleVersion\\": \\"20.3.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"dropLegacyItemEffectiveLocationFunctions.sql\\",\\n      \\"fromModuleVersion\\": \\"21.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"addIdentifierTypesUpcIsmn.sql\\",\\n      \\"fromModuleVersion\\": \\"21.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"inventory-hierarchy/addHoldingsIfItemsSuppressedItemsAndHoldingsView.sql\\",\\n      \\"fromModuleVersion\\": \\"22.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"itemStatisticalCodeReferenceCheckTrigger.sql\\",\\n      \\"fromModuleVersion\\": \\"22.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"statisticalCodeTypesReferenceCheckTrigger.sql\\",\\n      \\"fromModuleVersion\\": \\"23.0.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"statisticalCodeIdReferenceCheckTrigger.sql\\",\\n      \\"fromModuleVersion\\": \\"23.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"changeUUIDCastInGetStatisticalCodesFunction.sql\\",\\n      \\"fromModuleVersion\\": \\"23.1.0\\"\\n    },\\n    {\\n      \\"run\\": \\"after\\",\\n      \\"snippetPath\\": \\"inventory-hierarchy/correctGetUpdatedInstanceIdsView.sql\\",\\n      \\"fromModuleVersion\\": \\"23.1.0\\"\\n    },\\n    {\\n        \\"run\\":\\"after\\",\\n        \\"snippetPath\\":\\"dropCallNumberNormalizationFunctions.sql\\",\\n        \\"fromModuleVersion\\":\\"24.0.0\\"\\n    }\\n  ]\\n}\\n", "moduleVersion": "mod-inventory-storage-999.0.0"}
\.


--
-- Data for Name: rmb_internal_analyze; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.rmb_internal_analyze (tablename) FROM stdin;
\.


--
-- Data for Name: rmb_internal_index; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.rmb_internal_index (name, def, remove) FROM stdin;
loan_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS loan_type_name_idx_unique ON lotus_mod_inventory_storage.loan_type (lower(f_unaccent(jsonb->>'name')))	f
material_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS material_type_name_idx_unique ON lotus_mod_inventory_storage.material_type (lower(f_unaccent(jsonb->>'name')))	f
locinstitution_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS locinstitution_name_idx_unique ON lotus_mod_inventory_storage.locinstitution (lower(f_unaccent(jsonb->>'name')))	f
loccampus_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS loccampus_name_idx_unique ON lotus_mod_inventory_storage.loccampus (lower(f_unaccent(jsonb->>'name')))	f
loclibrary_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS loclibrary_name_idx_unique ON lotus_mod_inventory_storage.loclibrary (lower(f_unaccent(jsonb->>'name')))	f
location_primaryServicePoint_idx	CREATE INDEX IF NOT EXISTS location_primaryServicePoint_idx ON lotus_mod_inventory_storage.location (left(lower(jsonb->>'primaryServicePoint'),600))	f
location_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS location_name_idx_unique ON lotus_mod_inventory_storage.location (lower(f_unaccent(jsonb->>'name')))	f
location_code_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS location_code_idx_unique ON lotus_mod_inventory_storage.location (lower(f_unaccent(jsonb->>'code')))	f
service_point_pickupLocation_idx	CREATE INDEX IF NOT EXISTS service_point_pickupLocation_idx ON lotus_mod_inventory_storage.service_point (left(lower(jsonb->>'pickupLocation'),600))	f
service_point_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS service_point_name_idx_unique ON lotus_mod_inventory_storage.service_point (lower(f_unaccent(jsonb->>'name')))	f
service_point_code_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS service_point_code_idx_unique ON lotus_mod_inventory_storage.service_point (lower(f_unaccent(jsonb->>'code')))	f
service_point_user_userId_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS service_point_user_userId_idx_unique ON lotus_mod_inventory_storage.service_point_user (lower(f_unaccent(jsonb->>'userId')))	f
identifier_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS identifier_type_name_idx_unique ON lotus_mod_inventory_storage.identifier_type (lower(f_unaccent(jsonb->>'name')))	f
instance_relationship_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_relationship_type_name_idx_unique ON lotus_mod_inventory_storage.instance_relationship_type (lower(f_unaccent(jsonb->>'name')))	f
contributor_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS contributor_type_name_idx_unique ON lotus_mod_inventory_storage.contributor_type (lower(f_unaccent(jsonb->>'name')))	f
contributor_type_code_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS contributor_type_code_idx_unique ON lotus_mod_inventory_storage.contributor_type (lower(f_unaccent(jsonb->>'code')))	f
contributor_name_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS contributor_name_type_name_idx_unique ON lotus_mod_inventory_storage.contributor_name_type (lower(f_unaccent(jsonb->>'name')))	f
instance_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_type_name_idx_unique ON lotus_mod_inventory_storage.instance_type (lower(f_unaccent(jsonb->>'name')))	f
instance_type_code_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_type_code_idx_unique ON lotus_mod_inventory_storage.instance_type (lower(f_unaccent(jsonb->>'code')))	f
instance_format_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_format_name_idx_unique ON lotus_mod_inventory_storage.instance_format (lower(f_unaccent(jsonb->>'name')))	f
instance_format_code_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_format_code_idx_unique ON lotus_mod_inventory_storage.instance_format (lower(f_unaccent(jsonb->>'code')))	f
nature_of_content_term_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS nature_of_content_term_name_idx_unique ON lotus_mod_inventory_storage.nature_of_content_term (lower(f_unaccent(jsonb->>'name')))	f
classification_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS classification_type_name_idx_unique ON lotus_mod_inventory_storage.classification_type (lower(f_unaccent(jsonb->>'name')))	f
electronic_access_relationship_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS electronic_access_relationship_name_idx_unique ON lotus_mod_inventory_storage.electronic_access_relationship (lower(f_unaccent(jsonb->>'name')))	f
statistical_code_type_code_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS statistical_code_type_code_idx_unique ON lotus_mod_inventory_storage.statistical_code_type (lower(f_unaccent(jsonb->>'code')))	f
statistical_code_code_statisticalCodeTypeId_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS statistical_code_code_statisticalCodeTypeId_idx_unique ON lotus_mod_inventory_storage.statistical_code (lower(f_unaccent(jsonb->>'code')) , lower(f_unaccent(jsonb->>'statisticalCodeTypeId')))	f
statistical_code_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS statistical_code_name_idx_unique ON lotus_mod_inventory_storage.statistical_code (lower(f_unaccent(jsonb->>'name')))	f
instance_status_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_status_name_idx_unique ON lotus_mod_inventory_storage.instance_status (lower(f_unaccent(jsonb->>'name')))	f
instance_status_code_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_status_code_idx_unique ON lotus_mod_inventory_storage.instance_status (lower(f_unaccent(jsonb->>'code')))	f
mode_of_issuance_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS mode_of_issuance_name_idx_unique ON lotus_mod_inventory_storage.mode_of_issuance (lower(f_unaccent(jsonb->>'name')))	f
alternative_title_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS alternative_title_type_name_idx_unique ON lotus_mod_inventory_storage.alternative_title_type (lower(f_unaccent(jsonb->>'name')))	f
instance_source_idx	CREATE INDEX IF NOT EXISTS instance_source_idx ON lotus_mod_inventory_storage.instance (left(lower(jsonb->>'source'),600))	f
instance_indexTitle_idx	CREATE INDEX IF NOT EXISTS instance_indexTitle_idx ON lotus_mod_inventory_storage.instance (left(lower(f_unaccent(jsonb->>'indexTitle')),600))	f
instance_title_idx	CREATE INDEX IF NOT EXISTS instance_title_idx ON lotus_mod_inventory_storage.instance (left(lower(f_unaccent(jsonb->>'title')),600))	f
instance_statisticalCodeIds_idx	CREATE INDEX IF NOT EXISTS instance_statisticalCodeIds_idx ON lotus_mod_inventory_storage.instance (left(lower(jsonb->>'statisticalCodeIds'),600))	f
instance_contributors_idx	CREATE INDEX IF NOT EXISTS instance_contributors_idx ON lotus_mod_inventory_storage.instance (left(lower(f_unaccent(jsonb->>'contributors')),600))	f
instance_publication_idx	CREATE INDEX IF NOT EXISTS instance_publication_idx ON lotus_mod_inventory_storage.instance (left(lower(f_unaccent(jsonb->>'publication')),600))	f
instance_staffSuppress_idx	CREATE INDEX IF NOT EXISTS instance_staffSuppress_idx ON lotus_mod_inventory_storage.instance (left(lower(jsonb->>'staffSuppress'),600))	f
instance_discoverySuppress_idx	CREATE INDEX IF NOT EXISTS instance_discoverySuppress_idx ON lotus_mod_inventory_storage.instance (left(lower(jsonb->>'discoverySuppress'),600))	f
instance_metadata_updatedDate_idx	CREATE INDEX IF NOT EXISTS instance_metadata_updatedDate_idx ON lotus_mod_inventory_storage.instance (left(lower(jsonb->'metadata'->>'updatedDate'),600))	f
instance_hrid_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_hrid_idx_unique ON lotus_mod_inventory_storage.instance (lower(f_unaccent(jsonb->>'hrid')))	f
instance_matchKey_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_matchKey_idx_unique ON lotus_mod_inventory_storage.instance (lower(f_unaccent(jsonb->>'matchKey')))	f
instance_identifiers_idx_gin	CREATE INDEX IF NOT EXISTS instance_identifiers_idx_gin ON lotus_mod_inventory_storage.instance USING GIN ((lower(f_unaccent(jsonb->>'identifiers'))) public.gin_trgm_ops)	f
instance_identifiers_idx_ft	CREATE INDEX IF NOT EXISTS instance_identifiers_idx_ft ON lotus_mod_inventory_storage.instance USING GIN ( get_tsvector(f_unaccent(jsonb->>'identifiers')) )	f
instance_invalidIsbn_idx_ft	CREATE INDEX IF NOT EXISTS instance_invalidIsbn_idx_ft ON lotus_mod_inventory_storage.instance USING GIN ( get_tsvector(normalize_invalid_isbns(jsonb->'identifiers')) )	f
instance_isbn_idx_ft	CREATE INDEX IF NOT EXISTS instance_isbn_idx_ft ON lotus_mod_inventory_storage.instance USING GIN ( get_tsvector(normalize_isbns(jsonb->'identifiers')) )	f
ill_policy_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS ill_policy_name_idx_unique ON lotus_mod_inventory_storage.ill_policy (lower(f_unaccent(jsonb->>'name')))	f
call_number_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS call_number_type_name_idx_unique ON lotus_mod_inventory_storage.call_number_type (lower(f_unaccent(jsonb->>'name')))	f
holdings_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS holdings_type_name_idx_unique ON lotus_mod_inventory_storage.holdings_type (lower(f_unaccent(jsonb->>'name')))	f
authority_note_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS authority_note_type_name_idx_unique ON lotus_mod_inventory_storage.authority_note_type (lower(f_unaccent(jsonb->>'name')))	f
instance_note_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS instance_note_type_name_idx_unique ON lotus_mod_inventory_storage.instance_note_type (lower(f_unaccent(jsonb->>'name')))	f
holdings_note_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS holdings_note_type_name_idx_unique ON lotus_mod_inventory_storage.holdings_note_type (lower(f_unaccent(jsonb->>'name')))	f
item_note_type_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS item_note_type_name_idx_unique ON lotus_mod_inventory_storage.item_note_type (lower(f_unaccent(jsonb->>'name')))	f
item_damaged_status_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS item_damaged_status_name_idx_unique ON lotus_mod_inventory_storage.item_damaged_status (lower(f_unaccent(jsonb->>'name')))	f
holdings_records_source_name_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS holdings_records_source_name_idx_unique ON lotus_mod_inventory_storage.holdings_records_source (lower(f_unaccent(jsonb->>'name')))	f
holdings_record_callNumber_idx	CREATE INDEX IF NOT EXISTS holdings_record_callNumber_idx ON lotus_mod_inventory_storage.holdings_record (left(lower(jsonb->>'callNumber'),600))	f
holdings_record_callNumberAndSuffix_idx	CREATE INDEX IF NOT EXISTS holdings_record_callNumberAndSuffix_idx ON lotus_mod_inventory_storage.holdings_record (left(lower(concat_space_sql(holdings_record.jsonb->>'callNumber' , holdings_record.jsonb->>'callNumberSuffix')),600))	f
holdings_record_fullCallNumber_idx	CREATE INDEX IF NOT EXISTS holdings_record_fullCallNumber_idx ON lotus_mod_inventory_storage.holdings_record (left(lower(concat_space_sql(holdings_record.jsonb->>'callNumberPrefix' , holdings_record.jsonb->>'callNumber' , holdings_record.jsonb->>'callNumberSuffix')),600))	f
holdings_record_discoverySuppress_idx	CREATE INDEX IF NOT EXISTS holdings_record_discoverySuppress_idx ON lotus_mod_inventory_storage.holdings_record (left(lower(jsonb->>'discoverySuppress'),600))	f
holdings_record_hrid_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS holdings_record_hrid_idx_unique ON lotus_mod_inventory_storage.holdings_record (lower(f_unaccent(jsonb->>'hrid')))	f
item_accessionNumber_idx	CREATE INDEX IF NOT EXISTS item_accessionNumber_idx ON lotus_mod_inventory_storage.item (left(lower(jsonb->>'accessionNumber'),600))	f
item_status_name_idx	CREATE INDEX IF NOT EXISTS item_status_name_idx ON lotus_mod_inventory_storage.item (left(lower(f_unaccent(jsonb->'status'->>'name')),600))	f
item_callNumberAndSuffix_idx	CREATE INDEX IF NOT EXISTS item_callNumberAndSuffix_idx ON lotus_mod_inventory_storage.item (left(lower(concat_space_sql(item.jsonb->'effectiveCallNumberComponents'->>'callNumber' , item.jsonb->'effectiveCallNumberComponents'->>'suffix')),600))	f
item_fullCallNumber_idx	CREATE INDEX IF NOT EXISTS item_fullCallNumber_idx ON lotus_mod_inventory_storage.item (left(lower(concat_space_sql(item.jsonb->'effectiveCallNumberComponents'->>'prefix' , item.jsonb->'effectiveCallNumberComponents'->>'callNumber' , item.jsonb->'effectiveCallNumberComponents'->>'suffix')),600))	f
item_discoverySuppress_idx	CREATE INDEX IF NOT EXISTS item_discoverySuppress_idx ON lotus_mod_inventory_storage.item (left(lower(jsonb->>'discoverySuppress'),600))	f
item_purchaseOrderLineIdentifier_idx	CREATE INDEX IF NOT EXISTS item_purchaseOrderLineIdentifier_idx ON lotus_mod_inventory_storage.item (left(lower(jsonb->>'purchaseOrderLineIdentifier'),600))	f
item_effectiveCallNumberComponents_callNumber_idx	CREATE INDEX IF NOT EXISTS item_effectiveCallNumberComponents_callNumber_idx ON lotus_mod_inventory_storage.item (left(lower(jsonb->'effectiveCallNumberComponents'->>'callNumber'),600))	f
item_barcode_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS item_barcode_idx_unique ON lotus_mod_inventory_storage.item (lower(jsonb->>'barcode'))	f
item_hrid_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS item_hrid_idx_unique ON lotus_mod_inventory_storage.item (lower(f_unaccent(jsonb->>'hrid')))	f
bound_with_part_itemId_holdingsRecordId_idx_unique	CREATE UNIQUE INDEX IF NOT EXISTS bound_with_part_itemId_holdingsRecordId_idx_unique ON lotus_mod_inventory_storage.bound_with_part (lower(f_unaccent(jsonb->>'itemId')) , lower(f_unaccent(jsonb->>'holdingsRecordId')))	f
\.


--
-- Data for Name: rmb_job; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.rmb_job (id, jsonb) FROM stdin;
00d24c21-4c98-4917-b66b-330e6faffb55	{"id": "00d24c21-4c98-4917-b66b-330e6faffb55", "tenant": "lotus", "complete": true, "messages": [], "tenantAttributes": {"module_to": "mod-inventory-storage-999.0.0", "parameters": [{"key": "loadReference", "value": "true"}, {"key": "loadSample", "value": "true"}]}}
\.


--
-- Data for Name: service_point; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.service_point (id, jsonb, creation_date, created_by) FROM stdin;
7c5abc9f-f3d7-4856-b8d7-6712462ca007	{"id": "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "code": "Online", "name": "Online", "metadata": {"createdDate": "2022-06-23T15:13:59.224Z", "updatedDate": "2022-06-23T15:13:59.224Z"}, "staffSlips": [], "pickupLocation": false, "shelvingLagTime": 0, "discoveryDisplayName": "Online"}	2022-06-23 15:13:59.224	\N
c4c90014-c8c9-4ade-8f24-b5e313319f4b	{"id": "c4c90014-c8c9-4ade-8f24-b5e313319f4b", "code": "cd2", "name": "Circ Desk 2", "metadata": {"createdDate": "2022-06-23T15:13:59.225Z", "updatedDate": "2022-06-23T15:13:59.225Z"}, "staffSlips": [], "pickupLocation": true, "discoveryDisplayName": "Circulation Desk -- Back Entrance", "holdShelfExpiryPeriod": {"duration": 5, "intervalId": "Days"}}	2022-06-23 15:13:59.225	\N
3a40852d-49fd-4df2-a1f9-6e2641a6e91f	{"id": "3a40852d-49fd-4df2-a1f9-6e2641a6e91f", "code": "cd1", "name": "Circ Desk 1", "metadata": {"createdDate": "2022-06-23T15:13:59.228Z", "updatedDate": "2022-06-23T15:13:59.228Z"}, "staffSlips": [], "pickupLocation": true, "discoveryDisplayName": "Circulation Desk -- Hallway", "holdShelfExpiryPeriod": {"duration": 3, "intervalId": "Weeks"}}	2022-06-23 15:13:59.228	\N
\.


--
-- Data for Name: service_point_user; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.service_point_user (id, jsonb, creation_date, created_by, defaultservicepointid) FROM stdin;
29331180-f398-4cf2-b2d9-70bd6c59a7fc	{"id": "29331180-f398-4cf2-b2d9-70bd6c59a7fc", "userId": "3ada4a0c-e554-4749-8809-fee35fe2c7ad", "metadata": {"createdDate": "2022-06-23T15:14:01.486Z", "updatedDate": "2022-06-23T15:14:01.486Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.486	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
dc6eb69c-48dc-41e8-8cb2-9120ead8a7b4	{"id": "dc6eb69c-48dc-41e8-8cb2-9120ead8a7b4", "userId": "f1dc9a7e-492b-4f2b-848a-115b5919d589", "metadata": {"createdDate": "2022-06-23T15:14:01.485Z", "updatedDate": "2022-06-23T15:14:01.485Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.485	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c9931dc5-3ac0-4108-af3e-eef69b350d85	{"id": "c9931dc5-3ac0-4108-af3e-eef69b350d85", "userId": "956f39c5-92e3-4c26-bcdc-1827674710cf", "metadata": {"createdDate": "2022-06-23T15:14:01.484Z", "updatedDate": "2022-06-23T15:14:01.484Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.484	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8a373f62-c440-4079-82e3-8a2af2ff53bb	{"id": "8a373f62-c440-4079-82e3-8a2af2ff53bb", "userId": "a0dadce9-06ed-4b23-9fc0-6b5238aa92d8", "metadata": {"createdDate": "2022-06-23T15:14:01.484Z", "updatedDate": "2022-06-23T15:14:01.484Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.484	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ae33c288-0ce5-445f-8d7d-0a491ed9a54a	{"id": "ae33c288-0ce5-445f-8d7d-0a491ed9a54a", "userId": "6302b991-3223-4bc7-ae66-795d161f64ab", "metadata": {"createdDate": "2022-06-23T15:14:01.486Z", "updatedDate": "2022-06-23T15:14:01.486Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.486	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c86a406c-83ef-4afb-8fa7-c7977fe44857	{"id": "c86a406c-83ef-4afb-8fa7-c7977fe44857", "userId": "87c329f1-2220-4a8a-b750-ded39bbe9769", "metadata": {"createdDate": "2022-06-23T15:14:01.493Z", "updatedDate": "2022-06-23T15:14:01.493Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.493	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8670b32f-6d5f-4fc9-936d-b6e82937648b	{"id": "8670b32f-6d5f-4fc9-936d-b6e82937648b", "userId": "8f7a47c4-d66f-4dba-9255-f74507a2ecee", "metadata": {"createdDate": "2022-06-23T15:14:01.509Z", "updatedDate": "2022-06-23T15:14:01.509Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.509	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0aa4617e-4f7e-4b8a-8593-adc13a5c7078	{"id": "0aa4617e-4f7e-4b8a-8593-adc13a5c7078", "userId": "45e77e83-60a3-4031-89d5-81222043dec6", "metadata": {"createdDate": "2022-06-23T15:14:01.499Z", "updatedDate": "2022-06-23T15:14:01.499Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.499	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d79c1b6a-8b06-4c8f-b089-c54fc2849636	{"id": "d79c1b6a-8b06-4c8f-b089-c54fc2849636", "userId": "e505acd3-925e-4e2c-a255-8d11e25ba046", "metadata": {"createdDate": "2022-06-23T15:14:01.500Z", "updatedDate": "2022-06-23T15:14:01.500Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.5	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
bad67e54-c54e-40fb-9d97-5547b4dfdfef	{"id": "bad67e54-c54e-40fb-9d97-5547b4dfdfef", "userId": "2075c729-a9b8-43db-860c-60a3cc31a949", "metadata": {"createdDate": "2022-06-23T15:14:01.515Z", "updatedDate": "2022-06-23T15:14:01.515Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.515	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0fba3546-9ec8-4cae-99aa-2dd43538f185	{"id": "0fba3546-9ec8-4cae-99aa-2dd43538f185", "userId": "48861bba-0d73-4277-8f44-f3b65b038017", "metadata": {"createdDate": "2022-06-23T15:14:01.516Z", "updatedDate": "2022-06-23T15:14:01.516Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.516	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f493d49c-36af-49aa-b9e4-8b25d3573f62	{"id": "f493d49c-36af-49aa-b9e4-8b25d3573f62", "userId": "2884afb0-5bec-45c4-b9f4-0bc525bc0322", "metadata": {"createdDate": "2022-06-23T15:14:01.530Z", "updatedDate": "2022-06-23T15:14:01.530Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.53	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6ebdae1d-f9f5-4484-80b0-e604a8e5506c	{"id": "6ebdae1d-f9f5-4484-80b0-e604a8e5506c", "userId": "dc3cd5a5-4235-48c3-b9d3-a958863f7498", "metadata": {"createdDate": "2022-06-23T15:14:01.536Z", "updatedDate": "2022-06-23T15:14:01.536Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.536	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b5075891-aab9-47ee-9835-de7e033bc05b	{"id": "b5075891-aab9-47ee-9835-de7e033bc05b", "userId": "63ff6975-5d7f-46c1-983a-dba27d163c4a", "metadata": {"createdDate": "2022-06-23T15:14:01.537Z", "updatedDate": "2022-06-23T15:14:01.537Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.537	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
bd036630-1e78-484b-8c99-61376e8814df	{"id": "bd036630-1e78-484b-8c99-61376e8814df", "userId": "e76cf4c9-e15a-414e-81b9-672a90fb2745", "metadata": {"createdDate": "2022-06-23T15:14:01.539Z", "updatedDate": "2022-06-23T15:14:01.539Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.539	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e05e57e8-6051-491e-baa7-d4ef94d3def9	{"id": "e05e57e8-6051-491e-baa7-d4ef94d3def9", "userId": "89066e1d-0691-4514-ae37-586cf746d3f4", "metadata": {"createdDate": "2022-06-23T15:14:01.540Z", "updatedDate": "2022-06-23T15:14:01.540Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.54	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b98204d4-f226-4c70-b611-65676a210470	{"id": "b98204d4-f226-4c70-b611-65676a210470", "userId": "d4849a05-4066-4129-ae56-3dfc39498e36", "metadata": {"createdDate": "2022-06-23T15:14:01.546Z", "updatedDate": "2022-06-23T15:14:01.546Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.546	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
61fb2991-7e5e-44e4-bf23-b87464b77243	{"id": "61fb2991-7e5e-44e4-bf23-b87464b77243", "userId": "260f1870-7dee-452d-a379-301f063febda", "metadata": {"createdDate": "2022-06-23T15:14:01.556Z", "updatedDate": "2022-06-23T15:14:01.556Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.556	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
09983b7c-0fcc-4cfd-96c1-a52bfc4ee2b2	{"id": "09983b7c-0fcc-4cfd-96c1-a52bfc4ee2b2", "userId": "c2f1cefa-2ebb-4a7a-a420-84dcf2f89cf5", "metadata": {"createdDate": "2022-06-23T15:14:01.561Z", "updatedDate": "2022-06-23T15:14:01.561Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.561	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
316c00ce-bd27-41eb-9802-30840a57319d	{"id": "316c00ce-bd27-41eb-9802-30840a57319d", "userId": "2e5f9cc4-46ab-4dfe-b40a-8493296353fb", "metadata": {"createdDate": "2022-06-23T15:14:01.566Z", "updatedDate": "2022-06-23T15:14:01.566Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.566	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
11cc91ec-6a51-4bc4-933a-8a7ca6564ca7	{"id": "11cc91ec-6a51-4bc4-933a-8a7ca6564ca7", "userId": "86344e52-979a-45da-ad44-9edcc05c5312", "metadata": {"createdDate": "2022-06-23T15:14:01.572Z", "updatedDate": "2022-06-23T15:14:01.572Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.572	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1612bbc2-e609-4c7d-a7cf-ea47c6be1ee3	{"id": "1612bbc2-e609-4c7d-a7cf-ea47c6be1ee3", "userId": "2a424823-588a-45ee-9441-a6384b6614b2", "metadata": {"createdDate": "2022-06-23T15:14:01.578Z", "updatedDate": "2022-06-23T15:14:01.578Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.578	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
bebeff61-d63f-499d-b044-d1d17641cd2e	{"id": "bebeff61-d63f-499d-b044-d1d17641cd2e", "userId": "c1277b9b-b165-48ee-ac35-e737ed325f34", "metadata": {"createdDate": "2022-06-23T15:14:01.585Z", "updatedDate": "2022-06-23T15:14:01.585Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.585	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1d90162c-b4b2-43ed-a454-303df9a16297	{"id": "1d90162c-b4b2-43ed-a454-303df9a16297", "userId": "8616dd12-e244-4047-834a-db0c6cd1477b", "metadata": {"createdDate": "2022-06-23T15:14:01.590Z", "updatedDate": "2022-06-23T15:14:01.590Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.59	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
26ce7bed-efea-49e1-a036-9293363aeffa	{"id": "26ce7bed-efea-49e1-a036-9293363aeffa", "userId": "e5e950e0-3f56-4ff1-8e86-671cdbc37688", "metadata": {"createdDate": "2022-06-23T15:14:01.597Z", "updatedDate": "2022-06-23T15:14:01.597Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.597	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e61a83e8-3e6a-48d7-9dd6-ead0c8f1aa89	{"id": "e61a83e8-3e6a-48d7-9dd6-ead0c8f1aa89", "userId": "c0af9380-d277-4820-a607-15a2d9c50ba6", "metadata": {"createdDate": "2022-06-23T15:14:01.602Z", "updatedDate": "2022-06-23T15:14:01.602Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.602	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d59a081d-f4db-4487-a4c0-6add0f35389e	{"id": "d59a081d-f4db-4487-a4c0-6add0f35389e", "userId": "28724f2b-89b3-4a05-839c-2e77138e01a3", "metadata": {"createdDate": "2022-06-23T15:14:01.613Z", "updatedDate": "2022-06-23T15:14:01.613Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.613	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
5c18e2da-9e4e-4bbe-a87e-6ee2c3c5c605	{"id": "5c18e2da-9e4e-4bbe-a87e-6ee2c3c5c605", "userId": "5bec815c-72b9-452f-ac19-bc2793c94537", "metadata": {"createdDate": "2022-06-23T15:14:01.623Z", "updatedDate": "2022-06-23T15:14:01.623Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.623	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c05bd7cb-71ac-42ca-a9d6-81d0b9dd26ea	{"id": "c05bd7cb-71ac-42ca-a9d6-81d0b9dd26ea", "userId": "51e1e298-db10-465b-8c20-7f3d1e929834", "metadata": {"createdDate": "2022-06-23T15:14:01.630Z", "updatedDate": "2022-06-23T15:14:01.630Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.63	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e315edfa-ee05-4f6d-a76f-3ded0d430eb9	{"id": "e315edfa-ee05-4f6d-a76f-3ded0d430eb9", "userId": "30d7e2dd-db23-4832-b4be-30d3f5f83a60", "metadata": {"createdDate": "2022-06-23T15:14:01.638Z", "updatedDate": "2022-06-23T15:14:01.638Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.638	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c9459c74-e485-4473-8f45-e7200f711530	{"id": "c9459c74-e485-4473-8f45-e7200f711530", "userId": "ae2f6ce7-386a-4c5a-9fcf-e5f517a88ced", "metadata": {"createdDate": "2022-06-23T15:14:01.647Z", "updatedDate": "2022-06-23T15:14:01.647Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.647	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
418c05ac-6591-430a-bdd0-b9c76585fbaa	{"id": "418c05ac-6591-430a-bdd0-b9c76585fbaa", "userId": "be3113c3-0965-4bf4-97c2-40ff54501c2b", "metadata": {"createdDate": "2022-06-23T15:14:01.661Z", "updatedDate": "2022-06-23T15:14:01.661Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.661	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
59d1f5ab-28a6-4d1d-862a-db47879777ec	{"id": "59d1f5ab-28a6-4d1d-862a-db47879777ec", "userId": "70e0c050-e842-4eee-9632-967a49e43bb2", "metadata": {"createdDate": "2022-06-23T15:14:01.548Z", "updatedDate": "2022-06-23T15:14:01.548Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.548	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
eb792bd9-537d-4cc9-8634-fafe92f9d9d5	{"id": "eb792bd9-537d-4cc9-8634-fafe92f9d9d5", "userId": "17b26a4a-e481-4b86-8949-5ef6570eb622", "metadata": {"createdDate": "2022-06-23T15:14:01.557Z", "updatedDate": "2022-06-23T15:14:01.557Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.557	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c52ea7ab-098f-409b-a7c4-7cf25f04efcf	{"id": "c52ea7ab-098f-409b-a7c4-7cf25f04efcf", "userId": "c2a2f428-ab5f-46ce-b3ed-7d0ab39a0096", "metadata": {"createdDate": "2022-06-23T15:14:01.562Z", "updatedDate": "2022-06-23T15:14:01.562Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.562	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6456b799-2ed4-49b9-9549-745286e7b6e2	{"id": "6456b799-2ed4-49b9-9549-745286e7b6e2", "userId": "c0d4a2da-7c38-46f4-869c-797bb083ee2d", "metadata": {"createdDate": "2022-06-23T15:14:01.568Z", "updatedDate": "2022-06-23T15:14:01.568Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.568	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f2705695-876c-4498-a585-3cf98c91f080	{"id": "f2705695-876c-4498-a585-3cf98c91f080", "userId": "6e74dfe1-2eca-48bd-89ce-9fe1633920a3", "metadata": {"createdDate": "2022-06-23T15:14:01.575Z", "updatedDate": "2022-06-23T15:14:01.575Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.575	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3454fdc3-60e2-4057-9b0f-0fdd55b7d97b	{"id": "3454fdc3-60e2-4057-9b0f-0fdd55b7d97b", "userId": "55e09c25-0a7b-4df8-8bde-a8964b57ef40", "metadata": {"createdDate": "2022-06-23T15:14:01.586Z", "updatedDate": "2022-06-23T15:14:01.586Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.586	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6663caf3-a99e-4178-8b37-06c5b1b24b74	{"id": "6663caf3-a99e-4178-8b37-06c5b1b24b74", "userId": "62b25727-310f-4fa3-b308-666a6cf28c97", "metadata": {"createdDate": "2022-06-23T15:14:01.598Z", "updatedDate": "2022-06-23T15:14:01.598Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.598	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4aa923cf-e8df-4e4f-8f1a-4236aa9582b9	{"id": "4aa923cf-e8df-4e4f-8f1a-4236aa9582b9", "userId": "e308411d-773e-416e-be58-f16176c0549e", "metadata": {"createdDate": "2022-06-23T15:14:01.604Z", "updatedDate": "2022-06-23T15:14:01.604Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.604	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c4f68108-4048-4e0c-980b-ddb5bc43bb47	{"id": "c4f68108-4048-4e0c-980b-ddb5bc43bb47", "userId": "457a3c37-cada-47ed-ae8d-c8eda723251f", "metadata": {"createdDate": "2022-06-23T15:14:01.613Z", "updatedDate": "2022-06-23T15:14:01.613Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.613	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1ed93289-a9c4-4b8f-a138-9c49b87a758b	{"id": "1ed93289-a9c4-4b8f-a138-9c49b87a758b", "userId": "cd5994cf-6ee5-49b4-b58e-7bc70d724626", "metadata": {"createdDate": "2022-06-23T15:14:01.627Z", "updatedDate": "2022-06-23T15:14:01.627Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.627	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ccb8c468-38b4-4820-bcd0-b06f21619083	{"id": "ccb8c468-38b4-4820-bcd0-b06f21619083", "userId": "f1c2d681-faba-4950-918f-bf58d914ba1f", "metadata": {"createdDate": "2022-06-23T15:14:01.635Z", "updatedDate": "2022-06-23T15:14:01.635Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.635	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
a4ef73d7-f0d4-4245-a518-88c768894a95	{"id": "a4ef73d7-f0d4-4245-a518-88c768894a95", "userId": "2a81b279-d459-4022-82f6-69a569d196b9", "metadata": {"createdDate": "2022-06-23T15:14:01.648Z", "updatedDate": "2022-06-23T15:14:01.648Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.648	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f77b4fa5-fc90-4379-b1ed-23059da1de99	{"id": "f77b4fa5-fc90-4379-b1ed-23059da1de99", "userId": "2120fe62-ba0a-4dce-8701-f360368d5c30", "metadata": {"createdDate": "2022-06-23T15:14:01.655Z", "updatedDate": "2022-06-23T15:14:01.655Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.655	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0d0fda0c-904a-4f48-aa0a-1a8e458d9452	{"id": "0d0fda0c-904a-4f48-aa0a-1a8e458d9452", "userId": "10cb9367-c095-4872-9add-8aecdf339dd4", "metadata": {"createdDate": "2022-06-23T15:14:01.664Z", "updatedDate": "2022-06-23T15:14:01.664Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.664	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
42e70dcb-9c9e-4dc8-beda-9bfbd1e53c06	{"id": "42e70dcb-9c9e-4dc8-beda-9bfbd1e53c06", "userId": "e0b7cb11-7d1f-48d8-b8a5-bc138550313d", "metadata": {"createdDate": "2022-06-23T15:14:01.681Z", "updatedDate": "2022-06-23T15:14:01.681Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.681	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
eadbf918-9214-4683-bf23-9d6d8eab378d	{"id": "eadbf918-9214-4683-bf23-9d6d8eab378d", "userId": "bf93cf45-4c02-4a34-aad0-9ed949109630", "metadata": {"createdDate": "2022-06-23T15:14:01.696Z", "updatedDate": "2022-06-23T15:14:01.696Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.696	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1df00ee9-1d42-41fd-a808-a2a7f7b29483	{"id": "1df00ee9-1d42-41fd-a808-a2a7f7b29483", "userId": "43f60acf-2557-48e9-b457-12783925444f", "metadata": {"createdDate": "2022-06-23T15:14:01.551Z", "updatedDate": "2022-06-23T15:14:01.551Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.551	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c7c5448a-766c-4705-81c5-cc4f099269d6	{"id": "c7c5448a-766c-4705-81c5-cc4f099269d6", "userId": "a8a11126-40b9-45f0-aa6e-9408e57c4b47", "metadata": {"createdDate": "2022-06-23T15:14:01.558Z", "updatedDate": "2022-06-23T15:14:01.558Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.558	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6630f0ba-e39e-474b-9a9b-652dbb3df13c	{"id": "6630f0ba-e39e-474b-9a9b-652dbb3df13c", "userId": "fa18e51c-50d9-4fe3-ab58-c592ea30328a", "metadata": {"createdDate": "2022-06-23T15:14:01.565Z", "updatedDate": "2022-06-23T15:14:01.565Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.565	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4197d0d6-7027-40f1-a7b4-4105864d4fa1	{"id": "4197d0d6-7027-40f1-a7b4-4105864d4fa1", "userId": "f7a0a518-6ff3-4531-b54b-e630d61aede0", "metadata": {"createdDate": "2022-06-23T15:14:01.575Z", "updatedDate": "2022-06-23T15:14:01.575Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.575	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
70f0b453-7f2f-41d9-8d7b-a4e46dfc7a60	{"id": "70f0b453-7f2f-41d9-8d7b-a4e46dfc7a60", "userId": "47f7eaea-1a18-4058-907c-62b7d095c61b", "metadata": {"createdDate": "2022-06-23T15:14:01.581Z", "updatedDate": "2022-06-23T15:14:01.581Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.581	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
468af6b0-6189-4cab-9894-520059833ce3	{"id": "468af6b0-6189-4cab-9894-520059833ce3", "userId": "f303e908-30dc-4139-9542-4a4e206c4b96", "metadata": {"createdDate": "2022-06-23T15:14:01.588Z", "updatedDate": "2022-06-23T15:14:01.588Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.588	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
71eb9604-57b7-41bf-89cf-3ae954500c15	{"id": "71eb9604-57b7-41bf-89cf-3ae954500c15", "userId": "b4e91548-c387-4b01-aaa1-489afc3f6936", "metadata": {"createdDate": "2022-06-23T15:14:01.595Z", "updatedDate": "2022-06-23T15:14:01.595Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.595	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9a197e7e-a6e8-4afb-9790-0835425a3397	{"id": "9a197e7e-a6e8-4afb-9790-0835425a3397", "userId": "c926be9c-a8ce-4399-a9b3-11ec0fc8d6c9", "metadata": {"createdDate": "2022-06-23T15:14:01.599Z", "updatedDate": "2022-06-23T15:14:01.599Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.599	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3591e4f3-4b4a-4ebe-a602-7b34eeabe0e0	{"id": "3591e4f3-4b4a-4ebe-a602-7b34eeabe0e0", "userId": "0f1f1a5d-49b6-42f4-8b18-faa2ce0e7be4", "metadata": {"createdDate": "2022-06-23T15:14:01.607Z", "updatedDate": "2022-06-23T15:14:01.607Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.607	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9abdc9fc-a02d-441d-928d-b49641181c3c	{"id": "9abdc9fc-a02d-441d-928d-b49641181c3c", "userId": "969e6710-309e-41bd-ba35-2b97faec30b7", "metadata": {"createdDate": "2022-06-23T15:14:01.614Z", "updatedDate": "2022-06-23T15:14:01.614Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.614	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2b9ab721-55b7-4ac0-9422-c182ee25ae96	{"id": "2b9ab721-55b7-4ac0-9422-c182ee25ae96", "userId": "b09038a4-0386-4782-8ee8-11aa87e09887", "metadata": {"createdDate": "2022-06-23T15:14:01.619Z", "updatedDate": "2022-06-23T15:14:01.619Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.619	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
49ff14e5-8411-41c7-9be2-670f21f60106	{"id": "49ff14e5-8411-41c7-9be2-670f21f60106", "userId": "1f2608df-ff79-4576-b578-14627bbe87fa", "metadata": {"createdDate": "2022-06-23T15:14:01.626Z", "updatedDate": "2022-06-23T15:14:01.626Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.626	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
641d5a74-250f-40b1-b7fd-57fb74e812e8	{"id": "641d5a74-250f-40b1-b7fd-57fb74e812e8", "userId": "f6d2c74c-3181-4c25-9a21-1b1f4c30765f", "metadata": {"createdDate": "2022-06-23T15:14:01.634Z", "updatedDate": "2022-06-23T15:14:01.634Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.634	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9ae8e3af-2bae-4670-9ee6-7122213033f5	{"id": "9ae8e3af-2bae-4670-9ee6-7122213033f5", "userId": "9207540e-91e9-4a75-ad1e-65a715784326", "metadata": {"createdDate": "2022-06-23T15:14:01.640Z", "updatedDate": "2022-06-23T15:14:01.640Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.64	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6a427c70-db6b-4c49-83cb-8d62f66e3320	{"id": "6a427c70-db6b-4c49-83cb-8d62f66e3320", "userId": "dc6e1590-7021-433d-98a3-eda0f8d8fde1", "metadata": {"createdDate": "2022-06-23T15:14:01.645Z", "updatedDate": "2022-06-23T15:14:01.645Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.645	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
099b709e-3038-45b8-a74e-54058e240627	{"id": "099b709e-3038-45b8-a74e-54058e240627", "userId": "d0fc4228-2e42-49b2-a5b0-9df48897e8c0", "metadata": {"createdDate": "2022-06-23T15:14:01.652Z", "updatedDate": "2022-06-23T15:14:01.652Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.652	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
188ec68e-37f0-47a4-82eb-2dcff452a6f6	{"id": "188ec68e-37f0-47a4-82eb-2dcff452a6f6", "userId": "beaffbac-e56d-4e32-a653-b631945f060c", "metadata": {"createdDate": "2022-06-23T15:14:01.552Z", "updatedDate": "2022-06-23T15:14:01.552Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.552	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d01f2fe4-0ec7-4868-a30f-339cd129f7ed	{"id": "d01f2fe4-0ec7-4868-a30f-339cd129f7ed", "userId": "ab60d124-5d41-49c5-8aae-2ef2cd3704c2", "metadata": {"createdDate": "2022-06-23T15:14:01.560Z", "updatedDate": "2022-06-23T15:14:01.560Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.56	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b998878f-beba-409e-b885-027f50774f28	{"id": "b998878f-beba-409e-b885-027f50774f28", "userId": "65ec5d8b-c3f6-41d4-8026-fba1f7cae715", "metadata": {"createdDate": "2022-06-23T15:14:01.567Z", "updatedDate": "2022-06-23T15:14:01.567Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.567	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
80adea09-aa0e-4108-916b-fd30de860261	{"id": "80adea09-aa0e-4108-916b-fd30de860261", "userId": "8931291a-8f92-4044-a17f-49a546b489ce", "metadata": {"createdDate": "2022-06-23T15:14:01.569Z", "updatedDate": "2022-06-23T15:14:01.569Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.569	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c2992935-c8a1-4745-9cd1-47580ac6fe40	{"id": "c2992935-c8a1-4745-9cd1-47580ac6fe40", "userId": "066795ce-4938-48f2-9411-f3f922b51e1c", "metadata": {"createdDate": "2022-06-23T15:14:01.576Z", "updatedDate": "2022-06-23T15:14:01.576Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.576	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b9ac8200-d771-4106-84bc-6f24afaad7ee	{"id": "b9ac8200-d771-4106-84bc-6f24afaad7ee", "userId": "0a246f61-d85f-42b6-8dcc-48d25a46690b", "metadata": {"createdDate": "2022-06-23T15:14:01.579Z", "updatedDate": "2022-06-23T15:14:01.579Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.579	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0ab2f838-fa1b-44b8-b352-149d34ef6486	{"id": "0ab2f838-fa1b-44b8-b352-149d34ef6486", "userId": "b617c5f2-78d4-4c7e-bf0c-d21392a8c39f", "metadata": {"createdDate": "2022-06-23T15:14:01.587Z", "updatedDate": "2022-06-23T15:14:01.587Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.587	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
651f22fd-68cb-4d79-8f35-fa052a573aa9	{"id": "651f22fd-68cb-4d79-8f35-fa052a573aa9", "userId": "44e640f4-3e0e-4bb4-92af-6263108893b2", "metadata": {"createdDate": "2022-06-23T15:14:01.592Z", "updatedDate": "2022-06-23T15:14:01.592Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.592	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6b5cf37b-2c1d-477d-9df7-753d961129ad	{"id": "6b5cf37b-2c1d-477d-9df7-753d961129ad", "userId": "57db810d-d59c-4443-ab43-3542cfdf7905", "metadata": {"createdDate": "2022-06-23T15:14:01.601Z", "updatedDate": "2022-06-23T15:14:01.601Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.601	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d31e5595-0a3a-468c-b761-1f07ea3dfe23	{"id": "d31e5595-0a3a-468c-b761-1f07ea3dfe23", "userId": "251cfa92-24a7-45db-9f0d-acd2da450b50", "metadata": {"createdDate": "2022-06-23T15:14:01.609Z", "updatedDate": "2022-06-23T15:14:01.609Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.609	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b50fc96c-cb52-46f6-baf5-d781cf989f5c	{"id": "b50fc96c-cb52-46f6-baf5-d781cf989f5c", "userId": "0414af69-f89c-40f2-bea9-a9b5d0a179d4", "metadata": {"createdDate": "2022-06-23T15:14:01.618Z", "updatedDate": "2022-06-23T15:14:01.618Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.618	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c60283c6-6ba0-4771-bef4-fbf6dec89a4b	{"id": "c60283c6-6ba0-4771-bef4-fbf6dec89a4b", "userId": "0d6d38bf-aaf6-4976-a2ce-7ff787195982", "metadata": {"createdDate": "2022-06-23T15:14:01.631Z", "updatedDate": "2022-06-23T15:14:01.631Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.631	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
437bed5a-a0c5-4948-a214-af408b2f0b6b	{"id": "437bed5a-a0c5-4948-a214-af408b2f0b6b", "userId": "8af5eaca-d164-4a5f-9941-0467c6facffa", "metadata": {"createdDate": "2022-06-23T15:14:01.642Z", "updatedDate": "2022-06-23T15:14:01.642Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.642	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c67491d7-0277-4271-b2a9-4427db5049b4	{"id": "c67491d7-0277-4271-b2a9-4427db5049b4", "userId": "32134c21-fae8-497e-b2d2-daa1ba421070", "metadata": {"createdDate": "2022-06-23T15:14:01.649Z", "updatedDate": "2022-06-23T15:14:01.649Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.649	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
31e3e3c8-93a7-40cb-ab32-f4c176ba3c84	{"id": "31e3e3c8-93a7-40cb-ab32-f4c176ba3c84", "userId": "6b5a896c-c6f4-4a28-a89a-2e2ca6ff0d0e", "metadata": {"createdDate": "2022-06-23T15:14:01.653Z", "updatedDate": "2022-06-23T15:14:01.653Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.653	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3e606e84-219b-407a-9775-f911ae96da78	{"id": "3e606e84-219b-407a-9775-f911ae96da78", "userId": "7597bd13-9f57-4cd1-a7cf-dc0ac7375280", "metadata": {"createdDate": "2022-06-23T15:14:01.671Z", "updatedDate": "2022-06-23T15:14:01.671Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.671	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
55576b65-99e7-4bf3-abb4-c147393ae794	{"id": "55576b65-99e7-4bf3-abb4-c147393ae794", "userId": "52e47672-d456-40b6-9f2d-6597d3e9f942", "metadata": {"createdDate": "2022-06-23T15:14:01.660Z", "updatedDate": "2022-06-23T15:14:01.660Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.66	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e0b25406-932a-4728-a2ae-d9b3ff7d73a4	{"id": "e0b25406-932a-4728-a2ae-d9b3ff7d73a4", "userId": "c8edf675-9323-4410-9d14-0727e038dad0", "metadata": {"createdDate": "2022-06-23T15:14:01.666Z", "updatedDate": "2022-06-23T15:14:01.666Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.666	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b88d7217-84f9-41af-8e92-3f49564bacde	{"id": "b88d7217-84f9-41af-8e92-3f49564bacde", "userId": "50e30476-ee93-4b16-a53c-27ce2c4b49d7", "metadata": {"createdDate": "2022-06-23T15:14:01.673Z", "updatedDate": "2022-06-23T15:14:01.673Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.673	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8305748f-86ee-45d2-8afc-ff8a371827fe	{"id": "8305748f-86ee-45d2-8afc-ff8a371827fe", "userId": "dc5dab8d-f80a-476a-b920-3cf21eeee902", "metadata": {"createdDate": "2022-06-23T15:14:01.679Z", "updatedDate": "2022-06-23T15:14:01.679Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.679	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8b7951e1-07c2-4184-95e6-7fa0fad42750	{"id": "8b7951e1-07c2-4184-95e6-7fa0fad42750", "userId": "860b2291-c28a-4943-804a-169af01edef4", "metadata": {"createdDate": "2022-06-23T15:14:01.686Z", "updatedDate": "2022-06-23T15:14:01.686Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.686	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d67a71c8-e694-4384-8970-58f840382a40	{"id": "d67a71c8-e694-4384-8970-58f840382a40", "userId": "046353cf-3963-482c-9792-32ade0a33afa", "metadata": {"createdDate": "2022-06-23T15:14:01.692Z", "updatedDate": "2022-06-23T15:14:01.692Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.692	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8529dcc0-464b-4385-98a9-0363b382eba8	{"id": "8529dcc0-464b-4385-98a9-0363b382eba8", "userId": "e1e435da-97e2-4083-8657-832aeb549929", "metadata": {"createdDate": "2022-06-23T15:14:01.700Z", "updatedDate": "2022-06-23T15:14:01.700Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.7	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
38b14a75-062b-4015-b22c-dae9a8a445e7	{"id": "38b14a75-062b-4015-b22c-dae9a8a445e7", "userId": "d3409c88-7e3f-497a-b94c-69e85e23b45c", "metadata": {"createdDate": "2022-06-23T15:14:01.709Z", "updatedDate": "2022-06-23T15:14:01.709Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.709	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
914d4c58-6215-486d-a0d2-2a46240464f6	{"id": "914d4c58-6215-486d-a0d2-2a46240464f6", "userId": "15fa3eda-f495-496c-b21e-4f281b38a3ef", "metadata": {"createdDate": "2022-06-23T15:14:01.717Z", "updatedDate": "2022-06-23T15:14:01.717Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.717	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d4c7093c-c89c-4bd7-8d02-aad44dc8858c	{"id": "d4c7093c-c89c-4bd7-8d02-aad44dc8858c", "userId": "6f36265e-722a-490a-b436-806e63af2ea7", "metadata": {"createdDate": "2022-06-23T15:14:01.726Z", "updatedDate": "2022-06-23T15:14:01.726Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.726	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
5f64829d-6c89-4e48-a9e6-e2c764fec616	{"id": "5f64829d-6c89-4e48-a9e6-e2c764fec616", "userId": "1db3d6c7-6ac5-4b3c-b860-deb2df449736", "metadata": {"createdDate": "2022-06-23T15:14:01.736Z", "updatedDate": "2022-06-23T15:14:01.736Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.736	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c4d3181f-0ffe-4ddf-af67-a7400a2eb78a	{"id": "c4d3181f-0ffe-4ddf-af67-a7400a2eb78a", "userId": "78a21fb3-0e80-4172-8ccf-8a1d8d5e1553", "metadata": {"createdDate": "2022-06-23T15:14:01.745Z", "updatedDate": "2022-06-23T15:14:01.745Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.745	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6f1b81c0-d5d2-440f-bb74-661ac9dbe880	{"id": "6f1b81c0-d5d2-440f-bb74-661ac9dbe880", "userId": "5f9bb63a-66f1-47eb-bc19-f182af2fc3e7", "metadata": {"createdDate": "2022-06-23T15:14:01.755Z", "updatedDate": "2022-06-23T15:14:01.755Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.755	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e4ffc010-2d0e-437c-8fcf-0a3bcd634dee	{"id": "e4ffc010-2d0e-437c-8fcf-0a3bcd634dee", "userId": "0ab0736b-57ba-404c-9b17-d94de2cf4d9a", "metadata": {"createdDate": "2022-06-23T15:14:01.771Z", "updatedDate": "2022-06-23T15:14:01.771Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.771	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
406cda4c-b3e4-4acf-8a83-44699a31611e	{"id": "406cda4c-b3e4-4acf-8a83-44699a31611e", "userId": "a23eac4b-955e-451c-b4ff-6ec2f5e63e23", "metadata": {"createdDate": "2022-06-23T15:14:01.780Z", "updatedDate": "2022-06-23T15:14:01.780Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.78	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b443ad9f-ee8b-4e44-b34c-71061f304883	{"id": "b443ad9f-ee8b-4e44-b34c-71061f304883", "userId": "5a57f974-ea09-4c87-b7f5-e4144dde6128", "metadata": {"createdDate": "2022-06-23T15:14:01.793Z", "updatedDate": "2022-06-23T15:14:01.793Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.793	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6ead0e8a-e9b1-4c16-881a-132f21f297bc	{"id": "6ead0e8a-e9b1-4c16-881a-132f21f297bc", "userId": "95a99d37-66b5-4b8d-a598-ab36618f9aac", "metadata": {"createdDate": "2022-06-23T15:14:01.669Z", "updatedDate": "2022-06-23T15:14:01.669Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.669	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ae6a41a4-dc17-49e0-842d-12a1b64c7f0d	{"id": "ae6a41a4-dc17-49e0-842d-12a1b64c7f0d", "userId": "b7f677aa-e2db-4bb5-81f8-beee547bce68", "metadata": {"createdDate": "2022-06-23T15:14:01.677Z", "updatedDate": "2022-06-23T15:14:01.677Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.677	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1ab0f3b0-0ff5-4f4d-a04c-7c428a357c68	{"id": "1ab0f3b0-0ff5-4f4d-a04c-7c428a357c68", "userId": "ceecd8ee-9586-4024-b107-d368b58a1025", "metadata": {"createdDate": "2022-06-23T15:14:01.687Z", "updatedDate": "2022-06-23T15:14:01.687Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.687	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d228dd14-4375-4626-aea8-47929e47f48e	{"id": "d228dd14-4375-4626-aea8-47929e47f48e", "userId": "67002fdf-b2f6-4e1f-bab8-d750efb0558f", "metadata": {"createdDate": "2022-06-23T15:14:01.694Z", "updatedDate": "2022-06-23T15:14:01.694Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.694	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
940a3b4e-e803-4bee-8327-b322a7f49a7d	{"id": "940a3b4e-e803-4bee-8327-b322a7f49a7d", "userId": "abad6503-a51b-4fec-a1cd-b5f672b1ff7b", "metadata": {"createdDate": "2022-06-23T15:14:01.705Z", "updatedDate": "2022-06-23T15:14:01.705Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.705	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
dd5316fe-59bc-41af-bd98-cf7de1897105	{"id": "dd5316fe-59bc-41af-bd98-cf7de1897105", "userId": "5cc5bd09-d90e-4484-8058-74c237165877", "metadata": {"createdDate": "2022-06-23T15:14:01.712Z", "updatedDate": "2022-06-23T15:14:01.712Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.712	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0df4504b-e604-47a6-8bab-e4c6e015a1e0	{"id": "0df4504b-e604-47a6-8bab-e4c6e015a1e0", "userId": "589ee8e5-4fe4-4ab3-8a58-441cebea454a", "metadata": {"createdDate": "2022-06-23T15:14:01.720Z", "updatedDate": "2022-06-23T15:14:01.720Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.72	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f9f7cc1a-9856-4a54-b655-f112b11e3052	{"id": "f9f7cc1a-9856-4a54-b655-f112b11e3052", "userId": "86c9f455-a685-45d0-9d01-5943a1ba7e5b", "metadata": {"createdDate": "2022-06-23T15:14:01.731Z", "updatedDate": "2022-06-23T15:14:01.731Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.731	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9b88c3cd-e75f-4142-8c7f-f8b9ee87bd1a	{"id": "9b88c3cd-e75f-4142-8c7f-f8b9ee87bd1a", "userId": "23807f0f-6053-417c-b335-f3b0f84ceb8e", "metadata": {"createdDate": "2022-06-23T15:14:01.740Z", "updatedDate": "2022-06-23T15:14:01.740Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.74	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
fdb24f01-df4c-4732-a6f3-92a41f65b017	{"id": "fdb24f01-df4c-4732-a6f3-92a41f65b017", "userId": "4a5e1aa3-0733-45d9-b9cc-836b4e92d6ea", "metadata": {"createdDate": "2022-06-23T15:14:01.748Z", "updatedDate": "2022-06-23T15:14:01.748Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.748	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
09880c9d-129a-4e56-b014-fab8a6b61658	{"id": "09880c9d-129a-4e56-b014-fab8a6b61658", "userId": "71f28723-784e-4292-b794-af4ffca9178e", "metadata": {"createdDate": "2022-06-23T15:14:01.761Z", "updatedDate": "2022-06-23T15:14:01.761Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.761	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
cdc30de6-82a9-484d-bf28-9c1bf949ec92	{"id": "cdc30de6-82a9-484d-bf28-9c1bf949ec92", "userId": "cc0685f2-6ac2-4840-bb67-1493c14968c5", "metadata": {"createdDate": "2022-06-23T15:14:01.772Z", "updatedDate": "2022-06-23T15:14:01.772Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.772	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
88d00d6e-ae9e-4cad-84fc-7627765f7b54	{"id": "88d00d6e-ae9e-4cad-84fc-7627765f7b54", "userId": "c8c1eced-7ff5-44e2-89da-12d276c1e2bc", "metadata": {"createdDate": "2022-06-23T15:14:01.786Z", "updatedDate": "2022-06-23T15:14:01.786Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.786	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
26cd0128-1707-4128-b98b-28b3f16014c7	{"id": "26cd0128-1707-4128-b98b-28b3f16014c7", "userId": "e63273e7-48f5-4c43-ab4e-1751ecacaa21", "metadata": {"createdDate": "2022-06-23T15:14:01.794Z", "updatedDate": "2022-06-23T15:14:01.794Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.794	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e79d1581-4e03-478e-a7bd-184ebedcf237	{"id": "e79d1581-4e03-478e-a7bd-184ebedcf237", "userId": "2ee07dc7-835f-4a33-a783-db2ae3f1238c", "metadata": {"createdDate": "2022-06-23T15:14:01.800Z", "updatedDate": "2022-06-23T15:14:01.800Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.8	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4ab26026-17c4-4da1-8e95-7278f920c68f	{"id": "4ab26026-17c4-4da1-8e95-7278f920c68f", "userId": "e546d50a-926a-421f-8400-a041a2e9db79", "metadata": {"createdDate": "2022-06-23T15:14:01.808Z", "updatedDate": "2022-06-23T15:14:01.808Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.808	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b68b0d73-cff7-48dc-8cb1-811a14fb6d4d	{"id": "b68b0d73-cff7-48dc-8cb1-811a14fb6d4d", "userId": "4205e8ff-804d-45bb-9d6d-f75f845ce608", "metadata": {"createdDate": "2022-06-23T15:14:01.689Z", "updatedDate": "2022-06-23T15:14:01.689Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.689	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
059f3243-1949-4502-82be-c8d2821f5bd6	{"id": "059f3243-1949-4502-82be-c8d2821f5bd6", "userId": "a983c74e-b4f5-4ca9-94f0-b79efa947b27", "metadata": {"createdDate": "2022-06-23T15:14:01.698Z", "updatedDate": "2022-06-23T15:14:01.698Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.698	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
87cb6a3c-4adc-4f92-89d9-c5e8955981af	{"id": "87cb6a3c-4adc-4f92-89d9-c5e8955981af", "userId": "f5d7aed2-1647-4e83-b85e-74760f770799", "metadata": {"createdDate": "2022-06-23T15:14:01.710Z", "updatedDate": "2022-06-23T15:14:01.710Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.71	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
70d18cfe-7db8-4bb9-9538-51c112fa78e4	{"id": "70d18cfe-7db8-4bb9-9538-51c112fa78e4", "userId": "7dd96d33-6abf-4394-8768-647c76d79412", "metadata": {"createdDate": "2022-06-23T15:14:01.723Z", "updatedDate": "2022-06-23T15:14:01.723Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.723	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
014e9f58-67e9-4784-997e-3991a928c4f4	{"id": "014e9f58-67e9-4784-997e-3991a928c4f4", "userId": "a49cefad-7447-4f2f-9004-de32e7a6cc53", "metadata": {"createdDate": "2022-06-23T15:14:01.740Z", "updatedDate": "2022-06-23T15:14:01.740Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.74	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d97c4d17-8973-445c-9fd1-dadbc6fcedf2	{"id": "d97c4d17-8973-445c-9fd1-dadbc6fcedf2", "userId": "8cde4a35-f58b-492e-bd07-d668f7322253", "metadata": {"createdDate": "2022-06-23T15:14:01.757Z", "updatedDate": "2022-06-23T15:14:01.757Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.757	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3152a1df-8fcf-4ac8-8fe1-f0517a861afa	{"id": "3152a1df-8fcf-4ac8-8fe1-f0517a861afa", "userId": "dc13dcc6-2bda-412c-b046-1398d1becb75", "metadata": {"createdDate": "2022-06-23T15:14:01.765Z", "updatedDate": "2022-06-23T15:14:01.765Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.765	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9012a145-ec4e-49ab-9a63-860f6a053b77	{"id": "9012a145-ec4e-49ab-9a63-860f6a053b77", "userId": "a2468e40-fb7c-453c-a217-8388801e2407", "metadata": {"createdDate": "2022-06-23T15:14:01.779Z", "updatedDate": "2022-06-23T15:14:01.779Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.779	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2fb6dc45-eb8c-4a68-94f0-24da1338aa63	{"id": "2fb6dc45-eb8c-4a68-94f0-24da1338aa63", "userId": "5ed8a4be-f0d8-459d-9e9a-27f2e8c155af", "metadata": {"createdDate": "2022-06-23T15:14:01.789Z", "updatedDate": "2022-06-23T15:14:01.789Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.789	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
a5277563-e080-4eaa-9c9e-f864f84ac77e	{"id": "a5277563-e080-4eaa-9c9e-f864f84ac77e", "userId": "6b5f249c-7df1-4c2f-afc2-0ef6fc21b701", "metadata": {"createdDate": "2022-06-23T15:14:01.806Z", "updatedDate": "2022-06-23T15:14:01.806Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.806	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6f21ad4c-cb45-4270-800d-4396645e9ba5	{"id": "6f21ad4c-cb45-4270-800d-4396645e9ba5", "userId": "6f644096-0cb6-4d9c-9da4-0831b3625c0d", "metadata": {"createdDate": "2022-06-23T15:14:01.825Z", "updatedDate": "2022-06-23T15:14:01.825Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.825	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
389784f5-24b1-4d15-965c-007fc6b82565	{"id": "389784f5-24b1-4d15-965c-007fc6b82565", "userId": "888a321d-676e-42fc-b588-a677d16a76ec", "metadata": {"createdDate": "2022-06-23T15:14:01.843Z", "updatedDate": "2022-06-23T15:14:01.843Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.843	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
18e5b117-4ca2-45e6-a060-5d794cebd7cb	{"id": "18e5b117-4ca2-45e6-a060-5d794cebd7cb", "userId": "15b9deaf-1a59-4396-a8e5-d6c7e5b79b28", "metadata": {"createdDate": "2022-06-23T15:14:01.847Z", "updatedDate": "2022-06-23T15:14:01.847Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.847	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f6526e97-041f-44ec-aab9-83107b0243bd	{"id": "f6526e97-041f-44ec-aab9-83107b0243bd", "userId": "1b5795ad-5ad0-4ba5-9c62-a7b26eb2f6b8", "metadata": {"createdDate": "2022-06-23T15:14:01.856Z", "updatedDate": "2022-06-23T15:14:01.856Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.856	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b6e6708e-4549-4f71-924a-8c040878e76b	{"id": "b6e6708e-4549-4f71-924a-8c040878e76b", "userId": "7aa8082c-d1ed-4e33-bf0e-02d3efe5624b", "metadata": {"createdDate": "2022-06-23T15:14:01.867Z", "updatedDate": "2022-06-23T15:14:01.867Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.867	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
a6848b0b-2a81-40f9-a91f-b8291b9db01b	{"id": "a6848b0b-2a81-40f9-a91f-b8291b9db01b", "userId": "b7da16ef-f3d2-4c12-a564-858bc3eee366", "metadata": {"createdDate": "2022-06-23T15:14:01.874Z", "updatedDate": "2022-06-23T15:14:01.874Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.874	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
780ace32-116c-4466-bd03-a09be0435df0	{"id": "780ace32-116c-4466-bd03-a09be0435df0", "userId": "2220260d-12c7-49ab-9ac4-f923323f0cb3", "metadata": {"createdDate": "2022-06-23T15:14:01.707Z", "updatedDate": "2022-06-23T15:14:01.707Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.707	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7f0f898d-b720-4a48-9f8d-06518da2a460	{"id": "7f0f898d-b720-4a48-9f8d-06518da2a460", "userId": "8d65692e-fa98-49f2-9896-f9f6b0893358", "metadata": {"createdDate": "2022-06-23T15:14:01.722Z", "updatedDate": "2022-06-23T15:14:01.722Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.722	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b844f056-51f8-47b5-8d28-2367532d64a7	{"id": "b844f056-51f8-47b5-8d28-2367532d64a7", "userId": "fbc2b501-a8cc-43a7-8d8c-b68067b63a33", "metadata": {"createdDate": "2022-06-23T15:14:01.733Z", "updatedDate": "2022-06-23T15:14:01.733Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.733	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4bf5560f-8f37-4a33-bd18-a78effdf7665	{"id": "4bf5560f-8f37-4a33-bd18-a78effdf7665", "userId": "be2e9bdb-9884-4fe9-87d0-ba91e8425412", "metadata": {"createdDate": "2022-06-23T15:14:01.747Z", "updatedDate": "2022-06-23T15:14:01.747Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.747	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
43919a78-1ca3-4332-811a-2bda92419cd6	{"id": "43919a78-1ca3-4332-811a-2bda92419cd6", "userId": "488d4776-d0e2-4618-9ca9-78fa5dcc787c", "metadata": {"createdDate": "2022-06-23T15:14:01.763Z", "updatedDate": "2022-06-23T15:14:01.763Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.763	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
bfd0f6cb-7787-4c39-8e6e-ab731f4ec6ab	{"id": "bfd0f6cb-7787-4c39-8e6e-ab731f4ec6ab", "userId": "1c65f9c5-5970-48bb-aa72-82ef96fc145e", "metadata": {"createdDate": "2022-06-23T15:14:01.774Z", "updatedDate": "2022-06-23T15:14:01.774Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.774	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9b6317db-ca71-44b4-a767-9e5cbf579d4c	{"id": "9b6317db-ca71-44b4-a767-9e5cbf579d4c", "userId": "7d7f46e8-5f99-4ac8-aa86-83a23f4bd8de", "metadata": {"createdDate": "2022-06-23T15:14:01.788Z", "updatedDate": "2022-06-23T15:14:01.788Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.788	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e076edb8-6d69-43bc-9d5a-56bfc0616bbe	{"id": "e076edb8-6d69-43bc-9d5a-56bfc0616bbe", "userId": "bb4d8818-35cc-4cb6-b181-b5ccfb734744", "metadata": {"createdDate": "2022-06-23T15:14:01.804Z", "updatedDate": "2022-06-23T15:14:01.804Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.804	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
baa6171f-121a-49f1-aca4-ba26594dc227	{"id": "baa6171f-121a-49f1-aca4-ba26594dc227", "userId": "4f0e711c-d583-41e0-9555-b62f1725023f", "metadata": {"createdDate": "2022-06-23T15:14:01.814Z", "updatedDate": "2022-06-23T15:14:01.814Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.814	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
565a60ea-1d36-43d9-9ff0-8f381026c2e1	{"id": "565a60ea-1d36-43d9-9ff0-8f381026c2e1", "userId": "4ca6da61-a9fa-4226-850d-43aa2d89f9a6", "metadata": {"createdDate": "2022-06-23T15:14:01.823Z", "updatedDate": "2022-06-23T15:14:01.823Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.823	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
641ae3b5-ff7a-4fbb-a3cc-b81784246565	{"id": "641ae3b5-ff7a-4fbb-a3cc-b81784246565", "userId": "ec97250b-1ded-46a7-a8f6-a474f8fe622d", "metadata": {"createdDate": "2022-06-23T15:14:01.842Z", "updatedDate": "2022-06-23T15:14:01.842Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.842	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4b59c1b7-b52f-4a67-ba89-ec8251598ff3	{"id": "4b59c1b7-b52f-4a67-ba89-ec8251598ff3", "userId": "6f511507-dab9-42fb-b966-bb8a1330ee7a", "metadata": {"createdDate": "2022-06-23T15:14:01.854Z", "updatedDate": "2022-06-23T15:14:01.854Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.854	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9029f7a9-6a7c-40f4-9ffc-1ea2fa1a81d1	{"id": "9029f7a9-6a7c-40f4-9ffc-1ea2fa1a81d1", "userId": "0e64adb1-36ba-4cdd-9909-047612b7629e", "metadata": {"createdDate": "2022-06-23T15:14:01.865Z", "updatedDate": "2022-06-23T15:14:01.865Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.865	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
acf76d01-e60d-4d3a-920b-8c759fc391af	{"id": "acf76d01-e60d-4d3a-920b-8c759fc391af", "userId": "71f4828b-8ad5-4ae6-bfa6-45ecfe3f6c3c", "metadata": {"createdDate": "2022-06-23T15:14:01.868Z", "updatedDate": "2022-06-23T15:14:01.868Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.868	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
5b0a3519-c3c3-4220-a838-2a20cbc279ac	{"id": "5b0a3519-c3c3-4220-a838-2a20cbc279ac", "userId": "550a06c3-8d0c-4ae3-a267-b32527272772", "metadata": {"createdDate": "2022-06-23T15:14:01.880Z", "updatedDate": "2022-06-23T15:14:01.880Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.88	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
713fac8f-c532-4ca0-bfed-0a6fd258ab1d	{"id": "713fac8f-c532-4ca0-bfed-0a6fd258ab1d", "userId": "dd176277-5c2d-4310-bf2f-e45e312b5026", "metadata": {"createdDate": "2022-06-23T15:14:01.900Z", "updatedDate": "2022-06-23T15:14:01.900Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.9	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
38cc438a-69d6-40f2-911a-952be9552ea0	{"id": "38cc438a-69d6-40f2-911a-952be9552ea0", "userId": "65fcc41e-df15-459a-bf93-2f53cfa8ff7f", "metadata": {"createdDate": "2022-06-23T15:14:01.803Z", "updatedDate": "2022-06-23T15:14:01.803Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.803	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
52607046-5a10-4e16-bde6-c97530313e42	{"id": "52607046-5a10-4e16-bde6-c97530313e42", "userId": "ea3a1605-a930-4183-b04e-0b2fca3ae094", "metadata": {"createdDate": "2022-06-23T15:14:01.819Z", "updatedDate": "2022-06-23T15:14:01.819Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.819	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f271f14e-0124-4acb-bf14-3a2feff31f3a	{"id": "f271f14e-0124-4acb-bf14-3a2feff31f3a", "userId": "137d4cbd-00ff-4332-97cb-88373fa9b556", "metadata": {"createdDate": "2022-06-23T15:14:01.827Z", "updatedDate": "2022-06-23T15:14:01.827Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.827	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8d6ec17c-b62e-4356-ae38-b7448cd686f7	{"id": "8d6ec17c-b62e-4356-ae38-b7448cd686f7", "userId": "93136048-585b-466e-88f8-a10115e6d7e2", "metadata": {"createdDate": "2022-06-23T15:14:01.841Z", "updatedDate": "2022-06-23T15:14:01.841Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.841	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b8ff3271-856d-4465-a918-8c2f0de244ce	{"id": "b8ff3271-856d-4465-a918-8c2f0de244ce", "userId": "ade18246-c529-497f-bd4c-2c3f85e995ad", "metadata": {"createdDate": "2022-06-23T15:14:01.850Z", "updatedDate": "2022-06-23T15:14:01.850Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.85	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8722aafa-f05a-4605-8c0a-130f76f97b9f	{"id": "8722aafa-f05a-4605-8c0a-130f76f97b9f", "userId": "c2610e2c-a6f8-4336-95b6-54d716348b03", "metadata": {"createdDate": "2022-06-23T15:14:01.857Z", "updatedDate": "2022-06-23T15:14:01.857Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.857	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
a43dc7ba-90af-4807-a196-fc3de03cc994	{"id": "a43dc7ba-90af-4807-a196-fc3de03cc994", "userId": "e6dfcfef-e724-4485-870d-d2c4d1dcfdd9", "metadata": {"createdDate": "2022-06-23T15:14:01.863Z", "updatedDate": "2022-06-23T15:14:01.863Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.863	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
5ee5e459-96aa-4a93-a3a2-4f8d059da02c	{"id": "5ee5e459-96aa-4a93-a3a2-4f8d059da02c", "userId": "9ad09b01-7429-455f-9f64-e3897027db61", "metadata": {"createdDate": "2022-06-23T15:14:01.872Z", "updatedDate": "2022-06-23T15:14:01.872Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.872	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
17a3fefa-8f49-492d-ac3e-431666eb845e	{"id": "17a3fefa-8f49-492d-ac3e-431666eb845e", "userId": "c7d6c761-905e-4e7b-a616-a624175efe11", "metadata": {"createdDate": "2022-06-23T15:14:01.879Z", "updatedDate": "2022-06-23T15:14:01.879Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.879	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
79dcd243-018c-4fa8-8ffe-0ee611848d30	{"id": "79dcd243-018c-4fa8-8ffe-0ee611848d30", "userId": "785c6f6e-36a5-4434-8aa7-210bb55cea34", "metadata": {"createdDate": "2022-06-23T15:14:01.888Z", "updatedDate": "2022-06-23T15:14:01.888Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.888	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6ed69217-2143-467c-a597-71d84c8b0274	{"id": "6ed69217-2143-467c-a597-71d84c8b0274", "userId": "5e3d70ff-a89a-44a0-a2e2-4cae67668805", "metadata": {"createdDate": "2022-06-23T15:14:01.896Z", "updatedDate": "2022-06-23T15:14:01.896Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.896	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
82289f1a-a9a7-4b5c-a328-1899cc8070b4	{"id": "82289f1a-a9a7-4b5c-a328-1899cc8070b4", "userId": "4adf499e-c954-4bf9-9261-26720608e120", "metadata": {"createdDate": "2022-06-23T15:14:01.902Z", "updatedDate": "2022-06-23T15:14:01.902Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.902	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c1e25a2e-9809-4b27-a1a1-ada7f16e523d	{"id": "c1e25a2e-9809-4b27-a1a1-ada7f16e523d", "userId": "261e1062-a473-47f4-a00f-a197c4a87530", "metadata": {"createdDate": "2022-06-23T15:14:01.912Z", "updatedDate": "2022-06-23T15:14:01.912Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.912	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b219317e-c244-4b1f-87cd-62abf4c4183f	{"id": "b219317e-c244-4b1f-87cd-62abf4c4183f", "userId": "4fd8d3dd-ebc0-4d10-ae81-199e831be32e", "metadata": {"createdDate": "2022-06-23T15:14:01.918Z", "updatedDate": "2022-06-23T15:14:01.918Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.918	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
049ee99f-db2f-4b64-bb95-daf8548a7498	{"id": "049ee99f-db2f-4b64-bb95-daf8548a7498", "userId": "78284bd0-cdf1-4fc9-a404-739388b41cc7", "metadata": {"createdDate": "2022-06-23T15:14:01.928Z", "updatedDate": "2022-06-23T15:14:01.928Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.928	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4cf2cd63-5c85-4947-b176-4fc8f64290c3	{"id": "4cf2cd63-5c85-4947-b176-4fc8f64290c3", "userId": "745bdee1-458c-4076-bad1-be5a470c49fb", "metadata": {"createdDate": "2022-06-23T15:14:01.934Z", "updatedDate": "2022-06-23T15:14:01.934Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.934	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
78d2d835-5a10-4b89-af9a-332857755684	{"id": "78d2d835-5a10-4b89-af9a-332857755684", "userId": "3b464026-79e7-450a-a441-0e1d4f8ebf99", "metadata": {"createdDate": "2022-06-23T15:14:01.820Z", "updatedDate": "2022-06-23T15:14:01.820Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.82	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
44f70f44-1284-4c7a-9fe8-41c0b9420346	{"id": "44f70f44-1284-4c7a-9fe8-41c0b9420346", "userId": "2eb8fef6-95c8-491d-a6a3-00176997dca4", "metadata": {"createdDate": "2022-06-23T15:14:01.838Z", "updatedDate": "2022-06-23T15:14:01.838Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.838	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ae87a598-aaa8-49a6-bbc5-2a35d07b551b	{"id": "ae87a598-aaa8-49a6-bbc5-2a35d07b551b", "userId": "f0dc6802-450f-459a-9dc6-209086375b7f", "metadata": {"createdDate": "2022-06-23T15:14:01.845Z", "updatedDate": "2022-06-23T15:14:01.845Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.845	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2c6944d0-c42c-4818-8531-ee3a2a16969a	{"id": "2c6944d0-c42c-4818-8531-ee3a2a16969a", "userId": "2c9e8cdd-d2fe-485b-b663-34225637fe93", "metadata": {"createdDate": "2022-06-23T15:14:01.852Z", "updatedDate": "2022-06-23T15:14:01.852Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.852	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
85a75719-7cc0-42d5-b2c3-4d0aec9c79b5	{"id": "85a75719-7cc0-42d5-b2c3-4d0aec9c79b5", "userId": "f6cd72ab-3e89-44c0-99aa-54ab4844f167", "metadata": {"createdDate": "2022-06-23T15:14:01.861Z", "updatedDate": "2022-06-23T15:14:01.861Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.861	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
276dfec5-23c4-498d-bc0d-f8347c23f617	{"id": "276dfec5-23c4-498d-bc0d-f8347c23f617", "userId": "d80b45eb-5dc0-4635-b539-dac722cc3a50", "metadata": {"createdDate": "2022-06-23T15:14:01.873Z", "updatedDate": "2022-06-23T15:14:01.873Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.873	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0153c810-7b43-49b5-896d-11ab95857443	{"id": "0153c810-7b43-49b5-896d-11ab95857443", "userId": "bd566b21-d125-421c-9c78-2b9a8bc4c4f7", "metadata": {"createdDate": "2022-06-23T15:14:01.883Z", "updatedDate": "2022-06-23T15:14:01.883Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.883	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e92683cf-dcdd-4b81-9b36-e70c01fa30b3	{"id": "e92683cf-dcdd-4b81-9b36-e70c01fa30b3", "userId": "1b648069-8563-41c8-afc0-d8359f11503c", "metadata": {"createdDate": "2022-06-23T15:14:01.898Z", "updatedDate": "2022-06-23T15:14:01.898Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.898	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
108d50ce-8826-487d-ae95-e8ec82647385	{"id": "108d50ce-8826-487d-ae95-e8ec82647385", "userId": "872695bb-4157-4c6f-84c7-eb5b50b9ce17", "metadata": {"createdDate": "2022-06-23T15:14:01.903Z", "updatedDate": "2022-06-23T15:14:01.903Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.903	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e23217ec-e979-42ff-a3b1-dcded241b1da	{"id": "e23217ec-e979-42ff-a3b1-dcded241b1da", "userId": "d6c40971-39a6-4977-9d27-e83e731f51de", "metadata": {"createdDate": "2022-06-23T15:14:01.914Z", "updatedDate": "2022-06-23T15:14:01.914Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.914	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3a56f36d-5e1c-4528-94f6-93ee9ed3359f	{"id": "3a56f36d-5e1c-4528-94f6-93ee9ed3359f", "userId": "ffce08d4-c08d-4ff8-8ff8-060a5015aa2a", "metadata": {"createdDate": "2022-06-23T15:14:01.924Z", "updatedDate": "2022-06-23T15:14:01.924Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.924	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ae1074d8-e4de-4c7b-bc4f-439eec9def28	{"id": "ae1074d8-e4de-4c7b-bc4f-439eec9def28", "userId": "960ab857-c8c7-4445-8d24-1e4c33de3e86", "metadata": {"createdDate": "2022-06-23T15:14:01.932Z", "updatedDate": "2022-06-23T15:14:01.932Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.932	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
84b45658-09c4-4c2b-83a7-9fa438293eeb	{"id": "84b45658-09c4-4c2b-83a7-9fa438293eeb", "userId": "4f012e5c-840b-4f7a-b7e0-c2e3b1d41309", "metadata": {"createdDate": "2022-06-23T15:14:01.945Z", "updatedDate": "2022-06-23T15:14:01.945Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.945	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
038f7d4e-b86d-46b1-b6c5-be21b9a455a9	{"id": "038f7d4e-b86d-46b1-b6c5-be21b9a455a9", "userId": "975256dc-abdc-45d1-b51a-f9f9ca15a491", "metadata": {"createdDate": "2022-06-23T15:14:01.953Z", "updatedDate": "2022-06-23T15:14:01.953Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.953	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1df20c60-893f-4954-872a-b1c8b504e9da	{"id": "1df20c60-893f-4954-872a-b1c8b504e9da", "userId": "8e9d1a69-9745-4ad4-a8e8-6841a9441b40", "metadata": {"createdDate": "2022-06-23T15:14:01.969Z", "updatedDate": "2022-06-23T15:14:01.969Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.969	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4e717727-302e-4132-b5cc-6e404e36e9bb	{"id": "4e717727-302e-4132-b5cc-6e404e36e9bb", "userId": "368b8b4a-c3c2-436c-9cba-95dcac52ebf9", "metadata": {"createdDate": "2022-06-23T15:14:01.977Z", "updatedDate": "2022-06-23T15:14:01.977Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.977	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0de4f3ac-c106-4b27-ab1a-5530603bbb27	{"id": "0de4f3ac-c106-4b27-ab1a-5530603bbb27", "userId": "5dfffe75-267d-4133-b7bf-6d6daf26d5a4", "metadata": {"createdDate": "2022-06-23T15:14:01.884Z", "updatedDate": "2022-06-23T15:14:01.884Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.884	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
91998667-da9c-47bf-b888-293aca3ea540	{"id": "91998667-da9c-47bf-b888-293aca3ea540", "userId": "c6d99127-e834-4f60-9a77-f74646cd1618", "metadata": {"createdDate": "2022-06-23T15:14:01.901Z", "updatedDate": "2022-06-23T15:14:01.901Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.901	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b7c38f52-bc23-4b41-909a-0b4732fecd3b	{"id": "b7c38f52-bc23-4b41-909a-0b4732fecd3b", "userId": "6c6ab6f6-394a-44a5-8d5c-66f88f9ec01d", "metadata": {"createdDate": "2022-06-23T15:14:01.917Z", "updatedDate": "2022-06-23T15:14:01.917Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.917	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
fccec75e-6bbc-4032-9363-c9cb85a04c78	{"id": "fccec75e-6bbc-4032-9363-c9cb85a04c78", "userId": "c97f19d3-7a82-4891-aaa1-de087a9a903f", "metadata": {"createdDate": "2022-06-23T15:14:01.930Z", "updatedDate": "2022-06-23T15:14:01.930Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.93	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4053cee8-de55-41c5-9f6b-094bdd2ffe85	{"id": "4053cee8-de55-41c5-9f6b-094bdd2ffe85", "userId": "7b06cbcf-5d6d-431b-8922-20509d40f1ae", "metadata": {"createdDate": "2022-06-23T15:14:01.939Z", "updatedDate": "2022-06-23T15:14:01.939Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.939	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e606fb72-9c15-4d52-88e8-28566ce413ba	{"id": "e606fb72-9c15-4d52-88e8-28566ce413ba", "userId": "1dbf36ab-bba6-4725-8456-bda646796dd1", "metadata": {"createdDate": "2022-06-23T15:14:01.948Z", "updatedDate": "2022-06-23T15:14:01.948Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.948	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3e1bdd97-c260-4116-a3d0-5a0dcc86f0d1	{"id": "3e1bdd97-c260-4116-a3d0-5a0dcc86f0d1", "userId": "342971e4-43af-44c3-a8c3-478a97cc94bc", "metadata": {"createdDate": "2022-06-23T15:14:01.955Z", "updatedDate": "2022-06-23T15:14:01.955Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.955	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
81e7e49d-1680-4798-85f0-c2f192dddcd1	{"id": "81e7e49d-1680-4798-85f0-c2f192dddcd1", "userId": "e25fecaf-dfbf-4e59-bd3d-0493c1b519f5", "metadata": {"createdDate": "2022-06-23T15:14:01.970Z", "updatedDate": "2022-06-23T15:14:01.970Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.97	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6b258d7e-348d-437d-9f83-c37b648c9ff5	{"id": "6b258d7e-348d-437d-9f83-c37b648c9ff5", "userId": "daa9cf25-f333-447b-b577-158d6ce944a5", "metadata": {"createdDate": "2022-06-23T15:14:01.984Z", "updatedDate": "2022-06-23T15:14:01.984Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.984	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3959f19e-7b07-4d5d-bdc7-07ba9488a468	{"id": "3959f19e-7b07-4d5d-bdc7-07ba9488a468", "userId": "169aeb77-32f6-4f60-a2c0-db791b96e411", "metadata": {"createdDate": "2022-06-23T15:14:01.995Z", "updatedDate": "2022-06-23T15:14:01.995Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.995	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0c4fea27-1018-40b7-9567-2a5f1b6ddee1	{"id": "0c4fea27-1018-40b7-9567-2a5f1b6ddee1", "userId": "430f49de-6848-4cba-886a-4902cb9b887d", "metadata": {"createdDate": "2022-06-23T15:14:02.002Z", "updatedDate": "2022-06-23T15:14:02.002Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.002	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8363ff73-a4ca-4d7a-a5f8-51ed7d4dcbab	{"id": "8363ff73-a4ca-4d7a-a5f8-51ed7d4dcbab", "userId": "6f4111a4-8b6f-4008-9b95-ecd31db69234", "metadata": {"createdDate": "2022-06-23T15:14:02.011Z", "updatedDate": "2022-06-23T15:14:02.011Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.011	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0a59b128-c79f-4ba3-8f07-1d75d60b737e	{"id": "0a59b128-c79f-4ba3-8f07-1d75d60b737e", "userId": "e13b3d8d-71ff-49bd-9ea1-4ad7da8b1b8e", "metadata": {"createdDate": "2022-06-23T15:14:02.021Z", "updatedDate": "2022-06-23T15:14:02.021Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.021	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2ff000a6-fecc-46d9-97b2-4d4aa1583c97	{"id": "2ff000a6-fecc-46d9-97b2-4d4aa1583c97", "userId": "e59bc21a-884e-42a2-8792-163efc3662e7", "metadata": {"createdDate": "2022-06-23T15:14:02.038Z", "updatedDate": "2022-06-23T15:14:02.038Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.038	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1a6406a3-5264-41aa-aafc-79c8ad8d2e58	{"id": "1a6406a3-5264-41aa-aafc-79c8ad8d2e58", "userId": "a5fb6646-3e42-46ed-b686-e009e1490d2c", "metadata": {"createdDate": "2022-06-23T15:14:02.055Z", "updatedDate": "2022-06-23T15:14:02.055Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.055	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d6a73f76-88a8-4820-a381-7c014232de05	{"id": "d6a73f76-88a8-4820-a381-7c014232de05", "userId": "fb5de3c8-5293-440e-8448-a688c3a7367c", "metadata": {"createdDate": "2022-06-23T15:14:02.061Z", "updatedDate": "2022-06-23T15:14:02.061Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.061	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
819bd4a5-85b0-44b3-bb44-a049d98a2199	{"id": "819bd4a5-85b0-44b3-bb44-a049d98a2199", "userId": "292451b5-0026-4463-a762-d43fc6cc9122", "metadata": {"createdDate": "2022-06-23T15:14:01.909Z", "updatedDate": "2022-06-23T15:14:01.909Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.909	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
761f7ced-c6d0-4f51-9a3a-7e24066158e7	{"id": "761f7ced-c6d0-4f51-9a3a-7e24066158e7", "userId": "e1bcf784-484c-42ca-b502-cf2f2e57eca3", "metadata": {"createdDate": "2022-06-23T15:14:01.916Z", "updatedDate": "2022-06-23T15:14:01.916Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.916	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d929daf4-b810-49da-bb7f-933fbb26b1c2	{"id": "d929daf4-b810-49da-bb7f-933fbb26b1c2", "userId": "2084e201-b0da-4ac3-b3ae-873c48596093", "metadata": {"createdDate": "2022-06-23T15:14:01.929Z", "updatedDate": "2022-06-23T15:14:01.929Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.929	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7724f81a-536c-4e91-a289-3a59108a25dc	{"id": "7724f81a-536c-4e91-a289-3a59108a25dc", "userId": "1bcfd501-232e-47da-a511-fdd29ae3d692", "metadata": {"createdDate": "2022-06-23T15:14:01.947Z", "updatedDate": "2022-06-23T15:14:01.947Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.947	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
a325a8c3-f5bd-482d-973e-d1f3b993817d	{"id": "a325a8c3-f5bd-482d-973e-d1f3b993817d", "userId": "8853c9a2-cae2-4b5e-84ce-2b39bb809e5b", "metadata": {"createdDate": "2022-06-23T15:14:01.957Z", "updatedDate": "2022-06-23T15:14:01.957Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.957	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f26f5793-44bb-4d72-8e07-fe031656736e	{"id": "f26f5793-44bb-4d72-8e07-fe031656736e", "userId": "3387893c-7bde-4d2f-9ad2-d4974b3e959e", "metadata": {"createdDate": "2022-06-23T15:14:01.972Z", "updatedDate": "2022-06-23T15:14:01.972Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.972	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
489344c3-c12f-4d11-bdd8-e5529059a33b	{"id": "489344c3-c12f-4d11-bdd8-e5529059a33b", "userId": "08522da4-668a-4450-a769-3abfae5678ad", "metadata": {"createdDate": "2022-06-23T15:14:01.981Z", "updatedDate": "2022-06-23T15:14:01.981Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.981	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7fa599d3-472c-4644-b9c4-eefc6d187a77	{"id": "7fa599d3-472c-4644-b9c4-eefc6d187a77", "userId": "ef39251f-db4c-4253-9951-645735f84904", "metadata": {"createdDate": "2022-06-23T15:14:01.991Z", "updatedDate": "2022-06-23T15:14:01.991Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.991	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
46212d73-f5a1-4c81-92c7-d3c6a85c45bc	{"id": "46212d73-f5a1-4c81-92c7-d3c6a85c45bc", "userId": "21c08ac3-c287-4a21-b966-e263504aa773", "metadata": {"createdDate": "2022-06-23T15:14:02.000Z", "updatedDate": "2022-06-23T15:14:02.000Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
463c282d-26de-4ef2-880f-33d306467348	{"id": "463c282d-26de-4ef2-880f-33d306467348", "userId": "c573e5f8-a570-475d-a75e-88a4d2b757d2", "metadata": {"createdDate": "2022-06-23T15:14:02.008Z", "updatedDate": "2022-06-23T15:14:02.008Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.008	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0d488edb-064f-4801-842b-56c190d003c8	{"id": "0d488edb-064f-4801-842b-56c190d003c8", "userId": "b549fc60-9779-4bac-a4de-df8304ff69c4", "metadata": {"createdDate": "2022-06-23T15:14:02.019Z", "updatedDate": "2022-06-23T15:14:02.019Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.019	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
71545a7c-f93a-4c5d-b8a0-7b95d6419238	{"id": "71545a7c-f93a-4c5d-b8a0-7b95d6419238", "userId": "f3055954-ebc3-4da8-8a60-0b8f52480125", "metadata": {"createdDate": "2022-06-23T15:14:02.028Z", "updatedDate": "2022-06-23T15:14:02.028Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.028	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
5da8e9c4-24e2-4f8c-add5-1c2ca1f77d8a	{"id": "5da8e9c4-24e2-4f8c-add5-1c2ca1f77d8a", "userId": "d1f69036-a316-41e4-89c1-77f77a3c7f1d", "metadata": {"createdDate": "2022-06-23T15:14:02.037Z", "updatedDate": "2022-06-23T15:14:02.037Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.037	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f61bc4b8-d0c6-4b6b-8e9c-003fb606e605	{"id": "f61bc4b8-d0c6-4b6b-8e9c-003fb606e605", "userId": "78c51a90-e64f-49ce-8d28-e246a49c7f63", "metadata": {"createdDate": "2022-06-23T15:14:02.051Z", "updatedDate": "2022-06-23T15:14:02.051Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.051	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ffbf6599-6d70-4ff0-9bb2-87516fab5d1f	{"id": "ffbf6599-6d70-4ff0-9bb2-87516fab5d1f", "userId": "ff5ec271-7138-46d0-8ca1-f5f57790e5bd", "metadata": {"createdDate": "2022-06-23T15:14:02.058Z", "updatedDate": "2022-06-23T15:14:02.058Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.058	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
d5855b81-16c0-4ef2-9704-5c4a6e29fced	{"id": "d5855b81-16c0-4ef2-9704-5c4a6e29fced", "userId": "1200edd1-4b53-43e7-a9b7-fc590ab1c8d9", "metadata": {"createdDate": "2022-06-23T15:14:02.069Z", "updatedDate": "2022-06-23T15:14:02.069Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.069	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
1fa5d42c-34f6-4faf-96f8-79ef6a1b6d63	{"id": "1fa5d42c-34f6-4faf-96f8-79ef6a1b6d63", "userId": "f308aadb-9403-44de-9b5a-06792b78bb3a", "metadata": {"createdDate": "2022-06-23T15:14:01.943Z", "updatedDate": "2022-06-23T15:14:01.943Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.943	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
63371756-a2c6-4a07-9630-d7a160e325cd	{"id": "63371756-a2c6-4a07-9630-d7a160e325cd", "userId": "21ce4a36-4d3a-4a9d-98be-d40852799d9b", "metadata": {"createdDate": "2022-06-23T15:14:01.952Z", "updatedDate": "2022-06-23T15:14:01.952Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.952	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
816a43a6-021f-407d-b113-4614b7e3ca9d	{"id": "816a43a6-021f-407d-b113-4614b7e3ca9d", "userId": "6ff36aa8-c68d-42c9-b68b-ece603ea59d7", "metadata": {"createdDate": "2022-06-23T15:14:01.967Z", "updatedDate": "2022-06-23T15:14:01.967Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.967	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
cd5e4144-b383-4195-9ba9-6ad831d67cab	{"id": "cd5e4144-b383-4195-9ba9-6ad831d67cab", "userId": "b9a05706-9d87-499d-8e5e-47dc512a21c3", "metadata": {"createdDate": "2022-06-23T15:14:01.973Z", "updatedDate": "2022-06-23T15:14:01.973Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.973	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
21290487-d030-4265-a1c3-b2fc6a075bc6	{"id": "21290487-d030-4265-a1c3-b2fc6a075bc6", "userId": "197f8e91-5110-4487-ad36-b3d21a66059d", "metadata": {"createdDate": "2022-06-23T15:14:01.980Z", "updatedDate": "2022-06-23T15:14:01.980Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.98	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6d76ae54-156c-4e52-a7ab-ca61ad951793	{"id": "6d76ae54-156c-4e52-a7ab-ca61ad951793", "userId": "7ff26639-c033-442a-8bf4-2e896b17fcf9", "metadata": {"createdDate": "2022-06-23T15:14:01.986Z", "updatedDate": "2022-06-23T15:14:01.986Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.986	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ac44a8e2-0fc7-4ab5-a255-5616f693fa51	{"id": "ac44a8e2-0fc7-4ab5-a255-5616f693fa51", "userId": "69a7d4f8-a32a-46d8-a006-0e5ea69f34bc", "metadata": {"createdDate": "2022-06-23T15:14:01.992Z", "updatedDate": "2022-06-23T15:14:01.992Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.992	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3f9de53a-4447-4060-8e60-43802529c37b	{"id": "3f9de53a-4447-4060-8e60-43802529c37b", "userId": "2338689d-f27e-49fd-8bce-9f9bf7be6ea0", "metadata": {"createdDate": "2022-06-23T15:14:01.999Z", "updatedDate": "2022-06-23T15:14:01.999Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.999	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3a4f5bd3-1938-4a60-a768-a331591a00f4	{"id": "3a4f5bd3-1938-4a60-a768-a331591a00f4", "userId": "94fc2d88-359e-45e1-8360-ff6fb132cac4", "metadata": {"createdDate": "2022-06-23T15:14:02.005Z", "updatedDate": "2022-06-23T15:14:02.005Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.005	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4390a3c9-c0e1-4e22-b670-49d1adfb39d4	{"id": "4390a3c9-c0e1-4e22-b670-49d1adfb39d4", "userId": "b56b28b9-7f22-426e-a099-4f753be686fa", "metadata": {"createdDate": "2022-06-23T15:14:02.012Z", "updatedDate": "2022-06-23T15:14:02.012Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.012	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
dbb072c0-e64d-4956-992c-b1b0628a81e8	{"id": "dbb072c0-e64d-4956-992c-b1b0628a81e8", "userId": "223907c9-517b-4b10-a6bc-8f7fcb0a05c3", "metadata": {"createdDate": "2022-06-23T15:14:02.017Z", "updatedDate": "2022-06-23T15:14:02.017Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.017	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0d1a7f71-9546-4fb2-ad45-a93fb60ffbb3	{"id": "0d1a7f71-9546-4fb2-ad45-a93fb60ffbb3", "userId": "201be44f-2f29-47af-85da-2cbfc72ac29e", "metadata": {"createdDate": "2022-06-23T15:14:02.022Z", "updatedDate": "2022-06-23T15:14:02.022Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.022	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
3b48893d-550e-43bf-8d73-63984acaccd6	{"id": "3b48893d-550e-43bf-8d73-63984acaccd6", "userId": "a98abce3-be00-4d9a-a66a-0593d27b41e0", "metadata": {"createdDate": "2022-06-23T15:14:02.026Z", "updatedDate": "2022-06-23T15:14:02.026Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.026	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f0a94502-7dd7-4fb0-980b-95360b3603c9	{"id": "f0a94502-7dd7-4fb0-980b-95360b3603c9", "userId": "34a22ca8-9aff-4b1c-96c2-f908ddb068ae", "metadata": {"createdDate": "2022-06-23T15:14:02.034Z", "updatedDate": "2022-06-23T15:14:02.034Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.034	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
048bee6c-4943-4412-b0d1-606ce0f6c5e3	{"id": "048bee6c-4943-4412-b0d1-606ce0f6c5e3", "userId": "00bc2807-4d5b-4a27-a2b5-b7b1ba431cc4", "metadata": {"createdDate": "2022-06-23T15:14:02.046Z", "updatedDate": "2022-06-23T15:14:02.046Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.046	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
31c3369d-d98a-458f-8599-de79c24e8769	{"id": "31c3369d-d98a-458f-8599-de79c24e8769", "userId": "ac521a2a-d933-42f9-b3a4-2d7399880057", "metadata": {"createdDate": "2022-06-23T15:14:02.053Z", "updatedDate": "2022-06-23T15:14:02.053Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.053	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f1ddb032-a59b-4660-99da-1af6f612f240	{"id": "f1ddb032-a59b-4660-99da-1af6f612f240", "userId": "f046c9bd-45aa-4ab1-ad3f-461ead3dfdc1", "metadata": {"createdDate": "2022-06-23T15:14:01.983Z", "updatedDate": "2022-06-23T15:14:01.983Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.983	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6d39fcc6-d99c-418a-9d7c-7c5fc681d567	{"id": "6d39fcc6-d99c-418a-9d7c-7c5fc681d567", "userId": "c6a1f097-1292-441c-a760-682279a7f94c", "metadata": {"createdDate": "2022-06-23T15:14:01.989Z", "updatedDate": "2022-06-23T15:14:01.989Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.989	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f363dda7-a547-48e8-91df-f868a17c41cb	{"id": "f363dda7-a547-48e8-91df-f868a17c41cb", "userId": "b3dae815-3d30-49f9-ac26-363e661382a0", "metadata": {"createdDate": "2022-06-23T15:14:01.996Z", "updatedDate": "2022-06-23T15:14:01.996Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:01.996	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
dd407585-fcbc-40d7-b46c-eeabaa009aad	{"id": "dd407585-fcbc-40d7-b46c-eeabaa009aad", "userId": "9e87bfea-2d31-4cc3-9cef-9e1e67553243", "metadata": {"createdDate": "2022-06-23T15:14:02.006Z", "updatedDate": "2022-06-23T15:14:02.006Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.006	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8562ed87-0922-42b7-a047-16d6d7b8fd6b	{"id": "8562ed87-0922-42b7-a047-16d6d7b8fd6b", "userId": "a208cf17-a7f0-452d-ae0e-64011232c86d", "metadata": {"createdDate": "2022-06-23T15:14:02.015Z", "updatedDate": "2022-06-23T15:14:02.015Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.015	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4f17c256-f93b-4976-a213-080def24b664	{"id": "4f17c256-f93b-4976-a213-080def24b664", "userId": "66fe5bd9-1129-4b40-b54d-05b4c358463c", "metadata": {"createdDate": "2022-06-23T15:14:02.025Z", "updatedDate": "2022-06-23T15:14:02.025Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.025	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ed364508-e5cc-46f3-9589-1f23c3c9ed4d	{"id": "ed364508-e5cc-46f3-9589-1f23c3c9ed4d", "userId": "0a985a0a-b515-42a0-8ec2-1c2b7e8a1d8c", "metadata": {"createdDate": "2022-06-23T15:14:02.036Z", "updatedDate": "2022-06-23T15:14:02.036Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.036	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0ffe5ccd-9934-4951-a426-d07d18b141c2	{"id": "0ffe5ccd-9934-4951-a426-d07d18b141c2", "userId": "ea2ef01f-d732-4119-90ab-ee6df447548f", "metadata": {"createdDate": "2022-06-23T15:14:02.041Z", "updatedDate": "2022-06-23T15:14:02.041Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.041	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
8593f7b1-0e88-4e3a-b31c-eb762c416303	{"id": "8593f7b1-0e88-4e3a-b31c-eb762c416303", "userId": "48a3115d-d476-4582-b6a8-55c09eed7ec7", "metadata": {"createdDate": "2022-06-23T15:14:02.050Z", "updatedDate": "2022-06-23T15:14:02.050Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.05	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4c14254d-280a-4a20-bcda-3f24a898b348	{"id": "4c14254d-280a-4a20-bcda-3f24a898b348", "userId": "07066a1f-1fb7-4793-bbca-7cd8d1ea90ab", "metadata": {"createdDate": "2022-06-23T15:14:02.057Z", "updatedDate": "2022-06-23T15:14:02.057Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.057	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9b4cb80b-d312-485f-99c2-c45c3dd26336	{"id": "9b4cb80b-d312-485f-99c2-c45c3dd26336", "userId": "f57c13f1-6114-41b2-aa6f-33045068d6be", "metadata": {"createdDate": "2022-06-23T15:14:02.068Z", "updatedDate": "2022-06-23T15:14:02.068Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.068	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
913bc692-756e-477a-8c98-02823fe36263	{"id": "913bc692-756e-477a-8c98-02823fe36263", "userId": "aace299f-7a74-4118-9cf3-599110dce278", "metadata": {"createdDate": "2022-06-23T15:14:02.078Z", "updatedDate": "2022-06-23T15:14:02.078Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.078	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e4de865f-6dfb-4be9-9f0b-ed8e371a7447	{"id": "e4de865f-6dfb-4be9-9f0b-ed8e371a7447", "userId": "fad510e6-5b8d-4b10-b846-ce6ff7457629", "metadata": {"createdDate": "2022-06-23T15:14:02.086Z", "updatedDate": "2022-06-23T15:14:02.086Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.086	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
14920740-b5af-4b37-9f25-1ba2c0283ae5	{"id": "14920740-b5af-4b37-9f25-1ba2c0283ae5", "userId": "b3f61b07-a1b3-44ac-bb7f-622b90ac17c3", "metadata": {"createdDate": "2022-06-23T15:14:02.093Z", "updatedDate": "2022-06-23T15:14:02.093Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.093	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9819bba2-786a-4a85-a603-8a710f700166	{"id": "9819bba2-786a-4a85-a603-8a710f700166", "userId": "46ed7160-426b-460e-91b3-ab22a7d6fc26", "metadata": {"createdDate": "2022-06-23T15:14:02.101Z", "updatedDate": "2022-06-23T15:14:02.101Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.101	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
710b3d8c-e9f9-49de-a9a6-8be4a878e300	{"id": "710b3d8c-e9f9-49de-a9a6-8be4a878e300", "userId": "011dc219-6b7f-4d93-ae7f-f512ed651493", "metadata": {"createdDate": "2022-06-23T15:14:02.108Z", "updatedDate": "2022-06-23T15:14:02.108Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.108	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2458c7d1-787a-4584-8d41-73646be0cf56	{"id": "2458c7d1-787a-4584-8d41-73646be0cf56", "userId": "bc048122-1914-4971-ab0f-62303fef71aa", "metadata": {"createdDate": "2022-06-23T15:14:02.067Z", "updatedDate": "2022-06-23T15:14:02.067Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.067	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
95e20192-b3e9-4ad9-ad2f-f772d1e1a056	{"id": "95e20192-b3e9-4ad9-ad2f-f772d1e1a056", "userId": "22b0e29a-cc5d-456b-b272-b521ad5d2a39", "metadata": {"createdDate": "2022-06-23T15:14:02.073Z", "updatedDate": "2022-06-23T15:14:02.073Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.073	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
715246ac-eca7-494d-b968-a96fd9fc473d	{"id": "715246ac-eca7-494d-b968-a96fd9fc473d", "userId": "c9255397-a8cb-4208-9558-1aae0e6f2c68", "metadata": {"createdDate": "2022-06-23T15:14:02.081Z", "updatedDate": "2022-06-23T15:14:02.081Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.081	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
55c39724-cb81-49a1-826b-89a2854712a0	{"id": "55c39724-cb81-49a1-826b-89a2854712a0", "userId": "4acbd1f5-dbfe-4928-8325-2955e50faa4b", "metadata": {"createdDate": "2022-06-23T15:14:02.090Z", "updatedDate": "2022-06-23T15:14:02.090Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.09	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
980afae7-adda-4917-a0cb-7ba97dc7caaa	{"id": "980afae7-adda-4917-a0cb-7ba97dc7caaa", "userId": "eaeffd06-57d3-488c-bd1b-c39d5c62e97d", "metadata": {"createdDate": "2022-06-23T15:14:02.098Z", "updatedDate": "2022-06-23T15:14:02.098Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.098	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b161e231-af31-465d-beb5-c52035f6025c	{"id": "b161e231-af31-465d-beb5-c52035f6025c", "userId": "1dbc9318-9718-4e9d-b32a-6684cf258910", "metadata": {"createdDate": "2022-06-23T15:14:02.107Z", "updatedDate": "2022-06-23T15:14:02.107Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.107	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
80f27589-d2e5-49dc-87b3-b8f85c06c6ea	{"id": "80f27589-d2e5-49dc-87b3-b8f85c06c6ea", "userId": "f643e743-3496-4ecd-94d7-1ca2fdf56c82", "metadata": {"createdDate": "2022-06-23T15:14:02.114Z", "updatedDate": "2022-06-23T15:14:02.114Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.114	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b4d30c05-dac1-490b-b17d-b5975e37ef77	{"id": "b4d30c05-dac1-490b-b17d-b5975e37ef77", "userId": "67e40b72-66ca-4113-bed9-17a40bc448e0", "metadata": {"createdDate": "2022-06-23T15:14:02.118Z", "updatedDate": "2022-06-23T15:14:02.118Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.118	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7bf728f8-9642-4c77-9643-af4a7a84c1b2	{"id": "7bf728f8-9642-4c77-9643-af4a7a84c1b2", "userId": "f62dc160-eacc-4922-a0cb-e1ed68a44601", "metadata": {"createdDate": "2022-06-23T15:14:02.124Z", "updatedDate": "2022-06-23T15:14:02.124Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.124	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2dbc99b0-d78b-400a-82a4-f0cc3590b132	{"id": "2dbc99b0-d78b-400a-82a4-f0cc3590b132", "userId": "c78aa9ec-b7d3-4d53-9e43-20296f39b496", "metadata": {"createdDate": "2022-06-23T15:14:02.129Z", "updatedDate": "2022-06-23T15:14:02.129Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.129	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2f25cc4c-fd3a-410c-a19c-c4f1fb29ad5e	{"id": "2f25cc4c-fd3a-410c-a19c-c4f1fb29ad5e", "userId": "9a04ae0d-e39f-44c3-b520-43144f6d93e4", "metadata": {"createdDate": "2022-06-23T15:14:02.136Z", "updatedDate": "2022-06-23T15:14:02.136Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.136	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9926ef7c-bc5c-4c18-8b6e-54e7e89680d8	{"id": "9926ef7c-bc5c-4c18-8b6e-54e7e89680d8", "userId": "2a823816-c059-4703-becf-0cc68a734189", "metadata": {"createdDate": "2022-06-23T15:14:02.145Z", "updatedDate": "2022-06-23T15:14:02.145Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.145	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9073fff2-5a2b-4981-818e-1b5a64e2fb05	{"id": "9073fff2-5a2b-4981-818e-1b5a64e2fb05", "userId": "0cce8c30-0a0d-4ebb-a107-cf47ad35eafb", "metadata": {"createdDate": "2022-06-23T15:14:02.150Z", "updatedDate": "2022-06-23T15:14:02.150Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.15	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f14a56a2-3004-4fd6-a521-b778d93d0f01	{"id": "f14a56a2-3004-4fd6-a521-b778d93d0f01", "userId": "259d55dc-015d-420a-b13d-8706018305b1", "metadata": {"createdDate": "2022-06-23T15:14:02.156Z", "updatedDate": "2022-06-23T15:14:02.156Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.156	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
49354d0d-2b9f-4291-972e-1c30e2929607	{"id": "49354d0d-2b9f-4291-972e-1c30e2929607", "userId": "5e84b6a4-fde4-4099-ab54-c82c9041f685", "metadata": {"createdDate": "2022-06-23T15:14:02.160Z", "updatedDate": "2022-06-23T15:14:02.160Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.16	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2ba1b7e7-ce5c-4ed7-b993-0ac0e8f33c53	{"id": "2ba1b7e7-ce5c-4ed7-b993-0ac0e8f33c53", "userId": "4c564a60-8ac6-41f9-a8b6-088901a5f8ca", "metadata": {"createdDate": "2022-06-23T15:14:02.163Z", "updatedDate": "2022-06-23T15:14:02.163Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.163	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
689be60d-12fb-4a1c-813d-8265ada3d92c	{"id": "689be60d-12fb-4a1c-813d-8265ada3d92c", "userId": "a56edfbe-087e-4f79-bd7e-d855fbe746e4", "metadata": {"createdDate": "2022-06-23T15:14:02.071Z", "updatedDate": "2022-06-23T15:14:02.071Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.071	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
94a744ed-8e7d-4348-96bb-5de98d5389bb	{"id": "94a744ed-8e7d-4348-96bb-5de98d5389bb", "userId": "2c2e383d-7369-4aff-afb7-eb3db4cb71a0", "metadata": {"createdDate": "2022-06-23T15:14:02.084Z", "updatedDate": "2022-06-23T15:14:02.084Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.084	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
809fa977-759b-4b63-a47e-ddd9f4968c7c	{"id": "809fa977-759b-4b63-a47e-ddd9f4968c7c", "userId": "a7fb2289-b4dc-4deb-8fd3-86cf8e2b7db6", "metadata": {"createdDate": "2022-06-23T15:14:02.096Z", "updatedDate": "2022-06-23T15:14:02.096Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.096	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
92cb2c89-4e61-48e4-9367-714a9f2154f1	{"id": "92cb2c89-4e61-48e4-9367-714a9f2154f1", "userId": "54f65a75-f35b-4f56-86a6-fa4a3d957e57", "metadata": {"createdDate": "2022-06-23T15:14:02.104Z", "updatedDate": "2022-06-23T15:14:02.104Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.104	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
09c51609-8a67-427d-9b8c-1e98213408bb	{"id": "09c51609-8a67-427d-9b8c-1e98213408bb", "userId": "a5a80ce1-c00d-4ede-ba44-912c1e093948", "metadata": {"createdDate": "2022-06-23T15:14:02.111Z", "updatedDate": "2022-06-23T15:14:02.111Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.111	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
273e2cc4-7408-4e4b-bcd0-aca4e736b36c	{"id": "273e2cc4-7408-4e4b-bcd0-aca4e736b36c", "userId": "b4fa5d79-4af6-4623-9331-fabdfee79e0c", "metadata": {"createdDate": "2022-06-23T15:14:02.117Z", "updatedDate": "2022-06-23T15:14:02.117Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.117	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
9419615b-003c-4982-ac9f-41a4b96e4570	{"id": "9419615b-003c-4982-ac9f-41a4b96e4570", "userId": "0db6912a-40c0-41db-8d15-be05ff851f96", "metadata": {"createdDate": "2022-06-23T15:14:02.122Z", "updatedDate": "2022-06-23T15:14:02.122Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.122	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6ec2047b-960a-48b7-ae29-dbf9b920f1be	{"id": "6ec2047b-960a-48b7-ae29-dbf9b920f1be", "userId": "a601f1f2-88a4-465a-850e-8f50c28ce7d9", "metadata": {"createdDate": "2022-06-23T15:14:02.130Z", "updatedDate": "2022-06-23T15:14:02.130Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.13	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ff75e556-8df7-458c-a363-7447d3153eb4	{"id": "ff75e556-8df7-458c-a363-7447d3153eb4", "userId": "97f100da-9218-4351-bc14-ef0558f01625", "metadata": {"createdDate": "2022-06-23T15:14:02.134Z", "updatedDate": "2022-06-23T15:14:02.134Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.134	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c8090e54-de6e-4518-8f76-dcbb43cbeaad	{"id": "c8090e54-de6e-4518-8f76-dcbb43cbeaad", "userId": "56708cfe-750e-49ad-b72a-003ce7ad78a4", "metadata": {"createdDate": "2022-06-23T15:14:02.139Z", "updatedDate": "2022-06-23T15:14:02.139Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.139	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
5e098f39-3563-4bdd-a835-3f0aa88dcfdb	{"id": "5e098f39-3563-4bdd-a835-3f0aa88dcfdb", "userId": "5c4910af-508f-49f5-b2c2-f856ffd7f2aa", "metadata": {"createdDate": "2022-06-23T15:14:02.142Z", "updatedDate": "2022-06-23T15:14:02.142Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.142	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
45760f89-6467-4f79-bd18-06ef244662da	{"id": "45760f89-6467-4f79-bd18-06ef244662da", "userId": "11dd4634-e4a9-45f0-9683-fa4d7a8f9728", "metadata": {"createdDate": "2022-06-23T15:14:02.147Z", "updatedDate": "2022-06-23T15:14:02.147Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.147	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4026f725-51c6-429d-bd61-0ba888e364da	{"id": "4026f725-51c6-429d-bd61-0ba888e364da", "userId": "2cb8a9f5-5a04-4b26-89de-c5a522638de2", "metadata": {"createdDate": "2022-06-23T15:14:02.153Z", "updatedDate": "2022-06-23T15:14:02.153Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.153	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2706fd09-a2dd-407e-abf1-dac4d41efea3	{"id": "2706fd09-a2dd-407e-abf1-dac4d41efea3", "userId": "384272bb-efab-4e94-b3b8-f67f20796f20", "metadata": {"createdDate": "2022-06-23T15:14:02.158Z", "updatedDate": "2022-06-23T15:14:02.158Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.158	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
abcd040a-b09d-4ab7-be7d-3dd7873fcc2a	{"id": "abcd040a-b09d-4ab7-be7d-3dd7873fcc2a", "userId": "eaed771f-1472-4f5a-a31f-bbb5922ba5fe", "metadata": {"createdDate": "2022-06-23T15:14:02.162Z", "updatedDate": "2022-06-23T15:14:02.162Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.162	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
697657fa-7a10-436b-a5ef-58675b1ca26c	{"id": "697657fa-7a10-436b-a5ef-58675b1ca26c", "userId": "f4e0bd3e-1592-4a70-9f4a-41ccb6ca6b43", "metadata": {"createdDate": "2022-06-23T15:14:02.175Z", "updatedDate": "2022-06-23T15:14:02.175Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.175	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e56e3dfe-07de-4009-b419-612029a81872	{"id": "e56e3dfe-07de-4009-b419-612029a81872", "userId": "f45d047f-248d-424a-9571-8b1249279c02", "metadata": {"createdDate": "2022-06-23T15:14:02.083Z", "updatedDate": "2022-06-23T15:14:02.083Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.083	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7cab10e8-9ab9-43e1-b715-71d661a9d9b8	{"id": "7cab10e8-9ab9-43e1-b715-71d661a9d9b8", "userId": "acf8aab2-91ee-4210-bb7c-b688d66a9de4", "metadata": {"createdDate": "2022-06-23T15:14:02.091Z", "updatedDate": "2022-06-23T15:14:02.091Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.091	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
844b59bd-fb38-4d3c-8e81-49216d9eb224	{"id": "844b59bd-fb38-4d3c-8e81-49216d9eb224", "userId": "16757efe-86ac-40bb-bdd6-fafee02463c7", "metadata": {"createdDate": "2022-06-23T15:14:02.103Z", "updatedDate": "2022-06-23T15:14:02.103Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.103	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
777880cc-f431-4362-ac2f-c859a35bfe63	{"id": "777880cc-f431-4362-ac2f-c859a35bfe63", "userId": "42e9d211-4bfb-45fe-a088-f19d0a514f98", "metadata": {"createdDate": "2022-06-23T15:14:02.109Z", "updatedDate": "2022-06-23T15:14:02.109Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.109	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
f9f43dcd-03ea-4deb-8757-0f6f0fdef233	{"id": "f9f43dcd-03ea-4deb-8757-0f6f0fdef233", "userId": "04e1cda1-a049-463b-97af-98c59a8fd806", "metadata": {"createdDate": "2022-06-23T15:14:02.115Z", "updatedDate": "2022-06-23T15:14:02.115Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.115	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
13e8e39f-83db-4cbc-97e2-0fc946a17d01	{"id": "13e8e39f-83db-4cbc-97e2-0fc946a17d01", "userId": "e923bd61-bf27-42a9-8293-ed7738c24bca", "metadata": {"createdDate": "2022-06-23T15:14:02.121Z", "updatedDate": "2022-06-23T15:14:02.121Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.121	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
85b25dbb-c126-41ce-9c3a-2ead6b24d8cc	{"id": "85b25dbb-c126-41ce-9c3a-2ead6b24d8cc", "userId": "ab579dc3-219b-4f5b-8068-ab1c7a55c402", "metadata": {"createdDate": "2022-06-23T15:14:02.127Z", "updatedDate": "2022-06-23T15:14:02.127Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.127	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b10b4587-114d-48c5-8af6-76cda40ba832	{"id": "b10b4587-114d-48c5-8af6-76cda40ba832", "userId": "e14b1bc1-8784-4b55-bfbe-e5ec8ce0b07a", "metadata": {"createdDate": "2022-06-23T15:14:02.132Z", "updatedDate": "2022-06-23T15:14:02.132Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.132	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
4439f673-753a-47a0-8b99-44102008b863	{"id": "4439f673-753a-47a0-8b99-44102008b863", "userId": "3793853e-6297-424d-abea-24525079f658", "metadata": {"createdDate": "2022-06-23T15:14:02.137Z", "updatedDate": "2022-06-23T15:14:02.137Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.137	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
adcff4c9-ed63-4b1e-8861-fa6a816cd7fb	{"id": "adcff4c9-ed63-4b1e-8861-fa6a816cd7fb", "userId": "6c76eeec-183d-4635-9019-11ce8623d50c", "metadata": {"createdDate": "2022-06-23T15:14:02.146Z", "updatedDate": "2022-06-23T15:14:02.146Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.146	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
73f663e1-4eb4-4bfe-be44-c9cca0d9d178	{"id": "73f663e1-4eb4-4bfe-be44-c9cca0d9d178", "userId": "df84acd4-4425-47e9-9a25-db8eb2973950", "metadata": {"createdDate": "2022-06-23T15:14:02.152Z", "updatedDate": "2022-06-23T15:14:02.152Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.152	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
5402d436-d8b8-4083-a420-c91b7dadba54	{"id": "5402d436-d8b8-4083-a420-c91b7dadba54", "userId": "423d5beb-3196-449e-aacb-9595d6321950", "metadata": {"createdDate": "2022-06-23T15:14:02.157Z", "updatedDate": "2022-06-23T15:14:02.157Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.157	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
854876c2-2520-48fa-8374-67a3f9149ebf	{"id": "854876c2-2520-48fa-8374-67a3f9149ebf", "userId": "2fabd929-3ed9-40ae-aaf2-6c39c4bebf13", "metadata": {"createdDate": "2022-06-23T15:14:02.161Z", "updatedDate": "2022-06-23T15:14:02.161Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.161	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7a8c8d2c-7464-4951-90da-d35fe5215fef	{"id": "7a8c8d2c-7464-4951-90da-d35fe5215fef", "userId": "734f2e97-2c41-4e70-9b98-44cead2607e4", "metadata": {"createdDate": "2022-06-23T15:14:02.171Z", "updatedDate": "2022-06-23T15:14:02.171Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.171	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
e7d95919-fea1-43d6-b58e-18c3f839c9af	{"id": "e7d95919-fea1-43d6-b58e-18c3f839c9af", "userId": "c51cf0e7-ea33-4638-a54c-afffc75a680b", "metadata": {"createdDate": "2022-06-23T15:14:02.182Z", "updatedDate": "2022-06-23T15:14:02.182Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.182	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
6c32c6c8-0a52-4d9f-b3d5-d115f82f1b05	{"id": "6c32c6c8-0a52-4d9f-b3d5-d115f82f1b05", "userId": "eb81f274-3676-45a9-8e8d-8a151af2506b", "metadata": {"createdDate": "2022-06-23T15:14:02.116Z", "updatedDate": "2022-06-23T15:14:02.116Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.116	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7ea997ec-abe9-473d-a511-22ad0113f2f0	{"id": "7ea997ec-abe9-473d-a511-22ad0113f2f0", "userId": "eb7696da-a2c3-4166-8aba-757c42556d1e", "metadata": {"createdDate": "2022-06-23T15:14:02.119Z", "updatedDate": "2022-06-23T15:14:02.119Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.119	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
b2a99f48-aad0-47a0-b97f-80660a776305	{"id": "b2a99f48-aad0-47a0-b97f-80660a776305", "userId": "4cb9a24c-76e1-4755-9f54-f51115e00b53", "metadata": {"createdDate": "2022-06-23T15:14:02.123Z", "updatedDate": "2022-06-23T15:14:02.123Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.123	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ca5189f9-d7fb-48f2-858d-73ca4a4807a8	{"id": "ca5189f9-d7fb-48f2-858d-73ca4a4807a8", "userId": "01b9d72b-9aab-4efd-97a4-d03c1667bf0d", "metadata": {"createdDate": "2022-06-23T15:14:02.127Z", "updatedDate": "2022-06-23T15:14:02.127Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.127	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
0a895e56-9f14-4439-874d-112a79ccff47	{"id": "0a895e56-9f14-4439-874d-112a79ccff47", "userId": "0b6d1482-de21-4643-ae5b-90b4c7164c4a", "metadata": {"createdDate": "2022-06-23T15:14:02.131Z", "updatedDate": "2022-06-23T15:14:02.131Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.131	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
759430b8-808e-483c-b12b-ce09e6a0dd0b	{"id": "759430b8-808e-483c-b12b-ce09e6a0dd0b", "userId": "75da2654-00a8-4ca5-9c73-2bf9e1e5c883", "metadata": {"createdDate": "2022-06-23T15:14:02.137Z", "updatedDate": "2022-06-23T15:14:02.137Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.137	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
a271576e-c9a7-43f2-a5b3-1ca6a0d36c51	{"id": "a271576e-c9a7-43f2-a5b3-1ca6a0d36c51", "userId": "5cf0c0d9-17cc-42f1-87c1-10ec6476fc3a", "metadata": {"createdDate": "2022-06-23T15:14:02.139Z", "updatedDate": "2022-06-23T15:14:02.139Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.139	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
7c00fb0a-2d65-42cb-b8db-ea05f07aeb93	{"id": "7c00fb0a-2d65-42cb-b8db-ea05f07aeb93", "userId": "0aa0c321-9974-4a67-92dc-bca029d093e2", "metadata": {"createdDate": "2022-06-23T15:14:02.145Z", "updatedDate": "2022-06-23T15:14:02.145Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.145	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
2bb88321-b479-4cd9-9364-815cd5435776	{"id": "2bb88321-b479-4cd9-9364-815cd5435776", "userId": "1be7f410-6ec3-4e88-ac25-0f8d8c63274d", "metadata": {"createdDate": "2022-06-23T15:14:02.148Z", "updatedDate": "2022-06-23T15:14:02.148Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.148	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
877c4a6e-d8da-4c7b-9a6f-6fc21212b5c6	{"id": "877c4a6e-d8da-4c7b-9a6f-6fc21212b5c6", "userId": "d20d2f4e-6356-4dc3-b7b1-8b9fb5564e02", "metadata": {"createdDate": "2022-06-23T15:14:02.155Z", "updatedDate": "2022-06-23T15:14:02.155Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.155	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
ccf62300-d3ec-45c9-bc48-5f18a2cb014d	{"id": "ccf62300-d3ec-45c9-bc48-5f18a2cb014d", "userId": "b3e39715-0659-4776-9d40-abe655408d84", "metadata": {"createdDate": "2022-06-23T15:14:02.170Z", "updatedDate": "2022-06-23T15:14:02.170Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.17	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
177ec232-3c8d-4d3c-ac18-4ad4c40711d4	{"id": "177ec232-3c8d-4d3c-ac18-4ad4c40711d4", "userId": "420d485a-033f-4999-ab2c-12c00cd5ec07", "metadata": {"createdDate": "2022-06-23T15:14:02.178Z", "updatedDate": "2022-06-23T15:14:02.178Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.178	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
c9ec8f8a-8604-46d2-b9e8-3cf65c94361e	{"id": "c9ec8f8a-8604-46d2-b9e8-3cf65c94361e", "userId": "1ee0a888-ade1-4bc7-a9b6-15a2d46a6b18", "metadata": {"createdDate": "2022-06-23T15:14:02.174Z", "updatedDate": "2022-06-23T15:14:02.174Z"}, "servicePointsIds": ["c4c90014-c8c9-4ade-8f24-b5e313319f4b", "7c5abc9f-f3d7-4856-b8d7-6712462ca007", "3a40852d-49fd-4df2-a1f9-6e2641a6e91f"], "defaultServicePointId": "c4c90014-c8c9-4ade-8f24-b5e313319f4b"}	2022-06-23 15:14:02.174	\N	c4c90014-c8c9-4ade-8f24-b5e313319f4b
\.


--
-- Data for Name: statistical_code; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.statistical_code (id, jsonb, creation_date, created_by, statisticalcodetypeid) FROM stdin;
c7a32c50-ea7c-43b7-87ab-d134c8371330	{"id": "c7a32c50-ea7c-43b7-87ab-d134c8371330", "code": "ASER", "name": "Active serial", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.776Z", "updatedDate": "2022-06-23T15:13:59.776Z"}, "statisticalCodeTypeId": "e2ab27f9-a726-4e5e-9963-fff9e6128680"}	2022-06-23 15:13:59.776	\N	e2ab27f9-a726-4e5e-9963-fff9e6128680
d82c025e-436d-4006-a677-bd2b4cdb7692	{"id": "d82c025e-436d-4006-a677-bd2b4cdb7692", "code": "mss", "name": "Manuscripts (mss)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.777Z", "updatedDate": "2022-06-23T15:13:59.777Z"}, "statisticalCodeTypeId": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544"}	2022-06-23 15:13:59.777	\N	0d3ec58e-dc3c-4aa1-9eba-180fca95c544
e10796e0-a594-47b7-b748-3a81b69b3d9b	{"id": "e10796e0-a594-47b7-b748-3a81b69b3d9b", "code": "audstream", "name": "Streaming audio (audstream)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.779Z", "updatedDate": "2022-06-23T15:13:59.779Z"}, "statisticalCodeTypeId": "d816175b-578f-4056-af61-689f449c3c45"}	2022-06-23 15:13:59.779	\N	d816175b-578f-4056-af61-689f449c3c45
0e516e54-bf36-4fc2-a0f7-3fe89a61c9c0	{"id": "0e516e54-bf36-4fc2-a0f7-3fe89a61c9c0", "code": "ISER", "name": "Inactive serial", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.779Z", "updatedDate": "2022-06-23T15:13:59.779Z"}, "statisticalCodeTypeId": "e2ab27f9-a726-4e5e-9963-fff9e6128680"}	2022-06-23 15:13:59.779	\N	e2ab27f9-a726-4e5e-9963-fff9e6128680
16f2d65e-eb68-4ab1-93e3-03af50cb7370	{"id": "16f2d65e-eb68-4ab1-93e3-03af50cb7370", "code": "mfiche", "name": "Microfiche (mfiche)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.780Z", "updatedDate": "2022-06-23T15:13:59.780Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.78	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
950d3370-9a3c-421e-b116-76e7511af9e9	{"id": "950d3370-9a3c-421e-b116-76e7511af9e9", "code": "polsky", "name": "Polsky TECHB@R (polsky)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.785Z", "updatedDate": "2022-06-23T15:13:59.785Z"}, "statisticalCodeTypeId": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544"}	2022-06-23 15:13:59.785	\N	0d3ec58e-dc3c-4aa1-9eba-180fca95c544
a5ccf92e-7b1f-4990-ac03-780a6a767f37	{"id": "a5ccf92e-7b1f-4990-ac03-780a6a767f37", "code": "eserials", "name": "Serials, electronic (eserials)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.786Z", "updatedDate": "2022-06-23T15:13:59.786Z"}, "statisticalCodeTypeId": "d816175b-578f-4056-af61-689f449c3c45"}	2022-06-23 15:13:59.786	\N	d816175b-578f-4056-af61-689f449c3c45
b2c0e100-0485-43f2-b161-3c60aac9f68a	{"id": "b2c0e100-0485-43f2-b161-3c60aac9f68a", "code": "evisual", "name": "Visual, static, electronic", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.788Z", "updatedDate": "2022-06-23T15:13:59.788Z"}, "statisticalCodeTypeId": "d816175b-578f-4056-af61-689f449c3c45"}	2022-06-23 15:13:59.788	\N	d816175b-578f-4056-af61-689f449c3c45
b76a3088-8de6-46c8-a130-c8e74b8d2c5b	{"id": "b76a3088-8de6-46c8-a130-c8e74b8d2c5b", "code": "emaps", "name": "Maps, electronic (emaps)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.787Z", "updatedDate": "2022-06-23T15:13:59.787Z"}, "statisticalCodeTypeId": "d816175b-578f-4056-af61-689f449c3c45"}	2022-06-23 15:13:59.787	\N	d816175b-578f-4056-af61-689f449c3c45
30b5400d-0b9e-4757-a3d0-db0d30a49e72	{"id": "30b5400d-0b9e-4757-a3d0-db0d30a49e72", "code": "music", "name": "Music scores, print (music)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.790Z", "updatedDate": "2022-06-23T15:13:59.790Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.79	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
1c622d0f-2e91-4c30-ba43-2750f9735f51	{"id": "1c622d0f-2e91-4c30-ba43-2750f9735f51", "code": "mfilm", "name": "Microfilm (mfilm)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.789Z", "updatedDate": "2022-06-23T15:13:59.789Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.789	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
b6b46869-f3c1-4370-b603-29774a1e42b1	{"id": "b6b46869-f3c1-4370-b603-29774a1e42b1", "code": "arch", "name": "Archives (arch)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.791Z", "updatedDate": "2022-06-23T15:13:59.791Z"}, "statisticalCodeTypeId": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544"}	2022-06-23 15:13:59.791	\N	0d3ec58e-dc3c-4aa1-9eba-180fca95c544
c4073462-6144-4b69-a543-dd131e241799	{"id": "c4073462-6144-4b69-a543-dd131e241799", "code": "withdrawn", "name": "Withdrawn (withdrawn)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.793Z", "updatedDate": "2022-06-23T15:13:59.793Z"}, "statisticalCodeTypeId": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544"}	2022-06-23 15:13:59.793	\N	0d3ec58e-dc3c-4aa1-9eba-180fca95c544
97e91f57-fad7-41ea-a660-4031bf8d4ea8	{"id": "97e91f57-fad7-41ea-a660-4031bf8d4ea8", "code": "maps", "name": "Maps, print (maps)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.795Z", "updatedDate": "2022-06-23T15:13:59.795Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.795	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
ecab577d-a050-4ea2-8a86-ea5a234283ea	{"id": "ecab577d-a050-4ea2-8a86-ea5a234283ea", "code": "emusic", "name": "Music scores, electronic", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.795Z", "updatedDate": "2022-06-23T15:13:59.795Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.795	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
91b8f0b4-0e13-4270-9fd6-e39203d0f449	{"id": "91b8f0b4-0e13-4270-9fd6-e39203d0f449", "code": "rnonmusic", "name": "Non-music sound recordings (rnonmusic)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.796Z", "updatedDate": "2022-06-23T15:13:59.796Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.796	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
972f81d5-9f8f-4b56-a10e-5c05419718e6	{"id": "972f81d5-9f8f-4b56-a10e-5c05419718e6", "code": "visual", "name": "Visual materials, DVDs, etc. (visual)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.798Z", "updatedDate": "2022-06-23T15:13:59.798Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.798	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
6d584d0e-3dbc-46c4-a1bd-e9238dd9a6be	{"id": "6d584d0e-3dbc-46c4-a1bd-e9238dd9a6be", "code": "vidstream", "name": "Streaming video (vidstream)", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.800Z", "updatedDate": "2022-06-23T15:13:59.800Z"}, "statisticalCodeTypeId": "d816175b-578f-4056-af61-689f449c3c45"}	2022-06-23 15:13:59.8	\N	d816175b-578f-4056-af61-689f449c3c45
264c4f94-1538-43a3-8b40-bed68384b31b	{"id": "264c4f94-1538-43a3-8b40-bed68384b31b", "code": "XOCLC", "name": "Do not share with OCLC", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.802Z", "updatedDate": "2022-06-23T15:13:59.802Z"}, "statisticalCodeTypeId": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544"}	2022-06-23 15:13:59.802	\N	0d3ec58e-dc3c-4aa1-9eba-180fca95c544
775b6ad4-9c35-4d29-bf78-8775a9b42226	{"id": "775b6ad4-9c35-4d29-bf78-8775a9b42226", "code": "serials", "name": "Serials, print (serials)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.803Z", "updatedDate": "2022-06-23T15:13:59.803Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.803	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
6899291a-1fb9-4130-98ce-b40368556818	{"id": "6899291a-1fb9-4130-98ce-b40368556818", "code": "rmusic", "name": "Music sound recordings", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.802Z", "updatedDate": "2022-06-23T15:13:59.802Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.802	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
9d8abbe2-1a94-4866-8731-4d12ac09f7a8	{"id": "9d8abbe2-1a94-4866-8731-4d12ac09f7a8", "code": "ebooks", "name": "Books, electronic (ebooks)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.804Z", "updatedDate": "2022-06-23T15:13:59.804Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.804	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
b5968c9e-cddc-4576-99e3-8e60aed8b0dd	{"id": "b5968c9e-cddc-4576-99e3-8e60aed8b0dd", "code": "books", "name": "Book, print (books)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.805Z", "updatedDate": "2022-06-23T15:13:59.805Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.805	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
2850630b-cd12-4379-af57-5c51491a6873	{"id": "2850630b-cd12-4379-af57-5c51491a6873", "code": "mmedia", "name": "Mixed media (mmedia)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.812Z", "updatedDate": "2022-06-23T15:13:59.812Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.812	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
bb76b1c1-c9df-445c-8deb-68bb3580edc2	{"id": "bb76b1c1-c9df-445c-8deb-68bb3580edc2", "code": "compfiles", "name": "Computer files, CDs, etc (compfiles)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.807Z", "updatedDate": "2022-06-23T15:13:59.807Z"}, "statisticalCodeTypeId": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3"}	2022-06-23 15:13:59.807	\N	3abd6fc2-b3e4-4879-b1e1-78be41769fe3
38249f9e-13f8-48bc-a010-8023cd194af5	{"id": "38249f9e-13f8-48bc-a010-8023cd194af5", "code": "its", "name": "Information Technology Services (its)", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.811Z", "updatedDate": "2022-06-23T15:13:59.811Z"}, "statisticalCodeTypeId": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544"}	2022-06-23 15:13:59.811	\N	0d3ec58e-dc3c-4aa1-9eba-180fca95c544
f47b773a-bd5f-4246-ac1e-fa4adcd0dcdf	{"id": "f47b773a-bd5f-4246-ac1e-fa4adcd0dcdf", "code": "UCPress", "name": "University of Chicago Press Imprint", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.809Z", "updatedDate": "2022-06-23T15:13:59.809Z"}, "statisticalCodeTypeId": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544"}	2022-06-23 15:13:59.809	\N	0d3ec58e-dc3c-4aa1-9eba-180fca95c544
0868921a-4407-47c9-9b3e-db94644dbae7	{"id": "0868921a-4407-47c9-9b3e-db94644dbae7", "code": "ENF", "name": "Entry not found", "source": "UC", "metadata": {"createdDate": "2022-06-23T15:13:59.810Z", "updatedDate": "2022-06-23T15:13:59.810Z"}, "statisticalCodeTypeId": "e2ab27f9-a726-4e5e-9963-fff9e6128680"}	2022-06-23 15:13:59.81	\N	e2ab27f9-a726-4e5e-9963-fff9e6128680
\.


--
-- Data for Name: statistical_code_type; Type: TABLE DATA; Schema: lotus_mod_inventory_storage; Owner: postgres
--

COPY lotus_mod_inventory_storage.statistical_code_type (id, jsonb, creation_date, created_by) FROM stdin;
3abd6fc2-b3e4-4879-b1e1-78be41769fe3	{"id": "3abd6fc2-b3e4-4879-b1e1-78be41769fe3", "name": "ARL (Collection stats)", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.738Z", "updatedDate": "2022-06-23T15:13:59.738Z"}}	2022-06-23 15:13:59.738	\N
e2ab27f9-a726-4e5e-9963-fff9e6128680	{"id": "e2ab27f9-a726-4e5e-9963-fff9e6128680", "name": "SERM (Serial management)", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.739Z", "updatedDate": "2022-06-23T15:13:59.739Z"}}	2022-06-23 15:13:59.739	\N
0d3ec58e-dc3c-4aa1-9eba-180fca95c544	{"id": "0d3ec58e-dc3c-4aa1-9eba-180fca95c544", "name": "RECM (Record management)", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.739Z", "updatedDate": "2022-06-23T15:13:59.739Z"}}	2022-06-23 15:13:59.739	\N
d816175b-578f-4056-af61-689f449c3c45	{"id": "d816175b-578f-4056-af61-689f449c3c45", "name": "DISC (Discovery)", "source": "folio", "metadata": {"createdDate": "2022-06-23T15:13:59.740Z", "updatedDate": "2022-06-23T15:13:59.740Z"}}	2022-06-23 15:13:59.74	\N
\.


--
-- Name: hrid_holdings_seq; Type: SEQUENCE SET; Schema: lotus_mod_inventory_storage; Owner: postgres
--

SELECT pg_catalog.setval('lotus_mod_inventory_storage.hrid_holdings_seq', 1, false);


--
-- Name: hrid_instances_seq; Type: SEQUENCE SET; Schema: lotus_mod_inventory_storage; Owner: postgres
--

SELECT pg_catalog.setval('lotus_mod_inventory_storage.hrid_instances_seq', 1, false);


--
-- Name: hrid_items_seq; Type: SEQUENCE SET; Schema: lotus_mod_inventory_storage; Owner: postgres
--

SELECT pg_catalog.setval('lotus_mod_inventory_storage.hrid_items_seq', 1, false);


--
-- Name: rmb_internal_id_seq; Type: SEQUENCE SET; Schema: lotus_mod_inventory_storage; Owner: postgres
--

SELECT pg_catalog.setval('lotus_mod_inventory_storage.rmb_internal_id_seq', 1, true);


--
-- Name: alternative_title_type alternative_title_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.alternative_title_type
    ADD CONSTRAINT alternative_title_type_pkey PRIMARY KEY (id);


--
-- Name: async_migration_job async_migration_job_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.async_migration_job
    ADD CONSTRAINT async_migration_job_pkey PRIMARY KEY (id);


--
-- Name: audit_holdings_record audit_holdings_record_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.audit_holdings_record
    ADD CONSTRAINT audit_holdings_record_pkey PRIMARY KEY (id);


--
-- Name: audit_instance audit_instance_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.audit_instance
    ADD CONSTRAINT audit_instance_pkey PRIMARY KEY (id);


--
-- Name: audit_item audit_item_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.audit_item
    ADD CONSTRAINT audit_item_pkey PRIMARY KEY (id);


--
-- Name: authority_note_type authority_note_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.authority_note_type
    ADD CONSTRAINT authority_note_type_pkey PRIMARY KEY (id);


--
-- Name: authority authority_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.authority
    ADD CONSTRAINT authority_pkey PRIMARY KEY (id);


--
-- Name: bound_with_part bound_with_part_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.bound_with_part
    ADD CONSTRAINT bound_with_part_pkey PRIMARY KEY (id);


--
-- Name: call_number_type call_number_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.call_number_type
    ADD CONSTRAINT call_number_type_pkey PRIMARY KEY (id);


--
-- Name: classification_type classification_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.classification_type
    ADD CONSTRAINT classification_type_pkey PRIMARY KEY (id);


--
-- Name: contributor_name_type contributor_name_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.contributor_name_type
    ADD CONSTRAINT contributor_name_type_pkey PRIMARY KEY (id);


--
-- Name: contributor_type contributor_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.contributor_type
    ADD CONSTRAINT contributor_type_pkey PRIMARY KEY (id);


--
-- Name: electronic_access_relationship electronic_access_relationship_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.electronic_access_relationship
    ADD CONSTRAINT electronic_access_relationship_pkey PRIMARY KEY (id);


--
-- Name: holdings_note_type holdings_note_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_note_type
    ADD CONSTRAINT holdings_note_type_pkey PRIMARY KEY (id);


--
-- Name: holdings_record holdings_record_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT holdings_record_pkey PRIMARY KEY (id);


--
-- Name: holdings_records_source holdings_records_source_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_records_source
    ADD CONSTRAINT holdings_records_source_pkey PRIMARY KEY (id);


--
-- Name: holdings_type holdings_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_type
    ADD CONSTRAINT holdings_type_pkey PRIMARY KEY (id);


--
-- Name: hrid_settings hrid_settings_lock_key; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.hrid_settings
    ADD CONSTRAINT hrid_settings_lock_key UNIQUE (lock);


--
-- Name: hrid_settings hrid_settings_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.hrid_settings
    ADD CONSTRAINT hrid_settings_pkey PRIMARY KEY (id);


--
-- Name: identifier_type identifier_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.identifier_type
    ADD CONSTRAINT identifier_type_pkey PRIMARY KEY (id);


--
-- Name: ill_policy ill_policy_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.ill_policy
    ADD CONSTRAINT ill_policy_pkey PRIMARY KEY (id);


--
-- Name: instance_format instance_format_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_format
    ADD CONSTRAINT instance_format_pkey PRIMARY KEY (id);


--
-- Name: instance_note_type instance_note_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_note_type
    ADD CONSTRAINT instance_note_type_pkey PRIMARY KEY (id);


--
-- Name: instance instance_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance
    ADD CONSTRAINT instance_pkey PRIMARY KEY (id);


--
-- Name: instance_relationship instance_relationship_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_relationship
    ADD CONSTRAINT instance_relationship_pkey PRIMARY KEY (id);


--
-- Name: instance_relationship_type instance_relationship_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_relationship_type
    ADD CONSTRAINT instance_relationship_type_pkey PRIMARY KEY (id);


--
-- Name: instance_source_marc instance_source_marc_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_source_marc
    ADD CONSTRAINT instance_source_marc_pkey PRIMARY KEY (id);


--
-- Name: instance_status instance_status_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_status
    ADD CONSTRAINT instance_status_pkey PRIMARY KEY (id);


--
-- Name: instance_type instance_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_type
    ADD CONSTRAINT instance_type_pkey PRIMARY KEY (id);


--
-- Name: item_damaged_status item_damaged_status_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item_damaged_status
    ADD CONSTRAINT item_damaged_status_pkey PRIMARY KEY (id);


--
-- Name: item_note_type item_note_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item_note_type
    ADD CONSTRAINT item_note_type_pkey PRIMARY KEY (id);


--
-- Name: item item_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT item_pkey PRIMARY KEY (id);


--
-- Name: iteration_job iteration_job_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.iteration_job
    ADD CONSTRAINT iteration_job_pkey PRIMARY KEY (id);


--
-- Name: loan_type loan_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.loan_type
    ADD CONSTRAINT loan_type_pkey PRIMARY KEY (id);


--
-- Name: location location_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.location
    ADD CONSTRAINT location_pkey PRIMARY KEY (id);


--
-- Name: loccampus loccampus_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.loccampus
    ADD CONSTRAINT loccampus_pkey PRIMARY KEY (id);


--
-- Name: locinstitution locinstitution_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.locinstitution
    ADD CONSTRAINT locinstitution_pkey PRIMARY KEY (id);


--
-- Name: loclibrary loclibrary_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.loclibrary
    ADD CONSTRAINT loclibrary_pkey PRIMARY KEY (id);


--
-- Name: material_type material_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.material_type
    ADD CONSTRAINT material_type_pkey PRIMARY KEY (id);


--
-- Name: mode_of_issuance mode_of_issuance_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.mode_of_issuance
    ADD CONSTRAINT mode_of_issuance_pkey PRIMARY KEY (id);


--
-- Name: nature_of_content_term nature_of_content_term_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.nature_of_content_term
    ADD CONSTRAINT nature_of_content_term_pkey PRIMARY KEY (id);


--
-- Name: notification_sending_error notification_sending_error_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.notification_sending_error
    ADD CONSTRAINT notification_sending_error_pkey PRIMARY KEY (id);


--
-- Name: preceding_succeeding_title preceding_succeeding_title_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.preceding_succeeding_title
    ADD CONSTRAINT preceding_succeeding_title_pkey PRIMARY KEY (id);


--
-- Name: reindex_job reindex_job_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.reindex_job
    ADD CONSTRAINT reindex_job_pkey PRIMARY KEY (id);


--
-- Name: rmb_internal_index rmb_internal_index_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.rmb_internal_index
    ADD CONSTRAINT rmb_internal_index_pkey PRIMARY KEY (name);


--
-- Name: rmb_internal rmb_internal_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.rmb_internal
    ADD CONSTRAINT rmb_internal_pkey PRIMARY KEY (id);


--
-- Name: rmb_job rmb_job_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.rmb_job
    ADD CONSTRAINT rmb_job_pkey PRIMARY KEY (id);


--
-- Name: service_point service_point_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.service_point
    ADD CONSTRAINT service_point_pkey PRIMARY KEY (id);


--
-- Name: service_point_user service_point_user_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.service_point_user
    ADD CONSTRAINT service_point_user_pkey PRIMARY KEY (id);


--
-- Name: statistical_code statistical_code_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.statistical_code
    ADD CONSTRAINT statistical_code_pkey PRIMARY KEY (id);


--
-- Name: statistical_code_type statistical_code_type_pkey; Type: CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.statistical_code_type
    ADD CONSTRAINT statistical_code_type_pkey PRIMARY KEY (id);


--
-- Name: alternative_title_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX alternative_title_type_name_idx_unique ON lotus_mod_inventory_storage.alternative_title_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: audit_holdings_record_pmh_createddate_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX audit_holdings_record_pmh_createddate_idx ON lotus_mod_inventory_storage.audit_holdings_record USING btree (lotus_mod_inventory_storage.strtotimestamp(((jsonb -> 'record'::text) ->> 'updatedDate'::text)));


--
-- Name: audit_instance_pmh_createddate_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX audit_instance_pmh_createddate_idx ON lotus_mod_inventory_storage.audit_instance USING btree (lotus_mod_inventory_storage.strtotimestamp((jsonb ->> 'createdDate'::text)));


--
-- Name: audit_item_pmh_createddate_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX audit_item_pmh_createddate_idx ON lotus_mod_inventory_storage.audit_item USING btree (lotus_mod_inventory_storage.strtotimestamp(((jsonb -> 'record'::text) ->> 'updatedDate'::text)));


--
-- Name: authority_note_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX authority_note_type_name_idx_unique ON lotus_mod_inventory_storage.authority_note_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: bound_with_part_holdingsrecordid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX bound_with_part_holdingsrecordid_idx ON lotus_mod_inventory_storage.bound_with_part USING btree (holdingsrecordid);


--
-- Name: bound_with_part_itemid_holdingsrecordid_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX bound_with_part_itemid_holdingsrecordid_idx_unique ON lotus_mod_inventory_storage.bound_with_part USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'itemId'::text))), lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'holdingsRecordId'::text))));


--
-- Name: bound_with_part_itemid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX bound_with_part_itemid_idx ON lotus_mod_inventory_storage.bound_with_part USING btree (itemid);


--
-- Name: call_number_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX call_number_type_name_idx_unique ON lotus_mod_inventory_storage.call_number_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: classification_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX classification_type_name_idx_unique ON lotus_mod_inventory_storage.classification_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: contributor_name_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX contributor_name_type_name_idx_unique ON lotus_mod_inventory_storage.contributor_name_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: contributor_type_code_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX contributor_type_code_idx_unique ON lotus_mod_inventory_storage.contributor_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))));


--
-- Name: contributor_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX contributor_type_name_idx_unique ON lotus_mod_inventory_storage.contributor_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: electronic_access_relationship_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX electronic_access_relationship_name_idx_unique ON lotus_mod_inventory_storage.electronic_access_relationship USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: holdings_note_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX holdings_note_type_name_idx_unique ON lotus_mod_inventory_storage.holdings_note_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: holdings_record_callnumber_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_callnumber_idx ON lotus_mod_inventory_storage.holdings_record USING btree ("left"(lower((jsonb ->> 'callNumber'::text)), 600));


--
-- Name: holdings_record_callnumberandsuffix_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_callnumberandsuffix_idx ON lotus_mod_inventory_storage.holdings_record USING btree ("left"(lower(lotus_mod_inventory_storage.concat_space_sql(VARIADIC ARRAY[(jsonb ->> 'callNumber'::text), (jsonb ->> 'callNumberSuffix'::text)])), 600));


--
-- Name: holdings_record_callnumbertypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_callnumbertypeid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (callnumbertypeid);


--
-- Name: holdings_record_discoverysuppress_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_discoverysuppress_idx ON lotus_mod_inventory_storage.holdings_record USING btree ("left"(lower((jsonb ->> 'discoverySuppress'::text)), 600));


--
-- Name: holdings_record_effectivelocationid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_effectivelocationid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (effectivelocationid);


--
-- Name: holdings_record_fullcallnumber_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_fullcallnumber_idx ON lotus_mod_inventory_storage.holdings_record USING btree ("left"(lower(lotus_mod_inventory_storage.concat_space_sql(VARIADIC ARRAY[(jsonb ->> 'callNumberPrefix'::text), (jsonb ->> 'callNumber'::text), (jsonb ->> 'callNumberSuffix'::text)])), 600));


--
-- Name: holdings_record_holdingstypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_holdingstypeid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (holdingstypeid);


--
-- Name: holdings_record_hrid_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX holdings_record_hrid_idx_unique ON lotus_mod_inventory_storage.holdings_record USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'hrid'::text))));


--
-- Name: holdings_record_illpolicyid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_illpolicyid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (illpolicyid);


--
-- Name: holdings_record_instanceid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_instanceid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (instanceid);


--
-- Name: holdings_record_permanentlocationid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_permanentlocationid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (permanentlocationid);


--
-- Name: holdings_record_pmh_metadata_updateddate_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_pmh_metadata_updateddate_idx ON lotus_mod_inventory_storage.holdings_record USING btree (lotus_mod_inventory_storage.strtotimestamp(((jsonb -> 'metadata'::text) ->> 'updatedDate'::text)));


--
-- Name: holdings_record_sourceid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_sourceid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (sourceid);


--
-- Name: holdings_record_temporarylocationid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX holdings_record_temporarylocationid_idx ON lotus_mod_inventory_storage.holdings_record USING btree (temporarylocationid);


--
-- Name: holdings_records_source_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX holdings_records_source_name_idx_unique ON lotus_mod_inventory_storage.holdings_records_source USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: holdings_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX holdings_type_name_idx_unique ON lotus_mod_inventory_storage.holdings_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: identifier_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX identifier_type_name_idx_unique ON lotus_mod_inventory_storage.identifier_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: ill_policy_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX ill_policy_name_idx_unique ON lotus_mod_inventory_storage.ill_policy USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: instance_contributors_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_contributors_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'contributors'::text))), 600));


--
-- Name: instance_discoverysuppress_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_discoverysuppress_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower((jsonb ->> 'discoverySuppress'::text)), 600));


--
-- Name: instance_format_code_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_format_code_idx_unique ON lotus_mod_inventory_storage.instance_format USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))));


--
-- Name: instance_format_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_format_name_idx_unique ON lotus_mod_inventory_storage.instance_format USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: instance_hrid_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_hrid_idx_unique ON lotus_mod_inventory_storage.instance USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'hrid'::text))));


--
-- Name: instance_identifiers_idx_ft; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_identifiers_idx_ft ON lotus_mod_inventory_storage.instance USING gin (lotus_mod_inventory_storage.get_tsvector(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'identifiers'::text))));


--
-- Name: instance_identifiers_idx_gin; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_identifiers_idx_gin ON lotus_mod_inventory_storage.instance USING gin (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'identifiers'::text))) public.gin_trgm_ops);


--
-- Name: instance_indextitle_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_indextitle_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'indexTitle'::text))), 600));


--
-- Name: instance_instancestatusid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_instancestatusid_idx ON lotus_mod_inventory_storage.instance USING btree (instancestatusid);


--
-- Name: instance_instancetypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_instancetypeid_idx ON lotus_mod_inventory_storage.instance USING btree (instancetypeid);


--
-- Name: instance_invalidisbn_idx_ft; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_invalidisbn_idx_ft ON lotus_mod_inventory_storage.instance USING gin (lotus_mod_inventory_storage.get_tsvector(lotus_mod_inventory_storage.normalize_invalid_isbns((jsonb -> 'identifiers'::text))));


--
-- Name: instance_isbn_idx_ft; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_isbn_idx_ft ON lotus_mod_inventory_storage.instance USING gin (lotus_mod_inventory_storage.get_tsvector(lotus_mod_inventory_storage.normalize_isbns((jsonb -> 'identifiers'::text))));


--
-- Name: instance_matchkey_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_matchkey_idx_unique ON lotus_mod_inventory_storage.instance USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'matchKey'::text))));


--
-- Name: instance_metadata_updateddate_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_metadata_updateddate_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower(((jsonb -> 'metadata'::text) ->> 'updatedDate'::text)), 600));


--
-- Name: instance_modeofissuanceid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_modeofissuanceid_idx ON lotus_mod_inventory_storage.instance USING btree (modeofissuanceid);


--
-- Name: instance_note_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_note_type_name_idx_unique ON lotus_mod_inventory_storage.instance_note_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: instance_pmh_metadata_updateddate_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_pmh_metadata_updateddate_idx ON lotus_mod_inventory_storage.instance USING btree (lotus_mod_inventory_storage.strtotimestamp(((jsonb -> 'metadata'::text) ->> 'updatedDate'::text)));


--
-- Name: instance_publication_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_publication_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'publication'::text))), 600));


--
-- Name: instance_relationship_instancerelationshiptypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_relationship_instancerelationshiptypeid_idx ON lotus_mod_inventory_storage.instance_relationship USING btree (instancerelationshiptypeid);


--
-- Name: instance_relationship_subinstanceid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_relationship_subinstanceid_idx ON lotus_mod_inventory_storage.instance_relationship USING btree (subinstanceid);


--
-- Name: instance_relationship_superinstanceid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_relationship_superinstanceid_idx ON lotus_mod_inventory_storage.instance_relationship USING btree (superinstanceid);


--
-- Name: instance_relationship_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_relationship_type_name_idx_unique ON lotus_mod_inventory_storage.instance_relationship_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: instance_source_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_source_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower((jsonb ->> 'source'::text)), 600));


--
-- Name: instance_staffsuppress_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_staffsuppress_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower((jsonb ->> 'staffSuppress'::text)), 600));


--
-- Name: instance_statisticalcodeids_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_statisticalcodeids_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower((jsonb ->> 'statisticalCodeIds'::text)), 600));


--
-- Name: instance_status_code_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_status_code_idx_unique ON lotus_mod_inventory_storage.instance_status USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))));


--
-- Name: instance_status_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_status_name_idx_unique ON lotus_mod_inventory_storage.instance_status USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: instance_title_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX instance_title_idx ON lotus_mod_inventory_storage.instance USING btree ("left"(lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'title'::text))), 600));


--
-- Name: instance_type_code_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_type_code_idx_unique ON lotus_mod_inventory_storage.instance_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))));


--
-- Name: instance_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX instance_type_name_idx_unique ON lotus_mod_inventory_storage.instance_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: item_accessionnumber_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_accessionnumber_idx ON lotus_mod_inventory_storage.item USING btree ("left"(lower((jsonb ->> 'accessionNumber'::text)), 600));


--
-- Name: item_barcode_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX item_barcode_idx_unique ON lotus_mod_inventory_storage.item USING btree (lower((jsonb ->> 'barcode'::text)));


--
-- Name: item_callnumberandsuffix_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_callnumberandsuffix_idx ON lotus_mod_inventory_storage.item USING btree ("left"(lower(lotus_mod_inventory_storage.concat_space_sql(VARIADIC ARRAY[((jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text), ((jsonb -> 'effectiveCallNumberComponents'::text) ->> 'suffix'::text)])), 600));


--
-- Name: item_damaged_status_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX item_damaged_status_name_idx_unique ON lotus_mod_inventory_storage.item_damaged_status USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: item_discoverysuppress_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_discoverysuppress_idx ON lotus_mod_inventory_storage.item USING btree ("left"(lower((jsonb ->> 'discoverySuppress'::text)), 600));


--
-- Name: item_effectivecallnumbercomponents_callnumber_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_effectivecallnumbercomponents_callnumber_idx ON lotus_mod_inventory_storage.item USING btree ("left"(lower(((jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text)), 600));


--
-- Name: item_effectivelocationid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_effectivelocationid_idx ON lotus_mod_inventory_storage.item USING btree (effectivelocationid);


--
-- Name: item_fullcallnumber_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_fullcallnumber_idx ON lotus_mod_inventory_storage.item USING btree ("left"(lower(lotus_mod_inventory_storage.concat_space_sql(VARIADIC ARRAY[((jsonb -> 'effectiveCallNumberComponents'::text) ->> 'prefix'::text), ((jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text), ((jsonb -> 'effectiveCallNumberComponents'::text) ->> 'suffix'::text)])), 600));


--
-- Name: item_holdingsrecordid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_holdingsrecordid_idx ON lotus_mod_inventory_storage.item USING btree (holdingsrecordid);


--
-- Name: item_hrid_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX item_hrid_idx_unique ON lotus_mod_inventory_storage.item USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'hrid'::text))));


--
-- Name: item_materialtypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_materialtypeid_idx ON lotus_mod_inventory_storage.item USING btree (materialtypeid);


--
-- Name: item_note_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX item_note_type_name_idx_unique ON lotus_mod_inventory_storage.item_note_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: item_permanentloantypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_permanentloantypeid_idx ON lotus_mod_inventory_storage.item USING btree (permanentloantypeid);


--
-- Name: item_permanentlocationid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_permanentlocationid_idx ON lotus_mod_inventory_storage.item USING btree (permanentlocationid);


--
-- Name: item_pmh_metadata_updateddate_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_pmh_metadata_updateddate_idx ON lotus_mod_inventory_storage.item USING btree (lotus_mod_inventory_storage.strtotimestamp(((jsonb -> 'metadata'::text) ->> 'updatedDate'::text)));


--
-- Name: item_purchaseorderlineidentifier_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_purchaseorderlineidentifier_idx ON lotus_mod_inventory_storage.item USING btree ("left"(lower((jsonb ->> 'purchaseOrderLineIdentifier'::text)), 600));


--
-- Name: item_status_name_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_status_name_idx ON lotus_mod_inventory_storage.item USING btree ("left"(lower(lotus_mod_inventory_storage.f_unaccent(((jsonb -> 'status'::text) ->> 'name'::text))), 600));


--
-- Name: item_temporaryloantypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_temporaryloantypeid_idx ON lotus_mod_inventory_storage.item USING btree (temporaryloantypeid);


--
-- Name: item_temporarylocationid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX item_temporarylocationid_idx ON lotus_mod_inventory_storage.item USING btree (temporarylocationid);


--
-- Name: loan_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX loan_type_name_idx_unique ON lotus_mod_inventory_storage.loan_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: location_campusid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX location_campusid_idx ON lotus_mod_inventory_storage.location USING btree (campusid);


--
-- Name: location_code_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX location_code_idx_unique ON lotus_mod_inventory_storage.location USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))));


--
-- Name: location_institutionid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX location_institutionid_idx ON lotus_mod_inventory_storage.location USING btree (institutionid);


--
-- Name: location_libraryid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX location_libraryid_idx ON lotus_mod_inventory_storage.location USING btree (libraryid);


--
-- Name: location_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX location_name_idx_unique ON lotus_mod_inventory_storage.location USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: location_primaryservicepoint_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX location_primaryservicepoint_idx ON lotus_mod_inventory_storage.location USING btree ("left"(lower((jsonb ->> 'primaryServicePoint'::text)), 600));


--
-- Name: loccampus_institutionid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX loccampus_institutionid_idx ON lotus_mod_inventory_storage.loccampus USING btree (institutionid);


--
-- Name: loccampus_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX loccampus_name_idx_unique ON lotus_mod_inventory_storage.loccampus USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: locinstitution_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX locinstitution_name_idx_unique ON lotus_mod_inventory_storage.locinstitution USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: loclibrary_campusid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX loclibrary_campusid_idx ON lotus_mod_inventory_storage.loclibrary USING btree (campusid);


--
-- Name: loclibrary_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX loclibrary_name_idx_unique ON lotus_mod_inventory_storage.loclibrary USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: material_type_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX material_type_name_idx_unique ON lotus_mod_inventory_storage.material_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: mode_of_issuance_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX mode_of_issuance_name_idx_unique ON lotus_mod_inventory_storage.mode_of_issuance USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: nature_of_content_term_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX nature_of_content_term_name_idx_unique ON lotus_mod_inventory_storage.nature_of_content_term USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: preceding_succeeding_title_precedinginstanceid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX preceding_succeeding_title_precedinginstanceid_idx ON lotus_mod_inventory_storage.preceding_succeeding_title USING btree (precedinginstanceid);


--
-- Name: preceding_succeeding_title_succeedinginstanceid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX preceding_succeeding_title_succeedinginstanceid_idx ON lotus_mod_inventory_storage.preceding_succeeding_title USING btree (succeedinginstanceid);


--
-- Name: service_point_code_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX service_point_code_idx_unique ON lotus_mod_inventory_storage.service_point USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))));


--
-- Name: service_point_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX service_point_name_idx_unique ON lotus_mod_inventory_storage.service_point USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: service_point_pickuplocation_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX service_point_pickuplocation_idx ON lotus_mod_inventory_storage.service_point USING btree ("left"(lower((jsonb ->> 'pickupLocation'::text)), 600));


--
-- Name: service_point_user_defaultservicepointid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX service_point_user_defaultservicepointid_idx ON lotus_mod_inventory_storage.service_point_user USING btree (defaultservicepointid);


--
-- Name: service_point_user_userid_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX service_point_user_userid_idx_unique ON lotus_mod_inventory_storage.service_point_user USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'userId'::text))));


--
-- Name: statistical_code_code_statisticalcodetypeid_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX statistical_code_code_statisticalcodetypeid_idx_unique ON lotus_mod_inventory_storage.statistical_code USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))), lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'statisticalCodeTypeId'::text))));


--
-- Name: statistical_code_name_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX statistical_code_name_idx_unique ON lotus_mod_inventory_storage.statistical_code USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'name'::text))));


--
-- Name: statistical_code_statisticalcodetypeid_idx; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE INDEX statistical_code_statisticalcodetypeid_idx ON lotus_mod_inventory_storage.statistical_code USING btree (statisticalcodetypeid);


--
-- Name: statistical_code_type_code_idx_unique; Type: INDEX; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE UNIQUE INDEX statistical_code_type_code_idx_unique ON lotus_mod_inventory_storage.statistical_code_type USING btree (lower(lotus_mod_inventory_storage.f_unaccent((jsonb ->> 'code'::text))));


--
-- Name: holdings_record audit_holdings_record; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER audit_holdings_record AFTER DELETE ON lotus_mod_inventory_storage.holdings_record FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.audit_holdings_record_changes();


--
-- Name: instance audit_instance; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER audit_instance AFTER DELETE ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.audit_instance_changes();


--
-- Name: item audit_item; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER audit_item AFTER DELETE ON lotus_mod_inventory_storage.item FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.audit_item_changes();


--
-- Name: statistical_code check_item_statistical_code_reference_on_delete; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER check_item_statistical_code_reference_on_delete BEFORE DELETE ON lotus_mod_inventory_storage.statistical_code FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.process_statistical_code_delete();


--
-- Name: item check_statistical_code_references_on_insert; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER check_statistical_code_references_on_insert BEFORE INSERT ON lotus_mod_inventory_storage.item FOR EACH ROW WHEN ((((new.jsonb -> 'statisticalCodeIds'::text) IS NOT NULL) AND ((new.jsonb -> 'statisticalCodeIds'::text) <> '[]'::jsonb))) EXECUTE FUNCTION lotus_mod_inventory_storage.check_statistical_code_references();


--
-- Name: item check_statistical_code_references_on_update; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER check_statistical_code_references_on_update BEFORE UPDATE ON lotus_mod_inventory_storage.item FOR EACH ROW WHEN ((((new.jsonb -> 'statisticalCodeIds'::text) IS NOT NULL) AND ((new.jsonb -> 'statisticalCodeIds'::text) <> '[]'::jsonb) AND ((old.jsonb -> 'statisticalCodeIds'::text) IS DISTINCT FROM (new.jsonb -> 'statisticalCodeIds'::text)))) EXECUTE FUNCTION lotus_mod_inventory_storage.check_statistical_code_references();


--
-- Name: instance instance_check_statistical_code_references_on_insert; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER instance_check_statistical_code_references_on_insert BEFORE INSERT ON lotus_mod_inventory_storage.instance FOR EACH ROW WHEN ((((new.jsonb -> 'statisticalCodeIds'::text) IS NOT NULL) AND ((new.jsonb -> 'statisticalCodeIds'::text) <> '[]'::jsonb))) EXECUTE FUNCTION lotus_mod_inventory_storage.check_statistical_code_references();


--
-- Name: instance instance_check_statistical_code_references_on_update; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER instance_check_statistical_code_references_on_update BEFORE UPDATE ON lotus_mod_inventory_storage.instance FOR EACH ROW WHEN ((((new.jsonb -> 'statisticalCodeIds'::text) IS NOT NULL) AND ((new.jsonb -> 'statisticalCodeIds'::text) <> '[]'::jsonb) AND ((old.jsonb -> 'statisticalCodeIds'::text) IS DISTINCT FROM (new.jsonb -> 'statisticalCodeIds'::text)))) EXECUTE FUNCTION lotus_mod_inventory_storage.check_statistical_code_references();


--
-- Name: alternative_title_type set_alternative_title_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_alternative_title_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.alternative_title_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_alternative_title_type_md_json();


--
-- Name: alternative_title_type set_alternative_title_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_alternative_title_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.alternative_title_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.alternative_title_type_set_md();


--
-- Name: authority set_authority_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_authority_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.authority FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_authority_md_json();


--
-- Name: authority set_authority_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_authority_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.authority FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.authority_set_md();


--
-- Name: authority_note_type set_authority_note_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_authority_note_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.authority_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_authority_note_type_md_json();


--
-- Name: authority_note_type set_authority_note_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_authority_note_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.authority_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.authority_note_type_set_md();


--
-- Name: authority set_authority_ol_version_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_authority_ol_version_trigger BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.authority FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.authority_set_ol_version();


--
-- Name: bound_with_part set_bound_with_part_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_bound_with_part_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.bound_with_part FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_bound_with_part_md_json();


--
-- Name: bound_with_part set_bound_with_part_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_bound_with_part_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.bound_with_part FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.bound_with_part_set_md();


--
-- Name: call_number_type set_call_number_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_call_number_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.call_number_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_call_number_type_md_json();


--
-- Name: call_number_type set_call_number_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_call_number_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.call_number_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.call_number_type_set_md();


--
-- Name: classification_type set_classification_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_classification_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.classification_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_classification_type_md_json();


--
-- Name: classification_type set_classification_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_classification_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.classification_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.classification_type_set_md();


--
-- Name: contributor_name_type set_contributor_name_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_contributor_name_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.contributor_name_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_contributor_name_type_md_json();


--
-- Name: contributor_name_type set_contributor_name_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_contributor_name_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.contributor_name_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.contributor_name_type_set_md();


--
-- Name: electronic_access_relationship set_electronic_access_relationship_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_electronic_access_relationship_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.electronic_access_relationship FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_electronic_access_relationship_md_json();


--
-- Name: electronic_access_relationship set_electronic_access_relationship_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_electronic_access_relationship_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.electronic_access_relationship FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.electronic_access_relationship_set_md();


--
-- Name: holdings_note_type set_holdings_note_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_note_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.holdings_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_holdings_note_type_md_json();


--
-- Name: holdings_note_type set_holdings_note_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_note_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.holdings_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.holdings_note_type_set_md();


--
-- Name: holdings_record set_holdings_record_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_record_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.holdings_record FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_holdings_record_md_json();


--
-- Name: holdings_record set_holdings_record_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_record_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.holdings_record FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.holdings_record_set_md();


--
-- Name: holdings_record set_holdings_record_ol_version_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_record_ol_version_trigger BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.holdings_record FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.holdings_record_set_ol_version();


--
-- Name: holdings_records_source set_holdings_records_source_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_records_source_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.holdings_records_source FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_holdings_records_source_md_json();


--
-- Name: holdings_records_source set_holdings_records_source_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_records_source_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.holdings_records_source FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.holdings_records_source_set_md();


--
-- Name: holdings_type set_holdings_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.holdings_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_holdings_type_md_json();


--
-- Name: holdings_type set_holdings_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_holdings_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.holdings_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.holdings_type_set_md();


--
-- Name: alternative_title_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.alternative_title_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: async_migration_job set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.async_migration_job FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: authority set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.authority FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: authority_note_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.authority_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: bound_with_part set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.bound_with_part FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: call_number_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.call_number_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: classification_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.classification_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: contributor_name_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.contributor_name_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: contributor_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.contributor_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: electronic_access_relationship set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.electronic_access_relationship FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: holdings_note_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.holdings_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: holdings_record set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.holdings_record FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: holdings_records_source set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.holdings_records_source FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: holdings_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.holdings_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: hrid_settings set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.hrid_settings FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: identifier_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.identifier_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: ill_policy set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.ill_policy FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance_format set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_format FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance_note_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance_relationship set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_relationship FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance_relationship_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_relationship_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance_source_marc set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_source_marc FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance_status set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_status FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: instance_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: item set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.item FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: item_damaged_status set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.item_damaged_status FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: item_note_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.item_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: iteration_job set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.iteration_job FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: loan_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.loan_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: location set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.location FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: loccampus set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.loccampus FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: locinstitution set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.locinstitution FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: loclibrary set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.loclibrary FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: material_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.material_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: mode_of_issuance set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.mode_of_issuance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: nature_of_content_term set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.nature_of_content_term FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: notification_sending_error set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.notification_sending_error FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: preceding_succeeding_title set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.preceding_succeeding_title FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: reindex_job set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.reindex_job FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: service_point set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.service_point FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: service_point_user set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.service_point_user FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: statistical_code set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.statistical_code FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: statistical_code_type set_id_in_jsonb; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_id_in_jsonb BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.statistical_code_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_id_in_jsonb();


--
-- Name: identifier_type set_identifier_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_identifier_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.identifier_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_identifier_type_md_json();


--
-- Name: identifier_type set_identifier_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_identifier_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.identifier_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.identifier_type_set_md();


--
-- Name: ill_policy set_ill_policy_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_ill_policy_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.ill_policy FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_ill_policy_md_json();


--
-- Name: ill_policy set_ill_policy_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_ill_policy_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.ill_policy FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.ill_policy_set_md();


--
-- Name: instance set_instance_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_md_json();


--
-- Name: instance set_instance_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.instance_set_md();


--
-- Name: instance_note_type set_instance_note_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_note_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.instance_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_note_type_md_json();


--
-- Name: instance_note_type set_instance_note_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_note_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.instance_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.instance_note_type_set_md();


--
-- Name: instance set_instance_ol_version_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_ol_version_trigger BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.instance_set_ol_version();


--
-- Name: instance_relationship set_instance_relationship_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_relationship_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.instance_relationship FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_relationship_md_json();


--
-- Name: instance_relationship set_instance_relationship_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_relationship_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.instance_relationship FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.instance_relationship_set_md();


--
-- Name: instance_relationship_type set_instance_relationship_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_relationship_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.instance_relationship_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_relationship_type_md_json();


--
-- Name: instance_relationship_type set_instance_relationship_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_relationship_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.instance_relationship_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.instance_relationship_type_set_md();


--
-- Name: instance_source_marc set_instance_source_marc_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_source_marc_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.instance_source_marc FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_source_marc_md_json();


--
-- Name: instance_source_marc set_instance_source_marc_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_source_marc_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.instance_source_marc FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.instance_source_marc_set_md();


--
-- Name: instance set_instance_sourcerecordformat; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_sourcerecordformat BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_sourcerecordformat();


--
-- Name: instance_status set_instance_status_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_status_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.instance_status FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_status_md_json();


--
-- Name: instance_status set_instance_status_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_status_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.instance_status FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.instance_status_set_md();


--
-- Name: instance set_instance_status_updated_date; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_instance_status_updated_date BEFORE UPDATE ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_instance_status_updated_date();


--
-- Name: item_damaged_status set_item_damaged_status_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_item_damaged_status_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.item_damaged_status FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_item_damaged_status_md_json();


--
-- Name: item_damaged_status set_item_damaged_status_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_item_damaged_status_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.item_damaged_status FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.item_damaged_status_set_md();


--
-- Name: item set_item_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_item_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.item FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_item_md_json();


--
-- Name: item set_item_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_item_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.item FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.item_set_md();


--
-- Name: item_note_type set_item_note_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_item_note_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.item_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_item_note_type_md_json();


--
-- Name: item_note_type set_item_note_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_item_note_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.item_note_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.item_note_type_set_md();


--
-- Name: item set_item_ol_version_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_item_ol_version_trigger BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.item FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.item_set_ol_version();


--
-- Name: loan_type set_loan_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_loan_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.loan_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_loan_type_md_json();


--
-- Name: loan_type set_loan_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_loan_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.loan_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.loan_type_set_md();


--
-- Name: location set_location_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_location_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.location FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_location_md_json();


--
-- Name: location set_location_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_location_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.location FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.location_set_md();


--
-- Name: loccampus set_loccampus_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_loccampus_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.loccampus FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_loccampus_md_json();


--
-- Name: loccampus set_loccampus_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_loccampus_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.loccampus FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.loccampus_set_md();


--
-- Name: locinstitution set_locinstitution_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_locinstitution_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.locinstitution FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_locinstitution_md_json();


--
-- Name: locinstitution set_locinstitution_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_locinstitution_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.locinstitution FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.locinstitution_set_md();


--
-- Name: loclibrary set_loclibrary_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_loclibrary_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.loclibrary FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_loclibrary_md_json();


--
-- Name: loclibrary set_loclibrary_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_loclibrary_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.loclibrary FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.loclibrary_set_md();


--
-- Name: material_type set_material_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_material_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.material_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_material_type_md_json();


--
-- Name: material_type set_material_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_material_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.material_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.material_type_set_md();


--
-- Name: mode_of_issuance set_mode_of_issuance_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_mode_of_issuance_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.mode_of_issuance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_mode_of_issuance_md_json();


--
-- Name: mode_of_issuance set_mode_of_issuance_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_mode_of_issuance_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.mode_of_issuance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.mode_of_issuance_set_md();


--
-- Name: nature_of_content_term set_nature_of_content_term_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_nature_of_content_term_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.nature_of_content_term FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_nature_of_content_term_md_json();


--
-- Name: nature_of_content_term set_nature_of_content_term_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_nature_of_content_term_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.nature_of_content_term FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.nature_of_content_term_set_md();


--
-- Name: preceding_succeeding_title set_preceding_succeeding_title_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_preceding_succeeding_title_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.preceding_succeeding_title FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_preceding_succeeding_title_md_json();


--
-- Name: preceding_succeeding_title set_preceding_succeeding_title_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_preceding_succeeding_title_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.preceding_succeeding_title FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.preceding_succeeding_title_set_md();


--
-- Name: service_point set_service_point_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_service_point_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.service_point FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_service_point_md_json();


--
-- Name: service_point set_service_point_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_service_point_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.service_point FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.service_point_set_md();


--
-- Name: service_point_user set_service_point_user_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_service_point_user_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.service_point_user FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_service_point_user_md_json();


--
-- Name: service_point_user set_service_point_user_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_service_point_user_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.service_point_user FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.service_point_user_set_md();


--
-- Name: statistical_code set_statistical_code_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_statistical_code_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.statistical_code FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_statistical_code_md_json();


--
-- Name: statistical_code set_statistical_code_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_statistical_code_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.statistical_code FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.statistical_code_set_md();


--
-- Name: statistical_code_type set_statistical_code_type_md_json_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_statistical_code_type_md_json_trigger BEFORE UPDATE ON lotus_mod_inventory_storage.statistical_code_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.set_statistical_code_type_md_json();


--
-- Name: statistical_code_type set_statistical_code_type_md_trigger; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER set_statistical_code_type_md_trigger BEFORE INSERT ON lotus_mod_inventory_storage.statistical_code_type FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.statistical_code_type_set_md();


--
-- Name: bound_with_part update_bound_with_part_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_bound_with_part_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.bound_with_part FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_bound_with_part_references();


--
-- Name: holdings_record update_holdings_record_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_holdings_record_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.holdings_record FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_holdings_record_references();


--
-- Name: instance update_instance_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_instance_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_instance_references();


--
-- Name: instance_relationship update_instance_relationship_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_instance_relationship_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.instance_relationship FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_instance_relationship_references();


--
-- Name: instance_source_marc update_instance_source_marc; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_instance_source_marc AFTER INSERT OR DELETE OR UPDATE ON lotus_mod_inventory_storage.instance_source_marc FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_instance_source_marc();


--
-- Name: item update_item_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_item_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.item FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_item_references();


--
-- Name: item update_item_status_date; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_item_status_date BEFORE UPDATE ON lotus_mod_inventory_storage.item FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_item_status_date();


--
-- Name: location update_location_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_location_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.location FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_location_references();


--
-- Name: loccampus update_loccampus_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_loccampus_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.loccampus FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_loccampus_references();


--
-- Name: loclibrary update_loclibrary_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_loclibrary_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.loclibrary FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_loclibrary_references();


--
-- Name: preceding_succeeding_title update_preceding_succeeding_title_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_preceding_succeeding_title_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.preceding_succeeding_title FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_preceding_succeeding_title_references();


--
-- Name: service_point_user update_service_point_user_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_service_point_user_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.service_point_user FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_service_point_user_references();


--
-- Name: statistical_code update_statistical_code_references; Type: TRIGGER; Schema: lotus_mod_inventory_storage; Owner: postgres
--

CREATE TRIGGER update_statistical_code_references BEFORE INSERT OR UPDATE ON lotus_mod_inventory_storage.statistical_code FOR EACH ROW EXECUTE FUNCTION lotus_mod_inventory_storage.update_statistical_code_references();


--
-- Name: holdings_record callnumbertypeid_call_number_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT callnumbertypeid_call_number_type_fkey FOREIGN KEY (callnumbertypeid) REFERENCES lotus_mod_inventory_storage.call_number_type(id);


--
-- Name: loclibrary campusid_loccampus_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.loclibrary
    ADD CONSTRAINT campusid_loccampus_fkey FOREIGN KEY (campusid) REFERENCES lotus_mod_inventory_storage.loccampus(id);


--
-- Name: location campusid_loccampus_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.location
    ADD CONSTRAINT campusid_loccampus_fkey FOREIGN KEY (campusid) REFERENCES lotus_mod_inventory_storage.loccampus(id);


--
-- Name: service_point_user defaultservicepointid_service_point_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.service_point_user
    ADD CONSTRAINT defaultservicepointid_service_point_fkey FOREIGN KEY (defaultservicepointid) REFERENCES lotus_mod_inventory_storage.service_point(id);


--
-- Name: holdings_record effectivelocationid_location_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT effectivelocationid_location_fkey FOREIGN KEY (effectivelocationid) REFERENCES lotus_mod_inventory_storage.location(id);


--
-- Name: item effectivelocationid_location_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT effectivelocationid_location_fkey FOREIGN KEY (effectivelocationid) REFERENCES lotus_mod_inventory_storage.location(id);


--
-- Name: item holdingsrecordid_holdings_record_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT holdingsrecordid_holdings_record_fkey FOREIGN KEY (holdingsrecordid) REFERENCES lotus_mod_inventory_storage.holdings_record(id);


--
-- Name: bound_with_part holdingsrecordid_holdings_record_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.bound_with_part
    ADD CONSTRAINT holdingsrecordid_holdings_record_fkey FOREIGN KEY (holdingsrecordid) REFERENCES lotus_mod_inventory_storage.holdings_record(id);


--
-- Name: holdings_record holdingstypeid_holdings_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT holdingstypeid_holdings_type_fkey FOREIGN KEY (holdingstypeid) REFERENCES lotus_mod_inventory_storage.holdings_type(id);


--
-- Name: holdings_record illpolicyid_ill_policy_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT illpolicyid_ill_policy_fkey FOREIGN KEY (illpolicyid) REFERENCES lotus_mod_inventory_storage.ill_policy(id);


--
-- Name: instance_source_marc instance_source_marc_id_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_source_marc
    ADD CONSTRAINT instance_source_marc_id_fkey FOREIGN KEY (id) REFERENCES lotus_mod_inventory_storage.instance(id);


--
-- Name: holdings_record instanceid_instance_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT instanceid_instance_fkey FOREIGN KEY (instanceid) REFERENCES lotus_mod_inventory_storage.instance(id);


--
-- Name: instance_relationship instancerelationshiptypeid_instance_relationship_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_relationship
    ADD CONSTRAINT instancerelationshiptypeid_instance_relationship_type_fkey FOREIGN KEY (instancerelationshiptypeid) REFERENCES lotus_mod_inventory_storage.instance_relationship_type(id);


--
-- Name: instance instancestatusid_instance_status_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance
    ADD CONSTRAINT instancestatusid_instance_status_fkey FOREIGN KEY (instancestatusid) REFERENCES lotus_mod_inventory_storage.instance_status(id);


--
-- Name: instance instancetypeid_instance_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance
    ADD CONSTRAINT instancetypeid_instance_type_fkey FOREIGN KEY (instancetypeid) REFERENCES lotus_mod_inventory_storage.instance_type(id);


--
-- Name: loccampus institutionid_locinstitution_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.loccampus
    ADD CONSTRAINT institutionid_locinstitution_fkey FOREIGN KEY (institutionid) REFERENCES lotus_mod_inventory_storage.locinstitution(id);


--
-- Name: location institutionid_locinstitution_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.location
    ADD CONSTRAINT institutionid_locinstitution_fkey FOREIGN KEY (institutionid) REFERENCES lotus_mod_inventory_storage.locinstitution(id);


--
-- Name: bound_with_part itemid_item_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.bound_with_part
    ADD CONSTRAINT itemid_item_fkey FOREIGN KEY (itemid) REFERENCES lotus_mod_inventory_storage.item(id);


--
-- Name: location libraryid_loclibrary_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.location
    ADD CONSTRAINT libraryid_loclibrary_fkey FOREIGN KEY (libraryid) REFERENCES lotus_mod_inventory_storage.loclibrary(id);


--
-- Name: item materialtypeid_material_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT materialtypeid_material_type_fkey FOREIGN KEY (materialtypeid) REFERENCES lotus_mod_inventory_storage.material_type(id);


--
-- Name: instance modeofissuanceid_mode_of_issuance_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance
    ADD CONSTRAINT modeofissuanceid_mode_of_issuance_fkey FOREIGN KEY (modeofissuanceid) REFERENCES lotus_mod_inventory_storage.mode_of_issuance(id);


--
-- Name: item permanentloantypeid_loan_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT permanentloantypeid_loan_type_fkey FOREIGN KEY (permanentloantypeid) REFERENCES lotus_mod_inventory_storage.loan_type(id);


--
-- Name: holdings_record permanentlocationid_location_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT permanentlocationid_location_fkey FOREIGN KEY (permanentlocationid) REFERENCES lotus_mod_inventory_storage.location(id);


--
-- Name: item permanentlocationid_location_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT permanentlocationid_location_fkey FOREIGN KEY (permanentlocationid) REFERENCES lotus_mod_inventory_storage.location(id);


--
-- Name: preceding_succeeding_title precedinginstanceid_instance_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.preceding_succeeding_title
    ADD CONSTRAINT precedinginstanceid_instance_fkey FOREIGN KEY (precedinginstanceid) REFERENCES lotus_mod_inventory_storage.instance(id);


--
-- Name: holdings_record sourceid_holdings_records_source_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT sourceid_holdings_records_source_fkey FOREIGN KEY (sourceid) REFERENCES lotus_mod_inventory_storage.holdings_records_source(id);


--
-- Name: statistical_code statisticalcodetypeid_statistical_code_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.statistical_code
    ADD CONSTRAINT statisticalcodetypeid_statistical_code_type_fkey FOREIGN KEY (statisticalcodetypeid) REFERENCES lotus_mod_inventory_storage.statistical_code_type(id);


--
-- Name: instance_relationship subinstanceid_instance_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_relationship
    ADD CONSTRAINT subinstanceid_instance_fkey FOREIGN KEY (subinstanceid) REFERENCES lotus_mod_inventory_storage.instance(id);


--
-- Name: preceding_succeeding_title succeedinginstanceid_instance_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.preceding_succeeding_title
    ADD CONSTRAINT succeedinginstanceid_instance_fkey FOREIGN KEY (succeedinginstanceid) REFERENCES lotus_mod_inventory_storage.instance(id);


--
-- Name: instance_relationship superinstanceid_instance_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.instance_relationship
    ADD CONSTRAINT superinstanceid_instance_fkey FOREIGN KEY (superinstanceid) REFERENCES lotus_mod_inventory_storage.instance(id);


--
-- Name: item temporaryloantypeid_loan_type_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT temporaryloantypeid_loan_type_fkey FOREIGN KEY (temporaryloantypeid) REFERENCES lotus_mod_inventory_storage.loan_type(id);


--
-- Name: holdings_record temporarylocationid_location_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.holdings_record
    ADD CONSTRAINT temporarylocationid_location_fkey FOREIGN KEY (temporarylocationid) REFERENCES lotus_mod_inventory_storage.location(id);


--
-- Name: item temporarylocationid_location_fkey; Type: FK CONSTRAINT; Schema: lotus_mod_inventory_storage; Owner: postgres
--

ALTER TABLE ONLY lotus_mod_inventory_storage.item
    ADD CONSTRAINT temporarylocationid_location_fkey FOREIGN KEY (temporarylocationid) REFERENCES lotus_mod_inventory_storage.location(id);


--
-- Name: TABLE alternative_title_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.alternative_title_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE async_migration_job; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.async_migration_job TO lotus_mod_inventory_storage;


--
-- Name: TABLE audit_holdings_record; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.audit_holdings_record TO lotus_mod_inventory_storage;


--
-- Name: TABLE audit_instance; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.audit_instance TO lotus_mod_inventory_storage;


--
-- Name: TABLE audit_item; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.audit_item TO lotus_mod_inventory_storage;


--
-- Name: TABLE authority; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.authority TO lotus_mod_inventory_storage;


--
-- Name: TABLE authority_note_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.authority_note_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE bound_with_part; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.bound_with_part TO lotus_mod_inventory_storage;


--
-- Name: TABLE call_number_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.call_number_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE classification_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.classification_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE contributor_name_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.contributor_name_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE contributor_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.contributor_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE electronic_access_relationship; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.electronic_access_relationship TO lotus_mod_inventory_storage;


--
-- Name: TABLE holdings_note_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.holdings_note_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE holdings_record; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.holdings_record TO lotus_mod_inventory_storage;


--
-- Name: TABLE holdings_records_source; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.holdings_records_source TO lotus_mod_inventory_storage;


--
-- Name: TABLE holdings_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.holdings_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE hrid_settings; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.hrid_settings TO lotus_mod_inventory_storage;


--
-- Name: SEQUENCE hrid_holdings_seq; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON SEQUENCE lotus_mod_inventory_storage.hrid_holdings_seq TO lotus_mod_inventory_storage;


--
-- Name: SEQUENCE hrid_instances_seq; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON SEQUENCE lotus_mod_inventory_storage.hrid_instances_seq TO lotus_mod_inventory_storage;


--
-- Name: SEQUENCE hrid_items_seq; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON SEQUENCE lotus_mod_inventory_storage.hrid_items_seq TO lotus_mod_inventory_storage;


--
-- Name: TABLE identifier_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.identifier_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE ill_policy; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.ill_policy TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_format; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_format TO lotus_mod_inventory_storage;


--
-- Name: TABLE item; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.item TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_holdings_item_view; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_holdings_item_view TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_note_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_note_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_relationship; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_relationship TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_relationship_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_relationship_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_source_marc; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_source_marc TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_status; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_status TO lotus_mod_inventory_storage;


--
-- Name: TABLE instance_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.instance_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE item_damaged_status; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.item_damaged_status TO lotus_mod_inventory_storage;


--
-- Name: TABLE item_note_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.item_note_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE iteration_job; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.iteration_job TO lotus_mod_inventory_storage;


--
-- Name: TABLE loan_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.loan_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE location; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.location TO lotus_mod_inventory_storage;


--
-- Name: TABLE loccampus; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.loccampus TO lotus_mod_inventory_storage;


--
-- Name: TABLE locinstitution; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.locinstitution TO lotus_mod_inventory_storage;


--
-- Name: TABLE loclibrary; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.loclibrary TO lotus_mod_inventory_storage;


--
-- Name: TABLE material_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.material_type TO lotus_mod_inventory_storage;


--
-- Name: TABLE mode_of_issuance; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.mode_of_issuance TO lotus_mod_inventory_storage;


--
-- Name: TABLE nature_of_content_term; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.nature_of_content_term TO lotus_mod_inventory_storage;


--
-- Name: TABLE notification_sending_error; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.notification_sending_error TO lotus_mod_inventory_storage;


--
-- Name: TABLE preceding_succeeding_title; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.preceding_succeeding_title TO lotus_mod_inventory_storage;


--
-- Name: TABLE reindex_job; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.reindex_job TO lotus_mod_inventory_storage;


--
-- Name: TABLE rmb_internal; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.rmb_internal TO lotus_mod_inventory_storage;


--
-- Name: TABLE rmb_internal_analyze; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.rmb_internal_analyze TO lotus_mod_inventory_storage;


--
-- Name: TABLE rmb_internal_index; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.rmb_internal_index TO lotus_mod_inventory_storage;


--
-- Name: TABLE rmb_job; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.rmb_job TO lotus_mod_inventory_storage;


--
-- Name: TABLE service_point; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.service_point TO lotus_mod_inventory_storage;


--
-- Name: TABLE service_point_user; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.service_point_user TO lotus_mod_inventory_storage;


--
-- Name: TABLE statistical_code; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.statistical_code TO lotus_mod_inventory_storage;


--
-- Name: TABLE statistical_code_type; Type: ACL; Schema: lotus_mod_inventory_storage; Owner: postgres
--

GRANT ALL ON TABLE lotus_mod_inventory_storage.statistical_code_type TO lotus_mod_inventory_storage;


--
-- PostgreSQL database dump complete
--

