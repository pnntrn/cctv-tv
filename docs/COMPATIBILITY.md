# Camera and codec compatibility

Select the recorder/camera preset; CCTV detects the stream codec automatically. A viewer codec choice cannot change the camera encoder. Codec/bitrate/profile changes belong in the device's own configuration.

| Preset | Main stream (channel 1) | Substream (channel 1) |
| --- | --- | --- |
| Hikvision | `/Streaming/Channels/101` | `/Streaming/Channels/102` |
| VIGI NVR | `/live/1/1/avm` | `/live/1/2/avm` |
| VIGI camera | `/stream1` | `/stream2` |

Custom RTSP URLs remain available for devices with different endpoints. Presets choose URL paths, not the video codec. Either endpoint may use H.264 or H.265 according to the device's actual configuration.

## Technician checks

Open Settings and the recorder/camera connection preview. It initially tests the substream; use **ทดสอบภาพรอง** or **ทดสอบภาพหลัก** to check either endpoint. A successful result requires an actual rendered frame and shows its detected codec. Only one preview runs at a time. Both results remain visible; a failure is not presented as a verified picture. Retesting invalidates that stream's prior result, and old callbacks cannot verify a replacement stream.

Saving requires a decoded substream, preserving the viewing grid's existing requirement. Mainstream results are independent; failed main playback does not establish that a substream failed. Normal fullscreen viewing retains its fallback to the substream. Backgrounding or closing discards verification, releases the player and clears entered recorder passwords as before.

## H.265+ and device limits

Hikvision describes H.265+ as optimized H.265/HEVC. CCTV reports a stream advertised and decoded as HEVC as **H.265 / HEVC**; it cannot distinguish a vendor's smart-encoding switch from that codec label. A standard synthetic HEVC test is not proof of every H.265+ device/firmware mode. The TV decoder must support the actual profile, resolution and stream capacity. Use the two picture tests on the actual installation before considering a model verified.

The regression matrix covers synthetic H.264 substream / H.265 mainstream pairs, both configured and in-band codec parameters, and the three preset path families. It does not certify every Hikvision/VIGI model, proprietary encrypted transport or firmware feature. The RTSP module is video-only and ignores unused audio/application SDP sections; multi-payload video negotiation is not implemented. VIGI's SRTP setting can prevent third-party RTSP clients from pulling video; this viewer does not change that setting.

## Vendor references

- [Hikvision main/substream and H.265+ configuration](https://enpinfo.hikvision.com/hkwsen/unzip/20230410194813_20373_doc/GUID-784D17BC-AEA5-4AB1-A257-F1C3FD4C1B53.html)
- [VIGI third-party RTSP camera setup, stream1/stream2](https://www.tp-link.com/us/support/faq/4201/)
- [VIGI NVR live RTSP paths and SRTP setting](https://www.omadanetworks.com/us/support/faq/4677/)
- [VIGI NVR RTSP/SDP example, including unused application metadata](https://www.tp-link.com/uk/support/faq/5223/?app=vigi)

No camera credentials, authenticated URLs, private SDP or camera footage are included in these fixtures or notes.
