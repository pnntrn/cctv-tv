# Automatic codec detection and technician stream checks

Owner chose automatic codec detection with technician tests for both streams. Add explicit ภาพรอง and ภาพหลัก test buttons within the existing connection preview. Each result identifies a real decoded picture and detected H.264 or H.265/HEVC codec. The app does not change the recorder encoder, force a codec or label a synthetic HEVC fixture as vendor H.265+. One player runs at a time; switching/retesting invalidates old callbacks. Back/background closes and releases tests.

Preserve the existing saving contract: a decoded substream is required; mainstream results are separate and cannot verify the substream or hide a failure. Ordinary fullscreen viewing retains its existing main-to-sub fallback. Technician labels explain this. Camera viewing and codec auto-detection remain automatic, with no new controls for parents.

Cover Hikvision, VIGI NVR and VIGI camera URL presets with H.264 sub/H.265 main native loopback tests. Add parser regressions for official VIGI unused audio/application SDP; skip non-video sections before building their descriptions, while retaining strict video validation. Multi-payload video negotiation and changing NVR encoding are outside this patch. Vendor H.265+ optimized HEVC is subject to actual firmware/TV decoder checks; no claim of every model is supported.

Retain com.pnntrn.cctv, certificate, profile schema/key, drafts and updater feed. No new production dependency. Publish signed 0.4.4/code1012 candidate, PR unmerged. No local ADB/Mi Box operations.
