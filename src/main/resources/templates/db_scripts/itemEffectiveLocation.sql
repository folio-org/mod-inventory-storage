-- Returns item effectiveLocationId.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.calculate_item_effective_location(hr_perm_location text,
           hr_temp_location text, itm_perm_location text, itm_temp_location text) RETURNS jsonb
AS $$
  begin
   RETURN ('"' || coalesce(itm_temp_location, itm_perm_location, hr_temp_location, hr_perm_location) || '"')::jsonb;
  END;
  $$ LANGUAGE 'plpgsql';

-- Updates item effective location property in case holding record permanent or temp location has changed.
-- only items that do not have permanent or temp location set will be updated.
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_effective_location_on_holding_update() RETURNS trigger
AS $$
  begin
	  -- null-safe comparison of OLD vs NEW location
	  -- have to update effectiveLocation only if related fields have been changed
	  if (OLD.jsonb->>'permanentLocationId' is distinct from NEW.jsonb->>'permanentLocationId'
	  	  OR OLD.jsonb->>'temporaryLocationId' is distinct from NEW.jsonb->>'temporaryLocationId') THEN
	  	  UPDATE ${myuniversity}_${mymodule}.item
	  	  SET jsonb = jsonb_set(jsonb,
		  					  '{effectiveLocationId}',
		  					   ${myuniversity}_${mymodule}.calculate_item_effective_location(
	  							   new.jsonb->>'permanentLocationId',
	  							   new.jsonb->>'temporaryLocationId',
	  							   jsonb->>'permanentLocationId',
	  							   jsonb->>'temporaryLocationId')
	  			      )
	  	  WHERE holdingsrecordid = new.id
	  	  -- if item's either temp or perm location is not null then we don't need to update it's effective location
	  	  -- as item's location has more priority than holding's one.
	  	  AND jsonb->>'permanentLocationId' is null AND jsonb->>'temporaryLocationId' is null;
	  end if;
   RETURN NEW;
  END;
  $$ LANGUAGE 'plpgsql';

-- Set Item effective location in case insert or update of temporary/permanentLocationId or holdingsRecordId properties
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_effective_location_on_item_update() RETURNS trigger
AS $$
	declare
		hr_perm_location text;
		hr_temp_location text;
		effective_location jsonb;
	begin

	  -- skip the trigger in case item's sensitive fields were not updated.
	  if (TG_OP = 'UPDATE' and OLD.jsonb->>'holdingsRecordId' IS NOT DISTINCT FROM NEW.jsonb->>'holdingsRecordId'
	      AND OLD.jsonb->>'temporaryLocationId' IS NOT DISTINCT FROM NEW.jsonb->>'temporaryLocationId'
	      AND OLD.jsonb->>'permanentLocationId' IS NOT DISTINCT FROM NEW.jsonb->>'permanentLocationId') then
	     return new;
	  end if;

		-- we need to retrieve holding_record temp and perm location only if item location fields are empty
		-- otherwise it does not make any affect as item location takes precedence over holding location
		if(NEW.jsonb ->> 'temporaryLocationId' is null and NEW.jsonb ->> 'permanentLocationId' is null) then
			select jsonb->>'temporaryLocationId', jsonb->>'permanentLocationId'
			 into hr_temp_location, hr_perm_location
			from ${myuniversity}_${mymodule}.holdings_record
			 where id::text = new.jsonb->>'holdingsRecordId' limit 1;
		else
		  -- initialize to nulls as they won't change final result
			hr_perm_location = null;
			hr_temp_location = null;
		end if;
		new.jsonb = jsonb_set(new.jsonb,
		                      '{effectiveLocationId}',
		                      ${myuniversity}_${mymodule}.calculate_item_effective_location(
									            hr_perm_location,
									            hr_temp_location,
									            new.jsonb->>'permanentLocationId',
									            new.jsonb->>'temporaryLocationId')
									        );
	  return new;
	END;
$$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS update_effective_location_for_items ON ${myuniversity}_${mymodule}.holdings_record;
-- should be after update trigger, will allow item trigger to fetch up-to-date locations from holding
create trigger update_effective_location_for_items after update
on ${myuniversity}_${mymodule}.holdings_record for each row execute procedure ${myuniversity}_${mymodule}.update_effective_location_on_holding_update();


DROP TRIGGER IF EXISTS update_effective_location ON ${myuniversity}_${mymodule}.item;
create trigger update_effective_location before insert or update
on ${myuniversity}_${mymodule}.item for each row execute procedure ${myuniversity}_${mymodule}.update_effective_location_on_item_update();
