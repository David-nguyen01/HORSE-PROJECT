# TASK RH-STABLE-TASK-MANAGEMENT-06: Club Manager UI Terminology & Layout Refinement Report

**Date:** 2026-11-11  
**Author:** Antigravity  
**Task ID:** `RH-STABLE-TASK-MANAGEMENT-06`  
**Repository (Product Source):** `C:\DEV\RACE HORSE\HORSE-PROJECT` (Branch: `develop`)  
**Repository (Workflow & Mirror):** `C:\DEV\clone-horse` (Branch: `main`)  
**Deliverable Document:** `docs/RH_STABLE_TASK_MANAGEMENT_06_ADMIN_UI_TERMINOLOGY_REFINEMENT_REPORT.md`  
**Remote Up-Report:** `thangnx-mc/clone-horse/up-report/RH_STABLE_TASK_MANAGEMENT_06_ADMIN_UI_TERMINOLOGY_REFINEMENT_REPORT.md`

---

## 1. Executive Summary

| Attribute | State | Details |
|---|---|---|
| **Task Status** | **COMPLETE** | Layout deduplication, terminology naturalization, and helper text enhancements implemented |
| **Product Source Modified** | **YES** | Refined `TaskCatalogPage.jsx` and updated test suite `taskCatalog.test.js` |
| **Backend Modified** | **NO** | Zero modifications to Java code, REST controllers, or service logic |
| **Database Modified** | **NO** | Zero DDL migrations, DML writes, or schema alterations |
| **Backend / Frontend Restart** | **NO** | Zero process restarts performed; Vite HMR automatically refreshed presentation |
| **Report Push** | **SUCCESS** | Pushed to `thangnx-mc/clone-horse` under `up-report/` |

---

## 2. Layout Refinement: Removal of Duplicate Add Button

### 2.1 The Issue
On `/admin/care-tasks`, an Add button ("Thêm nhiệm vụ dự trữ") was previously rendered in the top-right page heading actions bar next to the "Làm mới" (Refresh) button. This created visual clutter and confusion alongside the tab-specific workflow.

### 2.2 The Solution
1. **Removed from Top-Right Header:** In `page-heading`, the action bar now exclusively contains the Refresh button (`btn-refresh-catalog`).
2. **Preserved inside Reserve Section:** The primary creation button (`btn-add-reserve-task`) is positioned cleanly in the section header of the Reserve tab ("Kho nhiệm vụ"), immediately adjacent to the section title and description.
3. **Empty State Button Preserved:** When the Reserve section is empty, `btn-empty-add-reserve` remains available in the empty state card to guide the manager.
4. **Active Tab Strict Invariant Maintained:** The Active List ("Danh sách đang áp dụng") strictly has **NO** Add button, preserving the core business invariant that new definitions cannot be created directly into the active daily rotation.

---

## 3. Comprehensive Terminology Naturalization: Removal of "Dự trữ"

Every user-facing occurrence of the term "dự trữ" in `TaskCatalogPage.jsx` has been replaced with natural Vietnamese phrasing:

