#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script must be run on macOS."
  exit 1
fi

echo "Building macOS DMG for PM2 Dash..."
./gradlew :composeApp:packageDmg -Pcompose.desktop.packaging.checkJdkVendor=false

DMG_DIR="${REPO_ROOT}/composeApp/build/compose/binaries/main/dmg"

if [[ ! -d "${DMG_DIR}" ]]; then
  echo "Build finished, but DMG output directory was not found: ${DMG_DIR}"
  exit 1
fi

LATEST_DMG="$(find "${DMG_DIR}" -maxdepth 1 -type f -name '*.dmg' | sort | tail -n 1)"

if [[ -z "${LATEST_DMG}" ]]; then
  echo "Build finished, but no .dmg file was found in: ${DMG_DIR}"
  exit 1
fi

echo
echo "DMG created:"
echo "${LATEST_DMG}"
