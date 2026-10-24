# Codec checks implementation plan

**Goal:** Auto-detect playback codec and test both streams in technician setup.
**Architecture:** Existing StreamTile remains automatic. A pure StreamCheckState owns per-stream verification and generation guards; existing preview hosts one replaceable player, two test buttons and independent results. SDP parsing ignores unused media before validation.
**Spec:** ../specs/2026-10-24-codec-checks-design.md
**Execution:** Inline parent for app; independent worker owns only SDP parser + VendorSdpTest, independent final review.

## Constraints and review focus

Same package/key/profiles; no dependency or recorder encoder change. Version0.4.4/code1012 candidate. No local ADB. Preserve substream Save gating, safe Back/background release, one decoder, independent main failure and stale callback suppression. Actual H265+ firmware/decoder remains a device check.

## Tasks

- [x] Add StreamCheckStateTest first: only SUB first frame enables saving; stale/retested/failed/background callbacks rejected; MAIN failure does not masquerade as SUB result. Observe missing-API RED, implement state and preview UI/codec labels, verify GREEN.
- [ ] Add native UI callback contract and three real H264-sub/H265-main codec/path matrix methods for Hikvision/VIGI_NVR/VIGI_CAMERA; verify actual decoded frames and both results, not only RTSP handshake. Export sub/main captures.
- [x] Worker observes unused dynamic audio/application parser RED, skips non-video sections and proves exact VIGI sample/strict video validation GREEN.
- [x] Full build/unit/lint, core/digest/updater/release checks; independent review; fix failures. Document vendor sources and honest compatibility scope.
- [ ] Push reviewed PR/tag with signed gates, require native GREEN, verify public bytes/version/certificate, update main download link and PR evidence, leave unmerged.
