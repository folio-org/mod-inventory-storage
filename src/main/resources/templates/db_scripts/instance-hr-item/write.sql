-- Input is a JSON array of the holdings records to insert.
-- Returns the JSON array of the jsonb column of the inserted holdings
-- records after triggers (_version, metadata, etc.) have been applied.
CREATE OR REPLACE FUNCTION insert_holdings(input jsonb)
  RETURNS jsonb AS $$
  DECLARE
    holding jsonb;
    holdings jsonb = '[]';
  BEGIN
    FOR holding IN
      INSERT INTO holdings_record (id, jsonb)
      SELECT (jsonb_array_elements(input)->>'id')::uuid, jsonb_array_elements(input)
      RETURNING jsonb
    LOOP
      holdings = holdings || holding;
    END LOOP;
    RETURN holdings;
  END;
$$ LANGUAGE plpgsql;

-- Input is a JSON array of the holdings records to update or insert.
-- Returns
-- { "holdingsRecords": [{"old": {...}, "new": {...}}, ...],
--   "items":           [{"old": {...}, "new": {...}}, ...]
-- }
-- providing old and new jsonb content of all updated holdings and items. For a newly
-- inserted holding only "new" is provided.
-- "new" has the content after triggers (_version, metadata, etc.) have been applied.
CREATE OR REPLACE FUNCTION upsert_holdings(input jsonb)
  RETURNS jsonb AS $$
  DECLARE
    holding jsonb;
    holdings jsonb = '[]';
    item jsonb;
    items jsonb = '[]';
  BEGIN
    FOR holding IN
      UPDATE holdings_record new
      SET jsonb = input.jsonb || jsonb_build_object('hrid', COALESCE(input.jsonb->'hrid', old.jsonb->'hrid'))
      FROM (SELECT (jsonb_array_elements(input)->>'id')::uuid id, jsonb_array_elements(input) jsonb) input,
           (SELECT id, jsonb FROM holdings_record FOR UPDATE) old
      WHERE new.id = input.id
        AND old.id = input.id
      RETURNING jsonb_build_object('old', old.jsonb, 'new', new.jsonb)
    LOOP
      IF holding->'new'->'hrid' <> holding->'old'->'hrid' THEN
        RAISE 'Cannot change hrid of holdings record id=%, old hrid=%, new hrid=%',
            holding->'old'->>'id', holding->'old'->>'hrid', holding->'new'->>'hrid'
            USING ERRCODE='239HR';
      END IF;
      holdings := holdings || holding;
      FOR item IN
        UPDATE item new
        SET jsonb =
          set_effective_shelving_order(old.jsonb || effective_call_number_components(holding->'new', new.jsonb))
          || jsonb_build_object('effectiveLocationId',
               COALESCE(temporarylocationid::text, permanentlocationid::text, holding->'new'->>'effectiveLocationId'))
          || jsonb_build_object('metadata', set_metadata(old.jsonb->'metadata', holding->'new'->'metadata'))
        FROM (SELECT id, jsonb FROM item FOR UPDATE) old
        WHERE old.id = new.id
          AND holdingsrecordid = (holding->'new'->>'id')::uuid
          AND (   (holding->'new'->'effectiveLocationId' IS DISTINCT FROM holding->'old'->'effectiveLocationId'
                   AND permanentlocationid IS NULL
                   AND temporarylocationid IS NULL)
               OR (holding->'new'->'callNumber' IS DISTINCT FROM holding->'old'->'callNumber'
                   AND length(trim(coalesce(old.jsonb->>'itemLevelCallNumber', ''))) = 0)
               OR (holding->'new'->'callNumberPrefix' IS DISTINCT FROM holding->'old'->'callNumberPrefix'
                   AND length(trim(coalesce(old.jsonb->>'itemLevelCallNumberPrefix', ''))) = 0)
               OR (holding->'new'->'callNumberSuffix' IS DISTINCT FROM holding->'old'->'callNumberSuffix'
                   AND length(trim(coalesce(old.jsonb->>'itemLevelCallNumberSuffix', ''))) = 0)
               OR (holding->'new'->'callNumberTypeId' IS DISTINCT FROM holding->'old'->'callNumberTypeId'
                   AND length(trim(coalesce(old.jsonb->>'itemLevelCallNumberTypeId', ''))) = 0)
              )
        RETURNING jsonb_build_object('old', old.jsonb, 'new', new.jsonb)
      LOOP
        items := items || item;
      END LOOP;
    END LOOP;

    FOR holding IN
      INSERT INTO holdings_record (id, jsonb)
      SELECT (jsonb_array_elements(input)->>'id')::uuid, jsonb_array_elements(input)
      ON CONFLICT DO NOTHING
      RETURNING jsonb_build_object('new', jsonb)
    LOOP
      holdings = holdings || holding;
    END LOOP;

    RETURN jsonb_build_object('holdingsRecords', holdings, 'items', items);
  END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION set_metadata(item_metadata jsonb, holding_metadata jsonb)
  RETURNS jsonb AS $$
  BEGIN
    IF holding_metadata->'updatedDate' IS NULL THEN
      item_metadata = item_metadata - 'updatedDate';
    ELSE
      item_metadata = jsonb_set(item_metadata, '{updatedDate}', holding_metadata->'updatedDate');
    END IF;
    IF holding_metadata->'updatedByUserId' IS NULL THEN
      item_metadata = item_metadata - 'updatedByUserId';
    ELSE
      item_metadata = jsonb_set(item_metadata, '{updatedByUserId}', holding_metadata->'updatedByUserId');
    END IF;
    RETURN item_metadata;
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


