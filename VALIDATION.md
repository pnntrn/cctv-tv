# Validation — CCTV 0.2

23 October 2026, Asia/Bangkok.

## Local checks

- Original 48 pure Java checks passed.
- 31 extended checks passed: recorder updates retain IDs/names/order, range changes, custom-camera preservation, legacy URL linking, PIN/session authorization and expiry, safe error categories, unauthenticated and Basic-authenticated RTSP DESCRIBE against loopback fixtures.
- All Java sources syntax parsed. Android resource XML and workflow YAML parsed.
- CCTV logo rendered and visually inspected.

## CI verification

This revision adds unit HTTP protocol tests and an Android landscape emulator smoke test. Verify the latest workflow run before treating a new APK as tested.

The HTTP protocol tests check the public pairing page, rejection of unauthenticated and cross-origin requests, pairing cookies, authenticated settings access, refusal to save without testing, single-use tested-draft proofs and server closure. The emulator flow captures native screens and checks grid/fullscreen/back, recorder settings and arrangement persistence.

## Remaining requirements

- Permanent release signing is implemented but requires the `CCTV_SIGNING_BUNDLE` GitHub repository secret. No signing identity is stored in git or public artifacts.
- The supplied Windows `ui-ux-pro-max` skill path was not accessible in this workspace or installed skill catalogs. The redesign does not claim to apply that skill.
- Real Hikvision/VIGI playback, phone access from another physical device, power-cycle behavior, performance and maximum simultaneous hardware decoders require the user's TV stick and network.
- Emulator fixture images show intentional loopback/offline streams, not real CCTV footage.
