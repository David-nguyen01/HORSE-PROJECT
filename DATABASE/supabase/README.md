# Supabase PostgreSQL migration

The `racehorse_local` SQL Server database remains unchanged. The Supabase project
`batrbucfjoyozmnpqfld` currently has these migrations applied:

1. `01_schema_draft.sql` — 31 tables in the private `dbo` schema, keys, checks and indexes.
2. `02_seed_reference.sql` — five demo accounts and roles, 67 role grants, two stables,
   ten racetracks, 40 lanes, five care routines, three standard feeding plans and
   five starter inventory items. No existing local user, horse, medical or finance
   data was copied. `10_seed_demo_data.sql` was intentionally not imported.
3. `03_business_triggers.sql` — PostgreSQL equivalents of the seven business triggers.
4. `04_private_schema_access.sql` — direct access denied to Supabase `anon` and
   `authenticated` roles. The Spring backend should connect with a trusted database
   credential; frontend clients must continue calling the backend API.

## Current limitation

The Spring backend is **not yet ready to use Supabase**. `application-supabase.properties`
and the PostgreSQL JDBC driver are present, but many `JdbcTemplate` queries still use
SQL Server syntax (`TOP`, `WITH (UPDLOCK,HOLDLOCK)`, `JSON_VALUE`, `SYSDATETIMEOFFSET`,
`DATEADD`, SQL Server pagination). Port and integration-test these before activating
`SPRING_PROFILES_ACTIVE=supabase`. The existing `local` profile keeps SQL Server working.

Supabase reports that RLS is disabled for the 31 `dbo` tables. The schema is private
and the `anon`/`authenticated` roles have no schema or table access, as verified after
deployment. If direct Supabase Data API access is enabled later, design role-specific
RLS policies before granting access. See the [RLS guide](https://supabase.com/docs/guides/database/postgres/row-level-security).

The five seeded accounts use the documented test password from the SQL Server seed.
Change those passwords before exposing an internet-facing backend.

## Reproducing the schema files

`generate_schema.py` reads the current local SQL Server schema through Windows
authentication and requires `pyodbc`. `generate_reference_seed.py` reads only the
checked-in `09_seed_reference_data.sql` file. The SQL files are committed artifacts;
the generators are not required to connect to Supabase.
