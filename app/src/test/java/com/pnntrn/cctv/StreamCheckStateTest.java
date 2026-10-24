package com.pnntrn.cctv;
import org.junit.Test;
import static org.junit.Assert.*;

public class StreamCheckStateTest {
    @Test public void onlyDecodedSubstreamEnablesSave() {
        StreamCheckState state = new StreamCheckState();
        assertFalse(state.canSave());
        int main = state.begin(StreamCheckState.Stream.MAIN);
        assertTrue(state.verified(main)); assertFalse(state.canSave());
        int sub = state.begin(StreamCheckState.Stream.SUB);
        assertFalse(state.canSave()); assertTrue(state.verified(sub)); assertTrue(state.canSave());
    }
    @Test public void switchingRejectsOldCallbacksAndKeepsIndependentResults() {
        StreamCheckState state = new StreamCheckState();
        int sub = state.begin(StreamCheckState.Stream.SUB); assertTrue(state.verified(sub));
        int main = state.begin(StreamCheckState.Stream.MAIN);
        assertFalse(state.failed(sub)); assertTrue(state.failed(main));
        assertTrue(state.canSave());
        assertEquals(StreamCheckState.Status.VERIFIED, state.status(StreamCheckState.Stream.SUB));
        assertEquals(StreamCheckState.Status.FAILED, state.status(StreamCheckState.Stream.MAIN));
        assertFalse(state.verified(main));
    }
    @Test public void retestingSubstreamRevokesEarlierSuccess() {
        StreamCheckState state = new StreamCheckState();
        int first = state.begin(StreamCheckState.Stream.SUB); state.verified(first);
        int second = state.begin(StreamCheckState.Stream.SUB);
        assertFalse(state.canSave()); assertFalse(state.verified(first));
        assertTrue(state.failed(second)); assertFalse(state.canSave());
        assertFalse(state.verified(second));
    }
    @Test public void failureAfterFirstFrameAndClosureRevokeVerification() {
        StreamCheckState state = new StreamCheckState();
        int sub = state.begin(StreamCheckState.Stream.SUB); state.verified(sub);
        assertTrue(state.failed(sub)); assertFalse(state.canSave());
        int fresh = state.begin(StreamCheckState.Stream.SUB); state.verified(fresh);
        state.close(); assertFalse(state.canSave());
        assertFalse(state.verified(fresh)); assertFalse(state.failed(fresh));
        assertEquals(StreamCheckState.Status.NOT_TESTED, state.status(StreamCheckState.Stream.SUB));
    }    @Test public void switchingCancelsUnfinishedProgressButRetainsFinishedResults() {
        StreamCheckState state = new StreamCheckState();
        int sub = state.begin(StreamCheckState.Stream.SUB);
        int main = state.begin(StreamCheckState.Stream.MAIN);
        assertEquals(StreamCheckState.Status.NOT_TESTED, state.status(StreamCheckState.Stream.SUB));
        assertEquals(StreamCheckState.Status.TESTING, state.status(StreamCheckState.Stream.MAIN));
        assertFalse(state.verified(sub)); assertTrue(state.verified(main));
        state.begin(StreamCheckState.Stream.SUB);
        assertEquals(StreamCheckState.Status.VERIFIED, state.status(StreamCheckState.Stream.MAIN));
    }

}
