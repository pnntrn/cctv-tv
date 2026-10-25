package com.pnntrn.cctv;

import org.json.JSONException;
import org.json.JSONObject;

/** Only nonsecret, raw editor text. Validation belongs to Test & save, never draft capture. */
public final class RecorderDraft {
    public String locationId = "", recorderId = "", preset = "HIKVISION";
    public String name = "", host = "", port = "554", username = "admin";
    public String first = "1", count = "16", prefix = "Camera";
    public String locationName = "";
    public int defaultGrid = 4;

    public String key() { return "draft:" + locationId + ":" + (recorderId.isEmpty() ? "new:" + preset : recorderId); }

    public String encode() throws JSONException {
        return new JSONObject().put("locationId", locationId).put("recorderId", recorderId).put("preset", preset)
                .put("name", name).put("host", host).put("port", port).put("username", username)
                .put("first", first).put("count", count).put("prefix", prefix)
                .put("locationName", locationName).put("defaultGrid", defaultGrid).toString();
    }

    public static RecorderDraft decode(String encoded) throws JSONException {
        JSONObject o = new JSONObject(encoded); RecorderDraft d = new RecorderDraft();
        d.locationId = o.optString("locationId", d.locationId); d.recorderId = o.optString("recorderId", d.recorderId);
        d.preset = o.optString("preset", d.preset); d.name = o.optString("name", d.name);
        d.host = o.optString("host", d.host); d.port = o.optString("port", d.port);
        d.username = o.optString("username", d.username); d.first = o.optString("first", d.first);
        d.count = o.optString("count", d.count); d.prefix = o.optString("prefix", d.prefix);
        d.locationName = o.optString("locationName", ""); d.defaultGrid = CameraRules.checkedGrid(o.optInt("defaultGrid",4)); return d;
    }
}
