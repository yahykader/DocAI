# Secrets Rotation Journal (BR-ROT-003)

Track every secret rotation event. Update this table immediately after each rotation.

| Secret Name | Last Rotated | Rotated By | Next Rotation Due | Notes |
|-------------|-------------|------------|-------------------|-------|
| `docai/prod/openai-api-key` | 2026-05-31 | initial setup | 2026-08-29 | First rotation — production launch |
| `docai/prod/keycloak-admin-password` | 2026-05-31 | initial setup | 2026-08-29 | First rotation — production launch |
| `docai/prod/stripe-secret-key` | 2026-05-31 | initial setup | 2026-08-29 | First rotation — production launch |
| `docai/prod/mongodb-uri` | 2026-05-31 | initial setup | 2026-11-27 | First rotation — production launch (180-day cycle) |
| `docai/staging/openai-api-key` | 2026-05-31 | initial setup | 2026-08-29 | First rotation — staging environment |
| `docai/staging/keycloak-admin-password` | 2026-05-31 | initial setup | 2026-08-29 | First rotation — staging environment |
| `docai/staging/stripe-secret-key` | 2026-05-31 | initial setup | 2026-08-29 | First rotation — staging environment |
| `docai/staging/mongodb-uri` | 2026-05-31 | initial setup | 2026-11-27 | First rotation — staging environment (180-day cycle) |
