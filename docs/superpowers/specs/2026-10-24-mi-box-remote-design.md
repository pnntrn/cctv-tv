# Mi Box remote compatibility — approved small change

CCTV must be usable with the owner's Mi Box remote, which has no Menu button. Keep the approved Thai, borderless idle grid and protect parents from accidental configuration changes.

## Approaches

Recommended: hold OK for one second to open the viewing menu; while that menu is open, hold Back for three seconds to request technician setup. Ordinary OK continues opening the selected camera, and ordinary Back continues fullscreen-to-grid, menu dismissal or the existing exit confirmation. This keeps all technician editing behind a deliberate hold and a confirmation focused on “keep watching”.

Alternative: make short Back on the grid open the viewing menu, with a long Back hold for setup. This changes the existing Back/exit behavior and is less consistent with the current navigation.

The owner approved the recommended mapping in chat. Existing Menu shortcuts remain supported for other remotes. No Home, microphone or volume buttons are intercepted.

## Implementation and finishing criteria

Use the existing Android input/dialog and playback stack; no new production dependencies. Cancel holds on release, cancellation, backgrounding and dialog dismissal. A held OK must not also open a camera or toggle its stream on release. A held Back in the viewing menu must not dismiss or accept the technician confirmation. Short/cancelled holds must never open editing.

Update Thai on-screen help and setup instructions. Add key-event and native regressions using only D-pad, OK and Back, including first setup with no cameras, normal camera selection/fullscreen/back, viewing options, safe cancellation and deliberate technician entry. Keep existing Menu coverage. Run build/lint/unit/native checks and inspect screenshots. Publish a new signed patch candidate with an increased version code; preserve app/package/signing identity and saved profiles. Update the existing PR without merging it.
