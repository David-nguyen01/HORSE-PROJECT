-- Reference seed converted from DATABASE/09_seed_reference_data.sql.

-- These are DEMO accounts sharing the documented test password. Replace before public use.

INSERT INTO dbo.users (id, username, email, password_hash, enabled) VALUES ('5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'admin', 'admin@gmail.com', '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u', TRUE) ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.user_roles (user_id, role) VALUES ('5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'CLUB_MANAGER') ON CONFLICT DO NOTHING;

INSERT INTO dbo.users (id, username, email, password_hash, enabled) VALUES ('22222222-2222-2222-2222-222222222222', 'head_trainer', 'trainer@gmail.com', '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u', TRUE) ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.user_roles (user_id, role) VALUES ('22222222-2222-2222-2222-222222222222', 'HEAD_TRAINER') ON CONFLICT DO NOTHING;

INSERT INTO dbo.users (id, username, email, password_hash, enabled) VALUES ('48772A9D-9DA0-44A8-90F2-0459B3ADB768', 'stable_staff', 'staff@gmail.com', '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u', TRUE) ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.user_roles (user_id, role) VALUES ('48772A9D-9DA0-44A8-90F2-0459B3ADB768', 'STABLE_STAFF') ON CONFLICT DO NOTHING;

INSERT INTO dbo.users (id, username, email, password_hash, enabled, wallet_balance_vnd, active_service_plan, service_plan_started_on, service_next_due_date) VALUES ('44444444-4444-4444-4444-444444444444', 'horse_owner', 'owner@gmail.com', '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u', TRUE, 50000000, 'P30', '2026-10-01', '2026-11-01') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.user_roles (user_id, role) VALUES ('44444444-4444-4444-4444-444444444444', 'HORSE_OWNER') ON CONFLICT DO NOTHING;

