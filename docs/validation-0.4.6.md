# CCTV0.4.6 MAIN playback investigation

Issue: https://github.com/pnntrn/cctv-tv/issues/2
Branch: fix/mainstream-vlc-playback, stacked on the existing TV/codec PR branch.

## Finishing criteria

Use the owner-approved LibVLC SDK for MAIN while preserving SUB/grid, Thai controls, first-frame verification, profiles, package and signing key. Pass unit/lint/build, native H264/HEVC with/without fmtp, lifecycle/connection cleanup and visual checks. Compare the same real20fps MAIN source against the measured1.92fps Media3 baseline; target at least16 displayed fps and owner smoothness confirmation. Verify ARM ABI/size/updater compatibility. Keep evaluation separate from public release: finish SDK distribution materials and obtain explicit release/tag approvals before publication.

## Evidence and implementation

Owner confirms0.4.5 displays video, but MAIN is continuously choppy while smooth in another app. One fullscreen decoder is active. A steady captured source segment has4080 complete pictures, one marker per picture and no sustained sequence loss, ~20fps timestamps and frame delivery median0.03ms/p9530.9ms/max332.9ms. No credentials or private footage are committed.

An opt-in retained-key diagnostic build measured2560x1440 MAIN with20 queued frames/sec but1.92 rendered fps over25s, ~4.2s buffered, zero app dropped/skipped frames and READY state. The physical FPS check failed its16fps target before the backend change. SUB streams rendered normally.

MAIN now uses LibVLC3.7.2 over TCP,500ms caching, hardware decode requested and one FFmpeg thread. SUB/grid remains Media3. Existing SurfaceView/aspect fit and first-frame callback contract remain; VLC success requires displayedPictures to advance, not merely Playing. Late callbacks/timeouts/teardown are guarded. Stop-detach-release order and shared-engine reference counts prevent resource retention. Diagnostics are opt-in aggregate data only. Thai transport labels accurately say MAIN always TCP and the location toggle affects SUB.

Local full unit/lint/release/AndroidTest build passed:61 unit methods, zero failures/errors, zero lint errors. Release review found JNI callbacks removed by shrinking; explicit keep rules fixed it. Release seeds/mapping retain the native factory/callback methods and mInstance. Targeted rebuilds and native APK/lint compile passed. Compressed ARM32/64 release is about45MB, below the old64MiB updater limit; debug retains Intel ABIs. The ARM APK is not an Intel-TV update. Native tests cover real H264/HEVC frames with/without fmtp, expected engine, actual codec and connection cleanup.

CI/native, signed LibVLC evaluation APK, physical FPS/owner smoothness, and complete SDK distribution materials remain pending and will be recorded in the Draft PR. The owner separately approved GPLv3 for original CCTV code. No version tag, public release, updater-feed promotion or merge is approved by these results.
