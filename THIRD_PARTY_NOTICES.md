# Third-party notices

## Vendored Media3 RTSP

The `rtsp-compat` module vendors Media3 RTSP **1.8.0** from the official Google Maven sources JAR. Its upstream license is Apache License 2.0. Preserve [the complete upstream license](rtsp-compat/LICENSE) and the individual source-file notices when distributing that code.

The recorded source JAR SHA-256 is `a8cf8fed25cc02ba3d385c9e262718d742c10a177ea923a57dcb756f65add0ca`. [UPSTREAM.md](rtsp-compat/UPSTREAM.md) records provenance and local changes: video-only SDP track selection, absent H.264 parameter-set handling and bounded in-band initialization using Media3's existing elementary-stream reader. This is modified upstream code, not an unmodified Media3 release.

## Synthetic test assets

`app/src/androidTest/assets/inband.h264` and `rtsp-compat/src/test/resources/inband.h264` are synthetic test fixtures generated from FFmpeg's `testsrc2` source with libx264. They contain no private CCTV footage. Their generation command and recorded hashes appear in [the playback investigation](docs/mi-box-hikvision-playback.md#shared-decoder-blocker-isolated-locally). FFmpeg is a local fixture-generation tool, not a bundled production dependency.

## Other dependencies and project code

Dependency coordinates are declared in [app/build.gradle](app/build.gradle) and [rtsp-compat/build.gradle](rtsp-compat/build.gradle). This document does not replace upstream licenses or assert an audited license inventory for every transitive dependency; review the exact resolved artifacts and their notices before redistribution.

The original CCTV project code currently has no project license. The owner is deciding that license. The Apache-2.0 license retained for vendored Media3 code does not itself license the rest of the project.
