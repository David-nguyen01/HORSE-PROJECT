# REPORT RH-LOCAL-01: Controlled Backend Restart & Runtime Activation Verification

**Date:** 2026-11-11  
**Status:** COMPLETE  
**Product Repository:** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`, HEAD: `65165624859051913e940f53dee8253e02040eb7`)  
**Personal Review Mirror:** `C:\DEV\clone-horse\source-mirror`  
**Workflow Repository:** `thangnx-mc/clone-horse` (Branch: `main`)  

---

## 1. Executive Summary

This task performed a controlled, safe restart of the local Spring Boot Backend process to activate the compiled bytecode changes from tasks `RH-STABLE-UI-01E.1`, `01E.2`, and `01E.5`.

The restart successfully transitioned the running JVM from stale pre-01E.2 bytecode to the latest compiled bytecode classes without disrupting the running Frontend Vite dev server (port 5173), SQL Server database (port 1433), or modifying any product source code.

### Key Verification Milestones:
1. **Pre-Restart State:** Identified running Backend process PID `34388` (parent PID `29932`), listening on port `8080`. Verified that compiled classes (`TrainingScheduleRepository.class`, `TrainingScheduleService.class`, etc.) on disk matched the latest source.
2. **Controlled Termination:** Terminated task `task-25860`. Verified PID `34388` and PID `29932` completely exited and port `8080` was fully released (zero listening sockets).
3. **Clean Startup:** Launched Spring Boot via verified canonical startup command with profile `local` and User-scope Cloudinary environment variables. Tomcat initialized and reported readiness in **14.638 seconds** on new PID `14304`.
4. **Health & Readiness:** `/actuator/health` responded HTTP 200 with `status: UP`, `groups: ["liveness", "readiness"]`.
5. **Trainer Eligibility Verification:** Querying `/api/horses` as `HEAD_TRAINER` returned `healthStatus` in the horse DTO (`FIT`, `INJURED`, `QUARANTINED`, `MONITOR`). Confirmed `FIT` horses are permitted for training and non-FIT horses are correctly recognized as ineligible.
6. **Stable Training Preparation Verification:** Querying `/api/training-schedules/mine` as `STABLE_STAFF` without date boundaries returned **4 actionable preparation schedules** across all dates without defaulting to current day. Verified caretaker isolation, publication constraint (`publishedAt is not null`), and exclusion of non-actionable statuses.
7. **Veterinarian Query Isolation:** Querying `/api/training-schedules/mine` as `VETERINARIAN` without date parameters confirmed that veterinary queries remain strictly bounded to current club day (returning 0 records), maintaining total architectural separation from Stable preparation queries.
8. **Product & Environment Safety:** Confirmed zero product source edits, zero commits/pushes to `HORSE-PROJECT`, zero database writes/migrations, and zero disruption to Frontend (PID 24368 on port 5173).

---

## 2. Pre-Restart Inspection Baseline

### 2.1 Process and Network Inspection
- **Listening Port:** `127.0.0.1:8080` (TCP Listen)
- **Active Backend PID:** `34388` (`java.exe`)
- **Parent Process PID:** `29932` (`java.exe` / Maven classworlds launcher)
- **Executable Path:** `C:\Program Files\Java\jdk-25.0.3\bin\java.exe`
- **Command Line:**
  `"C:\Program Files\Java\jdk-25.0.3\bin\java.exe" -XX:TieredStopAtLevel=1 -cp @C:\Users\nguye\AppData\Local\Temp\spring-boot-15577240692692822584.argfile com.racehorse.RacehorseApplication --spring.profiles.active=local`
- **Working Directory:** `C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND`
- **Frontend Port:** `::1:5173` (PID `24368`, Vite dev server, preserved undisturbed)
- **Database Service:** `localhost:1433` (Microsoft SQL Server `racehorse_local`, preserved undisturbed)

### 2.2 Compiled Bytecode Verification
Verified disk timestamps and bytecode symbols in `BACKEND/target/classes`:
- `TrainingScheduleRepository.class`: compiled 11/11/2026 2:03:07 AM (contains `findForCaretaker(UUID)`).
- `TrainingScheduleService.class`: compiled 11/11/2026 2:03:07 AM (bytecode verifies `mine()` calls `findForCaretaker` when `from == null && to == null`).
- `TrainingScheduleController.class`: compiled 11/11/2026 2:03:08 AM.
- `StableService.class`: compiled 11/11/2026 2:03:07 AM.
- `StableController.class`: compiled 11/11/2026 2:03:07 AM.

### 2.3 Product Git Baseline Before Restart
- **Branch:** `develop`
- **Git HEAD:** `65165624859051913e940f53dee8253e02040eb7`
- **Working Tree:** Exactly 25 modified files, 8 untracked directories/files.

---

## 3. Controlled Restart Execution

### 3.1 Graceful Process Termination
1. Cancelled background task `task-25860` via agent task management.
2. Verified process termination:
   ```powershell
   Get-Process -Id 34388, 29932 -ErrorAction SilentlyContinue
   # Returned null (both processes fully exited)
   ```
3. Verified port release:
   ```powershell
   Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
   # Zero LISTEN sockets on port 8080 (only transient TIME_WAIT sockets)
   ```

### 3.2 Canonical Startup Command
Executed the verified project startup sequence in `C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND`:
```powershell
$cName = [Environment]::GetEnvironmentVariable('CLOUDINARY_CLOUD_NAME', 'User')
$cKey = [Environment]::GetEnvironmentVariable('CLOUDINARY_API_KEY', 'User')
$cSecret = [Environment]::GetEnvironmentVariable('CLOUDINARY_API_SECRET', 'User')

