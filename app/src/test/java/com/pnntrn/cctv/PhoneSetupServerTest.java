package com.pnntrn.cctv;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class PhoneSetupServerTest {
    private String request(PhoneSetupServer server,String method,String path,String body,String cookie,String origin) throws Exception {
        URI uri=URI.create(server.address());
        try(Socket s=new Socket("127.0.0.1",uri.getPort())){
            s.setSoTimeout(10000);
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            String req=method+" "+path+" HTTP/1.1\r\nHost: "+uri.getAuthority()+"\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\n"
                    +(origin.isEmpty()?"":"Origin: "+origin+"\r\n")+(cookie.isEmpty()?"":"Cookie: cctv="+cookie+"\r\n")+"\r\n";
            s.getOutputStream().write(req.getBytes(StandardCharsets.UTF_8));s.getOutputStream().write(bytes);s.getOutputStream().flush();
            ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[2048];for(int n;(n=s.getInputStream().read(chunk))!=-1;)b.write(chunk,0,n);return b.toString("UTF-8");
        }
    }
    @Test public void pairingSameOriginProofAndClosure() throws Exception {
        AtomicInteger saves=new AtomicInteger();
        try(ServerSocket rtsp=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            Thread camera=new Thread(()->{try(Socket s=rtsp.accept()){
                BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));for(String l;(l=r.readLine())!=null&&!l.isEmpty();){}
                String body="v=0\r\nm=video 0 RTP/AVP 96\r\n";s.getOutputStream().write(("RTSP/1.0 200 OK\r\nContent-Length: "+body.length()+"\r\n\r\n"+body).getBytes(StandardCharsets.UTF_8));s.getOutputStream().flush();
            }catch(Exception ignored){}});camera.setDaemon(true);camera.start();
            try(PhoneSetupServer phone=new PhoneSetupServer("127.0.0.1","<h1>Pair</h1>",(action,input)->{
                if(action.equals("config"))return new JSONObject().put("location","Shop");
                if(action.equals("prepare"))return new JSONObject().put("kind","camera").put("sub","rtsp://127.0.0.1:"+rtsp.getLocalPort()+"/video").put("name","Tested camera");
                if(action.equals("save")){saves.incrementAndGet();assertEquals("Tested camera",input.getString("name"));return new JSONObject().put("message","Saved");}
                throw new IllegalArgumentException();
            })){
                assertTrue(request(phone,"GET","/","","","").contains("<h1>Pair</h1>"));
                assertTrue(request(phone,"GET","/api/config","","","").startsWith("HTTP/1.1 403"));
                assertTrue(request(phone,"POST","/api/pair","{\"pin\":\""+phone.session.pin+"\"}","","http://evil.invalid").startsWith("HTTP/1.1 403"));
                String paired=request(phone,"POST","/api/pair","{\"pin\":\""+phone.session.pin+"\"}","",phone.address());
                assertTrue(paired.startsWith("HTTP/1.1 200"));
                String cookie=paired.split("Set-Cookie: cctv=")[1].split(";")[0];
                assertTrue(paired.contains("HttpOnly; SameSite=Strict"));
                assertTrue(request(phone,"GET","/api/config","",cookie,"").contains("Shop"));
                assertTrue(request(phone,"POST","/api/save","{}",cookie,phone.address()).startsWith("HTTP/1.1 400"));
                String tested=request(phone,"POST","/api/test","{}",cookie,phone.address());
                JSONObject response=new JSONObject(tested.split("\r\n\r\n",2)[1]);assertTrue(response.getBoolean("okay"));
                assertTrue(request(phone,"POST","/api/save",new JSONObject().put("proof",response.getString("proof")).toString(),cookie,phone.address()).startsWith("HTTP/1.1 200"));
                assertEquals(1,saves.get());
                assertTrue(request(phone,"POST","/api/save",new JSONObject().put("proof",response.getString("proof")).toString(),cookie,phone.address()).startsWith("HTTP/1.1 400"));
                phone.close();assertFalse(phone.isActive());
            }
        }
    }
    @Test public void recorderJsonKeepsSecretsPrivate() throws Exception {
        Nvr n=new Nvr();n.host="nvr.local";n.password="private-secret";
        JSONObject publicData=ProfileStore.nvrJson(n,false);assertFalse(publicData.has("password"));
        Nvr restored=ProfileStore.nvrFromJson(ProfileStore.nvrJson(n,true));
        assertEquals(n.password,restored.password);assertEquals(n.id,restored.id);
    }
}
