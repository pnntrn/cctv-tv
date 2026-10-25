package com.pnntrn.cctv;

/** Health derived solely from the decoder's cumulative rendered output count. */
final class RenderedFrameHealth {
    enum State { CONNECTING, LIVE, STALLED }
    private long sampledAt = -1, lastFrameAt = -1;
    private int previousCount;
    private double fps;
    State sample(long now, int rendered) {
        rendered = Math.max(0, rendered);
        if (rendered < previousCount || (sampledAt >= 0 && now < sampledAt)) {
            lastFrameAt = -1; sampledAt = -1; fps = 0;
        }
        int delta = sampledAt < 0 ? rendered : rendered - previousCount;
        fps = sampledAt >= 0 && now > sampledAt ? Math.max(0, delta) * 1000.0 / (now - sampledAt) : 0;
        if (delta > 0) lastFrameAt = now;
        sampledAt = now; previousCount = rendered;
        return lastFrameAt < 0 ? State.CONNECTING : now - lastFrameAt >= 3000 ? State.STALLED : State.LIVE;
    }
    double framesPerSecond() { return fps; }
    long lastFrameTime() { return lastFrameAt; }
}
