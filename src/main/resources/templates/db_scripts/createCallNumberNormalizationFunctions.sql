-- this function is used for call number string and call number search value normalization.
-- It removes all non-alphanumeric symbols.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_call_number_string(callNumberString text)
RETURNS text AS $$
    SELECT regexp_replace(lower(callNumberString), '[^a-z0-9]', '', 'g');
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- Holdings call number normalization functions

-- Prefix + call number + suffix normalization. Nulls are omitted.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_holdings_full_call_number(holdingsRecord jsonb)
RETURNS text AS $$
  SELECT ${myuniversity}_${mymodule}.normalize_call_number_string(
    concat_ws('', holdingsRecord->>'callNumberPrefix',
      holdingsRecord->>'callNumber',
      holdingsRecord->>'callNumberSuffix'
  ));
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- call number + suffix normalization. Nulls are omitted.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_holdings_call_number_and_suffix(holdingsRecord jsonb)
RETURNS text AS $$
  SELECT ${myuniversity}_${mymodule}.normalize_call_number_string(
    concat_ws('', holdingsRecord->>'callNumber',
      holdingsRecord->>'callNumberSuffix'
  ));
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- Item call number normalization functions

-- Prefix + call number + suffix normalization. Nulls are omitted.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_item_full_call_number(item jsonb)
RETURNS text AS $$
  SELECT ${myuniversity}_${mymodule}.normalize_call_number_string(
    concat_ws('', item->'effectiveCallNumberComponents'->>'prefix',
      item->'effectiveCallNumberComponents'->>'callNumber',
      item->'effectiveCallNumberComponents'->>'suffix'
  ));
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- call number + suffix normalization. Nulls are omitted.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.normalize_item_call_number_and_suffix(item jsonb)
RETURNS text AS $$
  SELECT ${myuniversity}_${mymodule}.normalize_call_number_string(
    concat_ws('', item->'effectiveCallNumberComponents'->>'callNumber',
      item->'effectiveCallNumberComponents'->>'suffix'
  ));
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;
