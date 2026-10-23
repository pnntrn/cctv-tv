package com.pnntrn.cctv;

import java.net.URI;
import java.util.Locale;

/** Small, platform-independent policy for the public update feed. */
public final class UpdatePolicy {
    public static final long MAX_APK = 64L * 1024 * 1024;
    public static final int MAX_MANIFEST = 64 * 1024;
    public static final String FEED = "https://raw.githubusercontent.com/pnntrn/cctv-updates/main/latest.json";
    private UpdatePolicy() { }
    public static URI https(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPath().matches(".*(?:^|/)\\.{1,2}(?:/|$).*") || uri.getFragment() != null || uri.getPort() != -1 || !uri.normalize().equals(uri))
                throw new IllegalArgumentException("Update address must use HTTPS");
            return uri;
        } catch (java.net.URISyntaxException e) { throw new IllegalArgumentException("Invalid update address", e); }
    }
    public static boolean newer(long available, long installed) { return available > installed; }
    public static void validate(long code, String name, long size, String sha256, int minSdk) {
        if (code <= 0 || name == null || name.trim().isEmpty() || name.length() > 64 || size < 1 || size > MAX_APK
                || sha256 == null || !sha256.matches("[a-fA-F0-9]{64}") || minSdk < 26 || minSdk > 1000)
            throw new IllegalArgumentException("Invalid update information");
    }
    /** Limit recursion before Android's recursive JSONObject parser sees remote data. */
    public static void jsonDepth(String json) {
        int depth=0; boolean string=false, escaped=false;
        for(int i=0;i<json.length();i++) {
            char c=json.charAt(i);
            if(string) {
                if(escaped) escaped=false;
                else if(c=='\\') escaped=true;
                else if(c=='"') string=false;
            } else if(c=='"') string=true;
            else if(c=='{' || c=='[') { if(++depth>8) throw new IllegalArgumentException("Update data too deeply nested"); }
            else if(c=='}' || c==']') { if(--depth<0) throw new IllegalArgumentException("Invalid update data"); }
        }
        if(string || depth!=0) throw new IllegalArgumentException("Invalid update data");
    }
    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
