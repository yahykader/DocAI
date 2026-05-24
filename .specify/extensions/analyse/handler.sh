#!/bin/bash
# Handler for speckit-analyse skill
# Detects platform and dispatches to appropriate script

FEATURE_DIR="${FEATURE_DIR:-.}"
ANALYSE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "$OSTYPE" == "win32" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "msys" ]]; then
    # Windows: Use PowerShell script
    pwsh -NoProfile -ExecutionPolicy Bypass \
        -File "$ANALYSE_ROOT/scripts/powershell/architecture-compliance.ps1" \
        -FeatureDir "$FEATURE_DIR"
else
    # Unix-like: Use Bash script
    bash "$ANALYSE_ROOT/scripts/bash/architecture-compliance.sh" "$FEATURE_DIR"
fi
