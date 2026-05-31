# Secrets Rotation Policy (BR-ROT-001 to BR-ROT-004)

All secrets are stored in AWS Secrets Manager. Rotation is enforced by an AWS Config rule
`secretsmanager-secret-rotation-enabled` that alerts when a secret exceeds its rotation window.

## Rotation Schedule

| Secret Name (AWS Secrets Manager) | Service | Rotation Period | Rule |
|-----------------------------------|---------|-----------------|------|
| `docai/prod/claude-api-key` | Claude API / LLM adapter | **90 days** | BR-ROT-001 |
| `docai/prod/keycloak-admin-password` | Keycloak 26 | **90 days** | BR-ROT-001 |
| `docai/prod/stripe-secret-key` | Stripe billing | **90 days** | BR-ROT-001 |
| `docai/prod/mongodb-uri` | MongoDB Atlas | **180 days** | BR-ROT-002 |
| `docai/staging/claude-api-key` | Claude API / LLM adapter (staging) | **90 days** | BR-ROT-001 |
| `docai/staging/keycloak-admin-password` | Keycloak 26 (staging) | **90 days** | BR-ROT-001 |
| `docai/staging/stripe-secret-key` | Stripe billing (staging) | **90 days** | BR-ROT-001 |
| `docai/staging/mongodb-uri` | MongoDB Atlas (staging) | **180 days** | BR-ROT-002 |

## AWS Config Alert Setup

1. In AWS Config console, create a rule using the managed rule `secretsmanager-secret-rotation-enabled`.
2. Set `maximumAllowedRotationFrequency` to `90` for the 90-day secrets and `180` for MongoDB.
3. Configure an SNS topic to receive non-compliance notifications → alerts the on-call engineer.

## Reload Without Redeploy (BR-ROT-004)

Spring Boot reloads secrets from AWS Secrets Manager without a pod restart using:

```yaml
# application.yml (staging/prod profile)
spring:
  cloud:
    aws:
      secretsmanager:
        reload-strategy: restart_context
```

The `restart_context` strategy triggers an `ApplicationContext` refresh (equivalent to a
`ContextRefreshedEvent`) which re-injects `@Value` and `@ConfigurationProperties` beans with
the new secret value. No Kubernetes rolling restart is required.

## Local Development

Local secrets (docker-compose) use hard-coded placeholder values — see inline comments in
`docker-compose.yml` tagged `# LOCAL PROFILE ONLY`. These values MUST NOT be used in
staging or production.
