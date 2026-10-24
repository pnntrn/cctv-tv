# Viewing controls refinement

Spec: the owner's ten follow-up requirements after testing 0.5.0, resolved below.

## Global constraints
- All playback uses SUB. No new production dependencies, NVR encoding changes, profile wipe or credential logging.
- Preserve schema3 compatibility, existing custom camera names, staged technician edits and player lifecycle guards.
- Work on feat/substream-tv-workflow and existing Issue4/PR5. No merge, tag, public release or updater publication.

### Task 1: Implement and verify the complete refinement
Ownership: MainActivity, StreamTile, RTSP metadata propagation, model/profile optional metadata, resources and relevant tests. You are not alone in the codebase; preserve other edits. Do not spawn agents.

1. Cluster grid, location, next-page and Settings icons at top right, each at least48dp with Thai accessibility labels. Location click cycles to the next location, wrapping. Grid click continues cycling layouts. Preserve optional picker shortcuts if safe.
2. Toolbar text order: location name, layout name, page. Add next-page icon labelled หน้าถัดไป; wrap pages, retain a safe focus target for single/empty pages. From fullscreen, next-page returns to grid then advances.
3. Idle timeout1500ms. Even toolbar focus must return safely to the content before hiding. Open dialogs/settings retain their navigation. Remote input reveals toolbar normally.
4. One Back in normal viewing restores current location's configured default grid; preserve selected visible camera identity/page where possible. A second distinct short Back within700ms opens Android TV Home via MAIN/CATEGORY_HOME. Ignore held/repeated key events; reset double-Back state on other interaction, location change and lifecycle transition. Keep unsaved-edit cancel/confirmation guards. No accidental Home on dismissing dialogs/settings.
5. Settings icon opens existing technician confirmation (Cancel default) with one click, avoiding holds; keep old shortcuts compatible.
6. Camera names from RTSP: best effort meaningful SDP session/media name, propagated as Format.label if technically supported, including in-band discovery paths. Do not infer names from authenticated URLs. Sanitize BEFORE Format/logs: reject URLs/userinfo/control/bidi and generic session names, cap80codepoints. Prefer saved custom names; use detected name only over generated Camera N/กล้อง N names. Cache optional sanitized metadata without schema bump and clear when subURL changes. No ONVIF/API/dependency expansion. Explain limitations in docs.
7. Small persistent bottom-right per-tile indicator based on actual rendered output: live/FPS and no-new-frames state after3000ms; distinguish connecting/error, never synthetic heartbeat based only on a timer. Still scenes remain live if new frames arrive. Poll at1s, stop callbacks on release/background, no new players. Do not show bandwidth unless measured from real network byte counters; actual frame health fulfills owner's frozen-image concern.
8. Version0.5.1/code1016 for retained-key upgrade. Update relevant docs/CHANGELOG to final controls and test limitations.

Use TDD for meaningful behavior: host tests for name sanitizer/frame-health/back state when extracted; native regressions for controls/cycle/default Back/Home/idle/metadata/rendered-frame stalled behavior where feasible. Observe RED before implementation. Existing toolbar-focused idle test must change to1500ms behavior, not simply be removed. Run host/native checks and inspect synthetic screenshots. No device install or CI dispatch from worker; root handles delivery.

## Finishing criteria
Independent review, relevant host/native/lint/build checks, synthetic screenshot inspection, Draft PR updated, private signed APK prepared when checks pass. Public release remains separate approval.

## Ledger
- Base2062113. Ruling: continuous execution follows owner's Review and implement instruction; latest ten items supersede earlier toolbar/Back choices.
- Ruling: actual rendered FPS/frame age is the first indicator; no bandwidth number without a genuine byte measurement. A still scene can be healthy, so pixel motion is not required.
- Ruling: one Back restores default layout while keeping camera identity; second Back within700ms opens Android Home. This is reversible and follows the owner's explicit distinction.
