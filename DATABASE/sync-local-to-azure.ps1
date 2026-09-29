param(
    [string]$LocalServer = "localhost,1433",
    [string]$LocalUser = "sa",
    [string]$LocalPassword = "sa123456",
    [string]$AzureServer = "racehorse-training-management.database.windows.net,1433",
    [string]$AzureUser = "racehorse_admin@racehorse-training-management",
    [string]$AzurePassword = "RaceSWP391"
)

$localConnStr = "Server=$LocalServer;Database=racehorse;User Id=$LocalUser;Password=$LocalPassword;TrustServerCertificate=True;"
$azureConnStr = "Server=$AzureServer;Database=racehorse;User Id=$AzureUser;Password=$AzurePassword;Encrypt=True;TrustServerCertificate=True;"

$localConn = New-Object System.Data.SqlClient.SqlConnection($localConnStr)
$azureConn = New-Object System.Data.SqlClient.SqlConnection($azureConnStr)

$localConn.Open()
$azureConn.Open()

Write-Host "Both Local and Azure connections opened."

$azureAdminId = "5d036ea7-77fd-4d63-90ac-b90e1e4cae11"
$localAdminId = "d245f9a4-173b-4ee7-97d2-5b1baa6937ce"

$tx = $azureConn.BeginTransaction("DataMerge")

try {
    # 1. Merge Users
    Write-Host "Merging Users..."
    $cmd = $localConn.CreateCommand()
    $cmd.CommandText = "SELECT id, username, email, password_hash, enabled, created_at, updated_at, version FROM users WHERE email != 'admin@gmail.com'"
    $reader = $cmd.ExecuteReader()
    $newUsers = New-Object System.Collections.ArrayList
    while ($reader.Read()) {
        $userObj = [PSCustomObject]@{
            Id = $reader["id"]
            Username = $reader["username"]
            Email = $reader["email"]
            PasswordHash = $reader["password_hash"]
            Enabled = $reader["enabled"]
            CreatedAt = $reader["created_at"]
            UpdatedAt = $reader["updated_at"]
            Version = $reader["version"]
        }
        [void]$newUsers.Add($userObj)
    }
    $reader.Close()

    foreach ($u in $newUsers) {
        $ins = $azureConn.CreateCommand()
        $ins.Transaction = $tx
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM users WHERE id = @id OR email = @email)
BEGIN
    INSERT INTO users (id, username, email, password_hash, enabled, created_at, updated_at, version)
    VALUES (@id, @username, @email, @password_hash, @enabled, @created_at, @updated_at, @version)
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
        Write-Host "  -> Inserted/verified user: $($u.Email)"
    }

    # 2. Merge User Roles
    Write-Host "Merging User Roles..."
    $cmd = $localConn.CreateCommand()
    $cmd.CommandText = "SELECT user_id, role FROM user_roles"
    $reader = $cmd.ExecuteReader()
    $roles = New-Object System.Collections.ArrayList
    while ($reader.Read()) {
        $rObj = [PSCustomObject]@{
            UserId = $reader["user_id"]
            Role = $reader["role"]
        }
        [void]$roles.Add($rObj)
    }
    $reader.Close()

    foreach ($r in $roles) {
        $targetUserId = $r.UserId
        if ($targetUserId.ToString().ToLower() -eq $localAdminId.ToLower()) {
            $targetUserId = [Guid]$azureAdminId
        }
        $ins = $azureConn.CreateCommand()
        $ins.Transaction = $tx
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @user_id AND role = @role)
BEGIN
    INSERT INTO user_roles (user_id, role) VALUES (@user_id, @role)
END
"@
        $ins.Parameters.AddWithValue("@user_id", $targetUserId) | Out-Null
        $ins.Parameters.AddWithValue("@role", $r.Role) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
    }

    # 3. Merge Stables
    Write-Host "Merging Stables..."
    $cmd = $localConn.CreateCommand()
    $cmd.CommandText = "SELECT id, name, location, is_active FROM stables"
    $reader = $cmd.ExecuteReader()
    $stables = New-Object System.Collections.ArrayList
    while ($reader.Read()) {
        $sObj = [PSCustomObject]@{
            Id = $reader["id"]
            Name = $reader["name"]
            Location = $reader["location"]
            IsActive = $reader["is_active"]
        }
        [void]$stables.Add($sObj)
    }
    $reader.Close()

    foreach ($s in $stables) {
        $ins = $azureConn.CreateCommand()
        $ins.Transaction = $tx
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM stables WHERE id = @id)
BEGIN
    INSERT INTO stables (id, name, location, is_active)
    VALUES (@id, @name, @location, @is_active)
