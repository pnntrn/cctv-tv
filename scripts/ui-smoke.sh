#!/usr/bin/env bash
set -euo pipefail
# Fixture reset is permitted only on an emulator, never on an owner’s TV.
case "$(adb get-serialno)" in emulator-*) ;; *) echo 'Native fixtures require an emulator'; exit 1;; esac
mkdir -p ui-results
adb shell wm size 1920x1080
adb shell wm density 320
adb install -r "$(find test-apks -name app-debug.apk -print -quit)"
adb install -r "$(find test-apks -name app-debug-androidTest.apk -print -quit)"
: > ui-results/instrumentation.txt
for test_class in InBandPlaybackTest RecorderDraftFlowTest TvFlowTest; do
  adb shell pm clear com.pnntrn.cctv >/dev/null
  adb shell am instrument -w -e class "com.pnntrn.cctv.$test_class" com.pnntrn.cctv.test/androidx.test.runner.AndroidJUnitRunner | tee -a ui-results/instrumentation.txt
done
for name in grid fullscreen settings recorder updates; do
  adb exec-out run-as com.pnntrn.cctv cat "files/ui-$name.png" > "ui-results/$name.png" || rm -f "ui-results/$name.png"
done
# Instrumentation may exit zero even when assertions fail.
python3 - <<'PYCODE'
from pathlib import Path
import re
for p in Path('ui-results').glob('*.png'):
    if not p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
        p.unlink()
output=Path('ui-results/instrumentation.txt').read_text()
assert len(re.findall(r'OK \([0-9]+ tests?\)',output)) == 3, 'Android instrumentation assertions failed'
PYCODE
