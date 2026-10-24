# Releasing CCTV

CCTV0.5.1 (version code1016) is unreleased. The repository is `pnntrn/cctv-tv`; the app is **CCTV**, package `com.pnntrn.cctv`. Retain the package and signing key. This upgrade reads profile schemas1/2 and writes3; consult the rollback requirements below before testing an older build.

## Before tagging

1. Review the changelog and version in `app/build.gradle`. Increase the version code. An explicitly approved candidate tag such as `v0.5.1-rc.1` must agree with APK version `0.5.1`; the suffix identifies the candidate. Preparing or testing an APK does not authorize creating that tag.
2. Run the commands in [CONTRIBUTING.md](../CONTRIBUTING.md), including Python release-verifier tests. Require the build and native TV CI jobs to pass for the source being released. Inspect native screenshots. Do not convert historical emulator results into physical-device claims.
3. Confirm the retained `CCTV_SIGNING_BUNDLE` repository Actions secret is configured. It contains JSON fields `keystore` (base64 PKCS12), `password` and `alias`. Signing files are private, never source or release assets. `scripts/configure-signing.sh` is initial bootstrap tooling; do not generate a replacement key for existing installations.
4. Keep `rtsp-compat/LICENSE`, source notices and [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). Resolve the project's own licensing status before claiming a project license or redistribution permission.

## Tagged candidate publication

Push the reviewed candidate tag only when ready to publish. The tag workflow publishes after both build and native TV checks pass. It uses `scripts/prepare-release.py` to reject unsigned APKs, wrong package/certificate and mismatched version/tag inputs before preparing release assets. Consult `python3 scripts/prepare-release.py --help` for its exact local interface. Pull requests without signing credentials must still build and test; unsigned outputs must never be published as installable releases.

The installable asset is `cctv-release.apk`, accompanied by `SHA256SUMS` and the applicable license/notices. An approved `v0.5.1-rc.1` tag should be a prerelease. Release notes must accurately distinguish synthetic decoder checks from physical Mi Box capacity and in-place update/profile checks. Keep the review PR unmerged until its review is complete and merge is explicitly approved.

After publication, download the APK and checksums anonymously from [Releases](https://github.com/pnntrn/cctv-tv/releases). Check the SHA-256 and inspect it using Android SDK tools:

```sh
sha256sum cctv-release.apk
aapt dump badging cctv-release.apk
apksigner verify --verbose --print-certs cctv-release.apk
```

Confirm package `com.pnntrn.cctv`, version name `0.5.1`, version code `1016`, minimum SDK `26`, and retained certificate SHA-256:

```text
4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b
```

Check that README download links resolve to the signed candidate and that the checksum file names the downloaded asset.

## Upgrade and real-device checks

A retained-key release can update an existing retained-key installation while preserving encrypted profiles. Verify this on the owner's physical TV before claiming it passed. Old ephemeral debug-key builds may require a one-time uninstall to switch keys; uninstalling removes profiles. Never uninstall a user's app or alter TV installation settings without authorization.

Verify actual decoded Hikvision preview, saving all 11 channels, paging, fullscreen/back, background release, reconnect behavior and practical decoder capacity. Synthetic tests establish only their tested fixture behavior. Keep draft/candidate wording until physical playback and upgrade preservation are confirmed.

## Separate legacy updater feed

Tag publication in this repository does not change the separate `pnntrn/cctv-updates` feed. Updating that feed is a distinct publication step and needs write access to it. After all required checks, the existing tool verifies the retained certificate and version:

```sh
python3 scripts/publish-update.py path/to/cctv-release.apk --notes "Changes in this release"
```

It publishes the APK, release notes and feed with downloads pinned to an immutable APK commit. The app needs no account token. Keep signing keys private and never attach credentials, real camera footage or private logs to release notes.


## Private device evaluation and rollback

The0.4.6 MAIN experiment was private. Development0.5.1 is SUB-only and has no public tag/release approval. A retained-key evaluation APK can be produced with `workflow_dispatch` and `build_only=true`. This skips duplicate native execution only for that private artifact; the PR and every release tag still require the native TV gate on the same source. Never mark evaluation as a published release.

The0.5.1 package removes libVLC and its native libraries/ABI filters. Verify the actual APK is below the old64MiB updater limit and contains the original project GPLv3 license plus current Media3 notices. Historical LibVLC source obligations apply to those old private artifacts, not an SDK removed from this APK.

For rollback, preserve the installation and Keystore key. Unmodified0.4.x code rejects schema3; do not install it over migrated profiles. Keep the current schema3 model/reader/writer, migration and visibility support while reverting a defective UI/playback change, assign a higher version code, sign with the retained key and test before requesting distribution approval. Never uninstall, clear data, force a downgrade or copy another installation's encrypted profile/key. There is no server database; profiles may be unrecoverable if their Keystore key is removed.
