const fs = require('fs');
const path = require('path');

const controllers = [
    "BACKEND/src/main/java/com/racehorse/admin/AdminManagementController.java",
    "BACKEND/src/main/java/com/racehorse/admin/ManagementDashboardController.java",
    "BACKEND/src/main/java/com/racehorse/audit/controller/AuditLogController.java",
    "BACKEND/src/main/java/com/racehorse/auth/controller/AuthController.java",
    "BACKEND/src/main/java/com/racehorse/auth/controller/RolePermissionController.java",
    "BACKEND/src/main/java/com/racehorse/auth/controller/UserController.java",
    "BACKEND/src/main/java/com/racehorse/caretask/controller/CareTaskController.java",
    "BACKEND/src/main/java/com/racehorse/finance/controller/FinanceController.java",
    "BACKEND/src/main/java/com/racehorse/health/controller/BarnHealthController.java",
    "BACKEND/src/main/java/com/racehorse/health/controller/HorseMeasurementController.java",
    "BACKEND/src/main/java/com/racehorse/health/controller/InjuryController.java",
    "BACKEND/src/main/java/com/racehorse/health/controller/MedicalExaminationController.java",
    "BACKEND/src/main/java/com/racehorse/health/controller/PreventiveCareController.java",
    "BACKEND/src/main/java/com/racehorse/health/controller/TrainingLockController.java",
    "BACKEND/src/main/java/com/racehorse/horse/controller/HorseController.java",
    "BACKEND/src/main/java/com/racehorse/horse/controller/PedigreeController.java",
    "BACKEND/src/main/java/com/racehorse/incident/controller/IncidentReportController.java",
    "BACKEND/src/main/java/com/racehorse/inventory/controller/InventoryController.java",
    "BACKEND/src/main/java/com/racehorse/media/controller/HorsePhotoController.java",
    "BACKEND/src/main/java/com/racehorse/media/controller/HorseProfileFileController.java",
    "BACKEND/src/main/java/com/racehorse/media/controller/StoredFileController.java",
    "BACKEND/src/main/java/com/racehorse/notification/controller/NotificationController.java",
    "BACKEND/src/main/java/com/racehorse/nutrition/controller/NutritionPlanController.java",
    "BACKEND/src/main/java/com/racehorse/racing/controller/RaceEventController.java",
    "BACKEND/src/main/java/com/racehorse/racing/controller/RaceResultController.java",
    "BACKEND/src/main/java/com/racehorse/reporting/controller/OwnerReportController.java",
    "BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java",
    "BACKEND/src/main/java/com/racehorse/training/controller/FitnessDashboardController.java",
    "BACKEND/src/main/java/com/racehorse/training/controller/RacetrackController.java",
    "BACKEND/src/main/java/com/racehorse/training/controller/TelemetryController.java",
    "BACKEND/src/main/java/com/racehorse/training/controller/TrainingEvaluationController.java",
    "BACKEND/src/main/java/com/racehorse/training/controller/TrainingPlanController.java",
    "BACKEND/src/main/java/com/racehorse/training/controller/TrainingScheduleController.java"
];

let results = [];

controllers.forEach(c => {
    try {
        const content = fs.readFileSync(c, 'utf-8');
        const lines = content.split(/\r?\n/);
        let classMapping = '';
        
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const rm = line.match(/@RequestMapping\("([^"]+)"\)/);
            if (rm) classMapping = rm[1];
            
            const mm = line.match(/@(Get|Post|Put|Patch|Delete)Mapping(?:\((?:value=)?(?:")?([^"\)]*)(?:")?[^\)]*\))?/);
            if (mm) {
                const method = mm[1].toUpperCase();
                let p = mm[2] || "";
                if (p && !p.startsWith('/')) p = '/' + p;
                const fullPath = classMapping + p;
                
                let auth = "";
                const authRegex = /@PreAuthorize\("([^"]+)"\)/;
                
                if (line.match(authRegex)) {
                    auth = line.match(authRegex)[1];
                } else {
                    for (let j = Math.max(0, i - 3); j < i; j++) {
                        if (lines[j].match(authRegex)) {
                            auth = lines[j].match(authRegex)[1];
                            break;
                        }
                    }
                    if (!auth) {
                        for (let j = i + 1; j < Math.min(lines.length, i + 3); j++) {
                            if (lines[j].match(authRegex)) {
                                auth = lines[j].match(authRegex)[1];
                                break;
                            }
                        }
                    }
                }
                results.push(`| ${fullPath} | ${method} | ${path.basename(c)}:${i+1} | ${auth} |`);
            }
        }
    } catch (e) {
        console.error("Error on", c, e.message);
    }
});

console.log("| Path | Method | File:Line | @PreAuthorize |");
console.log("|---|---|---|---|");
results.forEach(r => console.log(r));
