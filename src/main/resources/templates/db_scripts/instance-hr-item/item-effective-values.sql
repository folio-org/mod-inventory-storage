CREATE OR REPLACE FUNCTION set_item_effective_values(holding jsonb, item jsonb)
  RETURNS jsonb AS $$
  BEGIN
    RETURN set_effective_shelving_order(
          item
          || jsonb_build_object('effectiveLocationId',
               COALESCE(item->>'temporaryLocationId', item->>'permanentLocationId', holding->>'effectiveLocationId'))
          || effective_call_number_components(holding, item));
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;

CREATE OR REPLACE FUNCTION set_item_effective_values_no_shelv_recalc(holding jsonb, item jsonb)
  RETURNS jsonb AS $$
  BEGIN
    RETURN item
          || jsonb_build_object('effectiveLocationId',
               COALESCE(item->>'temporaryLocationId', item->>'permanentLocationId', holding->>'effectiveLocationId'))
          || effective_call_number_components(holding, item);
  END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;
