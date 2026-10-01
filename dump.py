import os
import re

controllers = [
    "BACKEND/src/main/java/com/racehorse/admin/AdminManagementController.java",
    "BACKEND/src/main/java/com/racehorse/admin/ManagementDashboardController.java",
    "BACKEND/src/main/java/com/racehorse/audit/controller/AuditLogController.java",
    "BACKEND/src/main/java/com/racehorse/auth/controller/AuthController.java",
    "BACKEND/src/main/java/com/racehorse/auth/controller/RolePermissionController.java",
    "BACKEND/src/main/java/com/racehorse/auth/controller/UserController.java",
    "BACKEND/src/main/java/com/racehorse/caretask/controller/CareTaskController.java",
    "BACKEND/src/main/java/com/racehorse/finance/controller/FinanceController.java",
    "BACKEND/src/main/java/com/racehorse/health/controller/BarnHealthController.java",
    "BACKEND/src/main/java/com/racehorse\health\controller\HorseMeasurementController.java",
    "BACKEND/src/main/java/com/racehorse\health\controller\InjuryController.java",
    "BACKEND/src/main/java/com/racehorse\health\controller\MedicalExaminationController.java",
    "BACKEND/src/main/java/com/racehorse\health\controller\PreventiveCareController.java",
    "BACKEND/src/main/java/com/racehorse\health\controller\TrainingLockController.java",
    "BACKEND/src/main/java/com/racehorse\horse\controller\HorseController.java",
    "BACKEND/src/main/java/com/racehorse\horse\controller\PedigreeController.java",
    "BACKEND/src/main/java/com/racehorse\incident\controller\IncidentReportController.java",
    "BACKEND/src/main/java/com/racehorse\inventory\controller\InventoryController.java",
    "BACKEND/src/main/java/com/racehorse\media\controller\HorsePhotoController.java",
    "BACKEND/src/main/java/com/racehorse\media\controller\HorseProfileFileController.java",
    "BACKEND/src/main/java/com/racehorse\media\controller\StoredFileController.java",
    "BACKEND/src/main/java/com/racehorse\\notification\controller\\NotificationController.java",
    "BACKEND/src/main/java/com/racehorse\\nutrition\controller\\NutritionPlanController.java",
    "BACKEND/src/main/java/com/racehorse\\racing\controller\\RaceEventController.java",
    "BACKEND/src/main/java/com/racehorse\\racing\controller\\RaceResultController.java",
    "BACKEND/src/main/java/com/racehorse\\reporting\controller\\OwnerReportController.java",
    "BACKEND/src/main/java/com/racehorse\\stable\controller\\StableController.java",
    "BACKEND/src/main/java/com/racehorse\\training\controller\\FitnessDashboardController.java",
    "BACKEND/src/main/java/com/racehorse\\training\controller\\RacetrackController.java",
    "BACKEND/src/main/java/com/racehorse\\training\controller\\TelemetryController.java",
    "BACKEND/src/main/java/com/racehorse\\training\controller\\TrainingEvaluationController.java",
    "BACKEND/src/main/java/com/racehorse\\training\controller\\TrainingPlanController.java",
    "BACKEND/src/main/java/com/racehorse\\training\controller\\TrainingScheduleController.java",
]
controllers = [c.replace('\\', '/') for c in controllers]

results = []

for c in controllers:
    try:
        with open(c, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            class_mapping = ""
            for i, line in enumerate(lines):
                m = re.search(r'@RequestMapping\("([^"]+)"\)', line)
                if m:
                    class_mapping = m.group(1)
                    
                m2 = re.search(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\((?:value=)?(?:")?([^"\)]*)(?:")?[^\)]*\))?', line)
                if m2:
                    method = m2.group(1).upper()
                    path = m2.group(2) if m2.group(2) else ""
                    if not path.startswith('/'):
                        path = '/' + path if path else ''
                    full_path = class_mapping + path
                    if class_mapping == '' and not path:
                        # try to find in previous lines if mapping is multiline
                        pass
                    
                    # try to find preauthorize
                    auth = ""
                    if '@PreAuthorize' in line:
                        mauth = re.search(r'@PreAuthorize\("([^"]+)"\)', line)
                        if mauth: auth = mauth.group(1)
                    else:
                        for j in range(i-1, max(-1, i-3), -1):
                            if '@PreAuthorize' in lines[j]:
                                mauth = re.search(r'@PreAuthorize\("([^"]+)"\)', lines[j])
                                if mauth: auth = mauth.group(1)
                                break
                    if not auth:
                        for j in range(i+1, min(len(lines), i+3)):
                            if '@PreAuthorize' in lines[j]:
                                mauth = re.search(r'@PreAuthorize\("([^"]+)"\)', lines[j])
                                if mauth: auth = mauth.group(1)
                                break
                                
                    results.append(f"| {full_path} | {method} | {os.path.basename(c)}:{i+1} | {auth} |")
    except Exception as e:
        print(f"Error on {c}: {e}")

print("| Path | Method | File:Line | @PreAuthorize |")
print("|---|---|---|---|")
for r in results:
    print(r)
