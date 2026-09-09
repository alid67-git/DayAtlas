#!/usr/bin/env bash
# Generates DayAtlas.xcodeproj from project.yml (requires XcodeGen on a Mac).
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v xcodegen >/dev/null 2>&1; then
  echo "XcodeGen yok. Kur: brew install xcodegen" >&2
  exit 1
fi
xcodegen generate
echo "Hazır: open DayAtlas.xcodeproj"
