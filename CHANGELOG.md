# Changelog

Release candidates remain subject to the physical-device checks listed below. Source history is available in [GitHub releases](https://github.com/pnntrn/cctv-tv/releases).

## 0.5.1 — unreleased

- Cluster layout/location/next-page/Settings icons at the top right with48dp Thai-labelled targets; location/page clicks wrap, including fullscreen-to-grid paging. Show location, layout, then page.
- Hide viewing controls after1500ms even with toolbar focus, safely returning focus to content. Preserve open dialog/settings navigation and old picker shortcuts.
- One short Back restores the location's default grid while preserving visible camera identity/page. A second distinct short Back within700ms opens Android TV Home; held/repeated events and dialog/edit cancellation cannot arm Home.
- Show best-effort safe SDP media/session names over generated camera labels. Preserve custom names, propagate labels through H.264/HEVC in-band discovery, and cache sanitized optional metadata in schema3 until the SUB URL changes. No URL-derived names or ONVIF/API expansion.
- Reject generic Video/Audio Media titles and recorder media/RTSP/streaming server identifiers, including vendor prefixes, software versions and separator variants. Revalidate cached detected names; keep saved custom names and specific meaningful titles.
- Add persistent rendered-FPS/frame-health badges based on actual decoder output: connecting, live, no new frames after3000ms, or error. Still scenes remain live as frames render; no synthetic heartbeat or unmeasured bandwidth. Poll every1s and stop on background/release.
- Add host and isolated-emulator regressions for controls, Back, name safety/cache and rendered still/stalled streams. Emulator fixtures do not establish owner-device compatibility, real recorder names or physical decoder capacity.
- Keep playback SUB and retain package/signing/profile compatibility; current private candidate version code1018. Codes1016/1017 were prior installed evaluation builds. Public release/tag/updater publication remains separate approval.

## 0.5.0 — unreleased

- Use SUB for every camera view/test; remove MAIN controls and the unused VLC runtime.
- Back returns home to the CCTV grid. Add idle-hiding toolbar, centered location, layout/location icons and held Left/Right shortcuts.
- Add1/4/6/8/9/16 layouts, including one large plus five small.
- Live channel arrangement with explicit hidden/visible state and staged Save/Cancel.
- Combined single-NVR/location/default-grid setup; paired phone changes require decoded SUB and confirmation on the TV.
- Schema3 reads1/2, preserving every camera and credential while splitting legacy multi-NVR locations. Old unmodified0.4.x cannot read schema3; see rollback requirements.
- Retain package/signing key; version code1015. No recorder encoding change or automatic public release.

## 0.4.5 — release candidate

- Fix HEVC black-screen startup for an observed recorder FU sequence: the IDR start identifies type19, but continuation/end packets report type38. Use the start fragment's key status when completing the NAL instead of misclassifying the end header.
- Accumulate key status across one RTP-marker-delimited picture and clear it on marker/seek. Recognize defined BLA/IDR/CRA random-access pictures (types16–21).
- Add synthetic native decoder regressions for the observed header pattern with and without SDP codec parameters. No private camera footage or credentials are included.
- Retain package, signing key, profiles, recorder encoding and dependencies. Version code:1013. Physical-device verification is recorded in the PR.

## 0.4.4 — release candidate

- Auto-detect codec and give technicians separate substream/mainstream tests, with real decoded-frame results and detected H.264 or H.265/HEVC labels. H.265+ is not claimed as a separately detected mode.
- Preserve decoded-substream saving requirements. Main failures stay separate; retesting, stream replacement, closing and backgrounding invalidate old callbacks. Run only one preview player.
- Ignore unused audio/application SDP before media validation, including multi-format non-video sections; keep video validation strict.
- Add mixed H.264-sub/H.265-main native checks for Hikvision, VIGI NVR and VIGI camera paths, plus a compatibility guide. Actual smart-codec/firmware/TV decoder combinations still need device testing.
- Retain package/key/profiles and NVR encoding; no production dependency. Version code: 1012.

## 0.4.3 — release candidate

- Simplify Mi Box setup: hold OK for one second → choose **ตั้งค่า** → existing safe confirmation. Back closes normally and never requests setup.
- Support H.265/HEVC with no SDP fmtp by discovering real in-band VPS/SPS/PPS with bounded startup; preserve out-of-band HEVC and H.264 paths. Saving still requires a decoded frame.
- Add synthetic HEVC decoder/RTSP controls and encrypted-profile reopen/error-preservation regressions. No profile migration, reset or production dependency change.
- Keep package/key/profile identity; version code 1011. Owner-device diagnosis confirmed H.265 without fmtp and an existing location with no cameras. Historical missing-camera cause remains unconfirmed; verify the candidate on that device.

## 0.4.2 — release candidate

- Support Mi Box remotes without Menu: hold OK for one second for viewing options; inside that menu, hold Back for three seconds to request technician setup, then confirm.
- Keep short OK/Back camera navigation and existing Menu shortcuts. Held-key releases cannot select a menu item or dismiss/accept technician confirmation.
- Cancel pending holds when interrupted or backgrounded; setup still defaults to keep watching. Update Thai help for first setup and normal viewing.
- Preserve the CCTV name, package, retained signing key and profiles. Version code: 1010. Physical Mi Box/Hikvision and upgrade/profile checks remain required.

## 0.4.1 — release candidate

- Restore the remembered camera on its current page after reordering, including when switching locations. Fall back to a valid saved page when the remembered camera no longer exists.
- Correct Android 9 screenshot export and require the complete native UI capture set in CI.
- Add repository contribution, security and release guidance, issue forms and third-party notices.
- Pin the Gradle wrapper and verify signed release assets before tag publication, after build and native TV checks pass.
- Keep the CCTV app name, package `com.pnntrn.cctv`, retained signing identity, saved profile format and legacy updater feed. Version code: 1009.

Real Hikvision playback on the owner's Mi Box, the 11-channel installation, decoder capacity and encrypted profile retention after an in-place signed update still require physical-device verification. Synthetic emulator results do not establish those outcomes.

## 0.4.0 — release candidate

- Thai remote controls, an edge-to-edge grid, four-second idle controls and a separate technician setup gate.
- H.264 in-band initialization for missing SDP configuration, recorder draft recovery and decoded-preview save gating.
- Native TV navigation, playback, draft and lifecycle checks using synthetic fixtures.

See [0.4.0 validation](docs/validation-0.4.0.md) for the recorded results and limitations, and [playback investigation](docs/mi-box-hikvision-playback.md) for historical diagnosis. Older candidate statements in that investigation describe their recorded source state.