END
"@
        $ins.Parameters.AddWithValue("@id", $s.Id) | Out-Null
        $ins.Parameters.AddWithValue("@name", $s.Name) | Out-Null
        $ins.Parameters.AddWithValue("@location", $s.Location) | Out-Null
        $ins.Parameters.AddWithValue("@is_active", $s.IsActive) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
        Write-Host "  -> Inserted stable: $($s.Name)"
    }

    # 4. Merge Horses
    Write-Host "Merging Horses..."
    $cmd = $localConn.CreateCommand()
    $cmd.CommandText = @"
SELECT id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
       sire_id, dam_id, stable_location, status, created_at, updated_at, version,
       current_trainer_id, archived_at, health_status, health_changed_by_user_id,
       health_source_type, health_source_id, health_changed_at,
       payment_status, service_override, override_reason, paid_until
FROM horses
"@
    $reader = $cmd.ExecuteReader()
    $horses = New-Object System.Collections.ArrayList
    while ($reader.Read()) {
        $hObj = [PSCustomObject]@{
            Id = $reader["id"]
            Code = $reader["code"]
            Name = $reader["name"]
            Microchip = $reader["microchip_passport_number"]
            BirthDate = $reader["birth_date"]
            Gender = $reader["gender"]
            ColorMarkings = $reader["color_markings"]
            Breed = $reader["breed"]
            SireId = $reader["sire_id"]
            DamId = $reader["dam_id"]
            StableLocation = $reader["stable_location"]
            Status = $reader["status"]
            CreatedAt = $reader["created_at"]
            UpdatedAt = $reader["updated_at"]
            Version = $reader["version"]
            CurrentTrainerId = $reader["current_trainer_id"]
            ArchivedAt = $reader["archived_at"]
            HealthStatus = $reader["health_status"]
            HealthChangedBy = $reader["health_changed_by_user_id"]
            HealthSourceType = $reader["health_source_type"]
            HealthSourceId = $reader["health_source_id"]
            HealthChangedAt = $reader["health_changed_at"]
            PaymentStatus = $reader["payment_status"]
            ServiceOverride = $reader["service_override"]
            OverrideReason = $reader["override_reason"]
            PaidUntil = $reader["paid_until"]
        }
        [void]$horses.Add($hObj)
    }
    $reader.Close()

    foreach ($h in $horses) {
        $trainer = $h.CurrentTrainerId
        if ($trainer -and $trainer.ToString().ToLower() -eq $localAdminId.ToLower()) {
            $trainer = [Guid]$azureAdminId
        }
        $healthUser = $h.HealthChangedBy
        if ($healthUser -and $healthUser.ToString().ToLower() -eq $localAdminId.ToLower()) {
            $healthUser = [Guid]$azureAdminId
        }

        $ins = $azureConn.CreateCommand()
        $ins.Transaction = $tx
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM horses WHERE id = @id OR code = @code)
BEGIN
    INSERT INTO horses (
        id, code, name, microchip_passport_number, birth_date, gender, color_markings, breed,
        sire_id, dam_id, stable_location, status, created_at, updated_at, version,
        current_trainer_id, archived_at, health_status, health_changed_by_user_id,
        health_source_type, health_source_id, health_changed_at,
        payment_status, service_override, override_reason, paid_until
    )
    VALUES (
        @id, @code, @name, @microchip, @birth_date, @gender, @color_markings, @breed,
        @sire_id, @dam_id, @stable_location, @status, @created_at, @updated_at, @version,
        @current_trainer_id, @archived_at, @health_status, @health_changed_by_user_id,
        @health_source_type, @health_source_id, @health_changed_at,
        @payment_status, @service_override, @override_reason, @paid_until
    )
