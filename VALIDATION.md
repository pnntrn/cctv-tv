# Validation — CCTV 0.3.0 (1006)

## Passed locally

- Android build: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`, Gradle 8.11.1, JDK 17, Android SDK 35. Build succeeded; lint reported no errors.
- Five JUnit tests passed, including the core/extended checks, HTTP phone-setup tests and update checks.
- Standalone checks: 48 core, 31 extended, 22 update checks. Update checks cover HTTPS policy, version/size/digest rules, truncated/oversized/corrupt downloads, cancellation, late connection publication after cancellation, and JSON nesting limits.
- Independent code review found activity-recreation recovery, shared download-file races, cancellation publication, and JSON recursion concerns. Fixed by saving pending release state and re-verifying on return, serializing transfers with unique temporary files, synchronized cancellation/connection publication, and a JSON depth guard. Android lifecycle/installer behavior still needs device validation.
- Signed optimized APK: 430,842 bytes; package `com.pnntrn.cctv`, version 0.3.0, code 1006, minimum API 26.
- APK v2 signature verified. One RSA-3072 signer matches the retained 0.2 release identity: `4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b`.
- APK SHA-256: `a43862eee23d484b36ffe4158f9aa561dd32eaa2dded212032777ff1ec591bb0`.

## GitHub and distribution

The private source repository is `pnntrn/cctv-apk`. Its `CCTV_SIGNING_BUNDLE` Actions secret is configured with the retained release key. Private signing backups remain outside git.

[Workflow 39983382213](https://github.com/pnntrn/cctv-apk/actions/runs/39983382213) could not start: GitHub reported failed account payments or a spending limit requiring attention. No build steps ran in that workflow. The new release was built and verified locally instead.

The separate public repository `pnntrn/cctv-updates` contains only APKs and update information. Its `latest.json` references an immutable APK commit. No camera data, source files or signing secrets are published.

## Remaining validation

- The updated native emulator smoke test was attempted locally without hardware acceleration and has not completed. Do not claim the new updater UI or Android installer flow passed an emulator/device test.
- The prior 0.2 native flow passed [workflow 39948381626](https://github.com/pnntrn/cctv-apk/actions/runs/39948381626). Existing screenshots use loopback/offline fixtures, not real camera footage.
- Check update download, source permission, installation confirmation and saved-profile preservation on a real TV stick. Real Hikvision/VIGI playback, phone LAN access, decoder limits and power-cycle behavior also require physical hardware/network testing.
