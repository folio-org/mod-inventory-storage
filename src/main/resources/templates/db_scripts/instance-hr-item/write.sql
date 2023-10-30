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
