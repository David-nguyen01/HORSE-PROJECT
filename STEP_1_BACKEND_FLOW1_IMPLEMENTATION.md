# STEP 1 — BACKEND IMPLEMENTATION REPORT: FLOW 1 (FR01 & FR02)

**Repository:** SWP Racehorse Training Management System  
**Execution Date:** 2026-09-24  
**Scope:** Backend Only — Flow 1: Horse Profile & Pedigree (FR01, FR02)  
**Status:** COMPLETE / AUTOMATED TESTS PASS (42/42) / READY FOR FRONTEND INTEGRATION  

---

## A. Backend Files Changed

### 1. Modified Existing Files
* [`BACKEND/src/main/java/com/racehorse/horse/controller/HorseController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/horse/controller/HorseController.java)
  * **Role:** Horse master profile REST controller.
  * **Modifications:** 
    * Updated `@PreAuthorize` on `GET /api/horses` and `GET /api/horses/{id}` to allow `VETERINARIAN` and `STABLE_STAFF` along with `CLUB_MANAGER`, `HEAD_TRAINER`, and `HORSE_OWNER`.
    * Updated private helper `isPrivileged(Authentication auth)` to include `VETERINARIAN` and `STABLE_STAFF`, preventing 403 Forbidden for non-owners with profile view privileges.
* [`BACKEND/src/main/java/com/racehorse/media/service/HorseProfileFileService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/media/service/HorseProfileFileService.java)
  * **Role:** Horse avatar/main photo proposal service.
  * **Modifications:** Added `reject(UUID horseId, UUID proposalId, UUID rejecterId)` method to delete the uploaded asset from Cloudinary (via `CloudinaryStorageService`) and transition the proposal status in `dbo.main_file_proposals` to `DELETED`.
* [`BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java)
  * **Role:** Main photo proposal REST controller.
  * **Modifications:** Exposed `POST /api/horses/{horseId}/main-file/proposals/{proposalId}/reject` with `@PreAuthorize("hasAuthority('PERM_PROFILE_EDIT')")` (restricted to `CLUB_MANAGER` and `HEAD_TRAINER`).
* [`BACKEND/src/main/java/com/racehorse/racing/service/RaceResultService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/racing/service/RaceResultService.java)
  * **Role:** Horse racing results business service.
  * **Modifications:** Added `delete(UUID horseId, UUID resultId)` to allow removing race results belonging to a specific horse.
* [`BACKEND/src/main/java/com/racehorse/racing/controller/RaceResultController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/racing/controller/RaceResultController.java)
  * **Role:** Horse racing results REST controller.
  * **Modifications:** Exposed `DELETE /api/horses/{horseId}/race-results/{resultId}` with `@PreAuthorize("hasAuthority('PERM_PROFILE_EDIT')")`.
* [`BACKEND/src/test/java/com/racehorse/media/service/HorseProfileFileServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/media/service/HorseProfileFileServiceTest.java)
  * **Role:** Unit test suite for proposal workflows.
  * **Modifications:** Added `rejectingProposalDeletesCloudinaryAssetAndMarksDeleted()` test case.
* [`BACKEND/src/test/java/com/racehorse/racing/service/RaceResultServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/racing/service/RaceResultServiceTest.java)
  * **Role:** Unit test suite for race results.
  * **Modifications:** Added `deleteRemovesResultWhenFound()` test case.

### 2. Newly Created Files
* [`BACKEND/src/main/java/com/racehorse/media/dto/HorsePhotoResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/media/dto/HorsePhotoResponse.java)
  * **Role:** Response DTO for additional gallery photos (ID, horse ID, file ID, file URL, filename, mime type, file size, caption, display order, uploaded at).
