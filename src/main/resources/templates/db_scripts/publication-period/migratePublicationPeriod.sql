CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.migrate_publication_period(
    jsonb_data jsonb
) RETURNS jsonb AS $$
DECLARE
    pub_period jsonb;
    start_date text;
    end_date text;
    date_type_id text;
    dates jsonb := '{}'::jsonb;
BEGIN
    pub_period := jsonb_data -> 'publicationPeriod';
    start_date := NULLIF(pub_period ->> 'start', '');
    end_date := NULLIF(pub_period ->> 'end', '');

    -- Determine Date type
    IF (start_date IS NOT NULL AND end_date IS NOT NULL) THEN
        date_type_id := '8fa6d067-41ff-4362-96a0-96b16ddce267';
    ELSIF (start_date IS NOT NULL OR end_date IS NOT NULL) THEN
        date_type_id := '24a506e8-2a92-4ecc-bd09-ff849321fd5a';
    ELSE
        -- Remove publicationPeriod from jsonb
        jsonb_data := jsonb_data - 'publicationPeriod';
        RETURN jsonb_data;
    END IF;

    -- Build the JSONB Dates object
    IF start_date IS NOT NULL THEN
        dates := jsonb_set(dates, '{date1}', to_jsonb(substring(start_date FROM 1 FOR 4)), true);
    END IF;
    IF end_date IS NOT NULL THEN
        dates := jsonb_set(dates, '{date2}', to_jsonb(substring(end_date FROM 1 FOR 4)), true);
    END IF;
    dates := jsonb_set(dates, '{dateTypeId}', to_jsonb(date_type_id), true);

    -- Set the dates into jsonb
    jsonb_data := jsonb_set(jsonb_data, '{dates}', dates, true);

    -- Remove publicationPeriod from jsonb
    jsonb_data := jsonb_data - 'publicationPeriod';

    RETURN jsonb_data;
END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;
