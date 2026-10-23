# Mi Box / Hikvision consolidated playback follow-up

Status: draft. The branch now includes H.264 in-band initialization and recorder draft recovery. Local protocol tests, Android build/lint and all nine Android 9 native checks pass, including actual first-frame decoding. Real Mi Box / Hikvision playback remains unverified. It is not yet a confirmed fix for the owner's recorder.

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

## Compatibility implementation added to this draft

The existing Media3 RTSP 1.8.0 sources are vendored with Apache notices, using the existing H264Reader for real SPS/PPS parsing. In-band mode reconstructs complete single NAL, STAP-A and FU-A packets, defers the codec format until real configuration is parsed, and starts RTSP SETUP/PLAY before preparation so that the configuration can arrive. It does not repeat SETUP when ExoPlayer selects the prepared track. Broken fragments are dropped and memory is bounded. No new production playback library, fake initialization bytes, or production dimensions are added. Video-only track filtering happens before unused audio metadata can fail parsing.

Non-secret raw recorder edits are retained separately from encrypted saved profiles. Password views opt out of Android hierarchy saving/autofill; draft capture never reads passwords. Backgrounding clears the password, cancels preview verification and releases preview playback. Existing recorder credentials remain encrypted and a blank password keeps that saved credential. Back asks whether to keep or discard the draft; Menu and re-render cannot unexpectedly dismiss it. D-pad access reaches all channel fields and Test & save. Restoring a draft releases background camera streams before showing the editor.

The preview Save channels action remains gated by a decoded first frame. A failed or backgrounded preview cannot be re-enabled by a stale callback. Default grid remains four tiles, with a decoder-capacity hint for 9/16.

## Remaining work before this is a verified playback fix

- [ ] Establish which SDP track lacks fmtp, without logging credentials or raw authenticated URLs.
- [x] Implement and locally test H.264 in-band initialization without fake SPS/PPS, codec parameters or dummy production dimensions. Native/real-camera results remain separate below.
- [ ] Confirm whether H.265 needs separate handling; do not infer it from the original incorrect codec answer.
- [ ] Preserve non-secret recorder drafts across the actual failing keyboard/lifecycle/navigation transition. Avoid persisting or logging password drafts.
- [ ] Keep the long recorder form fully reachable by D-pad and make unsaved changes clear.
- [ ] Verify preview gating and saving all 11 channels; camera names/order and recorder identity survive relaunch.
- [ ] Check bulk recorder edits, paging and both stream presets.
- [ ] Check fullscreen/Back/Home release, reconnect and decoder capacity with 4/9/16 tile selections. The location default is 4, and a 16-tile selection was observed; do not promise 11 simultaneous hardware decoders.
- [ ] Build and sign a newer release with the retained signing identity, and verify in-place update plus existing encrypted profiles. Do not uninstall, replace the signing key or change billing settings.
- [ ] Verify real-camera playback on the Mi Box before removing draft status or claiming resolution.

No new production dependency is introduced. Any new playback library requires owner approval.

## Initial classification-only validation

Base: aca536018af7dbb4d46bd21fb9f99ffea615e378.

The regression failed first with "missing fmtp must not retry the same unsupported SDP". After the classifier change, the existing check-core.sh source set was compiled and both entry points run with JDK 17 on Windows: 48 core checks and 38 extended checks passed, including the loopback RTSP probe tests. The existing CoreChecksTest JUnit wrapper invokes these same entry points in Gradle.

At the initial classification-only commit, Android build/lint, the other updater/digest suites, instrumentation and post-change device playback had not been run. No new signed APK or update-feed entry is published by this PR.

## Upstream investigation references

- https://github.com/androidx/media/issues/901
- https://github.com/androidx/media/issues/1456
- https://github.com/androidx/media/issues/2208

These are investigation references, not proof of this recorder's authenticated SDP or a validated compatibility solution.


## Continued local checks

The original 48 core and 38 extended checks remain passing, along with both digest-auth variants and 22 updater checks. Android unit tests cover in-band parsing, malformed unused audio, broken FU-A, 11-camera paths/paging/order/recorder identity, and non-secret drafts. The full loopback RTSP-session regression passes: PLAY precedes real format discovery and track selection does not repeat SETUP. All 20 JUnit methods pass. Android lint has zero errors (21 warnings across the app and vendored module); debug, instrumentation APK and minified release builds succeed. Native fixture tests are separate from actual Hikvision playback and cannot establish Mi Box decoder capacity.

The PR stays in draft. No production update feed has been changed by this compatibility branch.

The latest local candidate is 0.3.1 (1007), com.pnntrn.cctv, 439742 bytes. APK SHA-256: 7ac6b1361eff41941e91e404aa6256e556c50fdc6a1256a9fdf7ecdbe8ebdbbf. Official apksigner verification passes with retained certificate SHA-256 4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b. In-place installation and post-update encrypted profiles on the owner’s TV are not yet verified.

The first Android 9 native run failed: the synthetic stream timed out, and dialog tests exposed asynchronous button binding and confirmation dismissal races. The branch now binds buttons synchronously after show, avoids stale confirmation callbacks, and covers both buffered and not-yet-buffered live initial selection positions. Both new protocol regressions failed before the correction and pass afterward. Native tests are being rerun; no successful native first frame or real-camera playback is claimed yet.

The second Android 9 native run passed the 11-channel callback-gated save/relaunch/lifecycle flow, both draft retention cases and remote navigation/reorder flow. Real first-frame decoding instead exposed a race: RtpDataLoadable published transport readiness before constructing/initializing its extractor, so a fast PLAY response could crash setTimestamp. Readiness is now posted after initialization; a latch-controlled regression failed before the fix and passes afterward. Setup is also guarded after period release. The preview callback contract fixture now cancels its intentionally invalid transport before injecting callbacks, avoiding a connection-refused race while retaining failure/stale-callback assertions. The independent native fixture still requires actual decoded playback and is being rerun.

