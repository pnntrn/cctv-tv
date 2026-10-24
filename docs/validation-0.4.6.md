# CCTV0.4.6 MAIN playback investigation

Issue: https://github.com/pnntrn/cctv-tv/issues/2
Branch: fix/mainstream-vlc-playback, stacked on the existing TV/codec PR branch.

## Finishing criteria

Use the owner-approved LibVLC SDK for MAIN while preserving SUB/grid, Thai controls, first-frame verification, profiles, package and signing key. Pass unit/lint/build, native H264/HEVC with/without fmtp, lifecycle/connection cleanup and visual checks. Compare the same real20fps MAIN source against the measured1.92fps Media3 baseline; target at least16 displayed fps and owner smoothness confirmation. Verify ARM ABI/size/updater compatibility. Keep evaluation separate from public release: finish SDK distribution materials and obtain explicit release/tag approvals before publication.

## Evidence and implementation

Owner confirms0.4.5 displays video, but MAIN is continuously choppy while smooth in another app. One fullscreen decoder is active. A steady captured source segment has4080 complete pictures, one marker per picture and no sustained sequence loss, ~20fps timestamps and frame delivery median0.03ms/p9530.9ms/max332.9ms. No credentials or private footage are committed.

An opt-in retained-key diagnostic build measured2560x1440 MAIN with20 queued frames/sec but1.92 rendered fps over25s, ~4.2s buffered, zero app dropped/skipped frames and READY state. The physical FPS check failed its16fps target before the backend change. SUB streams rendered normally.

MAIN now uses LibVLC3.7.2 over TCP, hardware decode requested and one FFmpeg thread. SUB/grid remains Media3. Existing SurfaceView/aspect fit and first-frame callback contract remain; VLC success requires displayedPictures to advance, not merely Playing. Late callbacks/timeouts/teardown are guarded. Stop-detach-release order and shared-engine reference counts prevent resource retention. Diagnostics are opt-in aggregate data only. Thai transport labels accurately say MAIN always TCP and the location toggle affects SUB.

Local full unit/lint/release/AndroidTest build passed:61 unit methods, zero failures/errors, zero lint errors. Release review found JNI callbacks removed by shrinking; explicit keep rules fixed it. Release seeds/mapping retain the native factory/callback methods and mInstance. Targeted rebuilds and native APK/lint compile passed. Compressed ARM32/64 release is about45MB, below the old64MiB updater limit; debug retains Intel ABIs. The ARM APK is not an Intel-TV update. Native tests cover real H264/HEVC frames with/without fmtp, expected engine, actual codec and connection cleanup.

Two signed private ARM evaluations passed CI unit/lint/build and retained-certificate verification, then installed with install-r on the owner's Mi Box without clearing data. The 500ms buffer evaluation measured13.60 displayed fps over60s; the 1500ms hardware-preroll evaluation measured13.73fps over30s. Both remain below the16fps target despite approximately20 decoded fps and zero reported lost pictures. Increasing the buffer has not established an improvement. The current evaluation uses1500ms; no smoothness claim is justified. Back returned to SUB/grid and reopening MAIN worked on the physical Box.

The owner confirms the1500ms evaluation remains continuously choppy. The physical Box reports5GHz Wi-Fi, RSSI-59dBm and433Mbps negotiated link speed. Total incoming Wi-Fi traffic averaged4.25Mbps over41.9s during fullscreen playback. Link speed is not a throughput or packet-loss guarantee; these observations do not establish bandwidth as the cause.

The initial PR native job timed out after three tests. Local reproduction found the synthetic RTSP server rejected libVLC's optional audio SETUP; it now negotiates separate bounded TCP channel pairs while still requiring exactly one video SETUP, actual decoded pictures and closed connections. Intentional client-close resets/broken pipes are accepted only after a test explicitly begins teardown. A new synthetic-credential failure test also proved quiet alone does not suppress Android native URL errors; verbose=-1 fixes that leak. All16 codec/privacy/lifecycle tests passed locally after these changes. A new full unit/lint/release/AndroidTest build passed. CI now records individual test progress and preserves codec evidence on a bounded class timeout.

The final CI gate, remaining physical performance investigation and complete SDK distribution materials remain pending. The owner separately approved GPLv3 for original CCTV code; the root license and bundled project license have been applied while preserving upstream notices. No version tag, public release, updater-feed promotion or merge is approved by these results.
