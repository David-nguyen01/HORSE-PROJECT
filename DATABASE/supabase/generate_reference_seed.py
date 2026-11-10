"""Convert the deterministic reference records in 09_seed_reference_data.sql.

This exports only checked-in seed values, never live rows from the local database.
"""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "09_seed_reference_data.sql"
DESTINATION = Path(__file__).with_name("02_seed_reference.sql")
ADMIN_ID = "5D036EA7-77FD-4D63-90AC-B90E1E4CAE11"
USERS = [
    (ADMIN_ID, "admin", "admin@gmail.com", "CLUB_MANAGER"),
    ("22222222-2222-2222-2222-222222222222", "head_trainer", "trainer@gmail.com", "HEAD_TRAINER"),
    ("48772A9D-9DA0-44A8-90F2-0459B3ADB768", "stable_staff", "staff@gmail.com", "STABLE_STAFF"),
    ("44444444-4444-4444-4444-444444444444", "horse_owner", "owner@gmail.com", "HORSE_OWNER"),
    ("55555555-5555-5555-5555-555555555555", "vet_dr", "vet@gmail.com", "VETERINARIAN"),
]
MODULES = ["PROFILE", "TRAINING", "MEDICAL", "STABLE_CARE", "RACING", "REPORTING", "SYSTEM", "INVENTORY"]
ACTIONS = ["VIEW", "EDIT", "APPROVE", "CLAIM"]


