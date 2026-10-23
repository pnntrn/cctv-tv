# CCTV 0.4.1 (1009) validation

## Local verification — 24 October 2026

Java 17, checksum-pinned Gradle wrapper 8.11.1, Android SDK 35/build-tools 35.0.0. Native tests use an isolated API 28 x86_64 emulator with a physical 1920×1080 display and synthetic fixtures only.

- Full wrapper command `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest`: BUILD SUCCESSFUL on final application sources. 28 JUnit methods, zero failures/errors. Lint: zero errors; 16 app and four vendored-module warnings. Local release output is unsigned; CI uses the retained signing identity.
- Standalone checks: 48 core, 38 extended, 22 updater, both MD5 Digest authentication variants passed.
- Release verifier: 12 Python tests passed, covering metadata, minimum Android, source version code/name, tag matching and retained signer. The actual unsigned optimized APK was rejected by apksigner before publication packaging.
- Official Gradle distribution checksum: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`; wrapper JAR: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`. Generated JAR matches; wrapper downloaded and built successfully with distribution validation.
- GitHub workflow pins supported Node 24 actions and Ubuntu 24.04. Official action inputs were crosschecked, and the workflow passes actionlint 1.7.12. Workflow, Dependabot and issue-form YAML parse; project-guidance relative links resolve; whitespace checks pass.
- Native playback: four tests passed (11.011s); recorder drafts: four (50.222s); Thai navigation: one (79.521s). These ran on the initial 0.4.1 application build; the later change is limited to Bundle camera restoration.
- Final rebuilt native viewing class: three tests passed (77.023s), covering existing idle geometry/remote safety/technician lifetime, switching locations with camera/page disagreement, and Activity recreation after reordering/removing the selected camera. Total native methods across classes: 12, zero failures.
- Inspected final 1920×1080 idle grid and viewing menu screenshots. The idle grid has no decorative border or menu, and offline fixture errors stay visible. Menu controls are Thai and contain viewing options only.
- Independent review found two gaps (Bundle restoration and release version-code validation); both were fixed and re-reviewed with no remaining findings. No production dependency added.

## Publication contract

The public repository is `pnntrn/cctv-tv`; CCTV app name, package `com.pnntrn.cctv`, profile format and retained signing certificate are unchanged. Description, topics and private vulnerability reporting were verified after renaming. Original-code licensing remains undecided; required third-party notices are bundled and attached.

The tag workflow requires build and native TV jobs, then verifies the signed APK package, source version name/code, matching tag, retained certificate and bundled notices before publishing. PR builds do not load signing credentials. The separate legacy updater feed remains unchanged. The review PR remains unmerged.

## Required physical-device checks

Actual authenticated Hikvision/Mi Box playback, the owner's 11-channel installation, hardware decoder capacity and encrypted profile preservation after a signed in-place update remain unverified. The APK is a release candidate until those owner-device checks pass. No owner TV was reset or uninstalled.
