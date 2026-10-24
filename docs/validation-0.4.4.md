# CCTV 0.4.4 / 1012 — automatic codec and separate stream checks

## Scope

Owner chose automatic codec detection with technician tests for both streams. Preview now offers ภาพรอง / ภาพหลัก test buttons, independent results and codec labels after an actual rendered frame. One player runs at a time. Saving preserves the decoded-substream requirement; a verified main stream cannot unlock an unverified substream, and a main failure does not overwrite a successful substream result. Close/background/retest/replacement reject stale callbacks. Normal viewing and main-to-sub fallback remain.

H.265+ is vendor-optimized HEVC, not a separately detectable codec label. Synthetic standard HEVC tests cannot establish every vendor firmware mode or TV decoder profile. [Compatibility and vendor references](COMPATIBILITY.md) list preset paths, detected-codec behavior and limits.

## Evidence

- Native fail-first [40066874472](https://github.com/pnntrn/cctv-tv/actions/runs/40066874472) failed at the expected absence of a separate substream test control. Pure stream-state tests first failed to compile for the missing API, then passed four methods. A review regression reproduced cancelled progress remaining visible after switching; the state now clears that unfinished result, and all five state methods pass in the final suite. Native codec tests first failed to compile for the missing detected-codec getter, then compiled after implementation.
- Vendor SDP tests reproduced unused dynamic audio/application failures before the parser fix. An additional unused multi-format regression reproduced the same rejection. All six targeted cases now pass, including strict malformed/multi-format video rejection.
- Full local Gradle test/lint/debug/release/AndroidTest build passed; 55 unit methods, zero failures/errors. Lint: zero errors, 20 existing warnings. Standalone release/workflow checks and final native gate evidence are recorded in the PR after verification.
- New native cases test real rendered H.264 substream and H.265 main stream via Hikvision, VIGI NVR and VIGI camera URL paths. They assert detected codecs and independent UI results, use D-pad selection to change streams, verify old/new connection release and export picture-test captures. These are synthetic loopback servers with generated video, not physical vendor certification.
- UI callback contracts separately exercise stale/retested/failed/background callbacks and main/sub save separation using test-only callbacks; they are not presented as decoder proof. Native first-frame callbacks establish decoding.

No local ADB/Mi Box access, NVR encoder change, new production dependency or storage/key migration. Package remains com.pnntrn.cctv, minimum API26, retained certificate `4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b`. Tag candidate v0.4.4-rc.2 must pass signed build/native gates. PR remains unmerged. Actual owner-device H.265+ decoding, all 11 channels/capacity and in-place profile retention remain device checks; the earlier missing-camera cause is unconfirmed.

The first candidate tag run [40067871562](https://github.com/pnntrn/cctv-tv/actions/runs/40067871562) passed build but failed one native assertion at VIGI-camera D-pad starting focus, after H.264 decoded. The other two pairs decoded both codecs; all other native methods passed. Publication was blocked. The fixture now waits for actual dialog-window input focus, establishes remote navigation mode, asserts the starting sub-button focus, and waits for the Right-key focus result before OK. The test still requires actual HEVC decoding and connection cleanup. Production source is unchanged; rc.2 reruns the full signed native gate.
