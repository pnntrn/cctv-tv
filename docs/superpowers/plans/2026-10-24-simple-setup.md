# Simpler Mi Box setup implementation plan

> Execute inline with superpowers:executing-plans; independent final review.

**Goal:** Make setup discoverable with a normal Settings menu choice.
**Architecture:** Extend existing viewing menu/confirmation, remove Back hold action while retaining release/cancellation protection.
**Tech stack:** Existing Java Android app, no new production dependencies.
**Spec:** ../specs/2026-10-24-simple-setup-design.md

## Global constraints

Retain com.pnntrn.cctv, retained certificate, profile schema/key and updater feed. Version 0.4.3/code1011. No local ADB/Mi Box access. Existing open PR remains unmerged.

## Review focus

Long OK release must not select Settings. Settings must not enter editing without confirmation. Back of any duration must not enter setup. Background/cancelled releases must not open dialogs. Saved encrypted NVR/camera identity and credentials must survive Activity close/relaunch and remain unreadable as plaintext; owner root cause remains unknown.

## Task 1: Tests and implementation

- [ ] Modify ViewingFlowTest first-setup method: require visible ตั้งค่า, select by D-pad, verify safe default and cancel/confirm, long Back closes without setup. Push test-only change and observe expected native RED in CI (no local ADB).
- [ ] Add Settings item and Thai hints in MainActivity/strings_tv; remove Back hold runnable/action, retain release-only dispatch and cancellation. Change TvViewingState Back to release-only; adapt unit regression to assert long Back never qualifies as hold.
- [ ] Add native encrypted-profile close/relaunch regression with synthetic NVR/password, verifying identity/grid/cameras and no plaintext file. Inspect 0.4.1→0.4.2 persistence diff, no speculative storage fix.
- [ ] Run unit/lint/debug/release/AndroidTest build and core/digest/updater/release-verifier checks. Independently review final diff.

## Task 2: Publish

- [ ] Update version/docs/release notes, commit/push PR and v0.4.3-rc.1 tag after local verification/review. Await build/native/signing gates.
- [ ] Verify public APK/hash/package/version/retained certificate; update main download link and PR evidence, preserve unmerged PR. Do not install or manipulate owner device.
