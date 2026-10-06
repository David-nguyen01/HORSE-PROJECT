# Training v3 API handoff

No API is mocked by the training pages in this checkout. The owner and trainer metrics, schedule lifecycle, emergency request, race registration, eligibility, and owner decision pages call implemented backend routes.

The following backend-only assignment routes are available for the separate stable-staff and veterinary interfaces. Those interfaces are intentionally not part of this change:

| Role | Method and route | Purpose | Access |
|---|---|---|---|
| STABLE_STAFF | `GET /api/paid-horse-assignments/caretaker/pending` | List paid active horses with no caretaker | `PERM_STABLE_CARE_EDIT` |
| STABLE_STAFF | `POST /api/paid-horse-assignments/caretaker/{horseId}` | Atomically assign self as caretaker; competing picks return HTTP 409 | `PERM_STABLE_CARE_EDIT` |
| VETERINARIAN | `GET /api/paid-horse-assignments/diagnosing-vet/pending` | List paid active horses with no diagnosing vet | `PERM_MEDICAL_EDIT` |
| VETERINARIAN | `POST /api/paid-horse-assignments/diagnosing-vet/{horseId}` | Atomically assign self as diagnosing vet; competing picks return HTTP 409 | `PERM_MEDICAL_EDIT` |

No additional API contract is currently mocked or missing for the approved training pages. Azure permission grants for the two assignment roles were not changed by these scripts; deployments should confirm the existing grants before wiring those separate clients.