INSERT INTO dbo.users (id, username, email, password_hash, enabled) VALUES ('55555555-5555-5555-5555-555555555555', 'vet_dr', 'vet@gmail.com', '$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u', TRUE) ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.user_roles (user_id, role) VALUES ('55555555-5555-5555-5555-555555555555', 'VETERINARIAN') ON CONFLICT DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'PROFILE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'PROFILE', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'PROFILE', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'PROFILE', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'TRAINING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'TRAINING', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'TRAINING', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'TRAINING', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'MEDICAL', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'MEDICAL', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'MEDICAL', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'MEDICAL', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'STABLE_CARE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'STABLE_CARE', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'STABLE_CARE', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'STABLE_CARE', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'RACING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'RACING', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'RACING', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'RACING', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'REPORTING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'REPORTING', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'REPORTING', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'REPORTING', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'SYSTEM', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'SYSTEM', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'SYSTEM', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'SYSTEM', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'INVENTORY', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'INVENTORY', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'INVENTORY', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('CLUB_MANAGER', 'INVENTORY', 'CLAIM', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'PROFILE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'PROFILE', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'PROFILE', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'TRAINING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'TRAINING', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'TRAINING', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'MEDICAL', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'MEDICAL', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'STABLE_CARE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'STABLE_CARE', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'RACING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'RACING', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'REPORTING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HEAD_TRAINER', 'INVENTORY', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HORSE_OWNER', 'PROFILE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HORSE_OWNER', 'TRAINING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HORSE_OWNER', 'MEDICAL', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HORSE_OWNER', 'STABLE_CARE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HORSE_OWNER', 'RACING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HORSE_OWNER', 'RACING', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('HORSE_OWNER', 'REPORTING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'PROFILE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'TRAINING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'TRAINING', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'STABLE_CARE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'STABLE_CARE', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'MEDICAL', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'INVENTORY', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('STABLE_STAFF', 'INVENTORY', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('VETERINARIAN', 'PROFILE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('VETERINARIAN', 'TRAINING', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('VETERINARIAN', 'MEDICAL', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('VETERINARIAN', 'MEDICAL', 'EDIT', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('VETERINARIAN', 'MEDICAL', 'APPROVE', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) VALUES ('VETERINARIAN', 'STABLE_CARE', 'VIEW', TRUE, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', CURRENT_TIMESTAMP) ON CONFLICT (role_code, module_code, action_code) DO NOTHING;

INSERT INTO dbo.stables (id, name, location, is_active) VALUES ('E8D772BF-F73A-4C0C-9175-DD33FF4D4568', 'Chuồng B', 'Khu chuồng trại B', TRUE) ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.stables (id, name, location, is_active) VALUES ('A1111111-1111-1111-1111-111111111111', 'Chuồng A', 'Khu chuồng trại A (Chăm sóc & Phục hồi)', TRUE) ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.racetracks (id, name, is_active, opens_at, closes_at, closes_next_day, slot_minutes) SELECT gen_random_uuid(), 'Sân tập ' || lpad(n::text, 2, '0'), TRUE, '06:00', '00:00', TRUE, 60 FROM generate_series(1, 10) AS n WHERE NOT EXISTS (SELECT 1 FROM dbo.racetracks r WHERE r.name='Sân tập ' || lpad(n::text, 2, '0'));

INSERT INTO dbo.racetrack_lanes (id, racetrack_id, lane_number, is_active) SELECT gen_random_uuid(), r.id, n, TRUE FROM dbo.racetracks r CROSS JOIN generate_series(1, 4) AS n WHERE r.name ~ '^Sân tập (0[1-9]|10)$' AND NOT EXISTS (SELECT 1 FROM dbo.racetrack_lanes l WHERE l.racetrack_id=r.id AND l.lane_number=n);

INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order) VALUES (gen_random_uuid(), 'ROUTINE_MORNING_STABLE_CHECK', 'Kiểm tra an toàn khu vực chuồng & thông gió', 'Kiểm tra tổng quan khu vực chuồng nuôi, chốt khóa an toàn, độ thông thoáng và đèn chiếu sáng đầu ca sáng.', 'OTHER', '07:00:00', 'FACILITY', TRUE, 10) ON CONFLICT (code) DO NOTHING;

INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order) VALUES (gen_random_uuid(), 'ROUTINE_WATER_TROUGH_CHECK', 'Kiểm tra & vệ sinh hệ thống máng nước tự động', 'Kiểm tra van phao tự động, áp lực nước và cọ rửa máng uống đảm bảo nguồn nước sạch cho toàn bộ chuồng trại.', 'FEEDING', '08:00:00', 'FACILITY', TRUE, 20) ON CONFLICT (code) DO NOTHING;

INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order) VALUES (gen_random_uuid(), 'ROUTINE_MORNING_MUCKING', 'Dọn phân chuồng & thay chất độn chuồng ca sáng', 'Thu gom phân, thay lớp rơm/mùn cưa ẩm ướt và rải vôi bột khử khuẩn lối đi hành lang chuồng.', 'MUCKING', '08:30:00', 'FACILITY', TRUE, 30) ON CONFLICT (code) DO NOTHING;

INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order) VALUES (gen_random_uuid(), 'ROUTINE_MIDDAY_FORAGE', 'Bổ sung cỏ khô & kiểm tra máng ăn trưa', 'Cấp thêm cỏ khô Alfalfa/Timothy cho từng ô chuồng và theo dõi lượng ăn của các ngựa trong chuồng.', 'FEEDING', '11:30:00', 'FACILITY', TRUE, 40) ON CONFLICT (code) DO NOTHING;

