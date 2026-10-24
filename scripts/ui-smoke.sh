#!/usr/bin/env bash
set -euo pipefail
# Fixture reset is permitted only on an emulator, never on an owner’s TV.
case "$(adb get-serialno)" in emulator-*) ;; *) echo 'Native fixtures require an emulator'; exit 1;; esac
mkdir -p ui-results
adb shell wm size reset
adb shell wm density reset
adb shell wm size | tee ui-results/display.txt
adb shell wm density | tee -a ui-results/display.txt
python3 - <<'DISPLAY'
from pathlib import Path
assert 'Physical size: 1920x1080' in Path('ui-results/display.txt').read_text(), 'Use the tv_1080p AVD profile'
DISPLAY
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell setprop log.tag.CCTVPlayback DEBUG
adb install -r "$(find test-apks -name app-debug.apk -print -quit)"
adb install -r "$(find test-apks -name app-debug-androidTest.apk -print -quit)"
: > ui-results/instrumentation.txt
for test_class in InBandPlaybackTest RecorderDraftFlowTest TvFlowTest ViewingFlowTest SubstreamWorkflowTest; do
  adb shell pm clear com.pnntrn.cctv >/dev/null
  adb logcat -c
  # Preserve the exact running test and codec evidence if a native call stalls.
  set +e
  timeout 8m adb shell am instrument -w -r -e class "com.pnntrn.cctv.$test_class" com.pnntrn.cctv.test/androidx.test.runner.AndroidJUnitRunner | tee -a ui-results/instrumentation.txt
  test_status=${PIPESTATUS[0]}
  set -e
  adb logcat -d -v brief -s CCTVPlayback ExoPlayerImplInternal MediaCodecVideoRenderer ACodec OMXClient MediaCodec SoftAVC SoftAVCDec OMXNodeInstance > "ui-results/$test_class-codec.txt"
  if [ "$test_status" -ne 0 ]; then
    echo "Native class $test_class failed or timed out (exit $test_status)"
    exit "$test_status"
  fi
  # Save each class's captures before the next clean-install fixture reset removes them.
  for name in grid fullscreen settings recorder updates idle viewing-menu technician-entry technician-empty mi-box-empty mi-box-menu mi-box-technician codec-sub six six-live; do
    if adb shell run-as com.pnntrn.cctv ls "files/ui-$name.png" >/dev/null 2>&1; then
      adb exec-out run-as com.pnntrn.cctv cat "files/ui-$name.png" > "ui-results/$name.png"
    fi
  done
done
# Instrumentation may exit zero even when assertions fail.
python3 - <<'PYCODE'
from pathlib import Path
import re, struct
for p in Path('ui-results').glob('*.png'):
    data=p.read_bytes()
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        p.unlink()
    else:
        assert struct.unpack('>II',data[16:24]) == (1920,1080), 'Native UI screenshots must use physical TV geometry'
required={'grid','fullscreen','settings','recorder','updates','idle','viewing-menu','technician-entry','technician-empty','mi-box-empty','mi-box-menu','mi-box-technician','codec-sub','six','six-live'}
assert required <= {p.stem for p in Path('ui-results').glob('*.png')}, 'Required native screenshots are missing'
output=Path('ui-results/instrumentation.txt').read_text()
assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed', output), 'Android instrumentation failed or crashed'
assert len(re.findall(r'OK \([0-9]+ tests?\)',output)) == 5, 'Android instrumentation assertions failed'
PYCODE