CREATE OR REPLACE FUNCTION set_item_effective_values(holding jsonb, item jsonb)
  RETURNS jsonb AS $$
  BEGIN
    RETURN set_effective_shelving_order(
          item
          || jsonb_build_object('effectiveLocationId',
               COALESCE(item->>'temporaryLocationId', item->>'permanentLocationId', holding->>'effectiveLocationId'))
          || effective_call_number_components(holding->'new', new.jsonb));
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


CREATE OR REPLACE FUNCTION effective_call_number_components(holding jsonb, item jsonb)
  RETURNS jsonb AS $$
  BEGIN
    RETURN jsonb_build_object('effectiveCallNumberComponents',
           effective_call_number_component('callNumber', item->>'itemLevelCallNumber', holding->>'callNumber')
        || effective_call_number_component('prefix', item->>'itemLevelCallNumberPrefix', holding->>'callNumberPrefix')
        || effective_call_number_component('suffix', item->>'itemLevelCallNumberSuffix', holding->>'callNumberSuffix')
        || effective_call_number_component('typeId', item->>'itemLevelCallNumberTypeId', holding->>'callNumberTypeId'));
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


CREATE OR REPLACE FUNCTION effective_call_number_component(key text, value1 text, value2 text)
  RETURNS jsonb AS $$
  BEGIN
    IF length(trim(value1)) > 0 THEN
      RETURN jsonb_build_object(key, value1);
    END IF;
    IF length(trim(value2)) > 0 THEN
      RETURN jsonb_build_object(key, value2);
    END IF;
    RETURN jsonb_build_object();
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE;


CREATE OR REPLACE FUNCTION set_effective_shelving_order(item jsonb)
  RETURNS jsonb AS $$
  DECLARE
    call_number text;
  BEGIN
    call_number = trim(item->'effectiveCallNumberComponents'->>'callNumber');
    IF call_number IS NULL OR call_number = '' THEN
      RETURN item - 'effectiveShelvingOrder';
    END IF;
    call_number = concat_ws(' ', call_number,
        trim2null(item->>'volume'),
        trim2null(item->>'enumeration'),
        trim2null(item->>'chronology'),
        trim2null(item->>'copyNumber'));
    CASE item->'effectiveCallNumberComponents'->>'typeId'
      WHEN '03dd64d0-5626-4ecd-8ece-4531e0069f35' THEN call_number = dewey_call_number(call_number);
      WHEN '95467209-6d7b-468b-94df-0f5d7ad2747d' THEN call_number = lc_nlm_call_number(call_number);  -- LC
      WHEN '054d460d-d6b9-4469-9e37-7a78a2266655' THEN call_number = lc_nlm_call_number(call_number);  -- NLM
      WHEN 'fc388041-6cd0-4806-8a74-ebe3b9ab4c6e' THEN call_number = su_doc_call_number(call_number);
      ELSE NULL;
    END CASE;
    RETURN item || jsonb_build_object('effectiveShelvingOrder',
        concat_ws(' ', call_number, trim2null(item->'effectiveCallNumberComponents'->>'suffix')));
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


