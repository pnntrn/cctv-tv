package com.pnntrn.cctv;
import org.junit.Test;
import static org.junit.Assert.*;
public class WorkflowDraftTest {
    @Test public void locationAndDefaultLayoutSurviveNonsecretDraft() throws Exception {
        RecorderDraft d=new RecorderDraft();d.locationName="บ้าน";d.defaultGrid=6;
        RecorderDraft restored=RecorderDraft.decode(d.encode());
        assertEquals("บ้าน",restored.locationName);assertEquals(6,restored.defaultGrid);
        assertFalse(d.encode().contains("password"));
    }
    @Test public void oldDraftGetsSafeDefaults() throws Exception {
        RecorderDraft d=RecorderDraft.decode("{}");assertEquals("",d.locationName);assertEquals(4,d.defaultGrid);
    }
}
