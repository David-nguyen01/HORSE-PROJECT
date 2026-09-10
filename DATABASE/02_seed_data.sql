USE [racehorse];
GO

DECLARE @admin_id UNIQUEIDENTIFIER;

SELECT @admin_id = id
FROM dbo.users
WHERE email = 'admin@gmail.com';

IF @admin_id IS NULL
BEGIN
    SET @admin_id = NEWID();

    INSERT INTO dbo.users (
        id,
        username,
        email,
        password_hash,
        enabled,
        created_at,
        updated_at,
        version
    )
    VALUES (
        @admin_id,
        'admin',
        'admin@gmail.com',
        '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u',
        1,
        SYSDATETIMEOFFSET(),
        SYSDATETIMEOFFSET(),
        0
    );
END;

IF NOT EXISTS (
    SELECT 1
    FROM dbo.user_roles
    WHERE user_id = @admin_id
      AND role = 'CLUB_MANAGER'
)
BEGIN
    INSERT INTO dbo.user_roles (user_id, role)
    VALUES (@admin_id, 'CLUB_MANAGER');
END;
GO
