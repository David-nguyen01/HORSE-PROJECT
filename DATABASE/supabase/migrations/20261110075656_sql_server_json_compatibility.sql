-- PostgreSQL equivalents used by legacy JDBC queries.
-- Keep these functions in the private dbo schema; the backend search_path is dbo,public.

CREATE OR REPLACE FUNCTION dbo.json_value(json_text text, json_path text)
RETURNS text
LANGUAGE sql IMMUTABLE STRICT
SET search_path = pg_catalog
AS $$
    SELECT json_text::jsonb #>> string_to_array(substr(json_path, 3), '.');
$$;

-- SQL Server JSON_MODIFY in lax mode removes a property when the new value is NULL.
-- The application passes JSON field values as strings.
CREATE OR REPLACE FUNCTION dbo.json_modify(json_text text, json_path text, new_value text)
RETURNS text
LANGUAGE sql IMMUTABLE
SET search_path = pg_catalog
AS $$
    SELECT CASE
        WHEN json_text IS NULL OR json_path IS NULL THEN NULL
        WHEN new_value IS NULL THEN
            (json_text::jsonb #- string_to_array(substr(json_path, 3), '.'))::text
        ELSE
            jsonb_set(json_text::jsonb,
                      string_to_array(substr(json_path, 3), '.'),
                      to_jsonb(new_value),
                      true)::text
    END;
$$;

CREATE OR REPLACE FUNCTION dbo.dateadd(datepart text, amount integer, value date)
RETURNS date
LANGUAGE sql IMMUTABLE STRICT
SET search_path = pg_catalog
AS $$
    SELECT CASE lower(datepart)
        WHEN 'day' THEN value + amount
        ELSE NULL
    END;
$$;

REVOKE ALL ON FUNCTION dbo.json_value(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION dbo.json_modify(text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION dbo.dateadd(text, integer, date) FROM PUBLIC;
