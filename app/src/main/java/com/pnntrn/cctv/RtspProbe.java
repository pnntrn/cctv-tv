package com.pnntrn.cctv;

import java.net.URI;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Short RTSP DESCRIBE check. Discovers video and authenticates; does not claim decoder compatibility. */
public final class RtspProbe {
    public static final class Result {
        public final boolean okay; public final String message;
        Result(boolean okay, String message) { this.okay = okay; this.message = message; }
    }
    private RtspProbe() {}
    public static Result test(String url) {
        try {
            CameraRules.validateUrl(url); URI u = URI.create(url);
            String auth = u.getRawUserInfo(), user = "", password = "";
            if (auth != null) {
                String[] parts = auth.split(":", 2);
                user = decode(parts[0]); password = parts.length == 2 ? decode(parts[1]) : "";
            }
            String authority = u.getRawAuthority(); authority = authority.substring(authority.lastIndexOf('@') + 1);
            String target = URI.create("rtsp://" + authority + u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery())).toASCIIString();
            try (Socket socket = new Socket()) {
                String host = u.getHost().replace("[", "").replace("]", "");
                socket.connect(new InetSocketAddress(host, u.getPort() < 0 ? 554 : u.getPort()), 3000);
                socket.setSoTimeout(4000);
                InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream();
                Response r = describe(out, in, target, "", 1);
                if (r.code == 401) {
                    String challenge = r.headers.getOrDefault("www-authenticate", "");
                    String authorization;
                    if (challenge.toLowerCase(Locale.ROOT).startsWith("basic")) {
                        authorization = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
                    } else if (challenge.toLowerCase(Locale.ROOT).startsWith("digest")) {
                        authorization = digest(challenge, user, password, target);
                    } else return new Result(false, "RTSP authentication method is unsupported");
                    r = describe(out, in, target, authorization, 2);
                }
                if (r.code == 401 || r.code == 403) return new Result(false, PlaybackFailure.AUTH.message);
                if (r.code == 404) return new Result(false, PlaybackFailure.PATH.message);
                if (r.code != 200) return new Result(false, "Camera rejected the stream request (RTSP " + r.code + ")");
                boolean video = r.body.contains("m=video ");
                return new Result(video, video ? "Video stream found • verify picture on the TV" : "No video track found at this stream path");
            }
        } catch (UnsupportedOperationException e) { return new Result(false, "RTSP authentication algorithm is unsupported"); }
        catch (Exception e) { return new Result(false, PlaybackFailure.classify(e, 0).message); }
    }
    private static String decode(String s) throws Exception { return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8"); }
    private static class Response { int code; Map<String,String> headers = new HashMap<>(); String body = ""; }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); int c;
        while ((c = in.read()) != -1) { if (c == '\n') return b.toString("UTF-8").replace("\r", ""); if (b.size() >= 8192) throw new IOException(); b.write(c); }
        throw new EOFException();
    }
    private static Response describe(OutputStream out, InputStream in, String uri, String auth, int seq) throws Exception {
        String request = "DESCRIBE " + uri + " RTSP/1.0\r\nCSeq: " + seq + "\r\nAccept: application/sdp\r\nUser-Agent: CCTV-TV\r\n"
                + (auth.isEmpty() ? "" : "Authorization: " + auth + "\r\n") + "\r\n";
        out.write(request.getBytes(StandardCharsets.UTF_8)); out.flush();
        Response r = new Response(); String[] status = line(in).split(" "); r.code = Integer.parseInt(status[1]);
        for (int i = 0; i < 64; i++) { String h = line(in); if (h.isEmpty()) break;
            int colon = h.indexOf(':'); if (colon > 0) r.headers.put(h.substring(0, colon).toLowerCase(Locale.ROOT), h.substring(colon + 1).trim());
            if (i == 63) throw new IOException();
        }
        int length = Integer.parseInt(r.headers.getOrDefault("content-length", "0"));
        if (length < 0 || length > 262144) throw new IOException();
        byte[] bytes = new byte[length]; new DataInputStream(in).readFully(bytes); r.body = new String(bytes, StandardCharsets.UTF_8); return r;
    }
    private static String md5(String s) throws Exception {
        byte[] bytes = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
    private static String quoted(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", ""); }
    private static String digest(String challenge, String user, String password, String uri) throws Exception {
        Map<String,String> p = new HashMap<>();
        Matcher m = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))").matcher(challenge);
        while (m.find()) p.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2) != null ? m.group(2) : m.group(3));
        if (!p.getOrDefault("algorithm", "MD5").equalsIgnoreCase("MD5")) throw new UnsupportedOperationException();
        String realm = p.getOrDefault("realm", ""), nonce = p.getOrDefault("nonce", "");
        String ha1 = md5(user + ":" + realm + ":" + password), ha2 = md5("DESCRIBE:" + uri);
        String cnonce = UUID.randomUUID().toString().replace("-", ""), qop = p.get("qop");
        if (qop != null && !Arrays.asList(qop.split("\\s*,\\s*")).contains("auth")) throw new UnsupportedOperationException();
        String response = md5(ha1 + ":" + nonce + ":" + (qop == null ? "" : "00000001:" + cnonce + ":auth:") + ha2);
        return "Digest username=\"" + quoted(user) + "\", realm=\"" + quoted(realm) + "\", nonce=\"" + quoted(nonce)
                + "\", uri=\"" + quoted(uri) + "\", response=\"" + response + "\""
                + (p.containsKey("opaque") ? ", opaque=\"" + quoted(p.get("opaque")) + "\"" : "")
                + (qop == null ? "" : ", qop=auth, nc=00000001, cnonce=\"" + cnonce + "\"");
    }
}
