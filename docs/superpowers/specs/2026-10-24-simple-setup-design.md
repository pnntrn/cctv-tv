# Simpler Mi Box setup — approved design

Owner-approved flow: hold OK for one second → choose **ตั้งค่า** in the viewing menu → existing safe confirmation → technician setup. The existing confirmation defaults to ดูต่อ. Back closes the viewing menu normally regardless of press length; it never requests setup. Keep the legacy Menu shortcut, viewing controls and idle grid.

Use the existing menu and confirmation rather than a new screen or dependency. Add Settings as the last menu item so ordinary camera choices remain first. Update Thai first-launch and menu hints. Retain package com.pnntrn.cctv, release certificate, encrypted profile file/schema/key and updater feed. Publish 0.4.3/code1011 through gated signed automation; leave PR unmerged. No access to connected Mi Box or local ADB during owner configuration.

Check the reported missing profiles by inspecting the upgrade diff and adding synthetic encrypted-profile lifecycle regression coverage. Do not infer owner-device data loss root cause from an empty screen. No speculative storage migration or reset.

## Approved playback extension

Subsequent owner-device diagnosis confirmed the failing video is H265/90000 with no fmtp, accompanied by PCMU audio. Add bounded in-band HEVC VPS/SPS/PPS discovery through the existing Media3 H265 elementary-stream reader, then replay original RTP packets through the existing H265 RTP reader. Keep out-of-band H265 and H264 paths; do not invent codec data or enable saving without decoded video. Synthetic HEVC fixture must independently decode and pass actual native RTSP first-frame checks. No new production dependency or NVR encoder change.
