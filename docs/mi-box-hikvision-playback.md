# Mi Box / Hikvision consolidated playback follow-up

Status: draft. This change fixes classification and retry behavior for the observed missing-fmtp error; it does not yet make the affected stream play.

## Observed on the real device

- Xiaomi MIBOX4, Android 9 / SDK 28, 1920×1080.
- Signed CCTV 0.3.0 (1006), package com.pnntrn.cctv. In-app update checking succeeds and reports the installed release is current.
- Hikvision recorder setup with 11 channels, starting at channel 1. The owner entered the password only on the TV.
- Substream presets are /Streaming/Channels/102 through /Streaming/Channels/1102; mainstream presets are /Streaming/Channels/101 through /Streaming/Channels/1101.
- Test & save fails before a decoded preview. Successful Save channels and playback are unconfirmed.
- Sanitized exception cause: IOException -> IllegalArgumentException: missing attribute fmtp.
- The owner corrected the reported substream codec to H.264. Authenticated SDP has not been captured, so the failing track and exact SDP contents are unverified.
- Recorder reachability was confirmed from the Mi Box. Unauthenticated RTSP requests return 401; this establishes reachability only, not password validity.
- The recorder form disappeared during setup and had to be filled again. The precise lifecycle trigger remains unverified.

No camera password, authenticated RTSP URL, raw device log, camera footage or signing key is included.

## Implemented in this draft

PlaybackFailure previously returned UNKNOWN, whose retry flag is true, for the recorded exception chain. The new STREAM_METADATA classification has a fixed safe message and retry=false. StreamTile already releases its player and removes pending callbacks for non-retryable failures. The change does not synthesize SDP or codec initialization parameters and does not modify recorder settings.

Regression checks reproduce the nested exception, require non-retry behavior and a metadata-specific message, and retain authentication, decoder and unknown-failure distinctions.

## Remaining work before this is a playback fix

- [ ] Establish which SDP track lacks fmtp, without logging credentials or raw authenticated URLs.
- [ ] Implement and test proper H.264 compatibility, including valid in-band codec initialization when relevant. Do not insert fake SPS/PPS, codec parameters or dummy production dimensions.
- [ ] Confirm whether H.265 needs separate handling; do not infer it from the original incorrect codec answer.
- [ ] Preserve non-secret recorder drafts across the actual failing keyboard/lifecycle/navigation transition. Avoid persisting or logging password drafts.
- [ ] Keep the long recorder form fully reachable by D-pad and make unsaved changes clear.
- [ ] Verify preview gating and saving all 11 channels; camera names/order and recorder identity survive relaunch.
- [ ] Check bulk recorder edits, paging and both stream presets.
- [ ] Check fullscreen/Back/Home release, reconnect and decoder capacity with 4/9/16 tile selections. The location default is 4, and a 16-tile selection was observed; do not promise 11 simultaneous hardware decoders.
- [ ] Build and sign a newer release with the retained signing identity, and verify in-place update plus existing encrypted profiles. Do not uninstall, replace the signing key or change billing settings.
- [ ] Verify real-camera playback on the Mi Box before removing draft status or claiming resolution.

No new production dependency is introduced. Any new playback library requires owner approval.

## Validation performed for this draft

Base: aca536018af7dbb4d46bd21fb9f99ffea615e378.

The regression failed first with "missing fmtp must not retry the same unsupported SDP". After the classifier change, the existing check-core.sh source set was compiled and both entry points run with JDK 17 on Windows: 48 core checks and 38 extended checks passed, including the loopback RTSP probe tests. The existing CoreChecksTest JUnit wrapper invokes these same entry points in Gradle.

Android build/lint, the other updater/digest suites, instrumentation and post-change device playback have not been run for this draft. No new signed APK or update-feed entry is published by this PR.

## Upstream investigation references

- https://github.com/androidx/media/issues/901
- https://github.com/androidx/media/issues/1456
- https://github.com/androidx/media/issues/2208

These are investigation references, not proof of this recorder's authenticated SDP or a validated compatibility solution.

