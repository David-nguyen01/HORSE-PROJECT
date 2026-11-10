-- Compatibility functions for PostgreSQL (Supabase) to support SQL Server queries
-- Run this in Supabase SQL Editor if you are migrating from SQL Server

CREATE OR REPLACE FUNCTION dbo.sysdatetimeoffset() 
RETURNS timestamptz 
LANGUAGE sql STABLE AS $$
    SELECT clock_timestamp();
$$;

CREATE OR REPLACE FUNCTION public.sysdatetimeoffset() 
RETURNS timestamptz 
LANGUAGE sql STABLE AS $$
    SELECT clock_timestamp();
$$;

CREATE OR REPLACE FUNCTION dbo.json_value(json_text text, json_path text) 
RETURNS text 
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    clean_key text;
BEGIN
    IF json_text IS NULL OR json_path IS NULL THEN
        RETURN NULL;
    END IF;
    clean_key := regexp_replace(json_path, '^\$\.', '');
    RETURN (json_text::jsonb ->> clean_key);
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.json_value(json_text text, json_path text) 
RETURNS text 
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    RETURN dbo.json_value(json_text, json_path);
END;
$$;

CREATE OR REPLACE FUNCTION dbo.dateadd(datepart text, num integer, d timestamptz)
RETURNS timestamptz
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    CASE lower(datepart)
        WHEN 'day', 'd', 'dd' THEN RETURN d + (num || ' days')::interval;
        WHEN 'month', 'm', 'mm' THEN RETURN d + (num || ' months')::interval;
        WHEN 'year', 'yy', 'yyyy' THEN RETURN d + (num || ' years')::interval;
        WHEN 'hour', 'hh' THEN RETURN d + (num || ' hours')::interval;
        WHEN 'minute', 'mi', 'n' THEN RETURN d + (num || ' minutes')::interval;
        ELSE RETURN d + (num || ' days')::interval;
    END CASE;
END;
$$;

CREATE OR REPLACE FUNCTION public.dateadd(datepart text, num integer, d timestamptz)
RETURNS timestamptz
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    RETURN dbo.dateadd(datepart, num, d);
END;
$$;

