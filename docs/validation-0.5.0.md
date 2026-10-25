# CCTV0.5.0 validation snapshot

Issue:#4. Draft PR:https://github.com/pnntrn/cctv-tv/pull/5 . Package com.pnntrn.cctv, code1015, minAPI26. No public tag/release/feed promotion or merge is approved by this record.

## Scope and finishing criteria

SUB only, including fullscreen and technician/phone confirmation. Back returns to the app grid; layouts1/4/6/8/9/16; idle toolbar and safe held-key shortcuts; one NVR per location; staged live channel ordering/visibility; lossless encrypted schema3 migration. Require unit/lint/build, actual decoder/native UI checks, independent review and an honest real-Box capacity report. No recorder encoding changes or profile wipe.

## Local evidence before final CI

- Baseline61 host tests passed. New layout/migration APIs were RED before implementation; the foundation host suite passed afterward. Independent review caught missing-cell navigation on partial six-camera pages; two regressions failed before the fix and the23-test geometry/hold suite passed afterward. Scoped re-review accepted it.
- Distinct synthetic SUB/MAIN streams reproduced wrong routing before the change.18 actual playback methods passed after SUB-only routing, including H264/HEVC, missing/present fmtp, vendor FU continuation, invalid MAIN/no fallback, credential privacy, release and six simultaneous decoded320x180 synthetic feeds. The six-feed capture is not a six-HD-stream physical capacity claim.
- Initial7 new workflow UI methods passed;5 existing draft,1 TV integration and7 existing viewing methods passed after adapting obsolete MAIN expectations to SUB-only behavior. Callback-only UI contract tests remain separate from real decoder tests.
- Final review on898500c found empty/all-hidden toolbar focus recovery and unrelated NVR edits resetting the selected layout. Background arrangement camera-identity drift was included in the same fix wave. Four new regressions failed before fixes; two default-layout controls passed. Commit e95e13b passed all13 workflow UI tests in37.153s and all74 host unit tests. Scoped re-review marked all findings addressed, with no material new breakage.
- Initial CI's toolbar test failed because it requested focus without proving it succeeded in Android touch mode. The test now injects a real D-pad Up and asserts focused toolbar before the unchanged4.3s idle/shortcut assertions. The targeted case and whole13-case workflow class passed afterward.
- Unit/lint/debug/minified release/AndroidTest build passed before the final fix wave; final source lint/release and CI are required before delivery. Additional scripts passed:56 core,38 extended,22 updater checks; Digest authentication with/without qop;12 Python release-verifier tests.

## Distribution and physical checks

Final-source CI, retained-key APK verification, in-place Mi Box upgrade, saved-location preservation and real six-substream counters are recorded in the PR as they finish. They were pending when this snapshot was committed; consult the current PR results rather than treating the historical baseline CI as final approval. Real logs/screenshots/credentials remain outside Git. Repository screenshots contain synthetic fixtures only.

The original project GPLv3 notice remains bundled; Media3 notices remain. LibVLC, its adapter, native libraries and APK notices are removed. Its source investigation remains historical evidence only.

## Recovery

Schema3 reads schemas1/2 without changing the Keystore alias/key. Every camera ID/credential is retained while legacy multi-NVR locations are split. Old unmodified0.4.x code rejects schema3. A rollback must keep schema3 model/reader/writer and visibility/migration support while reverting a defective behavior, use the retained signing key and a higher version code, and be tested before distribution approval. Never uninstall, clear data or force a downgrade.
