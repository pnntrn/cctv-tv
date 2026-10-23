package com.pnntrn.cctv;

import org.junit.Test;

public class CoreChecksTest {
    @Test public void streamUrlsValidationPagingAndReordering() { CoreChecks.run(); }
    @Test public void recorderUpdatesPairingAndProbes() throws Exception { ExtendedChecks.run(); }
}
