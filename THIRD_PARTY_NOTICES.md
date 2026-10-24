# Third-party notices

## Vendored Media3 RTSP

The `rtsp-compat` module vendors Media3 RTSP **1.8.0** from the official Google Maven sources JAR. Its upstream license is Apache License 2.0. Preserve [the complete upstream license](rtsp-compat/LICENSE) and the individual source-file notices when distributing that code.

The recorded source JAR SHA-256 is `a8cf8fed25cc02ba3d385c9e262718d742c10a177ea923a57dcb756f65add0ca`. [UPSTREAM.md](rtsp-compat/UPSTREAM.md) records provenance and local changes: video-only SDP track selection, absent H.264 parameter-set handling and bounded in-band initialization using Media3's existing elementary-stream reader. This is modified upstream code, not an unmodified Media3 release.

## Synthetic test assets

`app/src/androidTest/assets/inband.h264` and `rtsp-compat/src/test/resources/inband.h264` are synthetic test fixtures generated from FFmpeg's `testsrc2` source with libx264. They contain no private CCTV footage. Their generation command and recorded hashes appear in [the playback investigation](docs/mi-box-hikvision-playback.md#shared-decoder-blocker-isolated-locally). FFmpeg is a local fixture-generation tool, not a bundled production dependency.

## Other dependencies and project code

Dependency coordinates are declared in [app/build.gradle](app/build.gradle) and [rtsp-compat/build.gradle](rtsp-compat/build.gradle). This document does not replace upstream licenses or assert an audited license inventory for every transitive dependency; review the exact resolved artifacts and their notices before redistribution.

The owner approved GPLv3 or later for original CCTV code. The Apache2.0 license retained for vendored Media3 code and the licenses of LibVLC/native components remain in effect for those components. See LICENSE for original project terms.


## Development0.4.6 LibVLC evaluation

The owner approved `org.videolan.android:libvlc-all:3.7.2` for MAIN playback. Its publisher POM declares LGPL2.1; source/build inspection additionally identifies GPL-enabled contrib settings. [The pinned source index](third_party/libvlc/SOURCE-INDEX.md) records exact available source revisions, the artifact hash and remaining native inventory checks. [LGPL2.1](third_party/libvlc/LGPL-2.1.txt), [GPL2](third_party/libvlc/GPL-2.0.txt) and this evaluation source notice are bundled inside the APK.

This development APK is for private device evaluation. Do not publish0.4.6 until component source/license/relink materials are complete and the owner explicitly approves the release. The owner has separately approved GPLv3 or later for original CCTV code.
