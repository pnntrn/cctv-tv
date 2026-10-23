# Validation — CCTV 0.2

23 October 2026, Asia/Bangkok.

## Local checks

- Original 48 pure Java checks passed.
- 31 extended checks passed: recorder updates retain IDs/names/order, range changes, custom-camera preservation, legacy URL linking, PIN/session authorization and expiry, safe error categories, unauthenticated and Basic-authenticated RTSP DESCRIBE against loopback fixtures.
- All Java sources syntax parsed. Android resource XML and workflow YAML parsed.
- CCTV logo and native grid/fullscreen/recorder screenshots visually inspected.
- MD5 Digest authentication checked locally against a loopback fixture with and without qop, including escaped username, password, path and query characters (`scripts/check-digest.sh`).

## CI verification

[Workflow 39948381626](https://github.com/pnntrn/cctv-apk/actions/runs/39948381626) passed for application commit `85cbc4a9987cefd1324e5c8f1cc38a85da0faa2e`:

- Android unit tests (including HTTP setup protocol tests), lint, debug/release builds and instrumentation-test APK build succeeded.
- The native Android landscape emulator smoke test passed: grid focus, fullscreen/back, settings, recorder editing, pairing-dialog dismissal, reorder/save and reopen persistence.
- Native screenshot output is available in the workflow's `native-ui-screenshots` artifact. The grid, fullscreen and recorder captures were visually checked.
- Earlier emulator checks caught missing initial focus in touch mode and a malformed wordmark path; both were fixed before this successful run.

The HTTP protocol tests check the public pairing page, rejection of unauthenticated and cross-origin requests, pairing cookies, authenticated settings access, refusal to save without testing, single-use tested-draft proofs and server closure. The emulator flow captures native screens and checks grid/fullscreen/back, recorder settings and arrangement persistence.

## Remaining requirements

- A retained RSA-3072 identity was generated and privately backed up outside git. `CCTV_0.2.apk` (428,093 bytes) was signed and verified using APK signature schemes v2 and v3. Its certificate fingerprint matches the retained identity.
- Automatic future release signing still requires the `CCTV_SIGNING_BUNDLE` GitHub repository secret. No signing identity is stored in git or public artifacts.
- The supplied Windows `ui-ux-pro-max` skill path was not accessible in this workspace or installed skill catalogs. The redesign does not claim to apply that skill.
- Real Hikvision/VIGI playback, phone access from another physical device, power-cycle behavior, performance and maximum simultaneous hardware decoders require the user's TV stick and network.
- Emulator fixture images show intentional loopback/offline streams, not real CCTV footage.