def sql(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def generate() -> str:
    source = SOURCE.read_text(encoding="utf-8")
    match = re.search(r"DECLARE @hash VARCHAR\(100\)\s*=\s*'([^']+)'", source)
    if match is None:
        raise ValueError("The checked-in password hash was not found")
    password_hash = match.group(1)

    lines = [
        "-- Reference seed converted from DATABASE/09_seed_reference_data.sql.",
        "-- These are DEMO accounts sharing the documented test password. Replace before public use.",
    ]
    for user_id, username, email, role in USERS:
        extras = ", wallet_balance_vnd, active_service_plan, service_plan_started_on, service_next_due_date" if role == "HORSE_OWNER" else ""
        extra_values = ", 50000000, 'P30', '2026-10-01', '2026-11-01'" if extras else ""
        lines.append(
            "INSERT INTO dbo.users (id, username, email, password_hash, enabled" + extras + ") "
            f"VALUES ({sql(user_id)}, {sql(username)}, {sql(email)}, {sql(password_hash)}, TRUE{extra_values}) "
            "ON CONFLICT (id) DO NOTHING;"
        )
        lines.append(
            f"INSERT INTO dbo.user_roles (user_id, role) VALUES ({sql(user_id)}, {sql(role)}) "
            "ON CONFLICT DO NOTHING;"
        )

    permissions = {"CLUB_MANAGER": [(module, action) for module in MODULES for action in ACTIONS]}
    for role in ("HEAD_TRAINER", "HORSE_OWNER", "STABLE_STAFF", "VETERINARIAN"):
        match = re.search(
            rf"SELECT '{role}', m, a, 1, @adminId, @now\s+FROM \(VALUES\s*(.*?)\) permissions\(m, a\)",
            source,
            flags=re.S,
        )
        if match is None:
            raise ValueError(f"Missing permissions for {role}")
        permissions[role] = re.findall(r"\('([A-Z_]+)',\s*'([A-Z_]+)'\)", match.group(1))
    for role, grants in permissions.items():
        for module, action in grants:
            lines.append(
                "INSERT INTO dbo.role_permissions (role_code, module_code, action_code, granted, changed_by_user_id, changed_at) "
                f"VALUES ({sql(role)}, {sql(module)}, {sql(action)}, TRUE, {sql(ADMIN_ID)}, CURRENT_TIMESTAMP) "
                "ON CONFLICT (role_code, module_code, action_code) DO NOTHING;"
            )

    stables = [
        ("E8D772BF-F73A-4C0C-9175-DD33FF4D4568", "Chuồng B", "Khu chuồng trại B"),
        ("A1111111-1111-1111-1111-111111111111", "Chuồng A", "Khu chuồng trại A (Chăm sóc & Phục hồi)"),
    ]
    for stable_id, name, location in stables:
        lines.append(
            "INSERT INTO dbo.stables (id, name, location, is_active) "
            f"VALUES ({sql(stable_id)}, {sql(name)}, {sql(location)}, TRUE) ON CONFLICT (id) DO NOTHING;"
        )
    lines.extend([
        "INSERT INTO dbo.racetracks (id, name, is_active, opens_at, closes_at, closes_next_day, slot_minutes) "
        "SELECT gen_random_uuid(), 'Sân tập ' || lpad(n::text, 2, '0'), TRUE, '06:00', '00:00', TRUE, 60 "
        "FROM generate_series(1, 10) AS n WHERE NOT EXISTS "
        "(SELECT 1 FROM dbo.racetracks r WHERE r.name='Sân tập ' || lpad(n::text, 2, '0'));",
        "INSERT INTO dbo.racetrack_lanes (id, racetrack_id, lane_number, is_active) "
        "SELECT gen_random_uuid(), r.id, n, TRUE FROM dbo.racetracks r CROSS JOIN generate_series(1, 4) AS n "
        "WHERE r.name ~ '^Sân tập (0[1-9]|10)$' AND NOT EXISTS "
        "(SELECT 1 FROM dbo.racetrack_lanes l WHERE l.racetrack_id=r.id AND l.lane_number=n);",
    ])

    routines = re.findall(
        r"VALUES\s*\(NEWID\(\),\s*'(?P<code>ROUTINE_[A-Z_]+)',\s*N'(?P<title>[^']+)',\s*"
        r"N'(?P<description>[^']+)',\s*'(?P<type>[A-Z_]+)',\s*'(?P<time>[0-9:]+)',\s*"
        r"'(?P<scope>[A-Z_]+)',\s*1,\s*(?P<order>\d+)\);",
        source,
        flags=re.S,
    )
    if len(routines) != 5:
        raise ValueError(f"Expected 5 care routines, found {len(routines)}")
    for code, title, description, task_type, scheduled_time, scope, sort_order in routines:
        lines.append(
            "INSERT INTO dbo.care_routine_definitions "
            "(id, code, title, description, task_type, scheduled_time, scope, is_active, sort_order) "
            f"VALUES (gen_random_uuid(), {sql(code)}, {sql(title)}, {sql(description)}, {sql(task_type)}, "
            f"{sql(scheduled_time)}, {sql(scope)}, TRUE, {sort_order}) "
            "ON CONFLICT (code) DO NOTHING;"
        )

    records = re.findall(
        r"VALUES\s*\(\s*'(?P<id>[0-9A-Fa-f-]{36})'\s*,\s*'(?P<module>STABLE_CARE|INVENTORY)'\s*,\s*"
        r"'(?P<type>[A-Z_]+)'\s*,(?P<rest>.*?)N'(?P<json>\{.*?\})'\s*\)",
        source,
        flags=re.S,
    )
    if len(records) != 8:
        raise ValueError(f"Expected 8 standard plans and inventory items, found {len(records)}")
    for record_id, module, record_type, _, data in records:
        json.loads(data)
        source_table = "'api'" if module == "STABLE_CARE" else "NULL"
        event_date = "'2026-10-01'" if module == "STABLE_CARE" else "NULL"
        lines.append(
            "INSERT INTO dbo.module_records (id, module_code, record_type, source_table, user_id, status, event_date, data_json) "
            f"VALUES ({sql(record_id)}, {sql(module)}, {sql(record_type)}, "
            f"{source_table}, {sql(ADMIN_ID)}, 'ACTIVE', "
            f"{event_date}, {sql(data)}) "
            "ON CONFLICT (id) DO NOTHING;"
        )
    return "\n\n".join(lines) + "\n"


if __name__ == "__main__":
    DESTINATION.write_text(generate(), encoding="utf-8")
    print(DESTINATION)
