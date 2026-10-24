# Substream TV workflow implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans task by task. The owner's instruction is review and implement continuously.

**Goal:** Deliver simple substream-only family viewing and protected NVR/channel setup.
**Architecture:** Extend native Java UI, add pure layout geometry and backward-compatible profile fields; retain encrypted profiles and existing Media3 substream decoder.
**Tech Stack:** Java17, Android API26+, Media3, JUnit, Android instrumentation.
**Spec:** docs/superpowers/specs/2026-10-25-substream-tv-workflow-design.md

## Global Constraints
- No new production dependencies, no recorder encoding changes, no owner profile wipe.
- All playback uses substream; saved MAIN URLs remain data only.
- Layout sizes1,4,6,8,9,16; six is one2x2 tile plus five small tiles on3x3 geometry.
- Thai labels, explicit focus, hidden chrome after4s, guarded technician CRUD.
- Preserve every legacy camera ID/credential; hidden channels are filtered, not deleted.
- No merge, tag, public release or updater publication without explicit approval.

## Review Focus
- All-hidden/empty locations cannot leave a stale fullscreen player or trap navigation.
- Legacy multi-recorder locations and unlinked channels survive migration exactly once.
- Interrupted holds or dialog changes never activate a new control with the old key release.
- Hidden/reordered focused camera restores by identity and clamps page safely.
- Six simultaneous live previews may exceed physical hardware capacity; report actual checks.

### Task1: Layout/data foundation
Ownership: Camera.java, Location.java, CameraRules.java, TvViewingState.java, ProfileStore.java, new GridLayoutSpec.java and corresponding host tests. Do not edit MainActivity or StreamTile.
Interfaces: Camera.hidden boolean; Location.defaultGrid int and visibleCameras() returning ArrayList<Camera>; GridLayoutSpec.sizes() returns int[], cells(int size) returns List<Cell>, Cell public final int x,y,width,height, columns(int), rows(int), move(int selected,int count,int size,int dx,int dy). TvViewingState.move delegates geometry. Key enum adds LEFT/RIGHT with1000ms hold. ProfileStore normalizes multi-NVR locations using a testable pure helper; schema3 serialization covers fields.
- [ ] Write regression tests for six geometry,8 navigation, hidden filtering, default fields, multi-NVR migration and persisted visibility; watch RED.
- [ ] Implement minimal helpers and compatible serialization/migration; run host tests GREEN.
- [ ] Review diff; commit focused Conventional Commit; record exact tests.

### Task2: Substream viewing and top bar
Ownership: MainActivity.java, StreamTile.java, strings.xml, app/build.gradle and removing unused VlcStreamPlayer.java/keep rules; native/unit test updates. Integrate Task1 interfaces.
- [ ] Write native regressions for Back home, all playback SUB,6 layout geometry, top-bar reveal/hide/focus/long shortcuts; observe RED before implementation.
- [ ] Render defined cells with a small custom ViewGroup; filter hidden channels for viewing, preserve identity/paging, implement toolbar/remote release guards and Back home.
- [ ] Force all StreamTile instances to SUB and remove VLC production runtime/dependency; keep saved main data. Retire obsolete MAIN-engine assertions in favor of tests proving requested main flags still select SUB.
- [ ] Run relevant unit/native checks and build; inspect captures.

### Task3: NVR-only setup and visual channel management
Ownership: MainActivity.java, RecorderDraft.java if required, strings and tests/docs.
- [ ] Add tests for combined location/NVR CRUD/default layout; forbid second NVR/new standalone add paths; staged hide/reorder save/cancel/all-hidden recovery.
- [ ] Implement guarded setup pages and live substream channel tiles with source/destination swap and explicit visibility toggles, save/cancel. Preserve draft-password privacy and SUB decode save gate.
- [ ] Run UI/native checks including lifecycle; document resulting commands and behavior.

### Task4: Integration and delivery
- [ ] Fresh independent review of branch against spec and regression risks; fix important findings with tests.
- [ ] Run unit/lint/debug/minified release/native compilation, relevant script checks, all native tests and14+ screenshots; inspect actual layout/focus.
- [ ] Prepare signed private retained-key APK using existing CI; install-r on owner Box if reachable and verify saved profiles, SUB selection and six-tile capacity without NVR changes.
- [ ] Open Draft PR referencing new tracked Issue, attach PR, provide tested APK artifact/link and accurate limitations. Keep public release separate.

## Execution ledger
- Ruling: Back means CCTV grid, not Android launcher; owner asked implementation after clarification proposal, so use the least disruptive interpretation.
- Ruling: Work in the clean existing checkout on a new feature branch to preserve its configured Android tooling; no main/shared branch edits.
- Ruling: The owner's direct review-and-implement request selects continuous execution of the proposed flow, rather than another design approval pause.
