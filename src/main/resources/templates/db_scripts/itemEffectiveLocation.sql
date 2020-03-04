
-- Updates item effective location property in case holding's effective location has changed.
-- Only items that do not have permanent or temp location set will be updated.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_effective_location_on_holding_update() RETURNS trigger
AS $$
  DECLARE
    old_effective_location_id jsonb;
    new_effective_location_id jsonb;
  BEGIN
    old_effective_location_id = coalesce(OLD.jsonb->'temporaryLocationId', OLD.jsonb->'permanentLocationId');
    new_effective_location_id = coalesce(NEW.jsonb->'temporaryLocationId', NEW.jsonb->'permanentLocationId');

    -- null-safe comparison, do nothing if location is not changed.
    if (new_effective_location_id IS NOT DISTINCT FROM old_effective_location_id) THEN
      RETURN NEW;
    end if;

    UPDATE ${myuniversity}_${mymodule}.item
      SET jsonb = jsonb_set(jsonb, '{effectiveLocationId}', new_effective_location_id)
      WHERE holdingsRecordId = new.id
            -- if item's either temp or perm location is not null then we don't need to update it's effective location
            -- as item's location has more priority than holding's one.
            AND permanentLocationId IS NULL
            AND temporaryLocationId IS NULL;
    RETURN NEW;
  END;
  $$ LANGUAGE 'plpgsql';

-- Set Item effective location when inserting or updating an Item.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_effective_location_on_item_update() RETURNS trigger
AS $$
  declare
    effective_location_id jsonb;
  begin
    -- If location attributes set on item - use tem as they have higher priority
    effective_location_id = coalesce(NEW.jsonb->'temporaryLocationId', NEW.jsonb->'permanentLocationId');
    if (effective_location_id IS NOT NULL) then
      NEW.jsonb = jsonb_set(NEW.jsonb, '{effectiveLocationId}', effective_location_id);
      return NEW;
    end if;

    -- If 'effectiveLocationId' is present than it was set by holdings trigger,
    -- so we're skipping the main logic.
    effective_location_id = NEW.jsonb->'effectiveLocationId';
    if (effective_location_id IS NOT NULL) then
      -- Field exists so we know it was set by the holdings trigger
      -- because RMB always removes this read-only field.
      return NEW;
    end if;

    -- If sensitive fields (holdingsRecordId, permanent/temporaryLocationId) have not been changed
    -- No change in item's sensitive fields?
    if (TG_OP = 'UPDATE'
        AND (OLD.jsonb->'holdingsRecordId' IS NOT DISTINCT FROM NEW.jsonb->'holdingsRecordId')
        AND OLD.jsonb->'temporaryLocationId' IS NULL
        AND OLD.jsonb->'permanentLocationId' IS NULL) then
      -- No change in the relevant fields, we can use the old value and avoid a holdings query.
      effective_location_id = OLD.jsonb->'effectiveLocationId';
      if (effective_location_id IS NOT NULL) then
        -- We need to add it again because RMB always removes read-only fields.
        NEW.jsonb = jsonb_set(NEW.jsonb, '{effectiveLocationId}', effective_location_id);
      end if;
      return NEW;
    end if;

    -- If item has holdingsRecordId then use location from holdings_record
    SELECT coalesce(jsonb->'temporaryLocationId', jsonb->'permanentLocationId')
      INTO effective_location_id
      FROM ${myuniversity}_${mymodule}.holdings_record
      WHERE id = (NEW.jsonb->>'holdingsRecordId')::uuid
      LIMIT 1;

    NEW.jsonb = jsonb_set(NEW.jsonb, '{effectiveLocationId}', effective_location_id);

    return NEW;
  END;
$$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS update_effective_location_for_items ON ${myuniversity}_${mymodule}.holdings_record;
-- should be after update trigger, will allow item trigger to fetch up-to-date locations from holding
create trigger update_effective_location_for_items after update
on ${myuniversity}_${mymodule}.holdings_record for each row execute procedure ${myuniversity}_${mymodule}.update_effective_location_on_holding_update();

-- This trigger must run before the trigger update_item_references that
-- copies item.jsonb->>'effectiveLocationId' into item.effectiveLocationId.
-- This is true because they run in alphabetical order of trigger name, see
-- https://www.postgresql.org/docs/current/trigger-definition.html
DROP TRIGGER IF EXISTS update_effective_location ON ${myuniversity}_${mymodule}.item;
create trigger update_effective_location before insert or update
on ${myuniversity}_${mymodule}.item for each row execute procedure ${myuniversity}_${mymodule}.update_effective_location_on_item_update();
