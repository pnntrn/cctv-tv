# Changelog

Release candidates remain subject to the physical-device checks listed below. Source history is available in [GitHub releases](https://github.com/pnntrn/cctv-tv/releases).

## 0.4.1 — release candidate

- Restore the remembered camera on its current page after reordering, including when switching locations. Fall back to a valid saved page when the remembered camera no longer exists.
- Add repository contribution, security and release guidance, issue forms and third-party notices.
- Pin the Gradle wrapper and verify signed release assets before tag publication, after build and native TV checks pass.
- Keep the CCTV app name, package `com.pnntrn.cctv`, retained signing identity, saved profile format and legacy updater feed. Version code: 1009.

Real Hikvision playback on the owner's Mi Box, the 11-channel installation, decoder capacity and encrypted profile retention after an in-place signed update still require physical-device verification. Synthetic emulator results do not establish those outcomes.

## 0.4.0 — release candidate

- Thai remote controls, an edge-to-edge grid, four-second idle controls and a separate technician setup gate.
- H.264 in-band initialization for missing SDP configuration, recorder draft recovery and decoded-preview save gating.
- Native TV navigation, playback, draft and lifecycle checks using synthetic fixtures.

See [0.4.0 validation](docs/validation-0.4.0.md) for the recorded results and limitations, and [playback investigation](docs/mi-box-hikvision-playback.md) for historical diagnosis. Older candidate statements in that investigation describe their recorded source state.