CREATE OR REPLACE FUNCTION dewey_call_number(call_number text)
  RETURNS text AS $$
  DECLARE
    matches text[];
    class_digits text;
    class_decimal text;
    cutter text;
    other text;
  BEGIN
    matches = regexp_match(call_number, '^(\d+)(\.\d+)? *\.?(?:([A-Z]\d{1,3}(?:[A-Z]+)?) *(.*)|(.*))$');
    IF matches IS NULL THEN
      RETURN NULL;
    END IF;
    class_digits = matches[1];
    class_decimal = matches[2];
    cutter = matches[3];
    other = numerically_sortable(trim2null(concat(trim2null(matches[4]), trim2null(matches[5]))));
    RETURN concat_ws(' ', concat(sortable_number(class_digits), class_decimal),
                          cutter,
                          other
                    );
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


CREATE OR REPLACE FUNCTION lc_nlm_call_number(call_number text)
  RETURNS text AS $$
  DECLARE
    matches text[];
    classification text;
    classLetters text;
    classDigits text;
    classDecimal text;
    everythingElse text;
    classSuffix text;
    cutter text;
  BEGIN
    call_number = upper(call_number);
    matches = regexp_match(call_number, '^(([A-Z]+) *(?:(\d+)(\.\d+)?)?)(.*)$');
    IF matches IS NULL THEN
      RETURN NULL;
    END IF;
    classification = trim(matches[1]);
    classLetters = trim(matches[2]);
    classDigits = trim(matches[3]);
    classDecimal = trim(matches[4]);
    everythingElse = matches[5];
    CASE substr(classLetters, 1, 1)
      -- LC call numbers can't begin with I, O, W, X, or Y
      -- NLM call numbers begin with W or S
      WHEN 'I', 'O', 'X', 'Y' THEN
        RETURN NULL;
      ELSE
        NULL;
    END CASE;
    IF classDigits IS NULL THEN
      RETURN NULL;
    END IF;
    IF length(everythingElse) > 0 THEN
      -- combining greedy and non-greedy:
      -- https://www.postgresql.org/docs/current/functions-matching.html#POSIX-MATCHING-RULES
      matches = regexp_match(everythingElse, '(?:(.*?)(\.?[A-Z]\d+|^\.[A-Z]| \.[A-Z])(.*)){1,1}');
      IF matches IS NULL THEN
        classSuffix = trim2null(everythingElse);
      ELSE
        classSuffix = trim2null(matches[1]);
        cutter = trim(matches[2] || matches[3]);
      END IF;
    END IF;
    classSuffix = numerically_sortable(classSuffix);
    IF substr(classSuffix, 1, 1) BETWEEN 'A' AND 'Z' THEN
      classSuffix = '_' || classSuffix;
    END IF;
    cutter = cutter_shelf_key(cutter);
    return trim(concat_ws(' ', classLetters,
                               concat(length(classDigits), classDigits, classDecimal),
                               trim(classSuffix),
                               trim(cutter)
                         ));
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


