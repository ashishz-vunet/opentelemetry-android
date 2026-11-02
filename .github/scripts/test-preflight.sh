#!/usr/bin/env bash
# Self-check for preflight.sh's branch version rules. Run: bash .github/scripts/test-preflight.sh
set -uo pipefail

source "$(dirname "$0")/preflight.sh"

fail=0
ok() {
  if [ -z "$(version_format_error "$1" "$2")" ]; then echo "ok   — $1 accepts $2"
  else echo "FAIL — $1 should accept $2"; fail=1; fi
}
bad() {
  if [ -n "$(version_format_error "$1" "$2")" ]; then echo "ok   — $1 rejects $2"
  else echo "FAIL — $1 should reject $2"; fail=1; fi
}
must_not_exist() {
  local got
  got=$(versions_that_must_not_exist "$1" "$2" | tr '\n' ' ')
  if [ "$got" = "$3" ]; then echo "ok   — $1 $2 checks: $3"
  else echo "FAIL — $1 $2 checks '$got', want '$3'"; fail=1; fi
}

ok develop 1.1.0
ok working 1.1.0
bad develop 1.1.0-rc.1
bad develop 1.1.0-SNAPSHOT
ok rc/1.0 1.0.0-rc.1
ok rc/1.0 1.0.0-rc.12
bad rc/1.0 1.0.0
bad rc/1.0 1.0.0-rc
bad rc/1.0 1.0.0-rc.1-SNAPSHOT
bad rc/1.0 1.0.0-beta.1
ok release/1.0 1.0.0
bad release/1.0 1.0.0-rc.1
ok feature/x anything-goes

must_not_exist develop 1.1.0 "1.1.0 "
must_not_exist rc/1.0 1.0.0-rc.2 "1.0.0-rc.2 1.0.0 "
must_not_exist release/1.0 1.0.0 "1.0.0 "
must_not_exist feature/x 1.0.0 ""

exit "$fail"