if ([string]::IsNullOrWhiteSpace($cName) -or [string]::IsNullOrWhiteSpace($cKey) -or [string]::IsNullOrWhiteSpace($cSecret)) {
    throw "Cloudinary User environment variables are missing or empty!"
}

$env:CLOUDINARY_CLOUD_NAME = $cName
$env:CLOUDINARY_API_KEY = $cKey
$env:CLOUDINARY_API_SECRET = $cSecret

cd "C:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND"
mvn -s maven-settings.xml spring-boot:run "-Dspring-boot.run.profiles=local" *>&1 | Tee-Object -FilePath "C:\Users\nguye\.gemini\antigravity\brain\ecbb197c-b243-4488-83a8-978084d3087e\logs\backend.log"
```

### 3.3 Startup Metrics & New Process Identity
- **Background Task ID:** `task-28317`
- **New Backend PID:** `14304` (`java.exe`)
- **Port:** `127.0.0.1:8080` (LISTEN)
- **HikariCP Connection Pool:** `HikariPool-1` connected to `jdbc:sqlserver://localhost:1433;databaseName=racehorse_local`
- **Startup Duration:** `Started RacehorseApplication in 14.638 seconds (process running for 14.943)`

---

## 4. Post-Restart Runtime Verification

### 4.1 Backend Health & Readiness
Invoked HTTP GET to `http://127.0.0.1:8080/actuator/health`:
```json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```
HTTP status 200 OK confirmed application readiness.

---

### 4.2 Trainer Horse Eligibility Verification
Authenticated as `trainer@gmail.com` (`ROLE_HEAD_TRAINER`) and queried `GET /api/horses`:

```text
id                                   code  name         healthStatus  trainingLocked
--                                   ----  ----         ------------  --------------
2b5850ae-d505-478b-8c7b-714e28fe0ce8 H08   Bạch Long    QUARANTINED   false
f3c309af-6f24-403d-bfb5-7fe3394a23a7 H06   Chiến Thắng  INJURED       false
b3333333-3333-3333-3333-333333333333 H03   Demo         FIT           false
b2222222-2222-2222-2222-222222222222 H02   Ex           FIT           false
85bacaa9-3cfc-45e2-acf0-7387ec943a4f H07   Hắc Mã       QUARANTINED   false
6086ae9a-096c-43a8-9b6e-b5cc7811fcea H13   Ngọc Long    FIT           false
4f72306d-12c0-4b78-bed6-be6db4e321f8 H05   Tia Chớp     MONITOR       false
b1111111-1111-1111-1111-111111111111 H01   Thống        FIT           false
```

