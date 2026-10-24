package com.pnntrn.cctv;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecorderDraftTest {
    @Test public void rawInvalidAndEmptyNumericEditsSurviveRoundTrip() throws Exception {
        RecorderDraft d = new RecorderDraft();
        d.locationId = "fixture-location"; d.recorderId = "fixture-recorder";
        d.name = " Generic recorder "; d.host = "192.0.2.10"; d.username = "operator";
        d.port = "99999999999999999999"; d.first = ""; d.count = "not-a-number"; d.prefix = "Entrance";
        RecorderDraft restored = RecorderDraft.decode(d.encode());
        assertEquals(d.name, restored.name); assertEquals(d.host, restored.host);
        assertEquals(d.port, restored.port); assertEquals("", restored.first);
        assertEquals("not-a-number", restored.count); assertEquals(d.prefix, restored.prefix);
        assertEquals(d.key(), restored.key());
    }
    @Test public void unknownSecretKeysAreNeverReserialized() throws Exception {
        RecorderDraft d = RecorderDraft.decode("{\"password\":\"fixture-secret\",\"name\":\"Draft\"}");
        assertFalse(d.encode().contains("password")); assertFalse(d.encode().contains("fixture-secret"));
        for (java.lang.reflect.Field f : RecorderDraft.class.getDeclaredFields())
            assertFalse(f.getName().toLowerCase().contains("password"));
    }
    @Test public void editingAndAddingUseSeparateDraftKeys() {
        RecorderDraft add = new RecorderDraft(), edit = new RecorderDraft();
        add.locationId = edit.locationId = "location"; edit.recorderId = "recorder";
        assertNotEquals(add.key(), edit.key());
    }
}
