package com.pnntrn.cctv;

import android.app.AlertDialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.util.UnstableApi;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import static org.junit.Assert.*;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public class RecorderDraftFlowTest {
    private boolean fixtureOwned;
    @org.junit.Before public void requireEmptyEmulator() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Fixture tests require an emulator", android.os.Build.FINGERPRINT.contains("generic")
                || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        assertTrue("Refusing to overwrite saved profiles", new ProfileStore(c).load().isEmpty());
        assertTrue("Refusing to overwrite saved drafts", c.getSharedPreferences("recorder-drafts",0).getAll().isEmpty());
        fixtureOwned=true;
    }
    @org.junit.After public void cleanFixture() throws Exception {
        if (!fixtureOwned) return;
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        new ProfileStore(c).save(new ArrayList<>());
        c.getSharedPreferences("recorder-drafts",0).edit().clear().commit();
    }
    private Object get(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private EditText field(View v, String label) {
        if (v instanceof EditText && label.contentEquals(v.getContentDescription())) return (EditText)v;
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
            EditText found=field(((ViewGroup)v).getChildAt(i),label); if(found!=null)return found;
        }
        return null;
    }
    private EditText field(MainActivity a, String label) throws Exception {
        AlertDialog dialog=(AlertDialog)get(a,"recorderEditor"); assertNotNull(dialog);
        EditText result=field(dialog.getWindow().getDecorView(),label); assertNotNull(label,result); return result;
    }
    private void open(MainActivity a) throws Exception {
        Method m=MainActivity.class.getDeclaredMethod("editNvr",Nvr.class,CameraRules.Preset.class);
        m.setAccessible(true); m.invoke(a,null,CameraRules.Preset.HIKVISION);
    }
    @Test public void draftSurvivesMenuStopRecreationAndReopeningWithoutPassword() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        c.getSharedPreferences("recorder-drafts",Context.MODE_PRIVATE).edit().clear().commit();
        Location location=new Location("Draft fixture"); ArrayList<Location> locations=new ArrayList<>(); locations.add(location);
        new ProfileStore(c).save(locations);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{try {
                open(a); field(a,"Recorder name").setText("Unfinished fixture");
                field(a,"RTSP port").setText("999999999999"); field(a,"First channel").setText("");
                field(a,"Channel count").setText("not-a-number");
                assertEquals("not-a-number",field(a,"Channel count").getText().toString());
                EditText password=field(a,"Password"); password.setText("fixture-secret");
                assertFalse(password.isSaveEnabled()); assertFalse(password.isSaveFromParentEnabled());
                a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU));
                assertEquals("fixture-secret",field(a,"Password").getText().toString());
            }catch(Exception e){throw new AssertionError(e);}});
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
            scenario.onActivity(a->{try {
                assertEquals("Unfinished fixture",field(a,"Recorder name").getText().toString());
                assertEquals("999999999999",field(a,"RTSP port").getText().toString());
                assertEquals("",field(a,"First channel").getText().toString());
                assertEquals("not-a-number",field(a,"Channel count").getText().toString());
                assertEquals("",field(a,"Password").getText().toString());
                assertFalse(c.getSharedPreferences("recorder-drafts",0).getAll().toString().contains("fixture-secret"));
                AlertDialog editor=(AlertDialog)get(a,"recorderEditor");
                editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));
                editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
                AlertDialog confirmation=(AlertDialog)get(a,"draftExitDialog");
                assertNotNull(confirmation); assertTrue(confirmation.isShowing());
                assertTrue(editor.isShowing());
                assertEquals("not-a-number",field(a,"Channel count").getText().toString());
            }catch(Exception e){throw new AssertionError(e);}});
            // Keep draft is the explicit positive choice on the unsaved-changes dialog.
            androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("(?i)Keep draft"))),5000).click();
            scenario.onActivity(a->{try {open(a); assertEquals("Unfinished fixture",field(a,"Recorder name").getText().toString());
                assertEquals("not-a-number",field(a,"Channel count").getText().toString());
                ((AlertDialog)get(a,"recorderEditor")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();}
                catch(Exception e){throw new AssertionError(e);}});
            device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("(?i)Discard"))),5000).click();
            scenario.onActivity(a->{try {open(a); assertNotEquals("Unfinished fixture",field(a,"Recorder name").getText().toString());}
                catch(Exception e){throw new AssertionError(e);}});
        } finally {c.getSharedPreferences("recorder-drafts",0).edit().clear().commit();}
    }
    @Test public void previewBackFailureAndBackgroundNeverLeaveSaveVerified() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        c.getSharedPreferences("recorder-drafts",0).edit().clear().commit();
        Location location=new Location("Preview fixture"); ArrayList<Location> locations=new ArrayList<>(); locations.add(location);
        new ProfileStore(c).save(locations);
        final StreamTile.Listener[] callback=new StreamTile.Listener[1];
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{try {
                open(a); field(a,"Recorder name").setText("Retained editor");
                field(a,"IP address or hostname").setText("127.0.0.1"); field(a,"RTSP port").setText("9");
                ((AlertDialog)get(a,"recorderEditor")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                AlertDialog preview=(AlertDialog)get(a,"connectionPreview"); assertNotNull(preview);
                assertFalse(preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                ArrayList<?> streams=(ArrayList<?>)get(a,"streams");
                callback[0]=(StreamTile.Listener)get(streams.get(streams.size()-1),"listener");
                // Exercise the decoded-frame callback contract, without a real camera or credentials.
                callback[0].onFirstFrame();
            }catch(Exception e){throw new AssertionError(e);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{try {
                AlertDialog preview=(AlertDialog)get(a,"connectionPreview");
                assertTrue(preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                callback[0].onFailure(PlaybackFailure.NETWORK);
            }catch(Exception e){throw new AssertionError(e);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{try {
                AlertDialog preview=(AlertDialog)get(a,"connectionPreview");
                assertFalse(preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                callback[0].onFirstFrame(); // A late callback after failure must not re-verify.
            }catch(Exception e){throw new AssertionError(e);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{try {
                AlertDialog preview=(AlertDialog)get(a,"connectionPreview");
                assertFalse(preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                a.onBackPressed(); assertNull(get(a,"connectionPreview"));
                assertEquals("Retained editor",field(a,"Recorder name").getText().toString());
                field(a,"Password").setText("temporary-fixture");
                ((AlertDialog)get(a,"recorderEditor")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            }catch(Exception e){throw new AssertionError(e);}});
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED);
            callback[0].onFirstFrame(); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{try {
                assertNull(get(a,"connectionPreview"));
                assertEquals("Retained editor",field(a,"Recorder name").getText().toString());
                assertEquals("",field(a,"Password").getText().toString());
            }catch(Exception e){throw new AssertionError(e);}});
        } finally {c.getSharedPreferences("recorder-drafts",0).edit().clear().commit();}
    }

    @Test public void freshActivityRestoresPrivateDraftAndExistingCredentialNeverEntersIt() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        c.getSharedPreferences("recorder-drafts",0).edit().clear().commit();
        Location location=new Location("Existing fixture");
        Nvr recorder=new Nvr(); recorder.host="127.0.0.1"; recorder.port=9; recorder.count=1;
        recorder.password="persisted-fixture-credential";
        // No live camera is needed to exercise the existing-recorder editor.
        location.devices.add(recorder);
        location.cameras.add(new Camera("Loopback fixture", "rtsp://127.0.0.1:9/sub", "rtsp://127.0.0.1:9/main"));
        ArrayList<Location> locations=new ArrayList<>(); locations.add(location);
        new ProfileStore(c).save(locations);
        try {
            try(ActivityScenario<MainActivity> first=ActivityScenario.launch(MainActivity.class)) {
                first.onActivity(a->{try {
                    Method m=MainActivity.class.getDeclaredMethod("editNvr",Nvr.class,CameraRules.Preset.class);
                    m.setAccessible(true); m.invoke(a,recorder,recorder.preset);
                    assertEquals("",field(a,"Password").getText().toString());
                    field(a,"Recorder name").setText("Persisted nonsecret draft");
                    field(a,"Channel count").setText(""); field(a,"Password").setText("temporary-fixture-password");
                }catch(Exception e){throw new AssertionError(e);}});
            }
            // A new Activity has no retained editor or saved-instance Bundle from the first launch.
            try(ActivityScenario<MainActivity> second=ActivityScenario.launch(MainActivity.class)) {
                second.onActivity(a->{try {
                    assertEquals("Persisted nonsecret draft",field(a,"Recorder name").getText().toString());
                    assertTrue("Restored editor must not leave camera streams running", ((ArrayList<?>)get(a,"streams")).isEmpty());
                    assertEquals("",field(a,"Channel count").getText().toString());
                    assertEquals("",field(a,"Password").getText().toString());
                    String stored=c.getSharedPreferences("recorder-drafts",0).getAll().toString();
                    assertFalse(stored.contains("persisted-fixture-credential"));
                    assertFalse(stored.contains("temporary-fixture-password"));
                }catch(Exception e){throw new AssertionError(e);}});
            }
        } finally {c.getSharedPreferences("recorder-drafts",0).edit().clear().commit();}
    }

    @Test public void verifiedCallbackSavesElevenChannelsAndReleasesGridOnStop() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Location location=new Location("Eleven-channel fixture");
        ArrayList<Location> locations=new ArrayList<>();locations.add(location);new ProfileStore(c).save(locations);
        String[] ids=new String[2];
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{try {
                open(a);field(a,"Recorder name").setText("Fixture recorder");
                field(a,"IP address or hostname").setText("127.0.0.1");field(a,"RTSP port").setText("9");
                field(a,"First channel").setText("1");field(a,"Channel count").setText("11");
                field(a,"New camera name prefix").setText("กล้อง");
                EditText prefix=field(a,"New camera name prefix");prefix.requestFocus();
            }catch(Exception e){throw new AssertionError(e);}});
            androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressDPadDown();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{try {
                AlertDialog editor=(AlertDialog)get(a,"recorderEditor");
                assertTrue(editor.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus());
                editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                AlertDialog preview=(AlertDialog)get(a,"connectionPreview");
                assertFalse(preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                ArrayList<?> streams=(ArrayList<?>)get(a,"streams");
                // UI contract only. Actual native first-frame decoding is InBandPlaybackTest.
                ((StreamTile.Listener)get(streams.get(0),"listener")).onFirstFrame();
                preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertNull(get(a,"recorderEditor"));
                Location saved=new ProfileStore(c).load().get(0);
                assertEquals(11,saved.cameras.size());assertEquals(1,saved.devices.size());assertEquals(4,saved.grid);
                ids[0]=saved.devices.get(0).id;ids[1]=saved.cameras.get(10).id;
                assertEquals("Fixture recorder",saved.devices.get(0).name);
                for(int n=0;n<11;n++){
                    Camera camera=saved.cameras.get(n);assertEquals("กล้อง "+(n+1),camera.name);
                    assertEquals(ids[0],camera.deviceId);
                    assertTrue(camera.subUrl.endsWith("/Streaming/Channels/"+(n+1)+"02"));
                    assertTrue(camera.mainUrl.endsWith("/Streaming/Channels/"+(n+1)+"01"));
                }
                a.onBackPressed(); // Manager -> live grid, default four tiles.
                assertEquals(4,((ArrayList<?>)get(a,"streams")).size());
            }catch(Exception e){throw new AssertionError(e);}});
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(a->{try{assertTrue(((ArrayList<?>)get(a,"streams")).isEmpty());}
                catch(Exception e){throw new AssertionError(e);}});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.recreate();
            scenario.onActivity(a->{try{
                Location saved=new ProfileStore(c).load().get(0);
                assertEquals(11,saved.cameras.size());assertEquals(ids[0],saved.devices.get(0).id);
                assertEquals(ids[1],saved.cameras.get(10).id);assertNull(get(a,"recorderEditor"));
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }

}