INSERT INTO dbo.care_routine_definitions (id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order) VALUES (gen_random_uuid(), 'ROUTINE_EVENING_BEDDING_CHECK', 'Kiểm tra đệm lót & khóa chốt ca tối', 'San phẳng lớp đệm lót cuối ngày, bổ sung rơm sạch và chốt khóa an toàn toàn bộ cửa chuồng trước khi hết ca.', 'OTHER', '16:30:00', 'FACILITY', TRUE, 50) ON CONFLICT (code) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('C1010000-0000-0000-0000-000000000001', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', '2026-10-01', '{"planCode":"STD_TRAINING","planName":"Khẩu phần Huấn luyện Tiêu chuẩn","description":"Dành cho ngựa đua trong chu kỳ luyện tập tiêu chuẩn, đảm bảo năng lượng và thể lực bền bỉ.","targetGroup":"Ngựa tập luyện tiêu chuẩn","meals":[{"mealName":"Bữa sáng (06:30)","feedingTime":"06:30:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải & khoáng vi lượng: 40g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":1.0,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Vitamin E & B-Complex: 25g"}]}') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('C1010000-0000-0000-0000-000000000002', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', '2026-10-01', '{"planCode":"STD_RECOVERY","planName":"Khẩu phần Nghỉ ngơi & Phục hồi","description":"Dành cho ngựa sau thi đấu hoặc đang trong giai đoạn nghỉ dưỡng phục hồi thể lực, giảm tinh bột và tăng xơ tiêu hóa.","targetGroup":"Ngựa nghỉ dưỡng / phục hồi","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Men vi sinh tiêu hóa: 30g"},{"mealName":"Bữa trưa (12:00)","feedingTime":"12:00:00","concentrateKg":0.5,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":0.5,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":4.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 25g"}]}') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('C1010000-0000-0000-0000-000000000010', 'STABLE_CARE', 'STANDARD_FEEDING_PLAN', 'api', '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', '2026-10-01', '{"planCode":"STD_STANDARD","planName":"Khẩu phần Tiêu chuẩn Thường nhật","description":"Dành cho ngựa có trạng thái bình thường, đáp ứng nhu cầu chăm sóc và duy trì thể lực thường nhật.","targetGroup":"Ngựa thể trạng bình thường / chăm sóc thường nhật","meals":[{"mealName":"Bữa sáng (07:00)","feedingTime":"07:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.0,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Khoáng vi lượng & muối khoáng: 20g"},{"mealName":"Bữa trưa (11:30)","feedingTime":"11:30:00","concentrateKg":0.8,"concentrateType":"Cám viên dinh dưỡng cao năng lượng","forageKg":2.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":null},{"mealName":"Bữa chiều (17:00)","feedingTime":"17:00:00","concentrateKg":1.0,"concentrateType":"Yến mạch nguyên hạt cán dập (Oats)","forageKg":3.5,"forageType":"Cỏ khô Alfalfa (Mỹ vụ mới)","supplements":"Chế phẩm điện giải bù nước: 20g"}]}') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('A1010000-0000-0000-0000-000000000001', 'INVENTORY', 'INVENTORY_ITEM', NULL, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', NULL, '{"itemName":"Cỏ khô Alfalfa (Mỹ vụ mới)","category":"FEED","unit":"kg","quantityOnHand":850.0,"minThreshold":300.0,"location":"Kho Thức Ăn A - Dãy 1","description":"Cỏ linh lăng sấy khô giàu đạm & canxi cho ngựa đua","referencePrice":45000.0}') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('A1010000-0000-0000-0000-000000000002', 'INVENTORY', 'INVENTORY_ITEM', NULL, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', NULL, '{"itemName":"Yến mạch nguyên hạt cán dập (Oats)","category":"FEED","unit":"kg","quantityOnHand":120.0,"minThreshold":200.0,"location":"Kho Thức Ăn A - Dãy 2","description":"Yến mạch giàu tinh bột tiêu hóa nhanh cho ngựa luyện tập cường độ cao","referencePrice":38000.0}') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('A1010000-0000-0000-0000-000000000003', 'INVENTORY', 'INVENTORY_ITEM', NULL, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', NULL, '{"itemName":"Cám viên dinh dưỡng cao năng lượng","category":"FEED","unit":"bao (25kg)","quantityOnHand":45.0,"minThreshold":20.0,"location":"Kho Thức Ăn B","description":"Hỗn hợp cám viên bổ sung khoáng và vitamin thiết yếu cho ngựa đua","referencePrice":480000.0}') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('A1010000-0000-0000-0000-000000000004', 'INVENTORY', 'INVENTORY_ITEM', NULL, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', NULL, '{"itemName":"Chế phẩm điện giải & khoáng vi lượng","category":"FEED","unit":"hộp (2kg)","quantityOnHand":18.0,"minThreshold":10.0,"location":"Tủ Dinh Dưỡng Kho Y Tế","description":"Bột điện giải bù nước và điện giải sau buổi tập nặng","referencePrice":320000.0}') ON CONFLICT (id) DO NOTHING;

INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) VALUES ('A1010000-0000-0000-0000-000000000005', 'INVENTORY', 'INVENTORY_ITEM', NULL, '5D036EA7-77FD-4D63-90AC-B90E1E4CAE11', 'ACTIVE', NULL, '{"itemName":"Rơm lúa mì lót chuồng khử ẩm","category":"BEDDING","unit":"cuộn (20kg)","quantityOnHand":180.0,"minThreshold":50.0,"location":"Kho Đệm Lót C","description":"Rơm lúa mì sấy khô tự nhiên, hút ẩm tốt làm đệm chuồng","referencePrice":85000.0}') ON CONFLICT (id) DO NOTHING;
