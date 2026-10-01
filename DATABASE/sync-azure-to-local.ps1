param(
    [string]$LocalServer = "localhost,1433",
    [string]$LocalUser = "",
    [string]$LocalPassword = "",
    [string]$AzureServer = "racehorse-training-management.database.windows.net,1433",
    [string]$AzureUser = "racehorse_admin",
    [string]$AzurePassword = "RaceSWP391"
)

$localConnStr = if ($LocalUser) {
    "Server=$LocalServer;Database=racehorse;User Id=$LocalUser;Password=$LocalPassword;TrustServerCertificate=True;"
} else {
    "Server=$LocalServer;Database=racehorse;Integrated Security=True;TrustServerCertificate=True;"
}
$azureConnStr = "Server=$AzureServer;Database=racehorse;User Id=$AzureUser;Password=$AzurePassword;Encrypt=True;TrustServerCertificate=True;"

$azureConn = New-Object System.Data.SqlClient.SqlConnection($azureConnStr)
$localConn = New-Object System.Data.SqlClient.SqlConnection($localConnStr)

try {
    $azureConn.Open()
    $localConn.Open()
    Write-Host "Connected to Azure SQL and Local SQL."

    # 1. Sync Users
    Write-Host "1. Syncing Users..."
    $cmd = $azureConn.CreateCommand()
    $cmd.CommandText = "SELECT id, username, email, password_hash, enabled, created_at, updated_at, version FROM dbo.users"
    $r = $cmd.ExecuteReader()
    $users = New-Object System.Collections.ArrayList
    while ($r.Read()) {
        [void]$users.Add([PSCustomObject]@{
            Id = $r["id"]
            Username = $r["username"]
            Email = $r["email"]
            PasswordHash = $r["password_hash"]
            Enabled = $r["enabled"]
            CreatedAt = $r["created_at"]
            UpdatedAt = $r["updated_at"]
            Version = $r["version"]
        })
    }
    $r.Close()

    foreach ($u in $users) {
        $ins = $localConn.CreateCommand()
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE id = @id OR email = @email)
BEGIN
    INSERT INTO dbo.users (id, username, email, password_hash, enabled, created_at, updated_at, version)
    VALUES (@id, @username, @email, @password_hash, @enabled, @created_at, @updated_at, @version);
END
"@
        $ins.Parameters.AddWithValue("@id", $u.Id) | Out-Null
        $ins.Parameters.AddWithValue("@username", $u.Username) | Out-Null
        $ins.Parameters.AddWithValue("@email", $u.Email) | Out-Null
        $ins.Parameters.AddWithValue("@password_hash", $u.PasswordHash) | Out-Null
        $ins.Parameters.AddWithValue("@enabled", $u.Enabled) | Out-Null
        $ins.Parameters.AddWithValue("@created_at", $u.CreatedAt) | Out-Null
        $ins.Parameters.AddWithValue("@updated_at", $u.UpdatedAt) | Out-Null
        $ins.Parameters.AddWithValue("@version", $u.Version) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
    }

    # 2. Sync user_roles
    Write-Host "2. Syncing User Roles..."
    $cmd = $azureConn.CreateCommand()
    $cmd.CommandText = "SELECT user_id, role FROM dbo.user_roles"
    $r = $cmd.ExecuteReader()
    while ($r.Read()) {
        $ins = $localConn.CreateCommand()
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM dbo.user_roles WHERE user_id = @uid AND role = @role)
   AND EXISTS (SELECT 1 FROM dbo.users WHERE id = @uid)
BEGIN
    INSERT INTO dbo.user_roles (user_id, role) VALUES (@uid, @role);
END
"@
        $ins.Parameters.AddWithValue("@uid", $r["user_id"]) | Out-Null
        $ins.Parameters.AddWithValue("@role", $r["role"]) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
    }
    $r.Close()

    # 3. Sync stables
    Write-Host "3. Syncing Stables..."
    $cmd = $azureConn.CreateCommand()
    $cmd.CommandText = "SELECT id, name, location, is_active FROM dbo.stables"
    $r = $cmd.ExecuteReader()
    while ($r.Read()) {
        $ins = $localConn.CreateCommand()
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM dbo.stables WHERE id = @id)
BEGIN
    INSERT INTO dbo.stables (id, name, location, is_active) VALUES (@id, @name, @location, @is_active);
