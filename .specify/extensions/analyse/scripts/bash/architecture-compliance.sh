#!/bin/bash
# Architecture Compliance Analysis for Module 1.A (docai-setup-projet)
# Checks 10 mandatory points: ArchUnit, ADR-002/006/008/010, Setup standards
# Output: Markdown report with violations highlighted

set -e

FEATURE_DIR="${1:-.}"
REPORT_FILE="$FEATURE_DIR/analysis/architecture-compliance.md"
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo ".")

mkdir -p "$(dirname "$REPORT_FILE")"

# Initialize report
{
  echo "# Architecture Compliance Analysis — Module 1.A"
  echo ""
  echo "**Generated**: $(date -u +'%Y-%m-%d %H:%M:%S UTC')"
  echo "**Feature Directory**: $FEATURE_DIR"
  echo ""
  echo "## Summary"
  echo ""
  echo "| Point | Check | Status | Details |"
  echo "|-------|-------|--------|---------|"
} > "$REPORT_FILE"

# Helper function to check files for pattern
check_pattern() {
  local label="$1"
  local pattern="$2"
  local paths="$3"
  local is_blocking="$4"

  local found=0
  local files=""

  for path in $paths; do
    if [ -d "$REPO_ROOT/$path" ]; then
      if grep -r "$pattern" "$REPO_ROOT/$path" 2>/dev/null | head -1 >/dev/null; then
        found=1
        files=$(grep -r "$pattern" "$REPO_ROOT/$path" 2>/dev/null | cut -d: -f1 | sort -u | head -3 | tr '\n' ', ')
        break
      fi
    fi
  done

  local icon="🟡"
  local status="⚠️ WARNING"
  [ "$is_blocking" = "1" ] && icon="🔴" && status="❌ BLOCKING"
  [ $found -eq 0 ] && icon="✅" && status="✅ PASS"

  echo "| $icon $label | $pattern | $status | $([ $found -eq 1 ] && echo "Found in: $files" || echo "Not detected") |" >> "$REPORT_FILE"
}

# === ARCHITECTURE VIOLATIONS ===
echo "## Architecture Violations (ArchUnit Rules)" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

check_pattern "CHK001" "import org\\.springframework" "docai-domain/src" "1"
check_pattern "CHK002" "import com\\.mongodb" "docai-domain/src" "1"
check_pattern "CHK003" "Adapter.*calls.*Adapter" "docai-adapter*/src" "1"

# === ADR COMPLIANCE ===
echo "" >> "$REPORT_FILE"
echo "## ADR Compliance" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# ADR-002: Kafka partition key
{
  echo "### ADR-002: Kafka Partition Key (documentId)"
  echo ""
  if [ -f "$REPO_ROOT/docker-compose.yml" ]; then
    if grep -q "documentId" "$REPO_ROOT/docker-compose.yml"; then
      echo "✅ **PASS** — Kafka topics configured with documentId partition key"
    else
      echo "🟡 **WARNING** — Partition key configuration not found in docker-compose.yml"
    fi
  else
    echo "❓ **UNKNOWN** — docker-compose.yml not found"
  fi
} >> "$REPORT_FILE"

# ADR-006: Keycloak JWKS cache
{
  echo ""
  echo "### ADR-006: Keycloak JWKS Cache (1h TTL)"
  echo ""
  if [ -f "$REPO_ROOT/docai-bootstrap/src/main/resources/application.yml" ]; then
    if grep -q "PT1H\|cache.*1h\|3600" "$REPO_ROOT/docai-bootstrap/src/main/resources/application.yml"; then
      echo "✅ **PASS** — JWKS cache TTL configured (1h or 3600s)"
    else
      echo "🟡 **WARNING** — JWKS cache TTL not found in application.yml"
    fi
  else
    echo "❓ **UNKNOWN** — application.yml not found"
  fi
} >> "$REPORT_FILE"