CREATE OR REPLACE FUNCTION su_doc_call_number(call_number text)
  RETURNS text AS $$
  DECLARE
    matches text[];
  BEGIN
    matches = regexp_match(upper(call_number),
        '^([A-Z]+)\s*(\d+)(\.(?:[A-Z]+\d*|\d+))(/(?:[A-Z]+(?:\d+(?:-\d+)?)?|\d+(?:-\d+)?))?:?(.*)$');
    IF matches IS NULL THEN
      RETURN NULL;
    END IF;
    RETURN concat_ws(' ', matches[1], su_doc_part(matches[2]), su_doc_part(matches[3]), su_doc_part(matches[4]), su_doc_part(matches[5]));
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


CREATE OR REPLACE FUNCTION su_doc_part(part text)
  RETURNS text AS $$
  DECLARE
    chunk text;
    key text = '';
  BEGIN
    IF trim(part) = '' THEN
      RETURN NULL;
    END IF;
    IF starts_with(part, '.') OR starts_with(part, '/') OR starts_with(part, '-') OR starts_with(part, ':') THEN
      part = substr(part, 2);
    END IF;
    FOREACH chunk IN ARRAY regexp_split_to_array(part, '[./ -]') LOOP
      IF length(key) > 0 THEN
        key = concat(key, ' ');
      END IF;
      chunk = trim(chunk);
      CONTINUE WHEN chunk = '';
      IF substring(chunk, 1, 1) BETWEEN 'A' AND 'Z' THEN
        key = concat(key, ' !');
      ELSIF length(chunk) >= 3 THEN
        key = concat(key, '!');
      END IF;
      key = concat(key, numerically_sortable(chunk));
    END LOOP;
    RETURN key;
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


-- return null if trim(v) is empty, otherwise trim(v)
CREATE OR REPLACE FUNCTION trim2null(v text)
  RETURNS text AS $$
  BEGIN
    IF trim(v) = '' THEN
      RETURN null;
    END IF;
    RETURN trim(v);
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


-- return n with all leading 0 removed and the resulting string prepended with its length
-- 6.78 yiels 46.78, 5 yields 15, 0 yields 0
CREATE OR REPLACE FUNCTION sortable_number(n text)
  RETURNS text AS $$
  BEGIN
    n = regexp_replace(n, '^0+', '');
    RETURN concat(length(n), n);
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


-- A number is a sequence of digits and may contain a decimal, but not at the first position;
-- all leading zeros of a number are removed, and the length is prepended: 00123.45 becomes 6123.45
-- A-Z is kept.
-- A sequence of one or more other characters is replaced by a single space.
CREATE OR REPLACE FUNCTION numerically_sortable(s text)
  RETURNS text AS $$
  DECLARE
    match text;
    result text = '';
  BEGIN
    FOR match IN SELECT (regexp_matches(upper(s), '[A-Z]+|[0-9][.0-9]*|[^A-Z0-9]+', 'g'))[1] LOOP
      CASE
        WHEN substring(match, 1, 1) BETWEEN 'A' AND 'Z' THEN
          result = result || match || ' ';
        WHEN substring(match, 1, 1) BETWEEN '0' AND '9' THEN
          result = result || sortable_number(match);
        ELSE
          result = trim(result) || ' ';
      END CASE;
    END LOOP;
    RETURN trim(result);
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;


-- A number is a sequence of digits and may contain a decimal, but not at the first position;
-- all leading zeros of a number are removed, and the length is prepended: 00123.45 becomes 6123.45
-- A-Z is kept.
-- A sequence of one or more other characters is replaced by a single space.
CREATE OR REPLACE FUNCTION cutter_shelf_key(s text)
  RETURNS text AS $$
  DECLARE
    chunk text;
    matches text[];
    cutter text;
    suffix text;
    result text;
  BEGIN
    FOREACH chunk IN ARRAY regexp_split_to_array(s, '(?=[A-Z][0-9])') LOOP
      matches = regexp_match(chunk, '([A-Z][0-9]+)(.*)');
      IF matches IS NULL THEN
        -- before the first cutter
        result = trim2null(numerically_sortable(chunk));
      ELSE
        cutter = matches[1];
        suffix = trim2null(numerically_sortable(matches[2]));
        result = concat_ws(' ', result, cutter, suffix);
      END IF;
    END LOOP;
    RETURN result;
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;
