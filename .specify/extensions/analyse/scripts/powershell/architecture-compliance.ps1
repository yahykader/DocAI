# Architecture Compliance Analysis for Module 1.A (docai-setup-projet)
# Checks 10 mandatory points: ArchUnit, ADR-002/006/008/010, Setup standards
# Output: Markdown report with violations highlighted

param(
    [string]$FeatureDir = ".",
    [string]$ReportFile = $null
)

if ([string]::IsNullOrEmpty($ReportFile)) {
    $ReportFile = Join-Path $FeatureDir "analysis" "architecture-compliance.md"
}

$RepoRoot = (git rev-parse --show-toplevel 2>$null) -replace '\\', '/'
if ([string]::IsNullOrEmpty($RepoRoot)) {
    $RepoRoot = "."
}

# Ensure analysis directory exists
$AnalysisDir = Split-Path $ReportFile
if (-not (Test-Path $AnalysisDir)) {
    New-Item -ItemType Directory -Path $AnalysisDir -Force | Out-Null
}

# Initialize report
$Report = @(
    "# Architecture Compliance Analysis — Module 1.A"
    ""
    "**Generated**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss UTC')"
    "**Feature Directory**: $FeatureDir"
    ""
    "## Summary"
    ""
    "| Point | Check | Status | Details |"
    "|-------|-------|--------|---------|"
)

function Check-Pattern {
    param(
        [string]$Label,
        [string]$Pattern,
        [string[]]$Paths,
        [bool]$IsBlocking = $false
    )

    $found = $false
    $files = @()

    foreach ($path in $Paths) {
        $fullPath = Join-Path $RepoRoot $path
        if (Test-Path $fullPath -PathType Container) {
            $matches = Get-ChildItem -Path $fullPath -Recurse -File -ErrorAction SilentlyContinue |
                       Where-Object { $_.Name -match '\.(java|yml|yaml|xml|sh)$' } |
                       Select-String -Pattern $Pattern -ErrorAction SilentlyContinue |
                       Select-Object -First 1

            if ($matches) {
                $found = $true
                $files = Get-ChildItem -Path $fullPath -Recurse -File -ErrorAction SilentlyContinue |
                         Select-String -Pattern $Pattern -ErrorAction SilentlyContinue |
                         Select-Object -ExpandProperty Filename -Unique |
                         Select-Object -First 3
                break
            }
        }
    }

    $icon = "✅"
    $status = "✅ PASS"
    if ($found) {
        $icon = "🟡"
        $status = "⚠️ WARNING"
        if ($IsBlocking) {
            $icon = "🔴"
            $status = "❌ BLOCKING"
        }
    }

    $fileList = if ($found) { "Found in: $($files -join ', ')" } else { "Not detected" }

    $Report += "| $icon $Label | $Pattern | $status | $fileList |"
}

# === ARCHITECTURE VIOLATIONS ===
$Report += ""
$Report += "## Architecture Violations (ArchUnit Rules)"
$Report += ""

Check-Pattern "CHK001" "import org\.springframework" @("docai-domain/src") $true
Check-Pattern "CHK002" "import com\.mongodb" @("docai-domain/src") $true
Check-Pattern "CHK003" ".*Adapter.*calls.*Adapter" @("docai-adapter*/src") $true

# === ADR COMPLIANCE ===
$Report += ""
$Report += "## ADR Compliance"
$Report += ""

# ADR-002
$Report += "### ADR-002: Kafka Partition Key (documentId)"
$Report += ""

$dockerComposePath = Join-Path $RepoRoot "docker-compose.yml"
if (Test-Path $dockerComposePath) {
    $content = Get-Content $dockerComposePath -Raw
    if ($content -match "documentId") {
        $Report += "✅ **PASS** — Kafka topics configured with documentId partition key"
    } else {
        $Report += "🟡 **WARNING** — Partition key configuration not found in docker-compose.yml"
    }
} else {
    $Report += "❓ **UNKNOWN** — docker-compose.yml not found"
}

# ADR-006
$Report += ""
$Report += "### ADR-006: Keycloak JWKS Cache (1h TTL)"
$Report += ""

$appYmlPath = Join-Path $RepoRoot "docai-bootstrap/src/main/resources/application.yml"
if (Test-Path $appYmlPath) {
    $content = Get-Content $appYmlPath -Raw
    if ($content -match "PT1H|cache.*1h|3600") {
        $Report += "✅ **PASS** — JWKS cache TTL configured (1h or 3600s)"
    } else {
        $Report += "🟡 **WARNING** — JWKS cache TTL not found in application.yml"
    }
} else {
    $Report += "❓ **UNKNOWN** — application.yml not found"
}