* [`BACKEND/src/main/java/com/racehorse/media/service/HorsePhotoService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/media/service/HorsePhotoService.java)
  * **Role:** Service handling additional gallery photos (up to 10 photos per horse) leveraging `dbo.module_records` and `dbo.stored_files` without violating the 30-table schema limit.
  * **Features:** MIME type validation (JPEG, PNG, WEBP), max 10 photos constraint enforcement, display order validation/assignment (1..10), JSON payload serialization via Jackson 3 (`tools.jackson`), Cloudinary asset deletion upon photo removal.
* [`BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java)
  * **Role:** REST endpoints for gallery photos:
    * `GET /api/horses/{horseId}/photos` (`PERM_PROFILE_VIEW`)
    * `POST /api/horses/{horseId}/photos` (`PERM_PROFILE_EDIT`, multipart/form-data)
    * `DELETE /api/horses/{horseId}/photos/{photoId}` (`PERM_PROFILE_EDIT`)
* [`BACKEND/src/test/java/com/racehorse/media/service/HorsePhotoServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/media/service/HorsePhotoServiceTest.java)
  * **Role:** Unit test suite for `HorsePhotoService` covering:
    * Rejecting uploads exceeding 10 photos limit.
    * Rejecting non-image MIME types (e.g. `application/pdf`).
    * Automatic display order assignment.
    * Photo deletion and Cloudinary asset cleanup.
    * Photo not found handling.

### 3. Frontend Boundary Notice
* **Frontend Files Changed:** `0` (Strictly untouched in accordance with constraints).

---

## B. FR01 Implementation (Horse Master Profile)

| Feature | Requirement | Implementation Status | Implementation Details |
| :--- | :--- | :--- | :--- |
| **Profile CRUD** | Create, Read, Update, Delete horse profile | **COMPLETE** | `HorseService` and `HorseController` provide `POST /api/horses`, `GET /api/horses`, `GET /api/horses/{id}`, `PUT /api/horses/{id}`, and `DELETE /api/horses/{id}`. |
| **Profile Attributes** | Microchip, Registration No, Name, Breed, DOB, Gender, Color, Owner, Status | **COMPLETE** | Stored in `dbo.horses` with validation on unique microchip and registration number. |
| **Ownership Filter** | `HORSE_OWNER` can only see their horses; Trainers/Managers/Staff see all active | **COMPLETE** | Enforced at query and service level (`findByOwnerId` for owners; full list for privileged staff). |
| **Main Photo Proposals** | Upload proposal, Approve, Reject proposal | **COMPLETE** | Workflow supported: Owners/Trainers upload proposal (`POST .../main-file/proposals`). Managers/Head Trainers can approve (`POST .../approve`) or reject (`POST .../reject`). Rejected proposals delete asset from Cloudinary and mark row as `DELETED`. |
| **Gallery Photos** | Up to 10 additional photos with captions & ordering | **COMPLETE** | Implemented in `HorsePhotoService` + `HorsePhotoController`. Leverages `dbo.module_records` (`module_code='PROFILE'`, `record_type='HORSE_ADDITIONAL_FILE'`) and `dbo.stored_files`. Max 10 limit strictly validated. |

---

## C. FR02 Implementation (Pedigree & Racing History)

| Feature | Requirement | Implementation Status | Implementation Details |
| :--- | :--- | :--- | :--- |
| **3-Generation Pedigree** | Tree representation showing Sire & Dam up to 3 generations | **COMPLETE** | `PedigreeService.getPedigree(UUID horseId)` constructs recursive 3-generation tree (`PedigreeNodeDto` containing root, sire, dam, and grandparents). |
| **Lineage Cycle Prevention** | Prevent horse from being its own ancestor or recursive cycle | **COMPLETE** | `HorseService.validatePedigreeLineage(UUID horseId, UUID sireId, UUID damId)` verifies ancestor graph using BFS to prevent circular references before insert/update. |
| **Racing History CRUD** | Add, list, and delete racing performance records | **COMPLETE** | `RaceResultController` & `RaceResultService` provide `GET /api/horses/{horseId}/race-results`, `POST /api/horses/{horseId}/race-results`, and `DELETE /api/horses/{horseId}/race-results/{resultId}`. |
| **Racing Statistics** | Calculate total starts, 1st/2nd/3rd, win rate, place rate, total earnings | **COMPLETE** | Calculated dynamically in `RaceResultService.getSummary(UUID horseId)` and returned in `RaceResultSummaryDto`. |

---

## D. RBAC Changes

Alignment between SQL permission seeds (`V001__init.sql`, `V003__seed_rbac.sql`) and Controller `@PreAuthorize` rules:

| Endpoint | Method | Authority Required | Allowed Roles | Change Rationale |
| :--- | :--- | :--- | :--- | :--- |
| `/api/horses` | `GET` | `hasAuthority('PERM_PROFILE_VIEW')` | `CLUB_MANAGER`, `HEAD_TRAINER`, `HORSE_OWNER`, `VETERINARIAN`, `STABLE_STAFF` | **Fixed**: Added `VETERINARIAN` and `STABLE_STAFF` who already had `PROFILE:VIEW` in DB seeds but were getting 403 Forbidden. |
| `/api/horses/{id}` | `GET` | `hasAuthority('PERM_PROFILE_VIEW')` | `CLUB_MANAGER`, `HEAD_TRAINER`, `HORSE_OWNER` (if owner), `VETERINARIAN`, `STABLE_STAFF` | **Fixed**: Updated `isPrivileged` to include vet and staff. |
| `/api/horses` | `POST` | `hasAuthority('PERM_PROFILE_EDIT')` | `CLUB_MANAGER`, `HEAD_TRAINER` | Unchanged (restricted to management). |
| `/api/horses/{id}` | `PUT` | `hasAuthority('PERM_PROFILE_EDIT')` | `CLUB_MANAGER`, `HEAD_TRAINER` | Unchanged (restricted to management). |
| `/api/horses/{id}` | `DELETE` | `hasAuthority('PERM_PROFILE_EDIT')` | `CLUB_MANAGER` | Unchanged. |
| `/api/horses/{id}/main-file/proposals` | `POST` | `PERM_PROFILE_PROPOSE` | `HORSE_OWNER`, `HEAD_TRAINER` | Unchanged. |
| `/api/horses/{id}/main-file/proposals/{pid}/approve` | `POST` | `PERM_PROFILE_EDIT` | `CLUB_MANAGER`, `HEAD_TRAINER` | Unchanged. |
| `/api/horses/{id}/main-file/proposals/{pid}/reject` | `POST` | `PERM_PROFILE_EDIT` | `CLUB_MANAGER`, `HEAD_TRAINER` | **New**: Allows managers/trainers to reject proposed photos. |
| `/api/horses/{id}/photos` | `GET` | `PERM_PROFILE_VIEW` | All authorized roles with `PROFILE:VIEW` | **New**: Gallery photo viewing. |
| `/api/horses/{id}/photos` | `POST` | `PERM_PROFILE_EDIT` | `CLUB_MANAGER`, `HEAD_TRAINER` | **New**: Gallery photo upload. |
| `/api/horses/{id}/photos/{photoId}` | `DELETE` | `PERM_PROFILE_EDIT` | `CLUB_MANAGER`, `HEAD_TRAINER` | **New**: Gallery photo deletion. |
| `/api/horses/{id}/race-results` | `GET` | `PERM_PROFILE_VIEW` | All authorized roles with `PROFILE:VIEW` | Unchanged. |
| `/api/horses/{id}/race-results` | `POST` | `PERM_PROFILE_EDIT` | `CLUB_MANAGER`, `HEAD_TRAINER` | Unchanged. |
| `/api/horses/{id}/race-results/{rid}` | `DELETE` | `PERM_PROFILE_EDIT` | `CLUB_MANAGER`, `HEAD_TRAINER` | **New**: Allows deletion of race result records. |

---

## E. Database Changes

* **Schema Migrations:** **NONE (0 schema changes)**.
* **Table Count Constraint:** The system strictly maintains the 30-table maximum defined in `dbo.system_schema_version`.
* **Design Decision for Gallery Photos:**
  * Reused `dbo.module_records` with:
    * `module_code = 'PROFILE'`
    * `record_type = 'HORSE_ADDITIONAL_FILE'`
    * `entity_id = horseId`
    * `file_id = storedFile.id` (FK to `dbo.stored_files`)
    * `payload_json = {"caption": "...", "displayOrder": 1}`
  * Reused `dbo.stored_files` for metadata and Cloudinary URL references.
  * Reused `dbo.main_file_proposals` with status `DELETED` for rejected proposals.

---

## F. API Contract Changes

### 1. Reject Main Photo Proposal
* **URL:** `POST /api/horses/{horseId}/main-file/proposals/{proposalId}/reject`
* **Headers:** `Authorization: Bearer <token>`
* **Permission:** `PERM_PROFILE_EDIT` (`CLUB_MANAGER`, `HEAD_TRAINER`)
* **Request Body:** None
* **Success Response:** `200 OK`
  ```json
  {
    "id": "7fa85f64-5717-4562-b3fc-2c963f66afa6",
    "horseId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "proposedById": "2ba85f64-5717-4562-b3fc-2c963f66afa6",
    "proposedByName": "John Doe",
    "fileId": "4da85f64-5717-4562-b3fc-2c963f66afa6",
    "fileUrl": "https://res.cloudinary.com/...",
    "status": "DELETED",
    "reviewNote": null,
    "reviewedById": "1ea85f64-5717-4562-b3fc-2c963f66afa6",
    "reviewedAt": "2026-09-24T13:00:00Z",
    "createdAt": "2026-09-24T12:00:00Z"
  }
  ```
* **Error Responses:**
  * `400 Bad Request`: Proposal is not in `PENDING` status.
  * `403 Forbidden`: User lacks `PERM_PROFILE_EDIT`.
  * `404 Not Found`: Proposal or horse not found.

---

### 2. List Additional Gallery Photos
* **URL:** `GET /api/horses/{horseId}/photos`
* **Headers:** `Authorization: Bearer <token>`
* **Permission:** `PERM_PROFILE_VIEW` (`CLUB_MANAGER`, `HEAD_TRAINER`, `HORSE_OWNER`, `VETERINARIAN`, `STABLE_STAFF`)
* **Success Response:** `200 OK`
  ```json
  [
    {
      "id": "8ca85f64-5717-4562-b3fc-2c963f66afa6",
      "horseId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "fileId": "4da85f64-5717-4562-b3fc-2c963f66afa6",
      "fileUrl": "https://res.cloudinary.com/...",
      "originalFilename": "gallop_side.jpg",
      "mimeType": "image/jpeg",
      "fileSizeBytes": 2048576,
      "caption": "Side gallop view during morning drill",
      "displayOrder": 1,
      "uploadedAt": "2026-09-24T12:30:00Z"
    }
  ]
  ```

---

### 3. Upload Additional Gallery Photo
* **URL:** `POST /api/horses/{horseId}/photos`
* **Headers:** `Authorization: Bearer <token>`, `Content-Type: multipart/form-data`
* **Permission:** `PERM_PROFILE_EDIT` (`CLUB_MANAGER`, `HEAD_TRAINER`)
* **Request Form Parts:**
  * `file`: MultipartFile (Image file: JPEG, PNG, WEBP, max 10MB)
  * `caption`: String (optional, max 255 chars)
  * `displayOrder`: Integer (optional, 1..10)
* **Success Response:** `201 Created`
  ```json
  {
    "id": "8ca85f64-5717-4562-b3fc-2c963f66afa6",
    "horseId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "fileId": "4da85f64-5717-4562-b3fc-2c963f66afa6",
    "fileUrl": "https://res.cloudinary.com/...",
    "originalFilename": "gallop_side.jpg",
    "mimeType": "image/jpeg",
    "fileSizeBytes": 2048576,
    "caption": "Side gallop view during morning drill",
    "displayOrder": 1,
    "uploadedAt": "2026-09-24T12:30:00Z"
  }
  ```
* **Error Responses:**
  * `400 Bad Request`: Maximum of 10 photos reached; invalid MIME type (only JPEG, PNG, WEBP allowed); display order not in range 1..10.
  * `404 Not Found`: Horse not found.

---

### 4. Delete Additional Gallery Photo
* **URL:** `DELETE /api/horses/{horseId}/photos/{photoId}`
* **Headers:** `Authorization: Bearer <token>`
* **Permission:** `PERM_PROFILE_EDIT` (`CLUB_MANAGER`, `HEAD_TRAINER`)
* **Success Response:** `204 No Content`
* **Error Responses:**
  * `404 Not Found`: Photo or horse not found.

---

### 5. Delete Race Result
* **URL:** `DELETE /api/horses/{horseId}/race-results/{resultId}`
* **Headers:** `Authorization: Bearer <token>`
* **Permission:** `PERM_PROFILE_EDIT` (`CLUB_MANAGER`, `HEAD_TRAINER`)
* **Success Response:** `204 No Content`
* **Error Responses:**
  * `404 Not Found`: Race result or horse not found.

---

## G. Tests Executed & Results

Executed test suite command: `mvn test` in `BACKEND/`.

### Test Summary
```
[INFO] Results:
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Detailed Breakdown of Flow 1 Test Coverage
1. **`HorseServiceTest`** (5 tests - PASS):
   * `createHorse_savesAndReturnsDto`
   * `getHorseById_returnsDtoWhenExists`
   * `getHorseById_throwsWhenNotFound`
   * `listHorses_returnsFilteredList`
   * `validatePedigreeLineage_detectsCircularAncestry`
2. **`PedigreeServiceTest`** (1 test - PASS):
   * `getPedigree_constructsThreeGenerationTree`
3. **`HorseProfileFileServiceTest`** (5 tests - PASS):
   * `proposingMainFileCreatesPendingProposal`
   * `approvingProposalUpdatesHorseAndSetsStoredFile`
   * `approvingNonPendingProposalFails`
   * `rejectingProposalDeletesCloudinaryAssetAndMarksDeleted` *(NEW)*
   * `gettingActiveMainFileReturnsFileResponse`
4. **`ProfileFileValidatorTest`** (3 tests - PASS):
   * `validJpegPasses`
   * `oversizedFileFails`
   * `invalidMimeTypeFails`
5. **`HorsePhotoServiceTest`** (5 tests - PASS, *NEW*):
   * `uploadPhotoExceedingMaxLimitThrowsException`
   * `uploadPhotoNonImageFails`
   * `uploadPhotoSuccessAssignsAutoOrder`
   * `deletePhotoRemovesRecordAndCloudinaryAsset`
   * `deletePhotoNotFoundThrowsException`
6. **`RaceResultServiceTest`** (2 tests - PASS):
   * `getSummaryCalculatesWinAndPlaceRates`
   * `deleteRemovesResultWhenFound` *(NEW)*

---

## H. Remaining Backend Gaps

* **Flow 1 (FR01 & FR02):** **0 Gaps Remaining.**
  * All CRUD, Pedigree 3-generation tree, cycle prevention, main photo proposal approve/reject, gallery photos (max 10), and race results CRUD + summary calculation are fully implemented and automated-tested.
* **Subsequent Scope (Flow 2 & Flow 3):**
  * Flow 2 (FR03–FR07: Training Plan & Execution) and Flow 3 (FR08–FR12: Medical & Injury) remain for subsequent steps.

---

## I. Blocked / Unspecified Items

1. **Cloudinary Production Credentials:**
   * Automated tests use Mockito mocks for `CloudinaryStorageService`.
   * Real runtime photo uploads depend on environment variables (`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`). If not supplied, standard fallback / configuration exception applies.
2. **Display Order Collision Resolution:**
   * When `displayOrder` is omitted during upload, the backend auto-assigns `max(displayOrder) + 1`. If an explicit `displayOrder` is provided, it must be between 1 and 10.

---

## J. SRS Compliance Status

| Requirement ID | Name | Backend Implementation Status |
| :--- | :--- | :--- |
| **FR01** | Horse Master Profile | **COMPLETE** |
| **FR02** | Pedigree & Racing History | **COMPLETE** |
| **RBAC** | Flow 1 Permissions | **ALIGNED & COMPLETE** |
| **Database Contract** | 30-table ceiling preservation | **VERIFIED & COMPLIANT** |

*Flow 1 Backend is ready for frontend integration.*
