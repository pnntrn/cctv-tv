# CCTV 0.4.2 (1010) — Mi Box remote validation

## Approved controls

Hold OK for 1,000 ms to open viewing options. While that menu is open, hold Back for 3,000 ms to request technician setup, then confirm. The confirmation defaults to keep watching. Short OK/Back retain their camera and navigation actions; Menu shortcuts remain available on remotes with that button. Thai hints cover empty first launch, the grid, fullscreen and the viewing menu.

## Local checks — 24 October 2026

Java 17, checksum-pinned Gradle wrapper 8.11.1, SDK 35/build-tools 35.0.0; isolated API 28 x86_64 emulator at physical 1920×1080. No owner TV or private camera used.

- Native fail-first proof against the previous implementation: the new Menu-free setup test failed at the expected assertion that holding OK must open viewing options.
- Typed hold-state tests first failed to compile for the missing new API, then passed 12/12 after implementation. Full Gradle `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest`: BUILD SUCCESSFUL, 34 unit methods, zero failures/errors. Lint: zero errors, 20 existing warnings.
- Standalone checks: 48 core, 38 extended, 22 updater and both MD5 Digest variants passed. Release verifier 12 tests and actionlint passed. No production dependency added.
- Full native harness passed 14 methods: playback 4 (5.275s), recorder drafts 4 (16.682s), general Thai navigation 1 (19.849s), viewing/safety 5 (98.525s). Includes Menu-free first setup, both OK key codes, exact short fullscreen toggle, long-press popup transitions, initiating repeat/release suppression, cancellation, backgrounding, page restoration and existing Menu safety.
- All 12 required 1920×1080 PNGs exported. Menu choices and Thai technician hint remain visible together; confirmation defaults to keep watching. Initial empty-screen capture caught a transition; its test now waits for the visible help text and UI idle before capture. Focused repeat runs exposed unsynchronized input-window transitions. The tests now await actual Activity/dialog focus before new gestures and before delivering the initiating held key to a new popup. Both final focused Mi Box tests passed separately (18.519s and 24.498s); final empty/menu/confirmation screenshots were inspected. App production sources stayed unchanged throughout these fixture corrections.
- Scoped and whole-change code reviews passed with no blocking finding; the final test synchronization was re-reviewed. Remaining non-blocking coverage limit: focus regression invokes Activity callbacks directly; actual external-overlay Dialog-window focus-loss is not independently driven by a native test. The production dialog decor focus listener is reviewed. Actual Mi Box remote/system-overlay behavior remains an owner-device check.

## Distribution and remaining device checks

Publish as `v0.4.2-rc.1` only through signed tag automation after build/native gates. PR and tag checks may run in parallel on the same verified source. The app remains CCTV, package `com.pnntrn.cctv`, version 0.4.2/code 1010, minimum API 26. Retained release certificate SHA-256: `4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b`. Profile format, Android Keystore identity and legacy updater feed are unchanged; original project licensing is undecided.

Authenticated Hikvision playback, the owner's Mi Box physical remote, all 11 owner channels, decoder capacity and profile preservation after an in-place signed update remain unverified on hardware. Synthetic tests do not establish those outcomes. Install retained-key updates over the existing signed app; uninstalling deletes profiles.
