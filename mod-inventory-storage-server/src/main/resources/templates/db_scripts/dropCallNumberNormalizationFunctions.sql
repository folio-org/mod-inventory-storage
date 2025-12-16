DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_call_number_string(callNumberString text) CASCADE;

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_holdings_full_call_number(holdingsRecord jsonb) CASCADE;

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_holdings_call_number_and_suffix(holdingsRecord jsonb) CASCADE;

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_item_full_call_number(item jsonb) CASCADE;

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_item_call_number_and_suffix(item jsonb) CASCADE;
