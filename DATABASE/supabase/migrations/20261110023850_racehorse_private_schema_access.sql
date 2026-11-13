-- Keep the application tables reachable only through the trusted Spring backend.
-- The public Supabase anon/authenticated API roles must not access dbo directly.
REVOKE ALL ON SCHEMA dbo FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA dbo FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA dbo FROM PUBLIC, anon, authenticated;
