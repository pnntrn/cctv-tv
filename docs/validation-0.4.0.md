# CCTV 0.4.0 (1008) validation

## Local checks — 24 October 2026

Toolchain: Java 17, Gradle 8.11.1, Android SDK 35, API 28 x86_64 TV emulator with accelerated physical 1920×1080 display at 320dpi. Tests used synthetic video and loopback camera fixtures only.

- `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest`: BUILD SUCCESSFUL. All 24 JUnit methods passed with zero failures/errors. Lint: zero errors; 16 app warnings and four vendored-module warnings. The local optimized release was unsigned; publication uses the retained signing key in GitHub Actions.
- Standalone scripts: 48 core checks, 38 extended checks, 22 updater checks, and both MD5 Digest authentication variants passed.
- Native playback: four tests passed, including independent MediaCodec decoding, valid-SDP preview, in-band missing-fmtp preview/fullscreen, and RTSP connection release.
- Recorder drafts: four tests passed, including raw non-secret values across background/recreation, deliberate technician reentry, password exclusion, stale preview callbacks, 11-channel save, retained channel identity and releasing playback on stop.
- Thai navigation: recorder editing, updater dialog, pairing entry, reorder persistence and reopening passed. Moving cameras is now performed in technician setup; the same durable camera IDs are verified afterward.
- Viewing safety: one comprehensive native test passed for exact full-grid bounds, zero padding/margins, four-second chrome hiding, stream identity preservation on reveal, viewing-only Menu, long-OK protection, all 11 channels across pages, fullscreen/back selection retention, three-second Menu entry, safe default focus, dialog cleanup, deleting the last location, empty setup recovery and ending technician access on background.
- Total native result: ten methods, zero failures. Final recorded class durations: playback 16.662s, drafts 54.046s, Thai navigation 26.027s, viewing safety 51.301s.
- Inspected full-resolution grid, idle, setup, recorder form, viewing popup and technician-entry/empty-setup screenshots. Thai text remains readable; the idle grid occupies the whole display without decorative borders. Offline errors remain visible as intended.
- Independent code review found and verified corrections for empty-location setup recovery and dismissed-dialog retention. Native testing also verified the fix for safe Cancel focus after touch input. No new production dependency was added.

## Distribution

The repository is public. The signed release candidate is published under `v0.4.0-rc.1` in this same repository after its GitHub Actions build and native checks pass. The APK package is `com.pnntrn.cctv`, version name `0.4.0`, code `1008`, minimum Android API `26`.

The retained release certificate SHA-256 is `4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b`. Verify the downloaded APK signature and the published SHA256SUMS before distribution. Signing files remain private in the repository Actions secret; they are not part of source or release assets.

## Physical-device work still required

Authenticated Hikvision SDP, actual decoded Mi Box playback, the owner's 11-channel setup, hardware decoder capacity, and profile retention after an in-place signed update are not established by synthetic emulator tests. The release is a candidate for that verification. The legacy in-app update feed has not been changed, and this task does not uninstall the owner's app or alter TV security settings.
