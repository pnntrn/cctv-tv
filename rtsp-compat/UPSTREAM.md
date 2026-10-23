Media3 RTSP 1.8.0 vendored from the official Google Maven sources JAR.
Source SHA-256: a8cf8fed25cc02ba3d385c9e262718d742c10a177ea923a57dcb756f65add0ca
Apache-2.0; individual upstream file notices retained.
CCTV modifications: video-only SDP track selection; allow absent H264 parameter sets; bounded RFC6184 depacketization into the existing Media3 H264 elementary-stream reader for actual in-band SPS/PPS initialization. No native fallback dependency. H265 without SDP configuration remains unsupported and is reported without retry.

The MediaPeriod starts the real RTSP session before preparation only when H264 requires in-band configuration; SETUP is issued once per transport. Internal classes opt in to the Media3 UnstableApi contract. No upstream dependency versions are upgraded.