END
"@
        $ins.Parameters.AddWithValue("@id", $h.Id) | Out-Null
        $ins.Parameters.AddWithValue("@code", $h.Code) | Out-Null
        $ins.Parameters.AddWithValue("@name", $h.Name) | Out-Null
        $ins.Parameters.AddWithValue("@microchip", [object]$h.Microchip) | Out-Null
        $ins.Parameters.AddWithValue("@birth_date", $h.BirthDate) | Out-Null
        $ins.Parameters.AddWithValue("@gender", $h.Gender) | Out-Null
        $ins.Parameters.AddWithValue("@color_markings", $h.ColorMarkings) | Out-Null
        $ins.Parameters.AddWithValue("@breed", $h.Breed) | Out-Null
        $ins.Parameters.AddWithValue("@sire_id", [object]$h.SireId) | Out-Null
        $ins.Parameters.AddWithValue("@dam_id", [object]$h.DamId) | Out-Null
        $ins.Parameters.AddWithValue("@stable_location", $h.StableLocation) | Out-Null
        $ins.Parameters.AddWithValue("@status", $h.Status) | Out-Null
        $ins.Parameters.AddWithValue("@created_at", $h.CreatedAt) | Out-Null
        $ins.Parameters.AddWithValue("@updated_at", $h.UpdatedAt) | Out-Null
        $ins.Parameters.AddWithValue("@version", $h.Version) | Out-Null
        $ins.Parameters.AddWithValue("@current_trainer_id", [object]$trainer) | Out-Null
        $ins.Parameters.AddWithValue("@archived_at", [object]$h.ArchivedAt) | Out-Null
        $ins.Parameters.AddWithValue("@health_status", [object]$h.HealthStatus) | Out-Null
        $ins.Parameters.AddWithValue("@health_changed_by_user_id", [object]$healthUser) | Out-Null
        $ins.Parameters.AddWithValue("@health_source_type", [object]$h.HealthSourceType) | Out-Null
        $ins.Parameters.AddWithValue("@health_source_id", [object]$h.HealthSourceId) | Out-Null
        $ins.Parameters.AddWithValue("@health_changed_at", [object]$h.HealthChangedAt) | Out-Null
        $ins.Parameters.AddWithValue("@payment_status", $h.PaymentStatus) | Out-Null
        $ins.Parameters.AddWithValue("@service_override", $h.ServiceOverride) | Out-Null
        $ins.Parameters.AddWithValue("@override_reason", [object]$h.OverrideReason) | Out-Null
        $ins.Parameters.AddWithValue("@paid_until", [object]$h.PaidUntil) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
        Write-Host "  -> Inserted horse: $($h.Code) - $($h.Name)"
    }

    # 5. Merge Module Records
    Write-Host "Merging Module Records..."
    $cmd = $localConn.CreateCommand()
    $cmd.CommandText = "SELECT id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at FROM module_records"
    $reader = $cmd.ExecuteReader()
    $modRecords = New-Object System.Collections.ArrayList
    while ($reader.Read()) {
        $mObj = [PSCustomObject]@{
            Id = $reader["id"]
            ModuleCode = $reader["module_code"]
            RecordType = $reader["record_type"]
            SourceTable = $reader["source_table"]
            HorseId = $reader["horse_id"]
            UserId = $reader["user_id"]
            Status = $reader["status"]
            EventDate = $reader["event_date"]
            DataJson = $reader["data_json"]
            CreatedAt = $reader["created_at"]
            UpdatedAt = $reader["updated_at"]
        }
        [void]$modRecords.Add($mObj)
    }
    $reader.Close()

    foreach ($m in $modRecords) {
        $targetUserId = $m.UserId
        if ($targetUserId -and $targetUserId.ToString().ToLower() -eq $localAdminId.ToLower()) {
            $targetUserId = [Guid]$azureAdminId
        }

        # Update JSON references
        $cleanJson = $m.DataJson
        if ($cleanJson) {
            $cleanJson = $cleanJson.Replace($localAdminId.ToLower(), $azureAdminId.ToLower())
        }

        $ins = $azureConn.CreateCommand()
        $ins.Transaction = $tx
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM module_records WHERE id = @id)
BEGIN
    INSERT INTO module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
    VALUES (@id, @module_code, @record_type, @source_table, @horse_id, @user_id, @status, @event_date, @data_json, @created_at, @updated_at)
