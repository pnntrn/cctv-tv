#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-results
adb shell wm size 1920x1080
adb shell wm density 320
adb install -r "$(find test-apks -name app-debug.apk -print -quit)"
adb install -r "$(find test-apks -name app-debug-androidTest.apk -print -quit)"
adb shell am instrument -w com.pnntrn.cctv.test/androidx.test.runner.AndroidJUnitRunner | tee ui-results/instrumentation.txt
for name in grid fullscreen settings recorder; do
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
assert re.search(r'OK \([0-9]+ tests?\)',output), 'Android instrumentation assertions failed'
PYCODE