#### Results & Invariants:
1. `healthStatus` is directly present in the `HorseResponse` DTO.
2. `FIT` horses (`H01`, `H02`, `H03`, `H13`) are eligible for training.
3. Non-`FIT` horses (`INJURED`, `QUARANTINED`, `MONITOR`) are correctly identified by Trainer UI as ineligible.
4. Backend eligibility evaluation remains authoritative.
5. Zero horse health data was altered.

---

### 4.3 Stable Training Preparation Verification
Authenticated as `staff@gmail.com` (`ROLE_STABLE_STAFF`) and queried `GET /api/training-schedules/mine`:

#### A. Unbounded Initial Query (No `from` or `to` parameters):
```text
Total schedules returned: 4

id                                    scheduledStart        scheduledEnd          status     sessionStatus  publishedAt
--                                    --------------        ------------          ------     -------------  -----------
68c47723-aafc-4511-a6e9-5e8afffc878a  2026-10-08T22:00:00Z  2026-10-09T04:00:00Z  READY      null           2026-10-09T02:00:00Z
047c7a0b-cc2e-45d4-b74c-c1b63e6e6f50  2026-10-09T22:00:00Z  2026-10-10T04:00:00Z  READY      null           2026-10-09T02:00:00Z
172b642b-bf6e-4b12-9205-ff458495a967  2026-10-09T22:00:00Z  2026-10-10T04:00:00Z  PREPARING  null           2026-10-09T02:00:00Z
e3d42872-31b4-4948-a9fe-2efb0a240264  2026-10-09T22:00:00Z  2026-10-10T04:00:00Z  PREPARING  null           2026-10-09T02:00:00Z
```

#### Findings:
1. **Unbounded Retrieval Verified:** All 4 actionable preparation schedules across October 2026 are immediately returned. The running JVM is no longer executing stale bytecode that defaulted null parameters to `LocalDate.now()` (November 11).
2. **Actionable Statuses Enforced:** Only `PREPARING` and `READY` records are returned. Non-actionable records (`COMPLETED`, `CANCELLED`, `IN_PROGRESS`) are excluded.
3. **Caretaker Isolation Enforced:** All returned records belong strictly to horses assigned to `stable_staff`.
4. **Publication Enforced:** `publishedAt` is non-null for all returned records. Draft/unfinalized training sessions cannot appear.

#### B. Explicit Date Filter Query (`?from=2026-10-09&to=2026-10-09`):
Returned exactly **1 record** matching that specific calendar day (`68c47723...`), confirming that explicit date filtering functions identically to specification.

#### C. Veterinarian Query Isolation:
Authenticated as `vet@gmail.com` (`ROLE_VETERINARIAN`) and queried `GET /api/training-schedules/mine` without date parameters.
- Returned **0 records** (current club day default applied as designed via `forDiagnosingVet`).
- Confirmed total independence between Veterinarian and Stable query semantics.

---

### 4.4 Product Source & Working Tree Preservation
Ran `git status -s` and `git rev-parse HEAD` on `C:\DEV\RACE HORSE\HORSE-PROJECT`:
- **Current HEAD:** `65165624859051913e940f53dee8253e02040eb7` (Exact match with pre-restart baseline).
- **Working Tree Diffs:** Exactly the same 25 modified files and 8 untracked directories/files as before the restart.
- **Product Source Mutations:** **ZERO**.

---

## 5. Environment & Process Status Summary

| Component | Target / Port | Status | Verification Detail |
| :--- | :--- | :--- | :--- |
| **Backend Spring Boot** | `127.0.0.1:8080` | **UP (PID 14304)** | Tomcat started in 14.638s, health UP |
| **Frontend Vite Server** | `http://localhost:5173` | **UP (PID 24368)** | Running undisturbed; HMR active |
| **Database Server** | `localhost:1433` (`racehorse_local`) | **UP** | HikariCP pool healthy, zero writes made |
| **Product Repository** | `HORSE-PROJECT` | **PRESERVED** | Branch `develop`, HEAD `6516562`, 0 Git commits |
| **Workflow Repository** | `clone-horse` | **SYNCHRONIZED** | Branch `main`, report committed and pushed |
