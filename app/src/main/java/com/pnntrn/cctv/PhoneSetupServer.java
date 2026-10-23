package com.pnntrn.cctv;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ephemeral LAN setup endpoint; active only while the TV pairing dialog is open. */
public final class PhoneSetupServer implements AutoCloseable {
    public interface Handler { JSONObject handle(String action, JSONObject input) throws Exception; }
    private final ServerSocket server;
    private final Handler handler;
    private final String page;
    private final String authority;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private JSONObject pending;
    private String proof;
    public final PairingSession session = new PairingSession(now());
    private static long now() { return System.nanoTime() / 1000000; }
    public PhoneSetupServer(String address, String page, Handler handler) throws IOException {
        this.page = page; this.handler = handler;
        server = new ServerSocket(); server.bind(new InetSocketAddress(InetAddress.getByName(address), 0), 4);
        authority = address + ":" + server.getLocalPort();
        Thread worker = new Thread(() -> {
            while (active.get()) try (Socket socket = server.accept()) {
                if (session.expired(now())) { close(); break; }
                socket.setSoTimeout(5000); serve(socket);
            } catch (Exception ignored) { /* No request bodies, credentials or URLs are logged. */ }
        }, "cctv-phone-setup"); worker.setDaemon(true); worker.start();
    }
    public String address() { return "http://" + authority; }
    public static String localAddress() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface n = interfaces.nextElement();
            if (!n.isUp() || n.isLoopback()) continue;
            Enumeration<InetAddress> addresses = n.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress a = addresses.nextElement();
                if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        }
        throw new SocketException("No LAN address");
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int c; (c = in.read()) != -1;) {
            if (c == '\n') return b.toString("UTF-8").replace("\r", "");
            if (b.size() >= 8192) throw new IOException(); b.write(c);
        }
        throw new EOFException();
    }
    private void serve(Socket socket) throws Exception {
        InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream();
        String[] request = line(in).split(" "); if (request.length != 3) return;
        Map<String,String> headers = new HashMap<>();
        for (int i = 0; i < 64; i++) {
            String l = line(in); if (l.isEmpty()) break;
            int c = l.indexOf(':'); if (c > 0) headers.put(l.substring(0, c).toLowerCase(Locale.ROOT), l.substring(c + 1).trim());
            if (i == 63) return;
        }
        if (!authority.equals(headers.get("host"))) { respond(out, 403, "{}", ""); return; }
        String method = request[0], path = request[1], peer = socket.getInetAddress().getHostAddress();
        if (method.equals("GET") && path.equals("/")) { respond(out, 200, page, "", "text/html; charset=utf-8"); return; }
        String cookie = "";
        for (String part : headers.getOrDefault("cookie", "").split(";")) if (part.trim().startsWith("cctv=")) cookie = part.trim().substring(5);
        if (method.equals("GET") && path.equals("/api/config")) {
            if (!session.authorized(cookie, peer, now())) { respond(out, 403, "{}", ""); return; }
            respond(out, 200, handler.handle("config", new JSONObject()).toString(), ""); return;
        }
        if (!method.equals("POST") || !headers.getOrDefault("content-type", "").startsWith("application/json")
                || !address().equals(headers.get("origin")) || headers.containsKey("transfer-encoding")) {
            respond(out, 403, "{}", ""); return;
        }
        int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        if (length < 1 || length > 32768) { respond(out, 413, "{}", ""); return; }
        byte[] body = new byte[length]; new DataInputStream(in).readFully(body);
        JSONObject input = new JSONObject(new String(body, StandardCharsets.UTF_8));
        if (path.equals("/api/pair")) {
            String token = session.pair(input.optString("pin"), peer, now());
            if (token == null) { respond(out, 403, "{\"message\":\"PIN rejected or session closed\"}", ""); return; }
            respond(out, 200, "{}", "Set-Cookie: cctv=" + token + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=600\r\n"); return;
        }
        if (!active.get() || !session.authorized(cookie, peer, now())) { respond(out, 403, "{}", ""); return; }
        try {
            if (path.equals("/api/test")) {
                pending = null; proof = null;
                JSONObject draft = handler.handle("prepare", input);
                String url = draft.getString("kind").equals("nvr")
                        ? ProfileStore.nvrFromJson(draft.getJSONObject("nvr")).url(draft.getJSONObject("nvr").getInt("first"), false)
                        : draft.getString("sub");
                RtspProbe.Result result = RtspProbe.test(url);
                JSONObject response = new JSONObject().put("okay", result.okay).put("message", result.message);
                if (result.okay && active.get() && session.authorized(cookie, peer, now())) {
                    pending = draft; proof = UUID.randomUUID().toString(); response.put("proof", proof);
                }
                respond(out, 200, response.toString(), "");
            } else if (path.equals("/api/save")) {
                if (pending == null || !Objects.equals(proof, input.optString("proof"))) throw new IllegalArgumentException("Test this configuration before saving");
                JSONObject draft = pending; pending = null; proof = null;
                if (!active.get() || session.expired(now())) throw new IllegalArgumentException("Pairing session ended");
                respond(out, 200, handler.handle("save", draft).toString(), "");
            } else respond(out, 404, "{}", "");
        } catch (Exception e) {
            // Intentionally generic; exception messages can contain credentials.
            respond(out, 400, "{\"message\":\"Check the fields and channel range, or reopen pairing on the TV\"}", "");
        }
    }
    private static void respond(OutputStream out, int code, String body, String extra) throws IOException { respond(out, code, body, extra, "application/json"); }
    private static void respond(OutputStream out, int code, String body, String extra, String type) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " Response\r\nContent-Type: " + type + "\r\nContent-Length: " + bytes.length
                + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n"
                + extra + "\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8)); out.write(bytes); out.flush();
    }
    @Override public void close() {
        active.set(false);
        try { server.close(); } catch (IOException ignored) { }
    }
    public boolean isActive() { return active.get() && !session.expired(now()); }
}
