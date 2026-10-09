#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."

test_status=0
./gradlew --no-daemon connectedDebugAndroidTest || test_status=$?

mkdir -p app/build/reports/androidTests/screenshots
for screen in home settings activities tracking form more; do
  adb exec-out run-as com.reality.android.debug cat "files/verification-$screen.png" > "app/build/reports/androidTests/screenshots/$screen.png" || true
done
exit "$test_status"
