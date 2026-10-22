# CCTV TV

A small native Android TV app for direct live RTSP viewing. Built for a D-pad remote, Android TV / Google TV boxes and sticks running Android 8.0 or newer. No server, cloud login, WebView, account service, or bundled FFmpeg.

[Download the APK bundle](https://github.com/pnntrn/cctv-apk/actions/runs/39844447943/artifacts/12454414014), unzip it, and install **cctv-tv-debug.apk** for testing. This private repository requires GitHub sign-in. The unsigned release APK cannot be installed directly.

## What it does

- Save multiple locations, each with its own cameras and grid size.
- Add Hikvision NVR channels or VIGI NVR channels in one form: IP/hostname, port, username, password, first channel and channel count.
- Add VIGI standalone cameras or any camera with separate custom substream/mainstream RTSP URLs.
- Show 1, 4, 9 or 16 live **substreams** per page; page through additional cameras (up to 256 saved per location).
- Highlight a tile using the remote or mouse hover. **OK** opens its **mainstream** fullscreen. Hover only highlights; it does not start an extra stream.
- **Back** returns to the grid and focuses the camera you were watching. **Left/Right** in fullscreen switches cameras. **OK** in fullscreen toggles sub/main quality.
- Arrange cameras with a remote: choose **Arrange**, **OK** to pick up, arrows to swap positions, **OK** to drop, then **Save order**. **Back** cancels the changes. Streams are stopped during arrangement.
- Camera options also support moving directly to a numbered position, editing URLs/names, and deletion. Names and URLs are kept when order changes.
- Reconnect offline streams with 2–30 second backoff. A frame watchdog retries stalled connections. Decoder errors show a suggestion to reduce the tile count.
- Grid audio is disabled; fullscreen is also silent in this version.
- Settings and credentials are encrypted locally using Android Keystore AES-GCM and saved atomically. Android backups are disabled. Uninstalling / clearing app data removes profiles.

## Remote controls

| Control | Grid | Fullscreen |
| --- | --- | --- |
| D-pad | Move focus | Left/right changes camera |
| OK | Open mainstream | Toggle main/substream |
| Hold OK | Camera options | — |
| Back | Cancel arrangement, or exit prompt | Return to grid |
| Menu (if available) | Open camera manager | — |
| Prev / Next buttons | Change grid page | — |

Every feature is reachable through visible controls; a Menu key and number keys are optional.

## First setup

1. Add a location, for example **Shop**.
2. Open **+ Add** and select **Hikvision NVR** or **VIGI NVR**.
3. Enter the NVR's network address, RTSP port (usually 554), and its viewing credentials. Select the first channel and how many channels to add.
4. Select **Live**. The default 4-tile grid uses substreams. Choose **16 tiles** from Layout if your hardware supports it.
5. Focus a camera and press OK to view its mainstream. Press Back for the grid.

Use a camera/NVR viewing-only account when supported. The TV needs a direct network route to the camera/NVR: the same LAN or a VPN. This app does not use Hik-Connect or VIGI cloud relay.

### RTSP presets

| Device | Substream | Mainstream |
| --- | --- | --- |
| Hikvision NVR channel 1 | `/Streaming/Channels/102` | `/Streaming/Channels/101` |
| Hikvision NVR channel 16 | `/Streaming/Channels/1602` | `/Streaming/Channels/1601` |
| VIGI NVR channel 1 | `/live/1/2/avm` | `/live/1/1/avm` |
| VIGI camera (its own IP) | `/stream2` | `/stream1` |

URLs have the form `rtsp://USERNAME:PASSWORD@HOST:554/PATH`. The setup form encodes reserved characters in credentials automatically. In custom URLs, percent-encode characters such as `@`, `#`, `:` and spaces in usernames/passwords.

VIGI NVR RTSP requires supported firmware and an enabled RTSP server. Camera `/stream1` and `/stream2` paths are **not** NVR channel paths. If the preset differs on your firmware, edit each camera's two URLs. TP-Link documents the NVR server at [FAQ 4677](https://www.tp-link.com/pt/support/faq/4677/) and standalone cameras at [FAQ 3718](https://www.tp-link.com/us/support/faq/3718/). Hikvision channel URL examples are described in [Hikvision RTSP guidance](https://supportusa.hikvision.com/a/solutions/articles/17000129064).

### Fast settings for small TV sticks

Start with **4 tiles**. For a larger grid, configure camera substreams around **640×360 or 640×480, H.264, 5–10 fps, 256–512 kbps** as a starting point; tune for your picture quality and network. The app does not change NVR encoding settings.

16 simultaneous videos is a hardware capability, not a guarantee: some sticks can decode only a few streams at once. A 16-tile grid at 512 kbps per camera is roughly 8 Mbps of video payload, but decoder count and resolution can be the limiting factors before network bandwidth. If tiles fail, use 4 tiles and page through cameras. H.264 is the compatibility baseline. Other formats depend on the bundled Media3 version, the camera's RTSP payload, and the device codec; this has not yet been verified on hardware.

Only the current page plays. Entering fullscreen releases **all** grid players, then opens one stream. Back releases the fullscreen player and rebuilds the substream page. Camera manager screens, arrangement screens, and backgrounded activities release playback. TCP is the default; Location settings can select UDP with Media3's TCP fallback.

## Build and install

Pinned toolchain: **JDK 17**, **Gradle 8.11.1**, **Android Gradle Plugin 8.9.2**, **Android SDK 35**, **Media3 1.8.0**. The media dependencies are deliberately pinned; upgrade and test together.

Open the project in Android Studio and select a matching Gradle installation, or install Gradle 8.11.1 and SDK 35 and run:

```sh
bash scripts/check-core.sh
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
```

There is no Gradle wrapper binary in this source package. Android Studio can use a local Gradle installation; GitHub Actions installs the exact Gradle version automatically.

The GitHub workflow runs on pushes to `main`, pull requests, version tags, and manual dispatch. In **Actions → Build Android TV APK → successful run → Artifacts**, download **cctv-tv-apks**:

- `cctv-tv-debug.apk`: signed development APK, installable for device testing.
- `cctv-tv-release-unsigned.apk`: shrunk release output; **must be signed before installation**.
- `SHA256SUMS`: hashes of both outputs.

The debug key on a hosted runner is temporary. APKs from different runs may require uninstalling the previous build, which erases profiles. Before distributing regular updates, create a private release signing key, retain it securely, and configure CI secrets/signing. No private signing key is included here.

Install the development APK with Android TV's package installer (allow installation from your chosen file manager), or from a trusted computer using `adb install path/to/cctv-tv-debug.apk`. It appears as **CCTV TV** in the TV launcher. This is an Android app, not a Samsung Tizen app.

## Verification status

See [VALIDATION.md](VALIDATION.md) for executed checks, build limitations, and device acceptance steps. GitHub Actions has successfully run the unit tests, Android lint, and both APK builds. Real-camera playback and TV hardware behavior still need device testing.

## Privacy

All viewing connects directly to the RTSP URLs you configure. No analytics, backend, advertising, location permission, scanning, or file-storage permission. RTSP is normally unencrypted in transit: use your trusted LAN/VPN. Credentials are hidden in editable URL fields by default. This app does not intentionally log stream URLs or show raw player exceptions; the device/media libraries may produce diagnostic logs, so treat system logs as sensitive.
