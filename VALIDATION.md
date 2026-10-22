# Validation and handoff

Date: 23 October 2026 (Asia/Bangkok).

## Executed locally

- **48 pure Java checks passed**, executing the production CameraRules implementation with OpenJDK 17. They cover Hikvision and VIGI channel paths, IPv6 hosts, credential escaping including Thai characters, rejection of malformed URLs/hosts/ports/channels, page bounds, supported layouts, and reorder preservation.
- Android/resource XML and workflow YAML parsed successfully.
- All Java sources syntax-parsed using the JDK compiler without Android type resolution.

## Not yet verified

- Android compilation, Android lint, and APK packaging: this environment has no Android SDK or Gradle installation; outbound Android/Gradle download attempts timed out. The supplied GitHub Actions workflow runs these checks when uploaded to an accessible repository.
- Installation, TV launcher presentation, physical D-pad behavior, Keystore persistence, codec support, recovery behavior, and RTSP playback: no Android TV emulator, TV stick, or reachable NVR was provided.
- Performance and memory measurements: no measured APK size, startup latency, stream latency, or maximum stream count is claimed.

## Device acceptance checks

1. Install the signed development APK on an Android 8.0+ TV device and launch it from the TV launcher.
2. Use only the D-pad and OK to create **Shop** and add a Hikvision NVR; verify channel 1 and 16 both use substreams in a grid.
3. Add a VIGI NVR as a second location. Confirm the firmware's RTSP server is enabled and verify `/live/1/2/avm` and `/live/1/1/avm` independently.
4. Exercise layouts 1/4/9/16. Add 24 cameras and verify paging displays 17–24 on page 2 of the 16 layout.
5. Highlight tiles without opening extra streams. OK opens mainstream; Left/Right switches cameras; Back returns to substreams on the correct page and focused tile.
6. Check manager controls, dialogs, keyboards, long OK options, and exit prompt with a remote that has no number keys or Menu key.
7. Arrange across page boundaries, save, force-stop, and relaunch. Verify order and location persist. Arrange again and Back to cancel; verify original order returns.
8. Edit a custom camera with separate URLs, move it to a numbered position, rename it, and delete it. Verify neighboring cameras retain their names/URLs.
9. Disconnect the NVR/network, reconnect, and check automatic recovery. Use an invalid password and verify the UI stays responsive without revealing credentials.
10. Background the app while watching a grid/fullscreen and verify sessions stop; return and verify only visible streams restart. Check with NVR session counters or Android profiler.
11. Attempt 16 substreams; if the hardware codec limit is reached, check the decoder message and switch to 4 tiles. Confirm fullscreen works after freeing the grid decoders.
12. Reboot and reopen. Verify encrypted profiles load. Delete a location and verify only that location's cameras are removed.

## Before routine releases

Configure a retained private release key so updates install over previous versions without losing profiles. Run the workflow and the device acceptance checks before claiming a tested release.
