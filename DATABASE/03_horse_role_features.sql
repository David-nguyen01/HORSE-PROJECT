USE [racehorse];
GO

IF COL_LENGTH(N'dbo.horses', N'current_trainer_id') IS NULL
BEGIN
    ALTER TABLE dbo.horses ADD current_trainer_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.horses ADD CONSTRAINT fk_horses_current_trainer
        FOREIGN KEY (current_trainer_id) REFERENCES dbo.users(id);
    CREATE INDEX idx_horses_current_trainer_id ON dbo.horses(current_trainer_id);
END;
GO

IF COL_LENGTH(N'dbo.horses', N'archived_at') IS NULL
BEGIN
    ALTER TABLE dbo.horses ADD archived_at DATETIMEOFFSET NULL;
    CREATE INDEX idx_horses_archived_at ON dbo.horses(archived_at);
END;
GO

IF OBJECT_ID(N'dbo.horse_ownerships', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.horse_ownerships (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_horse_ownerships PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        owner_id UNIQUEIDENTIFIER NOT NULL,
        ownership_percentage DECIMAL(5, 2) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_ownerships_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_horse_ownerships_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_horse_ownerships_version DEFAULT 0,
        CONSTRAINT fk_horse_ownerships_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT fk_horse_ownerships_owner FOREIGN KEY (owner_id) REFERENCES dbo.users(id),
        CONSTRAINT uq_horse_ownerships_horse_owner UNIQUE (horse_id, owner_id),
        CONSTRAINT ck_horse_ownerships_percentage CHECK (
            ownership_percentage > 0 AND ownership_percentage <= 100)
    );

    CREATE INDEX idx_horse_ownerships_owner_id ON dbo.horse_ownerships(owner_id);
END;
GO

IF OBJECT_ID(N'dbo.race_results', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.race_results (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_race_results PRIMARY KEY,
        horse_id UNIQUEIDENTIFIER NOT NULL,
        race_date DATE NOT NULL,
        race_name VARCHAR(200) NOT NULL,
        racecourse VARCHAR(200) NOT NULL,
        distance VARCHAR(50) NOT NULL,
        finish_position INT NOT NULL,
        finish_time VARCHAR(50) NOT NULL,
        jockey VARCHAR(150) NOT NULL,
        track_condition VARCHAR(100) NULL,
        prize_money VARCHAR(100) NOT NULL,
        created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_results_created_at DEFAULT SYSDATETIMEOFFSET(),
        updated_at DATETIMEOFFSET NOT NULL CONSTRAINT df_race_results_updated_at DEFAULT SYSDATETIMEOFFSET(),
        version BIGINT NOT NULL CONSTRAINT df_race_results_version DEFAULT 0,
        CONSTRAINT fk_race_results_horse FOREIGN KEY (horse_id) REFERENCES dbo.horses(id) ON DELETE CASCADE,
        CONSTRAINT ck_race_results_finish_position CHECK (finish_position >= 1)
    );

    CREATE INDEX idx_race_results_horse_date
        ON dbo.race_results(horse_id, race_date DESC);
END;
GO
