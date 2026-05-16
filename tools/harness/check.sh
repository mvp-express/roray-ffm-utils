#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  printf 'roray-ffm-utils harness check failed: %s\n' "$1" >&2
  exit 1
}

require_file() { [[ -f "$ROOT/$1" ]] || fail "missing $1"; }
require_dir() { [[ -d "$ROOT/$1" ]] || fail "missing $1"; }
require_text() { grep -Fq "$2" "$ROOT/$1" || fail "$1 must reference $2"; }

require_file "AGENTS.md"
require_file "docs/INDEX.md"
require_file "docs/WORKFLOW.md"
require_file "docs/quality/README.md"
require_file "settings.gradle.kts"

require_text "settings.gradle.kts" "include(\"lib\")"
require_text "settings.gradle.kts" "include(\"benchmarks\")"

require_dir "lib/src/main/java/express/mvp/roray/ffm/utils/memory"
require_dir "lib/src/main/java/express/mvp/roray/ffm/utils/functions"
require_dir "lib/src/main/java/express/mvp/roray/ffm/concurrent/queue"
require_dir "lib/src/main/java/express/mvp/roray/ffm/pool"
require_dir "lib/src/main/java/express/mvp/roray/ffm/ds/map"

printf 'roray-ffm-utils harness check passed\n'