The third native run passed all four draft/save/lifecycle tests, while actual decoding still stalled after codec initialization. Access-unit inspection found that elementary-stream samples included the following picture’s SPS/PPS at their end. A regression requiring the last NAL of an RTP-marked picture to be its VCL data failed before the correction. Format discovery now discards its elementary-stream samples and replays a bounded startup RTP cache through the existing marker-aware RtpH264Reader once real SPS/PPS are known. All 20 unit methods pass; extracted samples also decode without FFmpeg errors. Native tests now include a control using real SPS/PPS from the same synthetic fixture as well as the missing-fmtp case. The reorder fixture waits for actual remote focus instead of assuming camera 1 is focused. Native decoding is being rerun and remains unverified at this commit.

The fourth native run passed all five draft/save/navigation/reorder checks. Both the valid-SDP control and missing-fmtp decoder test still stalled. The emulator reported qemu.skin=320x640 despite a logical wm-size override, and its UI captures were clipped 1080x640 images. The harness was changed to create the tv_1080p hardware profile with a matching physical skin, remove logical size overrides, verify physical 1920x1080 dimensions, and require a valid video Surface. Native cases cover preview/substream with and without fmtp, fullscreen in-band playback, and RTSP connection closure after releasing each decoded tile. This test-only change does not alter recorder encoding, production codec dimensions, signing or the stable feed. Those eight checks were pending at that commit; the subsequent failed run and local diagnosis are recorded below.

## Shared decoder blocker isolated locally

Run 40006776986 at 95a4493 failed all three video cases despite a valid physical TV display and Surface. The fullscreen case queued 75 decoder inputs and the preview/control cases queued 125; all rendered zero frames. The other five native checks passed. This rules out the earlier display-size hypothesis for that run.

The unchanged valid-SDP control was reproduced on a clean local Android 9 / SDK 28 x86_64 TV emulator: 118 decoder inputs, zero output frames, position zero, 23.4 seconds buffered, valid Surface. Both targeted RTSP session regressions passed locally.

A new direct MediaCodec regression bypasses RTSP, ExoPlayer and the Surface. With the original synthetic fixture it submitted 638 pictures without producing a decoded picture in 10 seconds. The fixture's SPS advertised max_num_ref_frames=0 and max_dec_frame_buffering=0. A controlled copy retaining the original picture bytes and changing only decoder buffering to one still failed; changing both fields to one decoded in 0.86 seconds. An encoder-generated comparison with those values decoded in 0.12 seconds. These are Android 9 synthetic-fixture results, not evidence about the owner's camera encoding.

Both test fixture copies are now regenerated from FFmpeg's synthetic testsrc2 source, with the same 320x180 size, 5fps, three slices per picture, single-NAL/FU-A coverage and repeated real SPS/PPS. No production codec headers are rewritten. The direct decoder check requires a real decoded output buffer before end-of-stream and is independent of the three first-frame/release regressions.

Fixture generation (FFmpeg 8.1.1 with libx264):

```sh
ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=320x180:rate=5' \
  -t 1 -c:v libx264 -profile:v baseline -level:v 1.1 -preset ultrafast \
  -tune zerolatency -x264-params 'keyint=2:min-keyint=2:scenecut=0:slices=3' \
  -force_key_frames 'expr:gte(t,n_forced*0.2)' -f h264 inband.h264
```

The encoder advertises one reference frame and one decoder buffer while forcing all five pictures to IDR. Copy the generated file to app/src/androidTest/assets/inband.h264 and rtsp-compat/src/test/resources/inband.h264. Both copies have SHA-256 748abf39ce4e41af7ae7621ecf49b5366f576b53094ee70ba9eeb902d44573d8. The original fixture SHA-256 was 016cd3356392aa7862f2862d7553f460912b082eb32a3aa0ad7bc11a003efe86. FFmpeg is a local fixture-generation tool only; it is not an app dependency.

## Verification after the fixture correction

All checks below ran locally; no hosted workflow was manually started or rerun:

- Android 9 / SDK 28, accelerated x86_64 TV emulator with physical 1920x1080 display at 320dpi: four decoder/playback tests passed in 10.674 seconds. These cover the independent direct decoder, valid-SDP preview, missing-fmtp preview and missing-fmtp fullscreen. Each StreamTile case still requires ExoPlayer's rendered-first-frame callback and verifies that release closes the RTSP connection.
- All four recorder draft/save/lifecycle checks passed (46.748 seconds); the remote navigation/pairing/reorder persistence check passed (86.361 seconds). Total native result: nine tests, zero failures. No assertions were removed or relaxed.
- Full Gradle command: testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest. BUILD SUCCESSFUL in 2m45s; 20 JUnit methods, zero failures/errors. The app lint report has 19 warnings and the vendored module has four; neither has errors.
- Core wrapper output retains 48 core, 38 extended and 22 updater checks. Both standalone digest-auth variants passed. FFmpeg decoded all five generated pictures without errors; both fixture copies have the same hash. git diff --check passed.

This follow-up changes only test fixtures, a native regression and this document. Production playback sources, dependencies, profiles, signing configuration and the stable update feed are unchanged. The local release build is unsigned because the retained release key was not brought into this isolated checkout. No APK was installed on the owner's device, no app was uninstalled, and no release/update was published. The real recorder's authenticated SDP, decoded Mi Box preview, saved 11-channel playback, decoder capacity and in-place signed update/profile retention still require device verification. Keep this PR in draft.
