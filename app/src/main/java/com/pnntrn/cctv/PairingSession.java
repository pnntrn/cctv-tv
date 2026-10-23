package com.pnntrn.cctv;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** One phone, five PIN attempts, ten minute session; no reusable camera credentials in pairing tokens. */
public final class PairingSession {
    private final long expires;
    public final String pin;
    private final String token;
    private String peer;
    private int attempts;
    public PairingSession(long now) {
        SecureRandom random = new SecureRandom(); pin = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        byte[] bytes = new byte[24]; random.nextBytes(bytes);
        token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); expires = now + 600000;
    }
    public boolean expired(long now) { return now >= expires || attempts >= 5; }
    public synchronized String pair(String code, String address, long now) {
        if (expired(now) || peer != null) return null;
        if (!same(pin, code)) { attempts++; return null; }
        peer = address; return token;
    }
    public synchronized boolean authorized(String value, String address, long now) {
        return !expired(now) && address.equals(peer) && same(token, value);
    }
    private static boolean same(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
