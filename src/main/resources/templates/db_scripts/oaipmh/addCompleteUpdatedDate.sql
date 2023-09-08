ALTER TABLE ${myuniversity}_${mymodule}.instance
  ADD COLUMN IF NOT EXISTS
    completeUpdatedDate timestamp with time zone;