END
"@
        $ins.Parameters.AddWithValue("@id", $r["id"]) | Out-Null
        $ins.Parameters.AddWithValue("@name", $r["name"]) | Out-Null
        $ins.Parameters.AddWithValue("@location", $r["location"]) | Out-Null
        $ins.Parameters.AddWithValue("@is_active", $r["is_active"]) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
    }
    $r.Close()

    # 4. Sync horses
    Write-Host "4. Syncing Horses..."
    $cmd = $azureConn.CreateCommand()
    $cmd.CommandText = "SELECT id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed, stable_location, status, created_at, updated_at, version, health_status FROM dbo.horses"
    $r = $cmd.ExecuteReader()
    while ($r.Read()) {
        $ins = $localConn.CreateCommand()
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM dbo.horses WHERE id = @id)
BEGIN
    INSERT INTO dbo.horses (id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed, stable_location, status, created_at, updated_at, version, health_status)
    VALUES (@id, @code, @name, @microchip, @birth_date, @gender, @color, @breed, @loc, @status, @created_at, @updated_at, @version, @health_status);
END
"@
        $ins.Parameters.AddWithValue("@id", $r["id"]) | Out-Null
        $ins.Parameters.AddWithValue("@code", $r["code"]) | Out-Null
        $ins.Parameters.AddWithValue("@name", $r["name"]) | Out-Null
        $ins.Parameters.AddWithValue("@microchip", $r["microchip_passport_number"]) | Out-Null
        $ins.Parameters.AddWithValue("@birth_date", $r["birth_date"]) | Out-Null
        $ins.Parameters.AddWithValue("@gender", $r["gender"]) | Out-Null
        $ins.Parameters.AddWithValue("@color", $r["color_markings"]) | Out-Null
        $ins.Parameters.AddWithValue("@breed", $r["breed"]) | Out-Null
        $ins.Parameters.AddWithValue("@loc", $r["stable_location"]) | Out-Null
        $ins.Parameters.AddWithValue("@status", $r["status"]) | Out-Null
        $ins.Parameters.AddWithValue("@created_at", $r["created_at"]) | Out-Null
        $ins.Parameters.AddWithValue("@updated_at", $r["updated_at"]) | Out-Null
        $ins.Parameters.AddWithValue("@version", $r["version"]) | Out-Null
        $hStatus = if ($r["health_status"] -ne [DBNull]::Value) { $r["health_status"] } else { "FIT" }
        $ins.Parameters.AddWithValue("@health_status", $hStatus) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
    }
    $r.Close()

    # 5. Sync module_records (Stalls & Stall Assignments)
    Write-Host "5. Syncing Module Records..."
    $cmd = $azureConn.CreateCommand()
    $cmd.CommandText = "SELECT id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at FROM dbo.module_records"
    $r = $cmd.ExecuteReader()
    while ($r.Read()) {
        $ins = $localConn.CreateCommand()
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = @id)
BEGIN
    INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (@id, @module_code, @record_type, @source_table, @horse_id, @user_id, @status, @event_date, @data_json, @created_at, @updated_at);
END
"@
        $ins.Parameters.AddWithValue("@id", $r["id"]) | Out-Null
        $ins.Parameters.AddWithValue("@module_code", $r["module_code"]) | Out-Null
        $ins.Parameters.AddWithValue("@record_type", $r["record_type"]) | Out-Null
        $ins.Parameters.AddWithValue("@source_table", $r["source_table"]) | Out-Null
        
        $hId = if ($r["horse_id"] -ne [DBNull]::Value) { $r["horse_id"] } else { [DBNull]::Value }
        $ins.Parameters.AddWithValue("@horse_id", $hId) | Out-Null
        
        $uId = if ($r["user_id"] -ne [DBNull]::Value) {
            $candidateId = $r["user_id"]
            $checkUser = $localConn.CreateCommand()
            $checkUser.CommandText = "SELECT id FROM dbo.users WHERE id = '$candidateId'"
            $exists = $checkUser.ExecuteScalar()
            if ($exists) {
                $candidateId
            } else {
                $localAdminCmd = $localConn.CreateCommand()
                $localAdminCmd.CommandText = "SELECT TOP 1 id FROM dbo.users WHERE email = 'admin@gmail.com'"
                $localAdminCmd.ExecuteScalar()
            }
        } else { [DBNull]::Value }
        $ins.Parameters.AddWithValue("@user_id", $uId) | Out-Null
        
        $ins.Parameters.AddWithValue("@status", $r["status"]) | Out-Null
        
        $eDate = if ($r["event_date"] -ne [DBNull]::Value) { $r["event_date"] } else { [DBNull]::Value }
        $ins.Parameters.AddWithValue("@event_date", $eDate) | Out-Null
        
        $ins.Parameters.AddWithValue("@data_json", $r["data_json"]) | Out-Null
        $ins.Parameters.AddWithValue("@created_at", $r["created_at"]) | Out-Null
        $ins.Parameters.AddWithValue("@updated_at", $r["updated_at"]) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
    }
    $r.Close()

    Write-Host "Data Sync from Azure to Local completed successfully!" -ForegroundColor Green
} finally {
    $azureConn.Close()
    $localConn.Close()
}
