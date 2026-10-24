# Thai TV Viewing Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for scoped implementation and independent review.

**Goal:** Safe Thai camera viewing with an edge-to-edge idle grid, separate technician setup, and a signed APK in the public repository.

**Architecture:** Keep the native Activity and RTSP stack. Add transient viewing chrome without rebuilding playback; gate management behind an explicit technician session. Preserve profile data and playback regressions.

**Tech Stack:** Java 17, native Android views, SDK 35/minimum 26, existing Media3 1.8.0 compatibility module. No new production dependencies.

**Spec:** User-approved `outputs/thai-tv-viewing-design.md` in the calling chat workspace; copied to `docs/superpowers/specs/2026-10-24-thai-tv-viewing-design.md`.

## Global Constraints

- Idle delay 4 seconds; technician Menu hold 3 seconds plus confirmation, with cancel initially focused.
- User data is preserved. One-NVR-first is a default workflow, not destructive enforcement.
- Ordinary viewing exposes no CRUD. Backgrounding exits technician access.
- Translate app and phone setup UI to Thai; preserve protocol fields and user names.
- Preserve signing certificate, package name and the legacy update feed.

## Review Focus

- Hidden controls must not steal D-pad focus or recreate streams: native idle/remote test.
- Long OK and normal Menu must never expose CRUD: native access test.
- Background/recreation must not bypass technician entry through saved drafts: native lifecycle test.
- 11 channels across pages remain reachable: native page-edge/fullscreen-back test.
- Thai errors must not reveal URLs/passwords: unit and native label checks plus visual review.

### Task 1: Viewing and technician navigation

**Files:** MainActivity.java, new TvViewingState.java if useful, values/strings_tv.xml, new ViewingFlowTest.java and focused policy tests.
**Interface:** Existing StreamTile.setChromeVisible(boolean) controls labels; errors remain visible. Existing profile/recorder models retain their data shape.

- [x] Add failing native/policy assertions for idle edge-to-edge grid and safe viewing Menu/long OK.
- [x] Implement camera-only grid with overlay, focus/label hiding, D-pad paging/fullscreen and safe technician gate.
- [x] Put CRUD/reorder/location mutation in setup and make single NVR the primary setup action. Resume drafts only after deliberate setup entry.
- [x] Translate all MainActivity labels to short Thai using resources. Use UiText.translate(Context,String) for validation messages returned by pure Java rules.
- [x] Verify focused tests and review the diff.

### Task 2: Thai supporting UI

**Files:** StreamTile.java, AppUpdater.java, PhoneSetupServer.java, res/raw/phone_setup.html, new UiText.java, values/strings_common.xml.
**Interface:** `UiText.translate(Context context, String message): String` translates known validation messages from CameraRules/Nvr; unknown technical errors use a safe Thai generic message rather than displaying raw diagnostics. Do not translate user-entered labels or protocol keys.

- [x] Translate supporting UI via Android resources; retain error classification/protocol behavior.
- [x] Translate phone setup HTML and safe server messages while preserving endpoint/JSON contracts.
- [x] Verify existing updater/phone protocol tests and label coverage; review the diff.

### Task 3: Integration, complete verification, publication

**Files:** existing androidTest classes, scripts/ui-smoke.sh, app/build.gradle, README.md, validation documentation.

- [x] Update existing native tests to new Thai technician flow, preserving every existing playback/draft assertion.
- [x] Bump to 0.4.0 (1008); run core/digest/updater checks and full Gradle unit/lint/debug/release/instrumentation build.
- [x] Run the full Android 9 TV suite; inspect idle/grid/menu/setup/fullscreen screenshots and fix failures.
- [x] Obtain independent review and resolve material findings.
- [ ] Push the verified changes to existing PR #1. Publish signed release candidate APK/checksum in this repository after green CI; verify anonymous download and signing identity. Keep PR open for review unless user explicitly requests merge.

## Execution record

Ruling: the user approved “build this design”; continue implementation without another plan-approval prompt. This is the same authorized redesign, not a new scope decision. The fresh isolated clone under work/repo is already on the PR branch and contains no unrelated edits.
