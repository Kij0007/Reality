#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."

test_status=0
capture_dir="/sdcard/Download/RealityVerification-$(date +%s)-$$"
./gradlew --no-daemon connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.realityScreenshotDir=$capture_dir" || test_status=$?

report_dir=app/build/reports/androidTests/screenshots
mkdir -p "$report_dir"
capture_status=0
for screen in login settings register validation; do
  temporary="$report_dir/$screen.png.tmp"
  rm -f "$temporary" "$report_dir/$screen.png"
  if ! adb pull "$capture_dir/verification-$screen.png" "$temporary"; then
    echo "Missing native screenshot: $screen" >&2
    capture_status=1
    continue
  fi
  if python3 - "$temporary" <<'PY'
import pathlib, struct, sys
path = pathlib.Path(sys.argv[1])
data = path.read_bytes()
assert len(data) > 1024, f'{path}: screenshot is too small'
assert data[:8] == b'\x89PNG\r\n\x1a\n', f'{path}: not a PNG'
assert data[12:16] == b'IHDR', f'{path}: missing PNG dimensions'
width, height = struct.unpack('>II', data[16:24])
assert width > 0 and height > 0, f'{path}: invalid PNG dimensions'
assert data[-12:] == b'\x00\x00\x00\x00IEND\xaeB`\x82', f'{path}: incomplete PNG'
print(f'Native screenshot verified: {path.name} ({width}x{height}, {len(data)} bytes)')
PY
  then
    mv "$temporary" "$report_dir/$screen.png"
  else
    rm -f "$temporary"
    capture_status=1
  fi
done
if (( test_status != 0 )); then
  exit "$test_status"
fi
exit "$capture_status"
