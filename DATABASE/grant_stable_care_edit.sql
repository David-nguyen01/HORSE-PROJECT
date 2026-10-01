-- KẾ HOẠCH FIX QUYỀN
UPDATE role_permissions 
SET granted = 1, changed_at = SYSDATETIMEOFFSET()
WHERE role_code IN ('HEAD_TRAINER', 'VETERINARIAN') 
  AND module_code = 'STABLE_CARE' 
  AND action_code = 'EDIT';
