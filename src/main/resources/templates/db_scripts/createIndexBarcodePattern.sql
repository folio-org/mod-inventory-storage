<#--
  Index for item barcode right truncation used by checkin and checkout:
  https://github.com/folio-org/ui-checkout/blob/v11.0.0/src/ScanItems.js#L163-L164
  https://github.com/folio-org/ui-checkin/blob/v9.2.0/src/Scan.js#L318-L319
  The existing btree index doesn’t support right truncation because barcode is
  Unicode and not "C" locale, for details see
  https://www.postgresql.org/docs/current/indexes-opclass.html
  https://www.cybertec-postgresql.com/en/indexing-like-postgresql-oracle/
-->
CREATE INDEX IF NOT EXISTS item_barcode_idx_pattern
  ON ${myuniversity}_${mymodule}.item (lower(jsonb->>'barcode') text_pattern_ops);