END
"@
        $ins.Parameters.AddWithValue("@id", $m.Id) | Out-Null
        $ins.Parameters.AddWithValue("@module_code", $m.ModuleCode) | Out-Null
        $ins.Parameters.AddWithValue("@record_type", $m.RecordType) | Out-Null
        $ins.Parameters.AddWithValue("@source_table", [object]$m.SourceTable) | Out-Null
        $ins.Parameters.AddWithValue("@horse_id", [object]$m.HorseId) | Out-Null
        $ins.Parameters.AddWithValue("@user_id", [object]$targetUserId) | Out-Null
        $ins.Parameters.AddWithValue("@status", [object]$m.Status) | Out-Null
        $ins.Parameters.AddWithValue("@event_date", [object]$m.EventDate) | Out-Null
        $ins.Parameters.AddWithValue("@data_json", $cleanJson) | Out-Null
        $ins.Parameters.AddWithValue("@created_at", $m.CreatedAt) | Out-Null
        $ins.Parameters.AddWithValue("@updated_at", $m.UpdatedAt) | Out-Null
        $ins.ExecuteNonQuery() | Out-Null
    }
    Write-Host "  -> Inserted $($modRecords.Count) module records."

    # 6. Merge Login Sessions
    Write-Host "Merging Login Sessions..."
    $cmd = $localConn.CreateCommand()
    $cmd.CommandText = "SELECT id, user_id, refresh_token_hash, ip_address, user_agent, last_activity_at, expires_at, revoked_at, created_at, updated_at, version FROM login_sessions"
    $reader = $cmd.ExecuteReader()
    $sessions = New-Object System.Collections.ArrayList
    while ($reader.Read()) {
        $sessObj = [PSCustomObject]@{
            Id = $reader["id"]
            UserId = $reader["user_id"]
            RefreshTokenHash = $reader["refresh_token_hash"]
            IpAddress = $reader["ip_address"]
            UserAgent = $reader["user_agent"]
            LastActivityAt = $reader["last_activity_at"]
            ExpiresAt = $reader["expires_at"]
            RevokedAt = $reader["revoked_at"]
            CreatedAt = $reader["created_at"]
            UpdatedAt = $reader["updated_at"]
            Version = $reader["version"]
        }
        [void]$sessions.Add($sessObj)
    }
    $reader.Close()

    $sessInserted = 0
    foreach ($sess in $sessions) {
        $targetUserId = $sess.UserId
        if ($targetUserId.ToString().ToLower() -eq $localAdminId.ToLower()) {
            $targetUserId = [Guid]$azureAdminId
        }

        $ins = $azureConn.CreateCommand()
        $ins.Transaction = $tx
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM login_sessions WHERE id = @id)
BEGIN
    INSERT INTO login_sessions (id, user_id, refresh_token_hash, ip_address, user_agent, last_activity_at, expires_at, revoked_at, created_at, updated_at, version)
    VALUES (@id, @user_id, @refresh_token_hash, @ip_address, @user_agent, @last_activity_at, @expires_at, @revoked_at, @created_at, @updated_at, @version)
