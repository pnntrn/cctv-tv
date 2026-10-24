# Substream TV workflow

The owner requested review and implementation after the proposed flow. Proceed with the existing Java/native Android UI; no new dependency or recorder encoding changes.

## Viewing
- All playback, including fullscreen and technician tests, uses the saved substream. No control can request MAIN. Keep saved MAIN addresses for reversible profile compatibility, but remove the unused libVLC production dependency/backend.
- Back closes the topmost transient dialog, returns fullscreen/settings to the CCTV camera grid, and remains on the grid when already home. Android Home retains its system behavior.
- Borderless video when idle. A translucent top bar overlays video on key/pointer activity, hides after4s inactivity, and stays visible while a bar control has focus. Layout icon left, location name centered, location icon right; accessible Thai labels and visible focus. Up from top-row cameras reaches the bar, Down returns to the selected camera. Click layout cycles layouts; long Left opens layout picker, long Right opens location picker. Hold shortcuts fire once and consume their release; ordinary direction navigation remains predictable.
- Layouts:1,4,6,8,9,16. Six uses a3x3 logical grid: first camera occupies the top-left2x2, five small cameras occupy the right column and bottom row. Eight uses4x2. Preserve aspect ratio with black letterboxing rather than cropping video.
- Runtime layout selection remembers the location's last choice; the technician separately sets its default layout. New/restarted location viewing starts with its saved last layout, initially the default.

## Technician setup
- Retain protected technician entry (hold OK for viewing menu, then hold Back3s plus confirmation); visible Settings requires the same confirmation. Parents can select layout/location without entering setup.
- NVR-only CRUD: create a location and its NVR together, location name and default grid in the same editor. Existing location has one recorder to edit/delete; no standalone camera/raw-URL add path or second recorder add path. Deleting an NVR/location requires confirmation and deletes only the app profile, never the recorder.
- Channel management has live substream previews, camera name/channel, visible/hidden state. Select a source then destination to swap; explicit save/cancel. Hidden channels retain configuration and never run in normal viewing. Offline channels remain visible unless explicitly hidden. All-hidden state provides a clear route to technician setup.
- Changes to order/visibility are staged until Save; Back cancels. Only visible page previews run; release on page change, background and exit.

## Data and migration
- Preserve encrypted storage key, package ID, IDs, credentials and saved URLs. New optional hidden/default-grid fields have backward-compatible defaults. Read schema1/2; write schema3.
- Split legacy multi-NVR locations into one per NVR, preserving every camera exactly once, IDs and each NVR's credentials; first location keeps its ID. Give extra locations unique deterministic IDs/names. Unlinked legacy cameras remain in the first location and are manageable/hideable, not silently deleted. Newly created profiles must use NVR CRUD.

## Approaches reviewed
Keep the old modal menus (less change, misses requested shortcuts); replace all UI with a new framework (large unrelated rewrite); extend native UI with a top overlay and geometry-driven layouts (chosen implementation direction for compatibility and minimal dependencies).

## Validation and boundaries
Unit coverage for layout geometry/navigation, held keys, filtering, migration and persistence. Native coverage for grid/fullscreen Back, substream-only playback, top-bar idle/focus/shortcuts, six layout, staged visual reorder/hide and NVR-only flow. Inspect synthetic1920x1080 captures; test six streams on the owner's Box if reachable, without clearing data. Do not promise six-HD-stream hardware capacity from synthetic tests. Fresh independent review, unit/lint/build/native checks, Draft PR with honest device limits. No automatic merge, public release/tag/feed promotion or recorder changes.