# ADR-008: CI MAVEN_OPTS
{
  echo ""
  echo "### ADR-008: CI Pipeline MAVEN_OPTS (-Xmx512m)"
  echo ""
  ci_files=$(find "$REPO_ROOT/.github" "$REPO_ROOT/.gitlab-ci" "$REPO_ROOT/.circleci" 2>/dev/null | grep -E '\.(yml|yaml|sh)$' || true)
  if [ -n "$ci_files" ]; then
    if echo "$ci_files" | xargs grep -l "MAVEN_OPTS\|-Xmx512m" 2>/dev/null | head -1 >/dev/null; then
      echo "✅ **PASS** — MAVEN_OPTS configured in CI pipeline"
    else
      echo "🟡 **WARNING** — MAVEN_OPTS=-Xmx512m not found in CI configuration"
    fi
  else
    echo "❓ **UNKNOWN** — CI configuration files not found (.github/workflows, .gitlab-ci.yml, etc.)"
  fi
} >> "$REPORT_FILE"

# ADR-010: MongoDB EXPLAIN PLAN
{
  echo ""
  echo "### ADR-010: MongoDB EXPLAIN PLAN Documentation"
  echo ""
  if [ -f "$REPO_ROOT/docai-adapter-out-mongodb/src/main/resources/application.yml" ]; then
    if grep -q "profiling\|EXPLAIN\|explain" "$REPO_ROOT/docai-adapter-out-mongodb/src/main/resources/application.yml"; then
      echo "✅ **PASS** — MongoDB profiling/EXPLAIN configured"
    else
      echo "🟡 **WARNING** — MongoDB slow query profiling not configured"
    fi
  else
    echo "❓ **UNKNOWN** — MongoDB adapter configuration not found"
  fi
} >> "$REPORT_FILE"

# === SETUP STANDARDS ===
echo "" >> "$REPORT_FILE"
echo "## Setup Standards Compliance" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# Seeding Service
{
  echo "### CHK008: SeedingService @Profile(\"seed\")"
  echo ""
  if grep -r "@Profile.*seed" "$REPO_ROOT/docai-application/src" 2>/dev/null | grep -i "SeedingService\|class.*Seed" >/dev/null 2>&1; then
    echo "✅ **PASS** — SeedingService marked with @Profile(\"seed\")"
  elif grep -r "class.*SeedingService" "$REPO_ROOT/docai-application/src" 2>/dev/null >/dev/null; then
    echo "🟡 **WARNING** — SeedingService found but @Profile(\"seed\") not verified"
  else
    echo "❓ **UNKNOWN** — SeedingService not yet implemented"
  fi
} >> "$REPORT_FILE"

# Kafka Topics
{
  echo ""
  echo "### CHK009: Kafka Topics (8 exact names)"
  echo ""
  echo "Expected topics:"
  echo "- docai.doc.uploaded"
  echo "- docai.doc.classified"
  echo "- docai.doc.extracted"
  echo "- docai.doc.fraud.analyzed"
  echo "- docai.doc.completed"
  echo "- docai.doc.failed"
  echo "- docai.doc.dlq"
  echo "- docai.outbox.relay"
  echo ""
  if [ -f "$REPO_ROOT/docker-compose.yml" ]; then
    topics_found=$(grep -o "docai\\.doc\\.[a-z.]*\|docai\\.outbox\\.[a-z]*" "$REPO_ROOT/docker-compose.yml" | sort -u | wc -l)
    echo "**Topics found**: $topics_found/8"
  else
    echo "❓ **UNKNOWN** — docker-compose.yml not found"
  fi
} >> "$REPORT_FILE"

# .gitignore
{
  echo ""
  echo "### CHK010: .env in .gitignore"
  echo ""
  if [ -f "$REPO_ROOT/.gitignore" ]; then
    if grep -q "^\\.env\|^[^#]*\\.env" "$REPO_ROOT/.gitignore"; then
      echo "✅ **PASS** — .env in .gitignore"
    else
      echo "❌ **BLOCKING** — .env NOT in .gitignore (secrets at risk)"
    fi
  else
    echo "⚠️  **WARNING** — .gitignore not found"
  fi
} >> "$REPORT_FILE"

# === SUMMARY ===
echo "" >> "$REPORT_FILE"
echo "## Recommendations" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "- 🔴 **BLOCKING** violations must be fixed before merge" >> "$REPORT_FILE"
echo "- 🟡 **WARNINGS** should be addressed during implementation" >> "$REPORT_FILE"
echo "- ✅ **PASS** items are compliant" >> "$REPORT_FILE"
echo "- ❓ **UNKNOWN** items are not yet implemented (expected in early phases)" >> "$REPORT_FILE"

echo ""
echo "✅ Analysis complete: $REPORT_FILE"
