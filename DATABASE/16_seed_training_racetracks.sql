/* Seed active training grounds only when a name is not already present. */
USE [racehorse];
GO

DECLARE @LaneCount INT = 4;

IF @LaneCount < 1
    THROW 51020, N'@LaneCount ph\u1ea3i l\u1edbn h\u01a1n ho\u1eb7c b\u1eb1ng 1.', 1;

DECLARE @TrackNames TABLE (name NVARCHAR(200) NOT NULL PRIMARY KEY);
INSERT INTO @TrackNames (name)
VALUES
    (N'Sân tập 01'), (N'Sân tập 02'), (N'Sân tập 03'), (N'Sân tập 04'), (N'Sân tập 05'),
    (N'Sân tập 06'), (N'Sân tập 07'), (N'Sân tập 08'), (N'Sân tập 09'), (N'Sân tập 10');

INSERT INTO dbo.racetracks (id, name, is_active)
SELECT NEWID(), names.name, 1
FROM @TrackNames names
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.racetracks track
    WHERE track.name = names.name
);

DECLARE @LaneNumber INT = 1;
WHILE @LaneNumber <= @LaneCount
BEGIN
    INSERT INTO dbo.racetrack_lanes (id, racetrack_id, lane_number, is_active)
    SELECT NEWID(), track.id, @LaneNumber, 1
    FROM dbo.racetracks track
    JOIN @TrackNames names ON names.name = track.name
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.racetrack_lanes lane
        WHERE lane.racetrack_id = track.id
          AND lane.lane_number = @LaneNumber
    );

    SET @LaneNumber += 1;
END;
GO
