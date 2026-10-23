package com.pnntrn.cctv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** On-demand, foreground-only updates. Never writes camera profiles or embeds account credentials. */
public final class AppUpdater implements AutoCloseable {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private UpdateTransfer transfer;
    private AlertDialog dialog;
    private Release release;
    private boolean closed, awaitingPermission;
    private long generation;
    private final PackageInfo installed;
    private static final class Release {
        long code, size; int minSdk; String name, url, hash, notes;
        static Release parse(byte[] bytes) throws Exception {
            String text=new String(bytes, StandardCharsets.UTF_8);
            UpdatePolicy.jsonDepth(text);JSONObject json = new JSONObject(text);
            if (json.getInt("schema") != 1 || !"com.pnntrn.cctv".equals(json.getString("packageName"))) throw new IllegalArgumentException("Invalid update feed");
            Release r = new Release(); r.code=json.getLong("versionCode");r.name=json.getString("versionName");r.size=json.getLong("size");
            r.hash=json.getString("sha256");r.url=json.getString("url");r.minSdk=json.getInt("minSdk");r.notes=json.optString("notes", "");
            UpdatePolicy.validate(r.code,r.name,r.size,r.hash,r.minSdk);UpdatePolicy.https(r.url);
            if (r.notes.length()>4000) throw new IllegalArgumentException("Release notes too long");return r;
        }
    }
    @SuppressWarnings("deprecation")
    public AppUpdater(Activity activity) throws PackageManager.NameNotFoundException {
        this.activity=activity;
        installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(), Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
    }
    @SuppressWarnings("deprecation") private static long version(PackageInfo info) { return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode; }
    public void show() { if (!closed) check(); }
    private void cancelWork() { generation++; if(transfer!=null)transfer.close();transfer=null; }
    private void replace(String title, String message, String primary, Runnable action, boolean busy) {
        if(dialog!=null) { dialog.setOnDismissListener(null);dialog.dismiss(); }
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                .setNegativeButton(busy?"Cancel":"Close",(d,w)->{});
        if(primary!=null)builder.setPositiveButton(primary,(d,w)->{});
        dialog=builder.create();
        dialog.setOnDismissListener(d->{ cancelWork(); dialog=null; });
        dialog.setOnShowListener(d->{
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if(primary!=null)positive.setOnClickListener(v->action.run());
            Button focus=primary!=null?positive:dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            focus.setMinHeight(dp(48));focus.setFocusableInTouchMode(true);focus.requestFocus();
        });
        dialog.show();dialog.getWindow().setLayout(dp(600),-2);
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private String current(){return "Installed: CCTV "+installed.versionName+" ("+version(installed)+")";}
    private void check() {
        cancelWork();release=null;final long token=generation;
        replace("CCTV updates",current()+"\n\nChecking update server…",null,null,true);
        UpdateTransfer task=new UpdateTransfer();transfer=task;
        WORKER.execute(()->{try{
            Release found=Release.parse(task.manifest(UpdatePolicy.FEED));
            post(token,()->{
                release=found;
                if(!UpdatePolicy.newer(found.code,version(installed)))replace("You’re up to date",current()+"\n\nNo newer version is available.","Check again",this::check,false);
                else if(found.minSdk>Build.VERSION.SDK_INT)replace("New version needs newer Android",current()+"\n\nCCTV "+found.name+" needs Android API "+found.minSdk+" or later.",null,null,false);
                else replace("CCTV "+found.name+" is available",current()+"\nDownload: "+String.format(java.util.Locale.ROOT,"%.1f MB",found.size/1048576.0)+"\n\n"+found.notes+"\n\nYour locations and camera order stay saved. Android will ask you to confirm installation.","Download update",this::download,false);
            });
        }catch(Exception e){post(token,()->error("Could not check for updates. Check your internet connection and try again.",this::check));}});
    }
    private void post(long token,Runnable result){handler.post(()->{if(!closed&&token==generation&&dialog!=null&&!activity.isFinishing())result.run();});}
    private void error(String message,Runnable retry){replace("Update unavailable",current()+"\n\n"+message,"Retry",retry,false);}
    private void download(){
        if(release==null)return;
        cancelWork();final long token=generation;Release target=release;
        replace("Downloading CCTV "+target.name,"Downloading… 0%\n\nYou can cancel with Back. Your camera settings stay saved.",null,null,true);
        UpdateTransfer task=new UpdateTransfer();transfer=task;
        WORKER.execute(()->{
            File apk=UpdateApkProvider.apk(activity), partial=null;
            try{
                if(!apk.getParentFile().isDirectory()&&!apk.getParentFile().mkdirs())throw new java.io.IOException("Cannot create update directory");
                partial=File.createTempFile("download-", ".part", apk.getParentFile());
                if(apk.exists()&&!apk.delete())throw new java.io.IOException("Cannot replace old update");
                task.download(target.url,partial,target.size,target.hash,p->post(token,()->dialog.setMessage("Downloading… "+p+"%\n\nYou can cancel with Back. Your camera settings stay saved.")));
                verify(partial,target);if(task.cancelled.get())throw new java.io.InterruptedIOException();
                if(!partial.renameTo(apk))throw new java.io.IOException("Cannot prepare installer");
                post(token,this::ready);
            }catch(Exception e){if(partial!=null)partial.delete();post(token,()->error("The update could not be downloaded or verified. Nothing has been installed. Check your connection and retry.",this::download));}
        });
    }
    @SuppressWarnings("deprecation") private void verify(File apk,Release target)throws Exception{
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||version(candidate)!=target.code||!UpdatePolicy.newer(version(candidate),version(installed))
                ||!target.name.equals(candidate.versionName)||candidate.applicationInfo==null||candidate.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)
            throw new SecurityException("Package mismatch");
        Signature[] expected=signers(installed),actual=signers(candidate);
        if(expected==null||actual==null||expected.length!=1||actual.length!=1||!expected[0].equals(actual[0]))throw new SecurityException("Signing key mismatch");
    }
    @SuppressWarnings("deprecation") private Signature[] signers(PackageInfo info){return Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;}
    private void ready(){replace("Ready to install CCTV "+release.name,"Download verified.\n\nYour locations and camera order stay saved. Select Install, then confirm on Android’s installer.","Install",this::install,false);}
    @SuppressWarnings("deprecation") private void install(){
        try{
            File apk=UpdateApkProvider.apk(activity);verify(apk,release);
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                replace("Allow CCTV updates","Android needs permission to install this update. Choose Allow from this source, then press Back to return here.","Open Android settings",()->{
                    awaitingPermission=true;
                    try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}
                    catch(Exception e){awaitingPermission=false;error("This TV does not expose the installation permission screen. Enable installation for CCTV in Android settings, then retry.",this::install);}
                },false);return;
            }
            Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/cctv-update.apk");
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
                    .setClipData(ClipData.newRawUri("CCTV update",uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        }catch(Exception e){error("Android could not open this update. Try downloading it again.",this::download);}
    }
    public void saveState(Bundle state) {
        if(release==null || !UpdateApkProvider.apk(activity).isFile())return;
        try {
            JSONObject json=new JSONObject();json.put("schema",1);json.put("packageName",activity.getPackageName());
            json.put("versionCode",release.code);json.put("versionName",release.name);json.put("size",release.size);
            json.put("sha256",release.hash);json.put("url",release.url);json.put("minSdk",release.minSdk);json.put("notes",release.notes);
            state.putString("update.pending",json.toString());
        }catch(Exception ignored){ }
    }
    public void restoreState(Bundle state) {
        try {
            String value=state.getString("update.pending", "");
            if(value.isEmpty() || value.length()>UpdatePolicy.MAX_MANIFEST)return;
            release=Release.parse(value.getBytes(StandardCharsets.UTF_8));
            if(!UpdatePolicy.newer(release.code,version(installed))){release=null;return;}
            awaitingPermission=UpdateApkProvider.apk(activity).isFile();
        }catch(Exception ignored){release=null;}
    }
    public void resume(){
        if(!awaitingPermission || release==null)return;
        awaitingPermission=false;cancelWork();long token=generation;Release target=release;
        replace("CCTV updates",current()+"\n\nVerifying downloaded update…",null,null,true);
        WORKER.execute(()->{try{
            File apk=UpdateApkProvider.apk(activity);
            try(java.io.InputStream in=new java.io.FileInputStream(apk)) {
                UpdateTransfer.copy(in,new java.io.OutputStream(){public void write(int b){} public void write(byte[] b,int o,int n){}},
                        target.size,target.hash,new java.util.concurrent.atomic.AtomicBoolean(),p->{});
            }
            verify(apk,target);post(token,this::ready);
        }catch(Exception e){post(token,()->error("The saved download is no longer available. Download it again.",this::check));}});
    }
    public void stop(){cancelWork();if(dialog!=null){dialog.setOnDismissListener(null);dialog.dismiss();dialog=null;}}
    @Override public void close(){stop();closed=true;handler.removeCallbacksAndMessages(null);}
}
