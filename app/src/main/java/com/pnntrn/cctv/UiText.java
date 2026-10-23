package com.pnntrn.cctv;

import android.content.Context;

/** Localizes an allowlist of safe rule messages; never exposes arbitrary diagnostics. */
public final class UiText {
    private UiText() {}

    public static String translate(Context context, String message) {
        int resource = R.string.common_validation_error;
        if (message != null) switch (message) {
            case "Channel must be 1–256": resource = R.string.common_channel_range; break;
            case "Port must be 1–65535": resource = R.string.common_port_range; break;
            case "Enter an IP address or hostname only": resource = R.string.common_host_only; break;
            case "Use separate URLs for a custom camera": resource = R.string.common_custom_urls; break;
            case "Use a full rtsp://host/path URL; encode special characters in credentials": resource = R.string.common_rtsp_url; break;
            case "Invalid camera position": resource = R.string.common_camera_position; break;
            case "Enter a recorder name": resource = R.string.common_recorder_name; break;
            case "Enter a camera name": resource = R.string.common_camera_name; break;
            case "Enter a location name": resource = R.string.common_location_name; break;
            case "Channel range must be within 1–256": resource = R.string.common_channel_range; break;
            case "Choose an NVR preset": resource = R.string.common_nvr_preset; break;
            case "A standalone camera has one channel": resource = R.string.common_one_channel; break;
            case "Maximum 256 cameras per location": resource = R.string.common_camera_limit; break;
            case "Pairing ended":
            case "Pairing session ended": resource = R.string.common_pairing_ended; break;
            case "Test this configuration before saving": resource = R.string.common_test_before_save; break;
            case "RTSP authentication method is unsupported":
            case "RTSP authentication algorithm is unsupported": resource = R.string.common_rtsp_auth; break;
            case "Video stream found • verify picture on the TV": resource = R.string.common_probe_success; break;
            case "No video track found at this stream path": resource = R.string.common_no_video; break;
            default:
                for (PlaybackFailure failure : PlaybackFailure.values()) {
                    if (failure.message.equals(message)) return playback(context, failure);
                }
                break;
        }
        return context.getString(resource);
    }

    public static String playback(Context context, PlaybackFailure failure) {
        int resource;
        switch (failure) {
            case STREAM_METADATA: resource = R.string.common_playback_metadata; break;
            case AUTH: resource = R.string.common_playback_auth; break;
            case PATH: resource = R.string.common_playback_path; break;
            case DECODER: resource = R.string.common_playback_decoder; break;
            case NETWORK: resource = R.string.common_playback_network; break;
            case TIMEOUT: resource = R.string.common_playback_timeout; break;
            default: resource = R.string.common_playback_unknown; break;
        }
        return context.getString(resource);
    }
}
