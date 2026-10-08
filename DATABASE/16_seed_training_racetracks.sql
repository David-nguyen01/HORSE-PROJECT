/* Seed active training grounds only when a name is not already present. */
USE [racehorse];
GO

DECLARE @LaneCount INT = 4;

IF @LaneCount < 1
    THROW 51020, N'@LaneCount ph\u1ea3i l\u1edbn h\u01a1n ho\u1eb7c b\u1eb1ng 1.', 1;

DECLARE @TrackNames TABLE (name NVARCHAR(200) NOT NULL PRIMARY KEY);
INSERT INTO @TrackNames (name)
VALUES
    (N'Sân Ánh Sao'), (N'Sân Bạch Mã'), (N'Sân Bình Minh'), (N'Sân Đồng Xanh'), (N'Sân Gió Đồi'),
    (N'Sân Hoàng Hôn'), (N'Sân Mây Trắng'), (N'Sân Ngân Hà'), (N'Sân Thiên Mã'), (N'Sân Vầng Trăng');

INSERT INTO dbo.racetracks (id, name, is_active, slot_minutes)
SELECT NEWID(), names.name, 1, 360
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
