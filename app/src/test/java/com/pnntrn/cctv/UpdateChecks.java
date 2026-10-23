package com.pnntrn.cctv;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
public final class UpdateChecks {
    private static int checks;
    private static void ok(boolean value) { checks++; if (!value) throw new AssertionError("update check " + checks); }
    private static void rejects(Runnable value) { boolean rejected=false;try{value.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected); }
    public static void main(String[] args) throws Exception {
        ok(UpdatePolicy.https("https://raw.githubusercontent.com/pnntrn/cctv-updates/main/latest.json").getScheme().equals("https"));
        for(String value:new String[]{"http://example.com/a","https://u:p@example.com/a","file:///a","https://example.com/a#x","https://example.com:443/a","https://example.com/../a"}) rejects(()->UpdatePolicy.https(value));
        ok(!UpdatePolicy.newer(10,10));ok(!UpdatePolicy.newer(9,10));ok(UpdatePolicy.newer(11,10));
        rejects(()->UpdatePolicy.validate(0,"0.3",30,"f".repeat(64),26));
        rejects(()->UpdatePolicy.validate(11,"0.3",UpdatePolicy.MAX_APK+1,"f".repeat(64),26));
        rejects(()->UpdatePolicy.validate(11,"0.3",30,"bad",26));
        rejects(()->UpdatePolicy.validate(11,"0.3",30,"f".repeat(64),0));
        byte[] bytes="verified apk fixture".getBytes(StandardCharsets.UTF_8);
        String digest=UpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        UpdateTransfer.copy(new ByteArrayInputStream(bytes),out,bytes.length,digest,new AtomicBoolean(),p->{});ok(java.util.Arrays.equals(bytes,out.toByteArray()));
        for(int expected:new int[]{bytes.length-1,bytes.length+1}) {
            boolean bad=false;try{UpdateTransfer.copy(new ByteArrayInputStream(bytes),new ByteArrayOutputStream(),expected,digest,new AtomicBoolean(),p->{});}catch(IOException e){bad=true;}ok(bad);
        }
        boolean bad=false;try{UpdateTransfer.copy(new ByteArrayInputStream(bytes),new ByteArrayOutputStream(),bytes.length,"0".repeat(64),new AtomicBoolean(),p->{});}catch(IOException e){bad=true;}ok(bad);
        bad=false;try{UpdateTransfer.copy(new ByteArrayInputStream(bytes),new ByteArrayOutputStream(),bytes.length,digest,new AtomicBoolean(true),p->{});}catch(InterruptedIOException e){bad=true;}ok(bad);
        UpdatePolicy.jsonDepth("{\"notes\":\"[{}]\",\"extra\":[{}]}");ok(true);
        rejects(()->UpdatePolicy.jsonDepth("[".repeat(10000)+"]".repeat(10000)));
        UpdateTransfer cancelledTransfer=new UpdateTransfer();cancelledTransfer.close();
        final boolean[] disconnected={false};
        java.net.HttpURLConnection late=new java.net.HttpURLConnection(new java.net.URL("https://example.com")) {
            public void connect(){throw new AssertionError("Cancelled transfer connected");}
            public void disconnect(){disconnected[0]=true;}
            public boolean usingProxy(){return false;}
        };
        boolean refused=false;try{cancelledTransfer.publish(late);}catch(InterruptedIOException e){refused=true;}
        ok(refused && disconnected[0]);
        System.out.println("Passed "+checks+" update checks");
    }
}
