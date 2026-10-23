# CCTV

A lightweight native Android TV / Google TV RTSP viewer for Android 8.0+. Direct connections to Hikvision, VIGI and custom RTSP cameras. No cloud account, streaming server, WebView, analytics, or bundled FFmpeg.

## Viewing

- Multiple locations; 1, 4, 9 or 16 live substreams per page; up to 256 saved cameras per location.
- D-pad highlights a tile. **OK** opens the mainstream. **Back** restores the substream grid and focused camera.
- Left/right changes fullscreen camera. OK toggles main/substream. Mainstream failure automatically falls back once to the substream.
- Fullscreen labels hide after four seconds; remote input reveals them again. Playback errors remain visible.
- The last location, camera and grid page are remembered after a normal close / reopen.
- **Arrange → OK to pick up → arrows → OK to drop → Save order**. Back cancels arrangement. Settings also has a direct numbered move action.
- Only the current page plays. Fullscreen releases all grid decoders. Settings, arrangement and backgrounding release playback. Audio is disabled in this version.
- Focus rings, 48dp controls, more TV-safe spacing, a compact live header and a native CCTV wordmark/icon.

Sixteen streams requires enough hardware decoders. Start with four tiles on smaller sticks. Suggested initial substream encoding: H.264, roughly 640×360 or 640×480, 5–10 fps and 256–512 kbps; tune the NVR itself. No measured stream-count or latency guarantee is made.

## Setup and recorder editing

1. Create a location, such as **Shop**.
2. Open **Settings → + Add** and choose Hikvision NVR, VIGI NVR, VIGI standalone camera, or Custom RTSP.
3. Enter the recorder address, RTSP port, credentials and channel range.
4. **Test & save** previews the first substream on the TV. **Save channels** becomes available after a decoded video frame.
5. Use **Settings → Recorders** to edit an entire recorder's IP, port, credentials and channel range. Existing linked camera IDs, names and order stay intact. Channels outside a changed range are removed; new channels are appended.

Canonical preset URLs from the original app are linked to shared recorder settings when the old profile file is loaded. Other URLs stay standalone. Editing a camera's URLs directly makes it standalone, so later recorder edits do not overwrite the custom URLs. Standalone camera forms include a test preview and an explicit show/hide URLs control.

| Device | Substream | Mainstream |
| --- | --- | --- |
| Hikvision NVR channel 1 | `/Streaming/Channels/102` | `/Streaming/Channels/101` |
| Hikvision NVR channel 16 | `/Streaming/Channels/1602` | `/Streaming/Channels/1601` |
| VIGI NVR channel 1 | `/live/1/2/avm` | `/live/1/1/avm` |
| VIGI standalone camera | `/stream2` | `/stream1` |

VIGI NVR RTSP requires compatible firmware and an enabled server. Camera paths `/stream1` and `/stream2` are not NVR channel paths. Custom URLs support firmware-specific alternatives. Credentials entered in the preset form are escaped automatically.

## Set up from your phone

1. On the TV open **Settings → Set up from phone**.
2. On a phone on the same trusted LAN/Wi-Fi, open the address shown on the TV.
3. Enter the six-digit pairing code. One phone is allowed per session; five failed PIN attempts close access. The session expires after ten minutes, closes when the TV dialog closes, and stops when the app backgrounds.
4. Add or edit a recorder or standalone camera. Blank saved passwords/URLs keep their existing values; saved passwords and RTSP URLs are never sent back to the phone.
5. **Test connection** checks RTSP authentication and whether a video stream exists. It is a protocol check, not a decoder/picture guarantee. **Save to TV** uses a single-use proof for that exact tested configuration. A TV preview verifies decoding separately.

This is a temporary local HTTP service, not a cloud service. Use a trusted network: credentials travel over that local connection. The service uses an origin/host check, same-site HTTP-only session cookies, a bounded request size and one paired IP. It has no file browsing, cloud relay, open CORS or background setup server. Closing pairing discards pending drafts and invalidates the session.

## Error messages

Authentication rejection, missing stream paths, network reachability, timeouts and decoder limits get separate messages. Retryable failures back off from 2 to 30 seconds. Authentication/path/decoder failures stop retrying until playback is reopened or settings change. Raw exceptions and credential-bearing URLs are not intentionally shown or logged by this app; system/media library diagnostics should still be treated as sensitive.

## APK builds and permanent signing

[GitHub Actions](https://github.com/pnntrn/cctv-apk/actions) produces **cctv-tv-apks** with:

- `cctv-tv-debug.apk`: installable development build.
- `cctv-release.apk`: installable optimized release **when retained signing is configured**.
- `cctv-release-unsigned.apk`: build output when signing is not configured; cannot be installed directly.
- `SHA256SUMS`: hashes of the outputs.

**Permanent signing requires one private GitHub repository secret: `CCTV_SIGNING_BUNDLE`.** It contains JSON with `keystore` (base64 PKCS12), `password`, and `alias`. The workflow uses it only in runner temporary storage, masks the password and deletes the keystore before artifact upload. No private key is committed or included in artifacts.

On a trusted computer with JDK 17, Python 3 and an authenticated GitHub CLI, run `bash scripts/configure-signing.sh` once. It creates a retained RSA key, keeps a private backup outside this project, and saves the repository secret without printing it. Retain that backup securely; never replace the key during normal updates. Then run the workflow again and install `cctv-release.apk`.

The old development build used an ephemeral debug key. Moving from that build to the retained release key may require a one-time uninstall, which removes local profiles. Subsequent releases signed with the retained key install as updates. Debug builds continue using development keys and are not the supported upgrade channel. CI version codes increase with workflow run number.

## Validation

- `bash scripts/check-core.sh` executes the production Java rules: original 48 checks plus recorder updates, legacy linking, pairing access and RTSP probe tests.
- GitHub Actions runs unit tests, HTTP setup protocol tests, Android lint and debug/release compilation.
- A landscape Android emulator smoke job checks navigation, fullscreen/back, settings, recorder editing, saved arrangement and reopen behavior. It publishes native screenshots under **native-ui-screenshots**. Fixture cameras use loopback URLs, with no real camera credentials or footage.
- A physical TV stick and reachable NVR are still needed to verify real-camera compatibility, Wi-Fi behavior and decoder capacity.

Toolchain: JDK 17, Gradle 8.11.1, AGP 8.9.2, SDK 35, Media3 1.8.0. There is no Gradle wrapper binary; CI installs the pinned Gradle distribution. Local commands: `gradle testDebugUnitTest lintDebug assembleDebug assembleRelease`.

Settings are encrypted locally using Android Keystore AES-GCM and saved atomically. Backups are disabled. Uninstalling / clearing app data deletes profiles. RTSP itself is normally unencrypted: use a trusted LAN/VPN. This is an Android app, not a Samsung Tizen app.

## Branding

The CCTV camera-grid mark, CCTV wordmark, TV banner and adaptive launcher icon are native vector assets. Editable artwork is in `brand/cctv-logo.svg`, with a PNG preview alongside it.
