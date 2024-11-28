CREATE INDEX IF NOT EXISTS item_barcode_idx_pattern
  ON ${myuniversity}_${mymodule}.item (lower(jsonb->>'barcode') text_pattern_ops);
