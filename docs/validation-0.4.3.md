# CCTV 0.4.3 / 1011 — simpler setup and HEVC compatibility

## Approved behavior

Hold OK for one second, select **ตั้งค่า**, then use the existing technician confirmation (default: **ดูต่อ**). Back closes the menu regardless of press length and never requests setup. Existing Menu shortcuts remain. The idle grid and camera viewing workflow are unchanged.

## Device evidence and diagnosis

The owner installed retained-key 0.4.2 in place on Mi Box Android 9. Follow-up diagnosis confirmed the location still exists but has no cameras; deletion of that location was not established. Recorder preview could not enable Save channels because playback failed with `IllegalArgumentException: missing attribute fmtp`. Authenticated DESCRIBE confirmed video H265/90000 with no fmtp and unused PCMU/8000 audio. No password, authenticated URL, raw SDP or camera footage is included here.

The vendored Media3 1.8.0 RTSP module, ProfileStore and manifest are identical between v0.4.1-rc.1 and v0.4.2-rc.1. H264 already supports missing fmtp, while H265 still required it. The new H265 unit regression reproduced the exact exception before implementation. The compatibility change discovers real in-band VPS/SPS/PPS and feeds the existing marker-aware H265 RTP reader; it does not invent codec data or bypass decoded-frame gating. Existing configured HEVC and H264 paths remain covered. Discovery is bounded to 8 seconds, 4 MiB of RTP payload and 4,096 packet records; complete NAL assembly is capped at 2 MiB. Empty/short payloads are rejected before retention. Historical missing-camera root cause is unconfirmed.

## Verification

- Native Settings fail-first CI [40061705529](https://github.com/pnntrn/cctv-tv/actions/runs/40061705529) failed at the expected absent Settings choice; its other native methods passed.
- Release-only Back unit regression failed against the previous 3-second behavior, then passed. HEVC missing-fmtp unit tests reproduced the exact parser exception before passing with real synthetic parameter sets.
- Local full Gradle test/lint/debug/release/AndroidTest build passed. Lint has zero errors and 20 existing warnings. The final suite has 44 unit methods with zero failures/errors. Native publication evidence is recorded in the PR after the final checks.
- 48 core, 38 extended, 22 updater, both MD5 Digest variants, 12 release-verifier tests and actionlint passed.
- Synthetic HEVC fixture: FFmpeg/libx265 testsrc2, 320×180, 5 fps, ten frames, no private video. Native coverage adds an independent Android decoder oracle, configured-HEVC control, missing-fmtp HEVC grid/fullscreen first-frame callbacks, and connection release; H264 controls remain.
- Synthetic encrypted-profile tests verify NVR/camera identities, credentials, page/grid selection after Activity recreation/close/relaunch; a separate test verifies unreadable files show an error and are not overwritten on close. These are not actual signed-upgrade or owner-profile recovery tests.
- No local ADB commands or owner-device operations were performed during this change. Native checks run in GitHub's isolated emulator.

## Distribution and limits

Publish only through tag build/native/signing gates as `v0.4.3-rc.1`, version 0.4.3/code1011, package `com.pnntrn.cctv`, minimum API 26. Retained certificate SHA-256: `4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b`. No storage schema/key change, reset, new production dependency, NVR encoder change or legacy updater feed change. Original-code license remains undecided; PR stays unmerged.

Actual owner-device HEVC decoding, saved 11 channels, capacity, physical remote and signed-update profile retention require device verification. Do not uninstall or clear app data to apply this retained-key update. Public APK hash/version/signature checks will be recorded in the PR after publication.
