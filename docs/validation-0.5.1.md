# CCTV0.5.1 evaluation

Issue:[#4](https://github.com/pnntrn/cctv-tv/issues/4). Draft PR:[#5](https://github.com/pnntrn/cctv-tv/pull/5). Refinement of the tested0.5.0 SUB-only workflow. This record does not authorize merge, public release, tags or updater promotion.

## Required checks

- Controls: adjacent top-right icons; cycling location/layout; next-page wrap; ordered location/layout/page text;1500ms idle timeout with usable focus recovery.
- Back: default grid restoration followed by Android Home only on a second distinct short press within700ms; dialogs, settings and staged edits remain safe.
- Frame health: actual rendered frame counters, connecting/live/no-new-frames/error states, release cleanup; static scenes remain healthy while frames arrive. No invented bandwidth values.
- RTSP names: safe meaningful SDP metadata only, preserve custom names, reject generic/unsafe names, preserve discovered-format labels and encrypted schema3 compatibility.
- Host/native/lint/minified build, independent review, synthetic screenshot inspection and retained-key private evaluation artifact verification.

## Baseline evidence

Final0.5.0 source2062113 passed [build and native CI](https://github.com/pnntrn/cctv-tv/actions/runs/40133601863):74 host methods,44 native methods and15 required1920x1080 synthetic captures. The retained-key private APK was verified and installed in place on the owner's Mi Box without changing first-install time or clearing profiles. The owner confirmed fullscreen SUB smooth. These results establish the baseline, not the0.5.1 refinements.

Six synthetic320x180 feeds decoded together in baseline tests. This does not establish six720p streams are smooth on physical hardware. Some configured channels had stream/decoder failures during private diagnostics; do not present them as six healthy real feeds. Real footage, authenticated URLs, keys and private logs are excluded from Git.

## Refinement results

Implementation864119e:85 host methods (50app and35RTSP), zero failures/errors/skips; debug and AndroidTest APK compilation passed. All15 initial workflow native methods passed on isolated emulator-5584, including focused-toolbar hide/restore, direct icons, location/page cycling, fullscreen next-page, default-grid identity restoration and double-Back Home. Subsequent focused Back/dialog safety and caption geometry regressions also passed. The expanded whole class remains a final-CI requirement.

Fresh actual-decoder checks passed for H26457.544s and HEVC57.159s: repeated identical pictures stay live while frames arrive, paused RTP becomes no-new-frames, resuming recovers, sampling stops and background releases players. Thai SDP name propagation also passed. The first Thai-name run exposed ASCII encoding in the synthetic server; correcting its response to UTF-8 fixed the fixture. An older decoder result was briefly misreported during development, then explicitly corrected; these are the fresh unique-log results.

Additional checks passed56core+38extended, Digest MD5 with/without qop,22updater checks and12Python release-verifier tests. Visual review caught fullscreen caption/help overlap; a failing geometry regression and measured help-height inset address it. Final independent review, lint/minified release and final-source CI remain pending at this snapshot; consult the PR for final private artifact status.

## Recovery and distribution

Retain package com.pnntrn.cctv, signing key, installation and schema3 reader/writer. Never uninstall, clear profiles or force a downgrade. Unmodified0.4.x cannot read migrated profiles. Rollback requires compatible schema3 code, a higher version code and retained key. No NVR encoding change, new production dependency or public publication is part of this evaluation.
