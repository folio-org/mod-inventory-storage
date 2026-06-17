CREATE INDEX IF NOT EXISTS item_formerids_idx_ft ON ${myuniversity}_${mymodule}.item
USING gin (
  ${myuniversity}_${mymodule}.get_tsvector(
    ${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'formerIds')
  )
);
