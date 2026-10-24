# LibVLC3.7.2 evaluation source index

- Publisher coordinate: org.videolan.android:libvlc-all:3.7.2
- AAR SHA256: d320af15ec9ff7715db2c2506c9858b0df6d97aaae40fb84f74439bd26178367
- Publisher POM declares LGPL2.1. Source/build inspection also finds GPL-enabled contrib settings; do not treat the POM as a complete native inventory.
- Official SDK source repository: https://code.videolan.org/videolan/libvlcjni
- Official Android tag source: https://github.com/videolan/vlc-android/commit/88b1250f92d57d05174a932bf970c688a4fec366
- That source pins LibVLCjni f6d8607210adba08f504e4b065f18620ba652de6 on libvlcjni-3.x. Exact matching source mirror: https://github.com/open-source-fixes/libvlcjni/tree/f6d8607210adba08f504e4b065f18620ba652de6
- LibVLCjni pins VLC base4c608da16c33560821be7e5c1ad4e34bf5a65398 and applies20 libvlc/patches files. Official VLC base: https://github.com/videolan/vlc/tree/4c608da16c33560821be7e5c1ad4e34bf5a65398
- Java API sources: https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.2/libvlc-all-3.7.2-sources.jar
- Binary embeds VLC version3.0.23-2-650-g81ac6800c7. Its generated post-patch hash is not yet mapped to a complete native source/build inventory.

LGPL2.1 and GPL2 license texts from the matched upstream sources accompany the APK. This is a private evaluation build. Contrib source/license/relink materials remain pending before public distribution. The owner separately approved GPLv3 or later for original CCTV code; this notice preserves the upstream component licenses.

## Pinned source-build inputs

This section records the source recipes reachable from the pins above. It is not a per-file license audit, an AAR build log, or evidence that rebuilding these inputs reproduces the published AAR. The `3.7.2` sources JAR contains Java API sources; the AAR itself contains native libraries, not their corresponding native source archives.

At the pinned LibVLCjni commit, `buildsystem/compile-libvlc.sh` defaults to `AVLC_CONTRIB_LICENSE=g`, sets `USE_FFMPEG=1`, and configures VLC with avformat, avcodec and swscale. In that mode it also passes `--enable-live555` and `--enable-dvdread`. VLC's pinned `contrib/bootstrap` defaults `BUILD_ENCODERS`, `BUILD_NETWORK`, `BUILD_DISCS`, `GPL` and `GNUV3` on. The recipes gate x264 on encoders plus GPL, dvdread on discs plus GPL, and live555 on network plus GNUV3; FFmpeg is selected by the recipe and `USE_FFMPEG=1`. `compile-libvlc.sh` can optionally consume prebuilt contribs; the AAR does not include the build invocation or its logs, so these recipe defaults are not proof of the exact native inputs used for this artifact.

| Recipe in VLC commit `4c608da16c33560821be7e5c1ad4e34bf5a65398` | Source pin recorded by the recipe | Local recipe patches |
| --- | --- | --- |
| [FFmpeg](https://github.com/videolan/vlc/tree/4c608da16c33560821be7e5c1ad4e34bf5a65398/contrib/src/ffmpeg) | Release archive `https://ffmpeg.org/releases/ffmpeg-8.1.tar.xz`; SHA-512 `9deb30b93c27873564ec094693e72ff552f63a09af3175f9118672b88299a4e9495502aa0fb2dfbcd45857ba255f24b829bdd3c3c17d0f1eee75423302a9af8c`. The recipe also contains a Git source hash `71fb6132637a2a430375c24afc381fff8b854fe7` for its alternate Git archive rule; the AAR build record does not identify which route was used. | `contrib/src/ffmpeg/*.patch`, as selected by the recipe/platform. |
| [x264](https://github.com/videolan/vlc/tree/4c608da16c33560821be7e5c1ad4e34bf5a65398/contrib/src/x264) | VideoLAN Git repository `https://code.videolan.org/videolan/x264.git`, commit `e067ab0b530395f90b578f6d05ab0a225e2efdf9`. | `contrib/src/x264/*.patch` (three recipe patches). |
| [libdvdread](https://github.com/videolan/vlc/tree/4c608da16c33560821be7e5c1ad4e34bf5a65398/contrib/src/dvdread) | `http://downloads.videolan.org/pub/videolan/libdvdread/6.1.3/libdvdread-6.1.3.tar.bz2`; SHA-512 `c98055829ccbbe2bb7b5df48f6e279cf6e91ecab39a35c37081581452f7df7531c1d7b8abd641c55eeee357235b2658b31478dbc307995cffdf8c011bd0dd0bc`. | `contrib/src/dvdread/*.patch` (three recipe patches); depends on libdvdcss. |
| [libdvdcss](https://github.com/videolan/vlc/tree/4c608da16c33560821be7e5c1ad4e34bf5a65398/contrib/src/dvdcss) | `http://downloads.videolan.org/pub/videolan/libdvdcss/1.4.3/libdvdcss-1.4.3.tar.bz2`; SHA-512 `0f9c0244feff5ca62459823810a39ede745b63fedb682707fcbaf0dff11fff42998cef31a281bb6380930123d079780d37a79871f6e628cdca9cfa06c64befb6`. | No local patch is listed by this recipe. |
| [live555](https://github.com/videolan/vlc/tree/4c608da16c33560821be7e5c1ad4e34bf5a65398/contrib/src/live555) | `http://downloads.videolan.org/pub/contrib/live555/live.2016.11.28.tar.gz`; SHA-512 `319639acef6474b2eec0bdfa3416ca3c88a60f57d9d22911eee018fc494978fde93a241556bc0ea309f0b6a35e0242bd44a8f4de83a845f80d9ca5f94254ade6`. | `contrib/src/live555/*.patch` (nine recipe patches). |

The VLC source tree at the pinned commit contains these recipes and patch files; the listed upstream dependency archives/checkouts are separate inputs and are not embedded by the VLC source tree, the Java sources JAR, or the AAR. A self-contained source package should include the selected dependency sources, the applicable recipe patches, the pinned LibVLCjni/VLC sources, and the build scripts. A source index alone is not that package.

Inspection of the AAR's native `libvlc.so` found avcodec/avformat, x264, DVD read/navigation/css-related strings, and configuration strings including `--enable-dvdread` and `--enable-live555`. These are component-presence indicators, not a complete native dependency or license inventory. The embedded VLC suffix `81ac6800c7` remains unmapped to a recovered post-patch source tree. No byte-for-byte rebuild has been demonstrated.