END
"@
        $ins.Parameters.AddWithValue("@id", $sess.Id) | Out-Null
        $ins.Parameters.AddWithValue("@user_id", $targetUserId) | Out-Null
        $ins.Parameters.AddWithValue("@refresh_token_hash", $sess.RefreshTokenHash) | Out-Null
        $ins.Parameters.AddWithValue("@ip_address", [object]$sess.IpAddress) | Out-Null
        $ins.Parameters.AddWithValue("@user_agent", [object]$sess.UserAgent) | Out-Null
        $ins.Parameters.AddWithValue("@last_activity_at", $sess.LastActivityAt) | Out-Null
        $ins.Parameters.AddWithValue("@expires_at", $sess.ExpiresAt) | Out-Null
        $ins.Parameters.AddWithValue("@revoked_at", [object]$sess.RevokedAt) | Out-Null
        $ins.Parameters.AddWithValue("@created_at", $sess.CreatedAt) | Out-Null
        $ins.Parameters.AddWithValue("@updated_at", $sess.UpdatedAt) | Out-Null
        $ins.Parameters.AddWithValue("@version", $sess.Version) | Out-Null
        $res = $ins.ExecuteNonQuery()
        if ($res -gt 0) { $sessInserted++ }
    }
    Write-Host "  -> Inserted $sessInserted login sessions."

    # 7. Merge Audit Logs
    Write-Host "Merging Audit Logs..."
    $cmd = $localConn.CreateCommand()
    $cmd.CommandText = "SELECT actor_user_id, occurred_at, module_code, action_code, entity_type, entity_id, description, old_values_json, new_values_json, ip_address, user_agent FROM audit_logs"
    $reader = $cmd.ExecuteReader()
    $auditLogs = New-Object System.Collections.ArrayList
    while ($reader.Read()) {
        $aObj = [PSCustomObject]@{
            ActorUserId = $reader["actor_user_id"]
            OccurredAt = $reader["occurred_at"]
            ModuleCode = $reader["module_code"]
            ActionCode = $reader["action_code"]
            EntityType = $reader["entity_type"]
            EntityId = $reader["entity_id"]
            Description = $reader["description"]
            OldValuesJson = $reader["old_values_json"]
            NewValuesJson = $reader["new_values_json"]
            IpAddress = $reader["ip_address"]
            UserAgent = $reader["user_agent"]
        }
        [void]$auditLogs.Add($aObj)
    }
    $reader.Close()

    $auditInserted = 0
    foreach ($a in $auditLogs) {
        $targetActor = $a.ActorUserId
        if ($targetActor -and $targetActor.ToString().ToLower() -eq $localAdminId.ToLower()) {
            $targetActor = [Guid]$azureAdminId
        }

        $ins = $azureConn.CreateCommand()
        $ins.Transaction = $tx
        $ins.CommandText = @"
IF NOT EXISTS (SELECT 1 FROM audit_logs WHERE occurred_at = @occurred_at AND module_code = @module_code AND action_code = @action_code AND description = @description)
BEGIN
    INSERT INTO audit_logs (actor_user_id, occurred_at, module_code, action_code, entity_type, entity_id, description, old_values_json, new_values_json, ip_address, user_agent)
    VALUES (@actor_user_id, @occurred_at, @module_code, @action_code, @entity_type, @entity_id, @description, @old_values_json, @new_values_json, @ip_address, @user_agent)
END
"@
        $ins.Parameters.AddWithValue("@actor_user_id", [object]$targetActor) | Out-Null
        $ins.Parameters.AddWithValue("@occurred_at", $a.OccurredAt) | Out-Null
        $ins.Parameters.AddWithValue("@module_code", $a.ModuleCode) | Out-Null
        $ins.Parameters.AddWithValue("@action_code", $a.ActionCode) | Out-Null
        $ins.Parameters.AddWithValue("@entity_type", [object]$a.EntityType) | Out-Null
        $ins.Parameters.AddWithValue("@entity_id", [object]$a.EntityId) | Out-Null
        $ins.Parameters.AddWithValue("@description", $a.Description) | Out-Null
        $ins.Parameters.AddWithValue("@old_values_json", [object]$a.OldValuesJson) | Out-Null
        $ins.Parameters.AddWithValue("@new_values_json", [object]$a.NewValuesJson) | Out-Null
        $ins.Parameters.AddWithValue("@ip_address", [object]$a.IpAddress) | Out-Null
        $ins.Parameters.AddWithValue("@user_agent", [object]$a.UserAgent) | Out-Null
        $res = $ins.ExecuteNonQuery()
        if ($res -gt 0) { $auditInserted++ }
    }
    Write-Host "  -> Inserted $auditInserted audit logs."

    $tx.Commit()
    Write-Host "SUCCESS: Data merge committed to Azure successfully!"
} catch {
    Write-Error "ERROR occurred: $_"
    $tx.Rollback()
    Write-Host "Transaction rolled back."
    throw $_
} finally {
    $localConn.Close()
    $azureConn.Close()
}