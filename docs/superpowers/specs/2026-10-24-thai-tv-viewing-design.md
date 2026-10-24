# Thai CCTV viewing workflow

## Intent

Parents should open CCTV and watch cameras safely. A technician or their child configures recorders separately. The user chose an edge-to-edge camera grid without borders when idle, rather than automatically opening a single camera.

## Approach

Keep the existing native Android TV application and playback stack. Separate everyday viewing from technician setup within the app. This preserves remote support, the installed package, encrypted profiles and release signing. A new web interface would add unnecessary dependencies. Merely hiding the existing toolbar would leave long-press editing and other accidental entry paths exposed.

## Viewing

- Launch directly into the last location's camera grid. Use the whole display: no permanent header, footer, grid borders or decorative gaps. Preserve each stream's aspect ratio rather than distorting or cropping footage.
- After four seconds without remote input, hide normal labels, selection outlines and viewing controls. Keep playback errors visible.
- A remote press reveals controls and the selected camera outline. Direction buttons select cameras; OK opens the selected camera fullscreen. Left/right in fullscreen changes cameras; Back returns to the same grid page and selected camera. Menu opens a viewing-only popup.
- At the grid's left/right page boundaries, D-pad navigation reaches the previous/next page, so all channels remain accessible without a permanent footer.
- The popup contains camera/grid viewing, grid size and existing-location switching. No add, edit, delete, reorder, connection transport or recorder fields appear in everyday viewing. Long OK does not open camera editing.
- Hide controls without recreating streams, restarting playback or changing camera selection. Do not intercept Home, volume or accessibility/system keys.
- No cameras configured: show a Thai setup message with a deliberate route to technician setup, rather than camera CRUD buttons on the viewing screen.

## Technician setup

- Hold Menu for three seconds, then confirm entry to a separate Thai technician page. A normal Menu press only shows viewing controls. Confirmation defaults to continuing viewing. This guards accidental entry; it is not an authentication lock.
- Setup contains location and NVR management, camera names/order/custom URLs, phone pairing, transport and updates. All delete actions require confirmation with Cancel initially focused.
- Returning to viewing or backgrounding ends technician access. Editing controls cannot be reached by navigating off the grid or holding OK on a tile. Existing draft recovery must not unexpectedly reopen setup during normal startup; the technician can resume a retained draft after entering setup.
- One location normally has one NVR. Lead setup with the location's NVR and its channels. For an existing NVR, the primary action edits that recorder rather than adding a duplicate. Keep extra recorders/custom cameras behind an advanced setup option. Preserve existing multiple-NVR data and channel IDs; do not remove or merge saved devices automatically.

## Thai language

Use short, natural Thai throughout viewing, setup, errors, dialogs, update UI and the phone setup page. Retain product names, RTSP/TCP/IP terms, URLs and user-entered names. Default generated camera labels use กล้อง. Keep English fallback resources where practical; use Android string resources rather than replacing protocol strings. Existing saved English names remain user data.

## Verification and release

- Add meaningful regressions for idle chrome, borderless grid geometry, remote selection/fullscreen/back, viewing-only Menu and long-OK safety, deliberate technician entry/exit, safe delete focus and retained draft access.
- Update existing native tests to the new flow and Thai labels without removing playback/save/lifecycle assertions. Run all unit/core/digest/update checks, Android lint, release/debug builds and the full Android 9 TV native suite. Inspect TV screenshots for Thai legibility and clipping.
- Keep the existing signing certificate and encrypted profiles. Increase the app version for the new UI. Publish a signed APK and SHA-256 checksum as a release in the now-public pnntrn/cctv-apk repository, and link it prominently from the default README.
- Actual Hikvision/Mi Box playback, hardware decoder capacity and in-place profile preservation still require physical-device verification. Label the downloadable build as a release candidate until those checks are complete. Keep the legacy updater feed unchanged in this task.

## Finishing criteria

Ordinary remote use permits watching and selecting cameras without editing configuration; the idle grid fills the TV without borders; all user-facing flows are Thai; technician setup remains available through deliberate entry; tests and visual inspection pass; the public repository provides a working signed APK download.
