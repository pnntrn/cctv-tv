package com.pnntrn.cctv;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/** HTTPS-only redirects and bounded, cancellable downloads. No Android or playback dependencies. */
public final class UpdateTransfer implements AutoCloseable {
    public final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile HttpURLConnection connection;
    private final long started = System.nanoTime();
    public InputStream open(String address) throws IOException {
        URI uri = UpdatePolicy.https(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            check();
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            publish(c); c.setInstanceFollowRedirects(false); c.setConnectTimeout(12000); c.setReadTimeout(15000);
            c.setRequestProperty("Accept-Encoding", "identity"); c.setRequestProperty("User-Agent", "CCTV-TV-Updater/1");
            c.setRequestProperty("Cache-Control", "no-cache");
            check(); int status = c.getResponseCode();
            check();
            if (status == 200) return c.getInputStream();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String next = c.getHeaderField("Location"); c.disconnect();
                if (next == null) throw new IOException("Invalid update redirect");
                try { uri = UpdatePolicy.https(uri.resolve(next).toString()); }
                catch (IllegalArgumentException e) { throw new IOException("Update redirect must use HTTPS", e); }
            } else { c.disconnect(); throw new IOException("Update server unavailable (" + status + ")"); }
        }
        throw new IOException("Too many update redirects");
    }
    private void check() throws InterruptedIOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
        if (System.nanoTime() - started > 180_000_000_000L) throw new InterruptedIOException("Update timed out");
    }
    public byte[] manifest(String address) throws IOException {
        try (InputStream in = open(address); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) { check(); if (bytes.size() + count > UpdatePolicy.MAX_MANIFEST) throw new IOException("Update information too large"); bytes.write(buffer, 0, count); }
            return bytes.toByteArray();
        } finally { disconnect(); }
    }
    public void download(String address, File destination, long size, String hash, IntConsumer progress) throws IOException {
        boolean complete = false;
        try (InputStream in = open(address); OutputStream out = new FileOutputStream(destination)) {
            copy(in, out, size, hash, cancelled, p -> { progress.accept(p); }); check(); complete = true;
        } finally { disconnect(); if (!complete) destination.delete(); }
    }
    public static void copy(InputStream in, OutputStream out, long size, String hash, AtomicBoolean cancelled, IntConsumer progress) throws IOException {
        if (size < 1 || size > UpdatePolicy.MAX_APK) throw new IOException("Invalid APK size");
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
        byte[] buffer = new byte[16384]; long total = 0; int last = -1; long deadline = System.nanoTime() + 180_000_000_000L;
        while (true) {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
            if (System.nanoTime() > deadline) throw new InterruptedIOException("Download timed out");
            int count = in.read(buffer); if (count == -1) break;
            total += count; if (total > size) throw new IOException("APK size does not match");
            out.write(buffer, 0, count); digest.update(buffer, 0, count);
            int percent = (int) (100 * total / size); if (last != percent) { last = percent; progress.accept(percent); }
        }
        if (total != size || !UpdatePolicy.hex(digest.digest()).equalsIgnoreCase(hash)) throw new IOException("APK verification failed; please retry");
        out.flush();
    }
    synchronized void publish(HttpURLConnection next) throws InterruptedIOException {
        if (cancelled.get()) { next.disconnect(); throw new InterruptedIOException("Cancelled"); }
        connection=next;
    }
    private synchronized void disconnect() { HttpURLConnection c = connection; if (c != null) c.disconnect(); }
    @Override public synchronized void close() { cancelled.set(true); disconnect(); }
}
