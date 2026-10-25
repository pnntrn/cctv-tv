# CCTV repository guidance

## Architecture

- Native Android TV/Google TV app in Java17, minAPI26, compile/target35. App ID is `com.pnntrn.cctv`.
- `app/` owns TV viewing, guarded technician CRUD, encrypted profiles, phone setup and updater. `rtsp-compat/` vendors pinned Media3 RTSP1.8.0 with documented local parser fixes; preserve its Apache notices.
- Development0.5.1 uses Media3 SUB only in grid/fullscreen/technician preview. Saved MAIN URLs are data only. No VLC runtime or native libraries are bundled. First-frame proof must come from actual rendered frames.
- Viewing chrome hides after1500ms, including focused toolbar controls after returning focus to content. One short Back restores the location default grid; a second distinct short Back within700ms opens Android Home. Reset the sequence on other input/dialog/location/lifecycle changes; retain draft guards.
- Sanitize SDP names before Format labels/logs. Custom names win; optional detected metadata remains schema3 and is invalidated on SUB URL changes. Per-tile health uses rendered decoder counters at1s intervals with3000ms no-new-frame state; no timer heartbeat or unmeasured bandwidth.
- Only visible streams may run; release grid decoders for fullscreen, setup and background. Preserve generation guards, independent SUB/MAIN test results and decoded-SUB save requirement.
- Layouts1/4/6/8/9/16 share GridLayoutSpec geometry; six is one2x2 large plus five small. Hidden channels are filtered only in viewing; technician changes are staged until Save. One NVR per location; schema3 migration preserves IDs/credentials and splits legacy multi-NVR locations. Do not claim untested physical decoder capacity.

## Commands and evidence

Use the committed Gradle8.11.1 wrapper, JDK17, Android platform35/build-tools35.0.0. Configure SDK in uncommitted local.properties or ANDROID_HOME. `.env.example` documents optional signing variables; Gradle does not load it automatically.

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
bash scripts/check-core.sh
bash scripts/check-digest.sh
bash scripts/check-updates.sh
python3 -m unittest discover -s scripts/tests -v
git diff --check
```

Use gradlew.bat on Windows. Run relevant checks; do not repeat full builds without a new change or unresolved concern. UI regressions live in app/src/androidTest with synthetic RTSP/decoder fixtures. scripts/ui-smoke.sh resets fixture data and is restricted to an isolated API28 x86_64 TV emulator at1920x1080. Never run it against an owner TV. Inspect required screenshots and distinguish emulator results from physical-device results. Record checks not run.

For authorized owner-device work, use an explicit ADB serial, retained-key install-r updates and non-destructive diagnostics. Never uninstall, clear app data, reset profiles, change NVR encoding or expose authenticated RTSP URLs without the necessary owner authorization. Opt-in CCTVPlayback logs contain aggregate counters only; disable the device log property after diagnosis. Keep real logs, packet captures, screenshots, credentials and signing material outside Git.

## GitHub workflow

Follow the owner's global engineering rules. Use meaningful Issues, focused short-lived branches and Conventional Commits. Search before creating Issues/PRs. Keep incomplete work in Draft PRs; describe checks, risks, compatibility and rollback. Main is the stable integration branch. Never merge or change repository settings/protection without explicit approval; prefer squash merge when approved.

.github/workflows/android.yml builds/tests PRs and main; v* tag pushes also automatically publish a release after native gates. Creating a version tag therefore requires explicit release authorization. Do not tag/publish/deploy automatically. A workflow_dispatch build_only=true produces a signed private evaluation artifact and skips that run's duplicate native emulator job; PR and tag native gates remain required, and default dispatch still runs them. Reuse existing workflows and avoid redundant executions. Dependabot currently proposes controlled monthly GitHub Actions updates; do not silently change alert or update settings.

## Releases and recovery

Follow docs/RELEASING.md. Signing uses the retained key supplied by CI; never commit it or print secret values. Verify package, increasing version code, certificate, APK size, notices and checksums before proposing publication. The legacy updater feed is a separate publication step and also needs explicit approval. Verify the public APK bytes and feed after an approved publication.

Keep THIRD_PARTY_NOTICES.md and dependency/source notices accurate. The owner approved GPLv3 or later for original CCTV code. Preserve third-party licenses and do not assume a dependency POM inventories every native component. Historical LibVLC source/license investigation is retained for old private artifacts; it is not a current runtime dependency.

There is no server database or production SQL migration. Profiles are local and encrypted with Android Keystore; uninstall/clear-data can destroy the key. Preserve the installation and Keystore key. Schema3 upgrades read schema1/2. Old unmodified0.4.x readers reject schema3: rollback must retain the current schema3 model/reader/writer and visibility/migration support while reverting the defective UI/playback behavior, use a higher version code and retained key, and be validated before distribution approval. Never install an unmodified old reader over migrated profiles. Do not promise profile recovery from an encrypted file after its key is lost. Do not force a downgrade or wipe data as a rollback.
