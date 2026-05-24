# speckit-analyse — Architecture Compliance Analysis

Automated validation of code and configuration against mandatory architecture rules, ADR compliance, and development standards.

## Purpose

Validate implementation changes against:
- **ArchUnit Rules** (3 blocking rules)
- **Architecture Decision Records** (ADR-002, ADR-006, ADR-008, ADR-010)
- **Development Setup Standards** (3 critical setup requirements)

**Output**: Markdown compliance report with violations highlighted and categorized by severity.

---

## Invocation

### Manual (Developer Pre-PR Check)

```bash
# Run from feature directory
/speckit-analyse

# Or explicitly target a feature directory
/speckit-analyse specs/002-maven-docker-setup
```

### Automatic Hook

Runs after `/speckit-plan` completes (if enabled in `.specify/extensions.yml`):

```yaml
after_plan:
  - extension: analyse
    command: speckit.analyse.architecture
    optional: true
    enabled: true
```

---

## 10-Point Checklist

### Architecture Violations (BLOCKING — 3 points)

| Point | Check | Violation |
|-------|-------|-----------|
| **CHK001** | Domain imports Spring | `import org.springframework.*` in docai-domain → ❌ BLOCKING |
| **CHK002** | Domain imports MongoDB | `import com.mongodb.*` in docai-domain → ❌ BLOCKING |
| **CHK003** | Adapters call adapters | Adapter class calls another Adapter → ❌ BLOCKING |

**Impact**: These violations break hexagonal architecture isolation. Merge is blocked.

---

### ADR Compliance (WARNINGS — 4 points)

| Point | ADR | Requirement | Check |
|-------|-----|-------------|-------|
| **CHK004** | ADR-002 | Kafka topics use `documentId` as partition key | `partition.key = documentId` in kafka-init or docker-compose.yml |
| **CHK005** | ADR-006 | Keycloak JWKS cache TTL is 1 hour | `spring.security.oauth2.resourceserver.jwt.cache = PT1H` (or 3600s) |
| **CHK006** | ADR-008 | CI jobs set `MAVEN_OPTS=-Xmx512m` | GitHub Actions, GitLab CI, CircleCI, or other CI config |
| **CHK007** | ADR-010 | MongoDB indexes created with EXPLAIN PLAN documented | Slow query profiling enabled in dev profile |

**Impact**: ADR violations indicate design deviation. Must be justified before merge.

---

### Setup Standards (CRITICAL — 3 points)

| Point | Check | Requirement |
|-------|-------|-------------|
| **CHK008** | SeedingService profile | `@Profile("seed")` annotation on SeedingService class |
| **CHK009** | Kafka topic names (8 exact) | All 8 topics created: `docai.doc.{uploaded, classified, extracted, fraud.analyzed, completed, failed, dlq}`, `docai.outbox.relay` |
| **CHK010** | .env secrets protection | `.env` file in `.gitignore` (blocks accidental secret commits) |

**Impact**: CHK008, CHK009 ensure environment safety; CHK010 prevents secret leaks.

---

## Report Format

```markdown
# Architecture Compliance Analysis — Module 1.A

**Generated**: 2026-05-24 14:32:00 UTC
**Feature Directory**: specs/002-maven-docker-setup

## Summary

| Point | Check | Status | Details |
|-------|-------|--------|---------|
| 🔴 CHK001 | import org.springframework | ❌ BLOCKING | Found in: docai-domain/src/main/java/Document.java |
| ✅ CHK002 | import com.mongodb | ✅ PASS | Not detected |
| 🟡 CHK004 | documentId partition key | ⚠️ WARNING | Partition key configuration not found |
| ✅ CHK005 | JWKS cache PT1H | ✅ PASS | Found in application.yml |
| ...

## Recommendations

- 🔴 **BLOCKING** violations must be fixed before merge
- 🟡 **WARNINGS** should be addressed during implementation
- ✅ **PASS** items are compliant
- ❓ **UNKNOWN** items are not yet implemented
```

---

## Integration with Development Workflow

### Phase 1: Setup (ÉTAPE 1-3)

Run before PR submission to catch setup violations early:

```bash
# After initial Maven/Docker/Keycloak setup
/speckit-analyse

# Address any 🔴 BLOCKING violations before pushing
```

### Phase 2: ADR Implementation (ÉTAPE 4-6)

Run after implementing ADR-specific features:

```bash
# After Kafka topic setup
/speckit-analyse  # Verify CHK004 (documentId partition key)

# After Keycloak configuration
/speckit-analyse  # Verify CHK005 (JWKS cache)
```

### CI Pipeline Integration

Add to `.github/workflows/` or equivalent:

```yaml
- name: Architecture Compliance Check
  run: .specify/extensions/analyse/scripts/bash/architecture-compliance.sh .
```

---

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All checks passed (no blocking violations) |
| 1 | Blocking violation detected (CHK001, CHK002, CHK003, CHK010) |
| 2 | Script error or missing configuration |

---

## Customization

Edit scripts to add/modify checks:

- **Bash**: `.specify/extensions/analyse/scripts/bash/architecture-compliance.sh`
- **PowerShell**: `.specify/extensions/analyse/scripts/powershell/architecture-compliance.ps1`

### Example: Add Custom Check

```bash
# In architecture-compliance.sh, add after ADR section:
{
  echo "### Custom Check: Feature X"
  echo ""
  if [ -f "$REPO_ROOT/path/to/file" ]; then
    if grep -q "pattern" "$REPO_ROOT/path/to/file"; then
      echo "✅ **PASS** — Feature X correctly configured"
    else
      echo "🟡 **WARNING** — Feature X pattern not found"
    fi
  fi
} >> "$REPORT_FILE"
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Report not generated | Verify feature directory contains `specs/` subdirectory |
| Pattern not matching | Check file encoding (UTF-8) and grep regex syntax |
| "UNKNOWN" for all checks | Ensure project structure matches expected paths (docai-domain/, docker-compose.yml) |
| PowerShell execution blocked | Run: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser` |

---

## Related

- **ADR References**: See CLAUDE.md for ADR-002, ADR-006, ADR-008, ADR-010 details
- **Architecture Standards**: `.specify/memory/constitution.md`
- **Comprehensive Checklist**: `specs/002-maven-docker-setup/checklists/comprehensive.md`
