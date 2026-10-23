# Mi Box Remote Compatibility Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for implementation and independent review.

**Goal:** Support the approved Mi Box controls without a Menu button and publish a signed 0.4.2 patch candidate.
**Architecture:** Extend existing remote hold state and Activity/dialog dispatch; use existing rendering and technician confirmation. Keep all production dependencies unchanged.
**Tech Stack:** Java17/Android SDK35, pinned Gradle8.11.1 and existing native test harness.
**Spec:** docs/superpowers/specs/2026-10-24-mi-box-remote-design.md, approved in chat.

## Global Constraints

- Hold OK1000ms opens viewing options; while this menu is open, hold Back3000ms requests technician confirmation. Short OK/Back retain camera/stream/fullscreen/back/exit behavior.
- Technician confirmation defaults to keep watching; no CRUD exposed by short/cancelled holds. Existing Menu shortcuts remain supported.
- Suppress held-key release/repeats across dialog transitions. Cancel timers on release, cancellation, focus changes and backgrounding. Preserve playback identity when opening viewing controls.
- App CCTV, package/signing/profile format and legacy updater feed unchanged; no new production dependencies. Version0.4.2/code1010, release candidate until owner-device checks.
- Existing PR updated and unmerged; original-code license remains undecided.

## Review Focus

- Hold OK across menu creation must not select its first item on release.
- Hold Back across confirmation creation must not dismiss or accept that dialog on release.
- Short/cancelled holds must not trigger delayed actions after release/backgrounding.
- Fullscreen OK must toggle once for short press and open menu without toggling for long press.
- No-camera first launch must permit setup using only OK and Back.

### Task1: Remote behavior and regressions

**Own:** MainActivity.java, TvViewingState.java, TvViewingStateTest.java, ViewingFlowTest.java, values/strings_tv.xml.
**Interfaces:** Retain menuDown/menuHeld/menuUp/cancelMenu; add typed state for OK and menu-only Back as needed, thresholds1000/3000ms. Existing rendering/confirmation functions remain authority for screen actions.
- [x] Write failing unit/native regressions for thresholds, cancellation, repeats/release suppression, short/fullscreen behavior and no-Menu first setup.
- [x] Run focused fail-first unit check; implement minimal hold/input dispatch and Thai hints. Keep Back behavior outside menu and Menu coverage.
- [x] Run focused unit checks. Parent runs full wrapper/native harness. Independent reviewer verifies spec and quality; fix findings.

### Task2: Patch publication

**Own:** build version, README/CHANGELOG/releasing guidance, workflow release notes, scripts/ui-smoke.sh capture requirements, validation notes and repository publication.
- [x] Bump0.4.2/code1010 and update user instructions/downloads to v0.4.2-rc.1; retain older validation history.
- [x] Run full build/unit/lint/core/digest/updater/release-verifier/native checks, inspect screenshots, review final diff.
- [ ] After local checks and review, push PR and tag the same verified source; run PR/tag CI in parallel. The tag workflow signs and publishes only after its build and native checks pass. Verify public APK/hash/certificate/version/tag and main download link; leave PR unmerged.

## Execution rulings

Owner approved build; continue without another execution-method question. Existing dedicated checkout reused. Implementer owns only Task1 files; parent owns Task2. Parent centralizes Gradle/ADB to avoid concurrent test interference. Record reports and review in work/mi-box-remote-ledger.md.
