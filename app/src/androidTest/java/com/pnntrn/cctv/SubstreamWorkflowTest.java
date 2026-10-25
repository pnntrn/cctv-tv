package com.pnntrn.cctv;

import android.app.AlertDialog;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Remote/layout UI contracts only. Actual decoder proof is in InBandPlaybackTest. */
@RunWith(AndroidJUnit4.class)
public final class SubstreamWorkflowTest {
    private boolean fixtureOwned;
    private static Object get(Object o,String name) throws Exception {
        var f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private static void fixture(MainActivity a,int size) throws Exception {
        Location l=new Location("สถานที่ทดสอบ"); l.grid=size;
        for(int i=0;i<8;i++) l.cameras.add(new Camera("กล้อง "+(i+1),"rtsp://127.0.0.1:9/sub","rtsp://127.0.0.1:9/main"));
        var f=MainActivity.class.getDeclaredField("current"); f.setAccessible(true); f.set(a,l);
        var render=MainActivity.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(a);
        for(Object tile:(ArrayList<?>)get(a,"streams")) ((StreamTile)tile).release();
    }
    private void requireFixture() throws Exception {
        assertTrue(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish"));
        assertTrue(new ProfileStore(InstrumentationRegistry.getInstrumentation().getTargetContext()).load().isEmpty());
        fixtureOwned=true;
    }
    @org.junit.After public void cleanOwnedFixture() throws Exception {
        if(!fixtureOwned)return;
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        new ProfileStore(context).save(new ArrayList<>());
        context.getSharedPreferences("recorder-drafts",0).edit().clear().commit();
    }
    private static void call(MainActivity a,String name) throws Exception {
        var m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);
    }
    private static void stopUiFixturePlayers(MainActivity a) throws Exception {
        for(Object tile:(ArrayList<?>)get(a,"streams"))((StreamTile)tile).release();
    }
    @Test public void backAtGridStaysHomeWithoutExitDialog() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,4); a.onBackPressed(); assertFalse(a.isFinishing());
                for(Object d:(java.util.Set<?>)get(a,"dialogs")) assertFalse("Back on home must not ask to exit",((AlertDialog)d).isShowing());
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    @Test public void sixLayoutProvidesSixCellsAndCenteredLocationBar() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,6);
                assertEquals(6,((ArrayList<?>)get(a,"tileViews")).size());
                assertNotNull(a.getWindow().getDecorView().findViewWithTag("viewing-toolbar"));
                assertNotNull(a.getWindow().getDecorView().findViewWithTag("layout-control"));
                assertNotNull(a.getWindow().getDecorView().findViewWithTag("location-control"));
            }catch(Exception e){throw new AssertionError(e);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                    new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"ui-refined-controls.png")));
            s.onActivity(a->{try {
                var cells=(ArrayList<?>)get(a,"tileViews"); View big=(View)cells.get(0),small=(View)cells.get(1);
                assertEquals(small.getWidth()*2,big.getWidth(),2);
                assertEquals(small.getHeight()*2,big.getHeight(),2);
            }catch(Exception e){throw new AssertionError(e);}});
            assertTrue(androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                    new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"ui-six.png")));
        }
    }
    @Test public void channelOrderAndVisibilityAreStagedUntilSaveAndBackCancels() throws Exception {
        requireFixture();var store=new ProfileStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,6);Location l=(Location)get(a,"current");((ArrayList<Location>)get(a,"locations")).add(l);store.save(java.util.List.of(l));
                String original=l.cameras.get(0).id;call(a,"enterTechnician");
                a.getWindow().getDecorView().findViewWithTag("manage-channels").performClick();
                ((View)((ArrayList<?>)get(a,"tileViews")).get(0)).performClick();
                ((View)((ArrayList<?>)get(a,"tileViews")).get(1)).performClick();call(a,"toggleSelectedHidden");
                assertEquals(original,store.load().get(0).cameras.get(0).id);assertFalse(store.load().get(0).cameras.get(0).hidden);
                a.onBackPressed();assertEquals(original,l.cameras.get(0).id);assertFalse(l.cameras.get(0).hidden);
                a.getWindow().getDecorView().findViewWithTag("manage-channels").performClick();
                ((View)((ArrayList<?>)get(a,"tileViews")).get(0)).performClick();
                ((View)((ArrayList<?>)get(a,"tileViews")).get(1)).performClick();call(a,"toggleSelectedHidden");
                a.getWindow().getDecorView().findViewWithTag("save-channels").performClick();
                assertEquals(original,store.load().get(0).cameras.get(1).id);assertTrue(store.load().get(0).cameras.get(1).hidden);
                a.onBackPressed();assertEquals(7,((Location)get(a,"current")).visibleCameras().size());stopUiFixturePlayers(a);
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    @Test public void allHiddenHasRecoveryMenuAndNeverOpensFullscreen() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{try{
                fixture(a,6);Location l=(Location)get(a,"current");for(Camera c:l.cameras)c.hidden=true;
                call(a,"render");assertEquals(0,((ArrayList<?>)get(a,"streams")).size());assertEquals(-1,get(a,"fullscreen"));
                assertNotNull(a.getWindow().getDecorView().findViewWithTag("viewing-toolbar"));
                call(a,"viewingMenu");assertTrue(((AlertDialog)get(a,"viewingMenu")).isShowing());
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    @Test public void fullscreenCaptionStaysAboveHelpWhileHealthKeepsBottomRight() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,4);((View)((ArrayList<?>)get(a,"tileViews")).get(0)).performClick();
            }catch(Exception e){throw new AssertionError(e);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            s.onActivity(a->{try {
                StreamTile tile=(StreamTile)get(a,"fullTile");View title=(View)get(tile,"title"),help=(View)get(a,"fullHelp"),health=tile.findViewWithTag("frame-health");
                assertTrue("Fullscreen camera name must stay above the help row",title.getBottom()<=help.getTop());
                assertEquals("Health remains pinned to the tile bottom",tile.getHeight(),health.getBottom());
                assertEquals("Health remains pinned to the tile right",tile.getWidth(),health.getRight());stopUiFixturePlayers(a);
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    @Test public void combinedNvrEditorShowsLocationAndDefaultGridAndRejectsSecondRecorder() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{try{
                fixture(a,6);Location l=(Location)get(a,"current");l.defaultGrid=6;
                ((ArrayList<Location>)get(a,"locations")).add(l);call(a,"enterTechnician");
                var edit=MainActivity.class.getDeclaredMethod("editNvr",Nvr.class,CameraRules.Preset.class);edit.setAccessible(true);edit.invoke(a,null,CameraRules.Preset.HIKVISION);
                assertEquals(l.name,((android.widget.EditText)get(a,"recorderLocationName")).getText().toString());
                assertEquals(2,((android.widget.Spinner)get(a,"recorderDefaultGrid")).getSelectedItemPosition());
                var finish=MainActivity.class.getDeclaredMethod("finishRecorderEditor",boolean.class);finish.setAccessible(true);finish.invoke(a,true);
                Nvr n=new Nvr();n.host="127.0.0.1";n.port=9;l.devices.add(n);edit.invoke(a,null,CameraRules.Preset.HIKVISION);
                assertNull("No second NVR editor may open",get(a,"recorderEditor"));
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    private static void awaitFocus(ActivityScenario<MainActivity> s) {
        boolean[] focus={false};long end=android.os.SystemClock.uptimeMillis()+5000;
        do{s.onActivity(a->focus[0]=a.hasWindowFocus());if(focus[0])return;android.os.SystemClock.sleep(25);}while(android.os.SystemClock.uptimeMillis()<end);
        fail("Activity did not regain remote focus");
    }
    @Test public void focusedToolbarReturnsToContentAtIdleAndDirectionalHoldsConsumeRelease() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{try{fixture(a,6);((ArrayList<Location>)get(a,"locations")).add((Location)get(a,"current"));
            }catch(Exception e){throw new AssertionError(e);}});
            awaitFocus(s);remoteKey(android.view.KeyEvent.KEYCODE_DPAD_UP);
            s.onActivity(a->{try{assertTrue("Real remote Up must focus the layout control before the idle check",((View)get(a,"layoutControl")).hasFocus());}catch(Exception e){throw new AssertionError(e);}});
            android.os.SystemClock.sleep(1900);
            s.onActivity(a->{try{assertFalse((Boolean)get(a,"chromeVisible"));assertEquals(View.GONE,((View)get(a,"viewingBar")).getVisibility());
                assertTrue("Idle must restore the content focus before hiding controls",((View)((ArrayList<?>)get(a,"tileViews")).get(0)).hasFocus());
            }catch(Exception e){throw new AssertionError(e);}});
            for(int key:new int[]{android.view.KeyEvent.KEYCODE_DPAD_LEFT,android.view.KeyEvent.KEYCODE_DPAD_RIGHT}){
                awaitFocus(s);long down=android.os.SystemClock.uptimeMillis();
                s.onActivity(a->a.dispatchKeyEvent(new android.view.KeyEvent(down,down,android.view.KeyEvent.ACTION_DOWN,key,0)));
                android.os.SystemClock.sleep(1150);
                s.onActivity(a->{try{
                    AlertDialog showing=null;for(Object d:(java.util.Set<?>)get(a,"dialogs"))if(((AlertDialog)d).isShowing())showing=(AlertDialog)d;
                    assertNotNull("Directional hold must open its picker",showing);
                    assertTrue("Picker must consume release from the shortcut",showing.dispatchKeyEvent(new android.view.KeyEvent(down,android.os.SystemClock.uptimeMillis(),android.view.KeyEvent.ACTION_UP,key,0)));
                    assertEquals(-1,get(a,"fullscreen"));assertFalse((Boolean)get(a,"managing"));showing.dismiss();
                }catch(Exception e){throw new AssertionError(e);}});
            }
        }
    }
    @Test public void toolbarClustersActionsCyclesLocationsAndPagesAndOpensSafeSettings() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,4);Location first=(Location)get(a,"current"),second=new Location("อีกสถานที่");
                ((ArrayList<Location>)get(a,"locations")).add(first);((ArrayList<Location>)get(a,"locations")).add(second);
            }catch(Exception e){throw new AssertionError(e);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            s.onActivity(a->{try {
                Location first=((ArrayList<Location>)get(a,"locations")).get(0),second=((ArrayList<Location>)get(a,"locations")).get(1);
                View bar=(View)get(a,"viewingBar");
                View layout=bar.findViewWithTag("layout-control"),location=bar.findViewWithTag("location-control"),next=bar.findViewWithTag("next-page-control"),settings=bar.findViewWithTag("settings-control");
                assertNotNull("Next page must be a direct TV control",next);assertNotNull("Settings must be a direct TV control",settings);
                assertEquals("หน้าถัดไป",next.getContentDescription().toString());
                for(View control:new View[]{layout,location,next,settings}){assertTrue(control.getWidth()>=Math.round(48*a.getResources().getDisplayMetrics().density));assertFalse(control.getContentDescription().toString().isEmpty());}
                assertTrue("All controls must cluster after the toolbar text",layout.getLeft()>bar.findViewWithTag("location-title").getLeft());
                assertTrue(layout.getLeft()<location.getLeft());assertTrue(location.getLeft()<next.getLeft());assertTrue(next.getLeft()<settings.getLeft());
                String caption=((android.widget.TextView)bar.findViewWithTag("location-title")).getText().toString();
                assertTrue(caption.indexOf("สถานที่ทดสอบ")<caption.indexOf("4 กล้อง"));assertTrue(caption.indexOf("4 กล้อง")<caption.indexOf("หน้า 1/2"));
                next.performClick();assertEquals(1,get(a,"page"));
                a.getWindow().getDecorView().findViewWithTag("next-page-control").performClick();assertEquals(0,get(a,"page"));
                ((View)((ArrayList<?>)get(a,"tileViews")).get(0)).performClick();assertEquals(0,get(a,"fullscreen"));
                a.getWindow().getDecorView().findViewWithTag("next-page-control").performClick();assertEquals(-1,get(a,"fullscreen"));assertEquals(1,get(a,"page"));
                a.getWindow().getDecorView().findViewWithTag("location-control").performClick();assertSame(second,get(a,"current"));
                a.getWindow().getDecorView().findViewWithTag("next-page-control").performClick();assertEquals(0,get(a,"page"));
                assertTrue("Empty pages keep a safe focus target",((View)get(a,"nextPageControl")).hasFocus());
                a.getWindow().getDecorView().findViewWithTag("location-control").performClick();assertSame(first,get(a,"current"));
                a.getWindow().getDecorView().findViewWithTag("settings-control").performClick();
                AlertDialog confirmation=(AlertDialog)get(a,"technicianConfirmation");assertTrue(confirmation.isShowing());
                assertTrue("Cancel must retain default focus",confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());
                confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertFalse((Boolean)get(a,"technician"));stopUiFixturePlayers(a);
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    @Test public void backRestoresDefaultGridAndIdentityBeforeSecondShortBackOpensAndroidHome() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,4);Location l=(Location)get(a,"current");l.defaultGrid=6;l.cameras.get(1).hidden=true;
                ((ArrayList<Location>)get(a,"locations")).add(l);
                var selected=MainActivity.class.getDeclaredField("focusedCamera");selected.setAccessible(true);selected.setInt(a,6);
                var page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);page.setInt(a,1);call(a,"render");
                ((View)((ArrayList<?>)get(a,"tileViews")).get(2)).performClick();
            }catch(Exception e){throw new AssertionError(e);}});
            awaitFocus(s);remoteKey(android.view.KeyEvent.KEYCODE_BACK);
            s.onActivity(a->{try {assertEquals(-1,get(a,"fullscreen"));assertEquals(6,((Location)get(a,"current")).grid);assertEquals(6,get(a,"focusedCamera"));assertEquals(1,get(a,"page"));stopUiFixturePlayers(a);}catch(Exception e){throw new AssertionError(e);}});
            remoteKey(android.view.KeyEvent.KEYCODE_BACK);awaitLifecycle(s,androidx.lifecycle.Lifecycle.State.CREATED);
        }
    }
    @Test public void heldBackAndDialogDismissCannotArmHomeAndOtherInputResetsIt() throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,4);Location l=(Location)get(a,"current");l.defaultGrid=6;((ArrayList<Location>)get(a,"locations")).add(l);
                long now=android.os.SystemClock.uptimeMillis();
                a.dispatchKeyEvent(new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK,0));
                a.dispatchKeyEvent(new android.view.KeyEvent(now,now+20,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK,1));
                a.dispatchKeyEvent(new android.view.KeyEvent(now,now+30,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK,0));
                assertEquals("Held/repeated Back must not navigate",4,l.grid);
                a.getWindow().getDecorView().findViewWithTag("settings-control").performClick();
            }catch(Exception e){throw new AssertionError(e);}});
            android.os.SystemClock.sleep(1700);
            s.onActivity(a->{try {assertTrue("Idle must preserve the open confirmation",((AlertDialog)get(a,"technicianConfirmation")).isShowing());}catch(Exception e){throw new AssertionError(e);}});
            remoteKey(android.view.KeyEvent.KEYCODE_BACK);awaitFocus(s);remoteKey(android.view.KeyEvent.KEYCODE_BACK);
            s.onActivity(a->{try {assertEquals(6,((Location)get(a,"current")).grid);assertFalse(a.isFinishing());stopUiFixturePlayers(a);}catch(Exception e){throw new AssertionError(e);}});
            assertEquals("Dismissing settings must not arm Home",androidx.lifecycle.Lifecycle.State.RESUMED,s.getState());
            remoteKey(android.view.KeyEvent.KEYCODE_DPAD_UP);remoteKey(android.view.KeyEvent.KEYCODE_BACK);
            assertEquals("Other remote input resets the double-Back window",androidx.lifecycle.Lifecycle.State.RESUMED,s.getState());
        }
    }
    private static void remoteKey(int key) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(key);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private static void holdRemoteOk() {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        long down=android.os.SystemClock.uptimeMillis();
        instrumentation.sendKeySync(new android.view.KeyEvent(down,down,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_CENTER,0));
        android.os.SystemClock.sleep(1150);
        instrumentation.sendKeySync(new android.view.KeyEvent(down,android.os.SystemClock.uptimeMillis(),android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_DPAD_CENTER,0));
        instrumentation.waitForIdleSync();
    }
    private void assertEmptyRemoteRecovery(boolean allHidden) throws Exception {
        requireFixture();
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try{
                if(allHidden){fixture(a,6);Location l=(Location)get(a,"current");for(Camera c:l.cameras)c.hidden=true;}
                call(a,"render");
            }catch(Exception e){throw new AssertionError(e);}});
            awaitFocus(s);
            for(int escape:new int[]{android.view.KeyEvent.KEYCODE_DPAD_DOWN,android.view.KeyEvent.KEYCODE_BACK}) {
                remoteKey(android.view.KeyEvent.KEYCODE_DPAD_UP);
                s.onActivity(a->{try{assertTrue("Up must reach the toolbar",((View)get(a,"viewingBar")).hasFocus());}catch(Exception e){throw new AssertionError(e);}});
                remoteKey(escape);
                s.onActivity(a->{try{assertFalse("Down/Back must leave the toolbar even without visible cameras",((View)get(a,"viewingBar")).hasFocus());}catch(Exception e){throw new AssertionError(e);}});
                holdRemoteOk();
                s.onActivity(a->{try{
                    assertNotNull("Hold OK after toolbar escape must open viewing menu",get(a,"viewingMenu"));
                    assertTrue(((AlertDialog)get(a,"viewingMenu")).isShowing());assertEquals(-1,get(a,"fullscreen"));
                }catch(Exception e){throw new AssertionError(e);}});
                remoteKey(android.view.KeyEvent.KEYCODE_BACK);awaitFocus(s);
            }
        }
    }
    @Test public void zeroLocationsRemoteDownAndBackRestoreHoldOkMenu() throws Exception { assertEmptyRemoteRecovery(false); }
    @Test public void allHiddenRemoteDownAndBackRestoreHoldOkMenu() throws Exception { assertEmptyRemoteRecovery(true); }
    private static void editRecorder(MainActivity a,Nvr existing) throws Exception {
        var edit=MainActivity.class.getDeclaredMethod("editNvr",Nvr.class,CameraRules.Preset.class);
        edit.setAccessible(true);edit.invoke(a,existing,CameraRules.Preset.HIKVISION);
    }
    /** UI callback contract fake only; real rendered SUB frames are covered by InBandPlaybackTest. */
    private static void saveRecorderAfterUiFrameCallback(MainActivity a) throws Exception {
        ((AlertDialog)get(a,"recorderEditor")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        AlertDialog preview=(AlertDialog)get(a,"connectionPreview");
        assertNotNull(preview);assertFalse("Save must require the SUB frame callback",preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        StreamTile tile=(StreamTile)((ArrayList<?>)get(a,"streams")).get(0);
        tile.release(); // Cancel invalid-port transport before the UI-only callback contract fake.
        ((StreamTile.Listener)get(tile,"listener")).onFirstFrame();
        assertTrue(preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertNull(get(a,"recorderEditor"));
    }
    private void assertExistingRecorderGrid(boolean changeDefault) throws Exception {
        requireFixture();var store=new ProfileStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                fixture(a,6);Location l=(Location)get(a,"current");l.defaultGrid=4;l.cameras.clear();
                Nvr n=new Nvr();n.host="127.0.0.1";n.port=9;n.count=8;n.password="old-test-password";n.apply(l);
                ((ArrayList<Location>)get(a,"locations")).add(l);store.save(java.util.List.of(l));call(a,"enterTechnician");editRecorder(a,n);
                ((android.widget.EditText)get(a,"recorderLocationName")).setText("Renamed location");
                ((android.widget.EditText[])get(a,"recorderFields"))[4].setText("replacement-test-password");
                if(changeDefault)((android.widget.Spinner)get(a,"recorderDefaultGrid")).setSelection(3);
                saveRecorderAfterUiFrameCallback(a);Location saved=store.load().get(0);
                assertEquals("Renamed location",saved.name);assertEquals("replacement-test-password",saved.devices.get(0).password);
                assertEquals(changeDefault?8:4,saved.defaultGrid);
                assertEquals("Unrelated edits preserve runtime layout; an explicit default change applies it",changeDefault?8:6,saved.grid);
                stopUiFixturePlayers(a);
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    @Test public void unrelatedRecorderEditsPreserveSavedRuntimeGrid() throws Exception { assertExistingRecorderGrid(false); }
    @Test public void explicitRecorderDefaultChangeAppliesNewGrid() throws Exception { assertExistingRecorderGrid(true); }
    @Test public void freshLocationInitializesRuntimeGridFromChosenDefault() throws Exception {
        requireFixture();var store=new ProfileStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                call(a,"enterTechnician");
                var current=MainActivity.class.getDeclaredField("current");current.setAccessible(true);current.set(a,new Location("New location"));editRecorder(a,null);
                ((android.widget.EditText[])get(a,"recorderFields"))[1].setText("127.0.0.1");
                ((android.widget.EditText[])get(a,"recorderFields"))[2].setText("9");
                ((android.widget.Spinner)get(a,"recorderDefaultGrid")).setSelection(2);
                saveRecorderAfterUiFrameCallback(a);Location saved=store.load().get(0);
                assertEquals(6,saved.defaultGrid);assertEquals(6,saved.grid);stopUiFixturePlayers(a);
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    private static void awaitLifecycle(ActivityScenario<MainActivity> scenario,androidx.lifecycle.Lifecycle.State state) {
        long end=android.os.SystemClock.uptimeMillis()+5000;
        do{if(scenario.getState()==state)return;android.os.SystemClock.sleep(25);}while(android.os.SystemClock.uptimeMillis()<end);
        assertEquals("Real Home/return must reach expected lifecycle",state,scenario.getState());
    }
    @Test public void backgroundArrangementCancelRestoresVisibleCameraIdentityAndPage() throws Exception {
        requireFixture();var store=new ProfileStore(InstrumentationRegistry.getInstrumentation().getTargetContext());String[] ids=new String[8];
        MainActivity[] original=new MainActivity[1];
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a->{try {
                original[0]=a;
                fixture(a,4);Location l=(Location)get(a,"current");for(int i=0;i<8;i++)ids[i]=l.cameras.get(i).id;
                l.cameras.get(1).hidden=true;l.cameras.get(3).hidden=true;l.lastCameraId=ids[7];l.lastPage=1;
                ((ArrayList<Location>)get(a,"locations")).add(l);
                var selected=MainActivity.class.getDeclaredField("focusedCamera");selected.setAccessible(true);selected.setInt(a,5);
                var page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);page.setInt(a,1);
                call(a,"render");store.save(java.util.List.of(l));call(a,"enterTechnician");
                a.getWindow().getDecorView().findViewWithTag("manage-channels").performClick();
                ((View)((ArrayList<?>)get(a,"tileViews")).get(0)).performClick();
                ((View)((ArrayList<?>)get(a,"tileViews")).get(1)).performClick();call(a,"toggleSelectedHidden");
                assertEquals("Staging must not write profiles",ids[0],store.load().get(0).cameras.get(0).id);
                assertEquals(ids[7],store.load().get(0).lastCameraId);stopUiFixturePlayers(a);
            }catch(Exception e){throw new AssertionError(e);}});
            assertTrue(androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome());
            awaitLifecycle(s,androidx.lifecycle.Lifecycle.State.CREATED);Location saved=store.load().get(0);
            assertEquals("Background cancellation must preserve the normal selected identity",ids[7],saved.lastCameraId);assertEquals(1,saved.lastPage);
            for(int i=0;i<8;i++){assertEquals(ids[i],saved.cameras.get(i).id);assertEquals(i==1||i==3,saved.cameras.get(i).hidden);}
            var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
            context.startActivity(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    |android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP|android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            awaitLifecycle(s,androidx.lifecycle.Lifecycle.State.RESUMED);awaitFocus(s);
            s.onActivity(a->{try {
                assertSame("Home return must reuse the same Activity",original[0],a);
                assertFalse((Boolean)get(a,"arranging"));assertFalse((Boolean)get(a,"technician"));
                assertEquals(5,get(a,"focusedCamera"));assertEquals(1,get(a,"page"));
                assertTrue(((View)((ArrayList<?>)get(a,"tileViews")).get(1)).hasFocus());stopUiFixturePlayers(a);
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    @Test public void phoneSetupRequiresActualTvSubstreamAndRejectsCustomCamera() throws Exception {
        requireFixture();var store=new ProfileStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try(var s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{try{
                fixture(a,4);Location l=(Location)get(a,"current");Nvr n=new Nvr();n.host="127.0.0.1";n.port=9;l.devices.add(n);
                ((ArrayList<Location>)get(a,"locations")).add(l);store.save(java.util.List.of(l));call(a,"enterTechnician");
                var action=MainActivity.class.getDeclaredMethod("phoneAction",String.class,org.json.JSONObject.class);action.setAccessible(true);
                try{action.invoke(a,"prepare",new org.json.JSONObject().put("preset","CUSTOM"));fail("Standalone camera creation must be unavailable");}
                catch(java.lang.reflect.InvocationTargetException expected){assertTrue(expected.getCause() instanceof IllegalArgumentException);}
                org.json.JSONObject response=(org.json.JSONObject)action.invoke(a,"save",new org.json.JSONObject().put("kind","nvr").put("nvr",ProfileStore.nvrJson(n,true)));
                assertTrue(response.getBoolean("pendingTv"));AlertDialog preview=(AlertDialog)get(a,"connectionPreview");
                assertFalse("RTSP metadata alone must not enable Save",preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                assertNull(preview.getWindow().getDecorView().findViewWithTag("stream-test-main"));
                assertEquals(8,store.load().get(0).cameras.size());call(a,"closeConnectionPreview");
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
}
