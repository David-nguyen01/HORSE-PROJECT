# Supabase PostgreSQL migrations

This directory is the Supabase CLI project used by the GitHub integration. The
hosted project reference is `batrbucfjoyozmnpqfld`. The application tables live in
the private `dbo` schema and are accessed only by the trusted Spring backend.

## GitHub integration

Configure Supabase Dashboard > Project Settings > Integrations > GitHub as follows:

- Repository: `David-nguyen01/HORSE-PROJECT`
- Working directory: `DATABASE`
- Deploy to production: enabled
- Production branch: `develop`
- Automatic/preview branching: disabled (not required for production deployment)

Supabase automatically applies only new files under `supabase/migrations/`. It does
not copy live table data to GitHub and it does not deploy the frontend or backend.

## Migration baseline

The first five filenames use the exact versions already registered in the hosted
project's migration history. Supabase will therefore skip them instead of trying to
recreate the existing schema:

1. `20261110023411_racehorse_initial_schema.sql`
2. `20261110023643_racehorse_reference_seed.sql`
3. `20261110023828_racehorse_business_triggers.sql`
4. `20261110023850_racehorse_private_schema_access.sql`
5. `20261110075656_sql_server_json_compatibility.sql`

`20261113120524_service_packages_catalog.sql` is applied and completes the service
package catalog without deleting the existing custom package or application data.

`20261113120525_harden_owner_reports_and_database_performance.sql` completes the
partially deployed owner-report content hash, preserves historical reports, adds
the concurrency-safe unique index, fixes trigger search paths, and adds a focused
set of workflow indexes. It performs no table truncation or row deletion.

## Rules for future changes

1. Never edit, rename, reorder, or delete an applied migration.
2. Create a new timestamped migration for every schema or reference-data change.
3. Test the SQL before pushing it to `develop`; this branch deploys to production.
4. Do not place database passwords, access tokens, or connection strings in Git.
5. Do not use `seed.sql` for live data. Production reference data must use an
   idempotent migration with conflict handling.
6. Deleting a migration file does not roll back the hosted database. Create a new
   forward migration to reverse a change.

With Supabase CLI installed, create a migration from the repository root with:

```powershell
npx supabase migration new <descriptive_name> --workdir DATABASE
```

This creates `DATABASE/supabase/migrations/<timestamp>_<descriptive_name>.sql`.

## Security model

RLS is currently disabled on the private `dbo` tables. The `anon` and
`authenticated` roles have neither schema usage nor table grants, so browser clients
cannot access these tables through the Supabase Data API. Do not expose `dbo` or add
frontend grants without first designing and testing role-specific RLS policies.

The seeded test accounts use a shared documented test password. Replace those
passwords before using the system with real users.

## Local SQL Server generators

The `racehorse_local` SQL Server database remains independent. `generate_schema.py`
reads it through Windows authentication and requires `pyodbc`.
`generate_reference_seed.py` converts the checked-in SQL Server reference seed.
Neither generator connects to or changes the hosted Supabase database.
