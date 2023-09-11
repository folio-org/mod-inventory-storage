ALTER TABLE ${myuniversity}_${mymodule}.instance
  ADD COLUMN IF NOT EXISTS
    complete_updated_date timestamp with time zone;
