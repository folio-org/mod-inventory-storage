ALTER TABLE ${myuniversity}_${mymodule}.preceding_succeeding_title DROP CONSTRAINT IF EXISTS preceding_or_succeeding_id_is_set;

ALTER TABLE ${myuniversity}_${mymodule}.preceding_succeeding_title
    ADD CONSTRAINT preceding_or_succeeding_id_is_set CHECK(
    jsonb->'precedingInstanceId' IS NOT NULL OR jsonb->'succeedingInstanceId' IS NOT NULL);
