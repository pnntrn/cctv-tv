package com.pnntrn.cctv;

/** Independent stream checks; only the active attempt may change its result. */
final class StreamCheckState {
    enum Stream { SUB, MAIN }
    enum Status { NOT_TESTED, TESTING, VERIFIED, FAILED }
    private final java.util.EnumMap<Stream, Status> results = new java.util.EnumMap<>(Stream.class);
    private Stream active;
    private int generation;
    StreamCheckState() { reset(); }
    private void reset() { for (Stream stream : Stream.values()) results.put(stream, Status.NOT_TESTED); }
    int begin(Stream stream) {
        if (active != null && active != stream && results.get(active) == Status.TESTING) {
            results.put(active, Status.NOT_TESTED);
        }
        active = stream; results.put(stream, Status.TESTING); return ++generation;
    }
    boolean verified(int attempt) {
        if (active == null || attempt != generation || results.get(active) != Status.TESTING) return false;
        results.put(active, Status.VERIFIED); return true;
    }
    boolean failed(int attempt) {
        if (active == null || attempt != generation || results.get(active) == Status.FAILED) return false;
        results.put(active, Status.FAILED); return true;
    }
    Status status(Stream stream) { return results.get(stream); }
    boolean canSave() { return results.get(Stream.SUB) == Status.VERIFIED; }
    void close() { generation++; active = null; reset(); }
}