| Location | Prior Wording (with "dự trữ") | Refined Natural Wording |
|---|---|---|
| **Page Subtitle** | `... giữa danh sách đang áp dụng và kho dự trữ.` | `... giữa danh sách đang áp dụng và kho nhiệm vụ.` |
| **KPI Metric Card** | `Kho nhiệm vụ dự trữ (Reserve)` | `Kho nhiệm vụ` |
| **KPI Subtext** | `Quy trình dự phòng sẵn sàng kích hoạt` | `Quy trình sẵn sàng kích hoạt để áp dụng` |
| **Navigation Tab 2** | `Kho nhiệm vụ dự trữ (Reserve Library)` | `Kho nhiệm vụ` |
| **Active Empty State** | `... chuyển các quy trình từ Kho dự trữ sang để áp dụng.` | `... chuyển các quy trình từ Kho nhiệm vụ sang để áp dụng.` |
| **Reserve Section Header** | *(Implicit from tab)* | `Danh sách nhiệm vụ trong kho` |
| **Reserve Section Subtext** | *(N/A)* | `Các quy trình sẵn sàng kích hoạt để áp dụng cho nhân viên Stable.` |
| **Reserve Add Button** | `Thêm nhiệm vụ dự trữ mới` | `Thêm nhiệm vụ mới` |
| **Reserve Empty Title** | `Kho nhiệm vụ dự trữ hiện đang trống` | `Kho nhiệm vụ hiện đang trống` |
| **Reserve Empty Subtext** | `Thư viện dự phòng dùng để lưu trữ các quy trình chuẩn bị...` | `Kho nhiệm vụ dùng để lưu trữ các quy trình chuẩn bị, quy trình theo mùa hoặc dự thảo nhiệm vụ mới.` |
| **Reserve Status Badge** | `DỰ TRỮ` | `TRONG KHO` |
| **Transfer to Reserve Button** | `Chuyển vào dự trữ` (Title: `... vào Kho dự trữ`) | `Chuyển về kho` (Title: `... vào Kho nhiệm vụ`) |
| **Create Modal Title** | `Tạo nhiệm vụ dự trữ mới` | `Tạo nhiệm vụ mới` |
| **Create Modal Subtext** | `Nhiệm vụ mới sẽ được thêm vào Kho nhiệm vụ dự trữ (Reserve Library).` | `Nhiệm vụ mới sẽ được lưu vào Kho nhiệm vụ.` |
| **Create Modal Notice** | `... nhiệm vụ mới luôn được tạo vào Kho dự trữ.` | `... nhiệm vụ mới luôn được tạo vào Kho nhiệm vụ.` |
| **Create Modal Submit** | `Lưu vào Kho dự trữ` | `Lưu vào Kho nhiệm vụ` |
| **Success Toast (Create)** | `Đã tạo mới nhiệm vụ dự trữ "${savedTask.title}".` | `Đã tạo mới nhiệm vụ "${savedTask.title}".` |
| **Success Toast (Transfer)** | `Đã chuyển "${task.title}" vào Kho nhiệm vụ dự trữ.` | `Đã chuyển "${task.title}" vào Kho nhiệm vụ.` |

> **Forensic Audit:** Case-insensitive search `/dự\s*trữ/i` on `TaskCatalogPage.jsx` returns **0 occurrences**.

---

## 4. Sort Order Field Clarification

### 4.1 Field Enhancement
In `TaskDefinitionModal`:
- Kept the existing numeric field `Thứ tự hiển thị` (`sortOrder`).
- Added the concise helper text directly below the input:
  > *"Số nhỏ hơn sẽ được hiển thị trước trong danh sách. Không ảnh hưởng đến giờ thực hiện nhiệm vụ."*

### 4.2 Technical Invariant
- Backend contract (`sort_order INT NOT NULL DEFAULT 100`) and ordering logic (`sort_order ASC, scheduled_time ASC`) remain completely unchanged.

---

## 5. Preservation of Architecture & Semantics

1. **State & API Contracts:**
   - Internal state `currentTab === 'RESERVE'` and `currentTab === 'ACTIVE'` unchanged.
   - API calls `careRoutineApi.getActive()`, `careRoutineApi.getReserve()`, `create`, `update`, `moveToActive`, `moveToReserve`, and `delete` remain intact.
   - Creation payload continues to strictly pass `isActive: false`.
2. **Stable Staff UI & Workflows:**
   - No modifications to `StableCarePage.jsx`.
   - Stable daily task generation, completion, and read-only active catalog view continue to operate seamlessly.
3. **Role Authorization:**
   - `/admin/care-tasks` remains strictly guarded for `ROLE_CLUB_MANAGER`.

---

## 6. Verification & Automated Test Results

### 6.1 Frontend Test Suite
Updated `FRONTEND/src/taskCatalog.test.js` to assert the refined layout and terminology:
- Test 3 asserts `TRONG KHO` badge and `Kho nhiệm vụ` tab label.
- Test 4 asserts zero Add buttons in `heading-actions`.
- Test 5 asserts Add button presence in Reserve section header and empty state.
- Test 7 asserts `Chuyển về kho` transfer button.
- Test 10 asserts `Kho nhiệm vụ hiện đang trống` empty state.
- Test 12 asserts the exact `Thứ tự hiển thị` helper description and verifies zero occurrences of `dự trữ` in `TaskCatalogPage.jsx`.

**Test Output:**
```
cd FRONTEND; npm.cmd test
✔ 88 tests passed across 0 test suites (0 failures, 179ms)
```

### 6.2 Production Build Verification
```
cd FRONTEND; npm.cmd run build
✓ 3202 modules transformed.
✓ built in 918ms without errors or warnings.
```

---

## 7. Runtime Status & Next Steps

- **Frontend:** Changes immediately live through Vite HMR.
- **Backend:** Running stably under PID 28604 on port 8080.
- **Database:** Intact without changes.
