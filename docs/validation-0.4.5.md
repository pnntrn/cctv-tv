# CCTV 0.4.5 validation

Finishing criteria: reproduce the physical black screen; establish a packet-level cause; obtain RED/GREEN sample-flag regressions; pass unit/lint/build and native decoder gates; install the retained-key candidate over the owner app and verify actual picture before claiming the device fixed. Publish the verified APK and update feed only after gates pass. Keep PR unmerged.

## Root cause

The owner authorized ADB diagnosis on the Xiaomi Mi Box. Installed CCTV0.4.4/code1012 was active. Camera startup configured an Amlogic HEVC decoder but produced no first frame. Forcing TCP did not restore the picture. Separate invalid channel paths returned RTSP404; that is a distinct configuration condition.

A temporary loopback RTSP relay in an unsaved recorder preview captured only packet metadata, with retained credentials remaining on the TV. The actual IDR chain had contiguous sequence numbers, one timestamp, header `62 01 93` at S=1 (type19), continuation headers `62 01 26` (type38), and final `62 01 66` at E=1/marker=1. P-picture chains similarly used type1 start and type2 continuation. No private video or authenticated URL is committed. The unsaved preview was discarded and the original transport restored.

The reader reconstructed coded bytes from the correct first header but classified key status from the final header. It therefore emitted these actual IDRs without the keyframe flag. The fix latches the first fragment's key status and applies it at completion. Per-picture flags also accumulate/reset consistently, and defined IRAP types16-21 are recognized.

## Automated evidence

Six new sample-flag methods compiled and ran against the original code: five failed, with the standard-FU control passing. They cover observed shifted continuation headers, standard FU, valid/reserved IRAP types, suffix SEI, aggregation flag leakage and seek reset. After the fix, all six passed. Native synthetic fixtures reproduce shifted continuation headers for actual HEVC decoding with and without fmtp. Remaining build/native/publication and physical install results are recorded in PR1 after verification.

Package remains com.pnntrn.cctv, version0.4.5/code1013, minAPI26, same retained certificate/profile format. No new production dependency or NVR encoding change. This is a candidate; one working physical recorder does not certify all firmware/TV combinations or decoder capacity.
