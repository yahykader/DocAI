# Data Model: CI/CD Pipeline & Standards (Module 1.B)

**Phase 1 output** | **Date**: 2026-05-29

---

## Overview

Module 1.B introduces no new MongoDB collections. The only "data model" artifact is the hexagonal `FeatureFlagPort` interface and its runtime representation — feature flag state held in Unleash (PostgreSQL-backed, not in MongoDB).

---

## Domain Port: FeatureFlagPort

**Location**: `docai-domain/src/main/java/fr/docai/domain/port/out/FeatureFlagPort.java`  
**Type**: Outbound port (adapter provided by `docai-bootstrap`)  
**Dependencies**: Zero (no Spring, no Unleash, no infrastructure imports)

```
FeatureFlagPort (interface)
├── isEnabled(flagName: String) : boolean
│     Global evaluation — no tenant context
└── isEnabled(flagName: String, tenantId: String) : boolean
      Per-tenant evaluation — returns tenant-specific toggle state
```

### Invariants

- `isEnabled()` MUST never throw an exception — callers assume a safe boolean return
- `isEnabled()` with unknown `flagName` returns `false` (fail-safe default)
- `tenantId` parameter is passed as Unleash context userId for per-tenant targeting

---

## Feature Flag Registry

Six named flags defined at application startup. Stored in Unleash (self-hosted PostgreSQL):

| Flag Name | Type | Default | Tenant-aware | Activates |
|-----------|------|---------|-------------|-----------|
| `billing.enabled` | Toggle | `false` | Yes | Stripe billing flow (Module 7) |
| `fraud.v2.enabled` | Toggle | `false` | Yes | New fraud detection algorithm (Module 3) |
| `extraction.mistral.enabled` | Toggle | `false` | Yes | Mistral LLM swap (Constitution: alternate AI) |
| `dashboard.search.enabled` | Toggle | `false` | Yes | Advanced search on dashboard (Module 5) |
| `notifications.inapp.enabled` | Toggle | `false` | Yes | In-app notification system |
| `maintenance.mode` | Toggle | `false` | No | Global maintenance gate (blocks all tenants) |

### Flag State Lifecycle

```
DEFINED (in code) → REGISTERED (in Unleash on startup) → EVALUATED (at call site)
                                                              ↓
                                                    true  (Unleash enabled)
                                                    false (Unleash disabled OR SDK unreachable)
```

---

## Adapter: UnleashFeatureFlagAdapter

**Location**: `docai-bootstrap/src/main/java/fr/docai/bootstrap/feature/UnleashFeatureFlagAdapter.java`  
**Implements**: `FeatureFlagPort`  
**Dependencies**: `io.getunleash:unleash-client-java`, `UnleashConfig` bean

```
UnleashFeatureFlagAdapter
├── unleash : DefaultUnleash      (injected via constructor)
├── isEnabled(flagName) : boolean
│     → try: unleash.isEnabled(flagName)
│     → catch: return false
└── isEnabled(flagName, tenantId) : boolean
      → try: unleash.isEnabled(flagName, UnleashContext.builder().userId(tenantId).build())
      → catch: return false
```

---

## Configuration Properties

Added to `docai-bootstrap/src/main/resources/application.yml`:

```yaml
docai:
  unleash:
    url: ${UNLEASH_URL:http://localhost:4242/api}
    api-token: ${UNLEASH_API_TOKEN:*:*.unleash-insecure-api-token}
    app-name: docai-backend
    environment: ${SPRING_PROFILES_ACTIVE:local}
    polling-interval: 15   # seconds
```

---

## Docker Compose Services (new)

Two new services added to `docker-compose.yml`:

```
unleash-db (postgres:16)
  port: 5433 (host) → 5432 (container)
  env: POSTGRES_DB=unleash, POSTGRES_USER=unleash, POSTGRES_PASSWORD=unleash
  volume: unleash_db_data

unleash (unleashorg/unleash-server:latest)
  port: 4242 (host) → 4242 (container)
  depends_on: unleash-db (healthy)
  env: DATABASE_URL=postgresql://unleash:unleash@unleash-db/unleash
       INIT_FRONTEND_API_TOKENS=*:*.unleash-insecure-api-token
  healthcheck: curl -f http://localhost:4242/health
```

---

## Kubernetes Resource Model

No new MongoDB documents. Kubernetes resources defined in `k8s/`:

```
Deployment (docai-backend)
  spec.replicas: 2 (initial)
  strategy: RollingUpdate (maxUnavailable=0, maxSurge=1)
  container.resources:
    requests: cpu=250m, memory=512Mi
    limits:   cpu=1000m, memory=1Gi
  probes:
    liveness:  GET :9091/actuator/health/liveness  (initial 90s, period 30s)
    readiness: GET :9091/actuator/health/readiness (initial 30s, period 10s)

Service (docai-backend)
  type: ClusterIP
  port: 8080 → targetPort: 8080

HorizontalPodAutoscaler (docai-backend)
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        targetAverageUtilization: 70
```
