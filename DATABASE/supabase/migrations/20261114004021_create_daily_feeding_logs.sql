-- The deployed application reads and writes this table for the feeding checklist.
-- The earlier standalone 07_daily_feeding_logs.sql is outside supabase/migrations
-- and was therefore not applied by the GitHub integration.
CREATE TABLE IF NOT EXISTS dbo.daily_feeding_logs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    horse_id UUID NOT NULL,
    feeding_date DATE NOT NULL,
    meal_key VARCHAR(100) NOT NULL,
    meal_name VARCHAR(100) NOT NULL,
    feeding_time TIME NOT NULL,
    plan_id UUID,
    plan_version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    confirmed_by_user_id UUID,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    meal_snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_daily_feeding_logs PRIMARY KEY (id),
    CONSTRAINT fk_daily_feeding_logs_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id),
    CONSTRAINT fk_daily_feeding_logs_user FOREIGN KEY (confirmed_by_user_id) REFERENCES dbo.users(id),
    CONSTRAINT uq_daily_feeding_logs_horse_date_meal UNIQUE (horse_id, feeding_date, meal_key),
    CONSTRAINT ck_daily_feeding_logs_snapshot CHECK ((meal_snapshot_json)::jsonb IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_daily_feeding_logs_date_horse
    ON dbo.daily_feeding_logs (feeding_date, horse_id);
CREATE INDEX IF NOT EXISTS idx_daily_feeding_logs_horse_date
    ON dbo.daily_feeding_logs (horse_id, feeding_date DESC);

-- Keep the private dbo table inaccessible through Supabase's browser roles.
REVOKE ALL ON dbo.daily_feeding_logs FROM PUBLIC, anon, authenticated;