# ADR-008
$Report += ""
$Report += "### ADR-008: CI Pipeline MAVEN_OPTS (-Xmx512m)"
$Report += ""

$ciDirs = @(".github", ".gitlab-ci", ".circleci")
$ciFound = $false
foreach ($dir in $ciDirs) {
    $ciPath = Join-Path $RepoRoot $dir
    if (Test-Path $ciPath) {
        $ciFiles = Get-ChildItem -Path $ciPath -Recurse -File -ErrorAction SilentlyContinue |
                  Select-String -Pattern "MAVEN_OPTS|-Xmx512m" -ErrorAction SilentlyContinue
        if ($ciFiles) {
            $Report += "✅ **PASS** — MAVEN_OPTS configured in CI pipeline"
            $ciFound = $true
            break
        }
    }
}
if (-not $ciFound) {
    $Report += "🟡 **WARNING** — MAVEN_OPTS=-Xmx512m not found in CI configuration"
}

# ADR-010
$Report += ""
$Report += "### ADR-010: MongoDB EXPLAIN PLAN Documentation"
$Report += ""

$mongoYmlPath = Join-Path $RepoRoot "docai-adapter-out-mongodb/src/main/resources/application.yml"
if (Test-Path $mongoYmlPath) {
    $content = Get-Content $mongoYmlPath -Raw
    if ($content -match "profiling|EXPLAIN|explain") {
        $Report += "✅ **PASS** — MongoDB profiling/EXPLAIN configured"
    } else {
        $Report += "🟡 **WARNING** — MongoDB slow query profiling not configured"
    }
} else {
    $Report += "❓ **UNKNOWN** — MongoDB adapter configuration not found"
}

# === SETUP STANDARDS ===
$Report += ""
$Report += "## Setup Standards Compliance"
$Report += ""

# Seeding Service
$Report += "### CHK008: SeedingService @Profile(""seed"")"
$Report += ""

$seedingPath = Join-Path $RepoRoot "docai-application/src"
if (Test-Path $seedingPath) {
    $seedingService = Get-ChildItem -Path $seedingPath -Recurse -File -ErrorAction SilentlyContinue |
                     Select-String -Pattern "@Profile.*seed|class.*SeedingService" -ErrorAction SilentlyContinue
    if ($seedingService) {
        $Report += "✅ **PASS** — SeedingService marked with @Profile(""seed"")"
    } else {
        $Report += "❓ **UNKNOWN** — SeedingService not yet implemented"
    }
} else {
    $Report += "❓ **UNKNOWN** — Application source not found"
}

# Kafka Topics
$Report += ""
$Report += "### CHK009: Kafka Topics (8 exact names)"
$Report += ""
$Report += "Expected topics:"
$Report += "- docai.doc.uploaded"
$Report += "- docai.doc.classified"
$Report += "- docai.doc.extracted"
$Report += "- docai.doc.fraud.analyzed"
$Report += "- docai.doc.completed"
$Report += "- docai.doc.failed"
$Report += "- docai.doc.dlq"
$Report += "- docai.outbox.relay"
$Report += ""

if (Test-Path $dockerComposePath) {
    $content = Get-Content $dockerComposePath -Raw
    $topicsFound = ([regex]::Matches($content, "docai\.doc\.[a-z.]*|docai\.outbox\.[a-z]*") | Select-Object -Unique).Count
    $Report += "**Topics found**: $topicsFound/8"
} else {
    $Report += "❓ **UNKNOWN** — docker-compose.yml not found"
}

# .gitignore
$Report += ""
$Report += "### CHK010: .env in .gitignore"
$Report += ""

$gitignorePath = Join-Path $RepoRoot ".gitignore"
if (Test-Path $gitignorePath) {
    $content = Get-Content $gitignorePath -Raw
    if ($content -match "^\s*\.env|^[^#]*\.env") {
        $Report += "✅ **PASS** — .env in .gitignore"
    } else {
        $Report += "❌ **BLOCKING** — .env NOT in .gitignore (secrets at risk)"
    }
} else {
    $Report += "⚠️  **WARNING** — .gitignore not found"
}

# === SUMMARY ===
$Report += ""
$Report += "## Recommendations"
$Report += ""
$Report += "- 🔴 **BLOCKING** violations must be fixed before merge"
$Report += "- 🟡 **WARNINGS** should be addressed during implementation"
$Report += "- ✅ **PASS** items are compliant"
$Report += "- ❓ **UNKNOWN** items are not yet implemented (expected in early phases)"

# Write report
$Report | Set-Content -Path $ReportFile -Encoding UTF8

Write-Host "✅ Analysis complete: $ReportFile"
