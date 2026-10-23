package com.pnntrn.cctv;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ExtendedChecks {
    static int checks;
    static void check(boolean b, String message) { checks++; if (!b) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        checks=0;
        Location l=new Location("Shop"); Nvr n=new Nvr();n.host="192.168.0.10";n.count=4;n.password="a@b";n.apply(l);
        check(l.cameras.size()==4,"add channels");
        Camera c=l.cameras.get(2);String id=c.id;c.name="Counter";
        CameraRules.swap(l.cameras,0,2);
        n.host="192.168.0.20";n.password="new:pass";n.apply(l);
        check(l.cameras.get(0).id.equals(id),"ID and order retained");
        check(c.name.equals("Counter"),"custom name retained");
        check(c.subUrl.contains("192.168.0.20") && c.subUrl.contains("new%3Apass"),"shared settings regenerated");
        Camera standalone=new Camera("Custom","rtsp://other/a","rtsp://other/b");l.cameras.add(standalone);
        n.first=2;n.count=4;n.apply(l);
        check(l.cameras.contains(standalone),"custom retained");
        check(l.cameras.get(0)==c,"range update preserves retained order");
        check(l.cameras.stream().noneMatch(x->x.deviceId.equals(n.id)&&x.channel==1),"out-of-range removed");
        check(l.cameras.stream().anyMatch(x->x.channel==5),"new channel added");
        check(l.devices.size()==1,"device updated without duplication");
        int count=l.cameras.size();n.port=0;
        try{n.apply(l);throw new AssertionError("invalid port accepted");}catch(IllegalArgumentException expected){checks++;}
        check(l.cameras.size()==count,"invalid update does not mutate");
        Location legacy=new Location("Legacy"); Nvr old=new Nvr();old.host="nvr.local";old.password="a@b";
        Camera legacyCam=new Camera("Shop",old.url(16,false),old.url(16,true));legacy.cameras.add(legacyCam);legacy.cameras.add(standalone);
        Nvr.linkLegacy(legacy);
        check(legacy.devices.size()==1,"legacy preset linked");
        check(legacyCam.channel==16 && !legacyCam.deviceId.isEmpty(),"legacy channel kept");
        check(standalone.deviceId.isEmpty(),"legacy custom left alone");
        PairingSession p=new PairingSession(100);
        check(p.pin.matches("[0-9]{6}"),"six digit pin");
        check(p.pair("wrong","phone",101)==null,"wrong pin rejected");
        String token=p.pair(p.pin,"phone",102);
        check(token!=null&&token.length()>=32,"random session token");
        check(p.authorized(token,"phone",103),"paired phone authorized");
        check(!p.authorized(token,"other",103),"different peer rejected");
        check(!p.authorized("wrong","phone",103),"wrong cookie rejected");
        check(p.pair(p.pin,"other",103)==null,"second phone blocked");
        check(!p.authorized(token,"phone",600100),"expired token rejected");
        PairingSession locked=new PairingSession(0);
        for(int i=0;i<5;i++)locked.pair("wrong","phone",0);
        check(locked.pair(locked.pin,"phone",1)==null,"five failed attempts lock session");
        check(PlaybackFailure.classify(new IOException("RTSP 401 secret-user"),0)==PlaybackFailure.AUTH,"authentication error");
        check(!PlaybackFailure.AUTH.message.contains("secret-user"),"credentials not exposed");
        check(PlaybackFailure.classify(new SocketTimeoutException(),0)==PlaybackFailure.TIMEOUT,"timeout error");
        check(PlaybackFailure.classify(new ConnectException(),0)==PlaybackFailure.NETWORK,"network error");
        check(PlaybackFailure.classify(new IOException("404"),0)==PlaybackFailure.PATH,"path error");
        check(PlaybackFailure.classify(new IOException(),4001)==PlaybackFailure.DECODER,"decoder error");
        testProbe(false);testProbe(true);
        System.out.println("Passed "+checks+" extended checks");
    }
    static void testProbe(boolean basic) throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            Thread thread=new Thread(()->{
                try(Socket s=server.accept()){
                    s.setSoTimeout(3000);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                    for(int round=0;round<(basic?2:1);round++){
                        StringBuilder headers=new StringBuilder();for(String line;(line=r.readLine())!=null&&!line.isEmpty();)headers.append(line).append('\n');
                        if(basic&&round==0){s.getOutputStream().write("RTSP/1.0 401 Unauthorized\r\nCSeq: 1\r\nWWW-Authenticate: Basic realm=\"camera\"\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.UTF_8));s.getOutputStream().flush();continue;}
                        if(basic&&!headers.toString().contains("Authorization: Basic dXNlcjpwYXNz"))throw new IOException("Basic authentication missing");
                        String body="v=0\r\nm=video 0 RTP/AVP 96\r\n";
                        s.getOutputStream().write(("RTSP/1.0 200 OK\r\nCSeq: "+(round+1)+"\r\nContent-Length: "+body.length()+"\r\n\r\n"+body).getBytes(StandardCharsets.UTF_8));s.getOutputStream().flush();
                    }
                }catch(Exception ignored){}
            });thread.setDaemon(true);thread.start();
            RtspProbe.Result result=RtspProbe.test("rtsp://user:pass@127.0.0.1:"+server.getLocalPort()+"/stream");
            check(result.okay,basic?"authenticated DESCRIBE succeeds":"DESCRIBE finds video");thread.join(4000);
        }
    }
}
