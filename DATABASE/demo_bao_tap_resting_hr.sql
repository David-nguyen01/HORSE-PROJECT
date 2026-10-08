/* SQL Server local demo: Bão Táp's resting heart rate = 36 BPM.
   These are independent statements for SQL editors that split scripts at semicolons.
   Change 36 below before the first run if a real resting measurement is available.
   Re-running does not add duplicate telemetry sessions or readings.
   RESTING is a calm baseline, not the heart rate one minute after training. */

INSERT INTO racehorse.dbo.telemetry_sessions
    (id, horse_id, training_schedule_id, started_at, ended_at,
     recorded_by_user_id, entry_method, measurement_context)
SELECT 'C9A4AB1B-88D9-49E7-977E-36566FBA0A55', h.id, NULL,
       SYSDATETIMEOFFSET(), NULL, u.id, 'MANUAL', 'RESTING'
FROM racehorse.dbo.horses h
JOIN racehorse.dbo.users u ON u.id = h.diagnosing_vet_user_id
WHERE h.id = '6FCD5749-F534-430F-A43F-93C805F11C82'
  AND h.code = 'LOCAL-HT-001'
  AND h.name = N'Bão Táp'
  AND u.email = 'doctor@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM racehorse.dbo.telemetry_sessions
      WHERE id = 'C9A4AB1B-88D9-49E7-977E-36566FBA0A55'
  );

INSERT INTO racehorse.dbo.telemetry_readings
    (telemetry_session_id, recorded_at, heart_rate_bpm, speed_kmh)
SELECT ts.id, SYSDATETIMEOFFSET(), 36, NULL
FROM racehorse.dbo.telemetry_sessions ts
JOIN racehorse.dbo.horses h ON h.id = ts.horse_id
JOIN racehorse.dbo.users u ON u.id = ts.recorded_by_user_id
WHERE ts.id = 'C9A4AB1B-88D9-49E7-977E-36566FBA0A55'
  AND h.id = '6FCD5749-F534-430F-A43F-93C805F11C82'
  AND ts.measurement_context = 'RESTING'
  AND u.email = 'doctor@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM racehorse.dbo.telemetry_readings
      WHERE telemetry_session_id = 'C9A4AB1B-88D9-49E7-977E-36566FBA0A55'
  );

SELECT h.name, tr.heart_rate_bpm AS resting_heart_rate_bpm,
       tr.recorded_at, ts.measurement_context, u.email AS recorded_by
FROM racehorse.dbo.telemetry_readings tr
JOIN racehorse.dbo.telemetry_sessions ts ON ts.id = tr.telemetry_session_id
JOIN racehorse.dbo.horses h ON h.id = ts.horse_id
JOIN racehorse.dbo.users u ON u.id = ts.recorded_by_user_id
WHERE ts.id = 'C9A4AB1B-88D9-49E7-977E-36566FBA0A55';
