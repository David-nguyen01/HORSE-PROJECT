ALTER TABLE dbo.module_records
ADD race_event_id_str AS CAST(JSON_VALUE(data_json, '$.race_event_id') AS VARCHAR(36));
GO

CREATE UNIQUE NONCLUSTERED INDEX uq_module_records_race_registration
ON dbo.module_records (horse_id, race_event_id_str)
WHERE record_type = 'RACE_REGISTRATIONS' AND status != 'WITHDRAWN';
GO
