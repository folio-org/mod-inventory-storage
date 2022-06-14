DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_call_number_string(callNumberString text);

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_holdings_full_call_number(holdingsRecord jsonb);

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_holdings_call_number_and_suffix(holdingsRecord jsonb);

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_item_full_call_number(item jsonb);

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.normalize_item_call_number_and_suffix(item jsonb);
