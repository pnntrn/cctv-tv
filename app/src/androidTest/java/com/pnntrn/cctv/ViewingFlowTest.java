package com.pnntrn.cctv;

import android.app.AlertDialog;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.util.UnstableApi;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.lang.reflect.Field;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public class ViewingFlowTest {
    private static void screenshot(UiDevice device, String name) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
        assertTrue(device.takeScreenshot(new File(directory, "ui-" + name + ".png")));
    }
    private static Object value(MainActivity activity, String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void hold(int key, long millis) {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        long down = SystemClock.uptimeMillis();
        instrumentation.sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, key, 0));
        SystemClock.sleep(millis);
        instrumentation.sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0));
    }
    private static void recreateWithProfileChange(ActivityScenario<MainActivity> scenario, ProfileStore store,
            Consumer<ArrayList<Location>> change) {
        Application application = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        boolean[] changed = { false };
        Application.ActivityLifecycleCallbacks callback = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityDestroyed(Activity activity) {
                if (!(activity instanceof MainActivity)) return;
                try {
                    ArrayList<Location> locations = store.load();
                    change.accept(locations); store.save(locations); changed[0] = true;
                } catch (Exception e) { throw new AssertionError(e); }
            }
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        };
        application.registerActivityLifecycleCallbacks(callback);
        try { scenario.recreate(); assertTrue("Profile change must occur between Activity instances", changed[0]); }
        finally { application.unregisterActivityLifecycleCallbacks(callback); }
    }
    @Test public void recreationRestoresCameraIdentityAndStaleIdFallsBackToSavedPage() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        ProfileStore store = new ProfileStore(context);
        assertTrue("Requires clean emulator profiles", store.load().isEmpty());
        assertTrue("Requires clean emulator drafts", context.getSharedPreferences("recorder-drafts", 0).getAll().isEmpty());
        Location location = new Location("Recreation location"); location.grid = 4;
        for (int i = 0; i < 11; i++) location.cameras.add(new Camera("Recreation camera " + (i + 1), "rtsp://127.0.0.1:9/sub", "rtsp://127.0.0.1:9/main"));
        ArrayList<Location> locations = new ArrayList<>(); locations.add(location);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            store.save(locations);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                assertTrue(device.wait(Until.hasObject(By.descStartsWith("Recreation camera 1,")), 5000));
                recreateWithProfileChange(scenario, store, saved -> Collections.swap(saved.get(0).cameras, 0, 8));
                assertTrue(device.wait(Until.hasObject(By.descStartsWith("Recreation camera 1,")), 5000));
                scenario.onActivity(a -> { assertEquals(8, value(a, "focusedCamera")); assertEquals(2, value(a, "page")); });
                recreateWithProfileChange(scenario, store, saved -> saved.get(0).cameras.remove(8));
                assertTrue(device.wait(Until.hasObject(By.descStartsWith("Recreation camera 10,")), 5000));
                scenario.onActivity(a -> { assertEquals(8, value(a, "focusedCamera")); assertEquals(2, value(a, "page")); });
            }
        } finally {
            store.save(new ArrayList<>());
            context.getSharedPreferences("recorder-drafts", 0).edit().clear().commit();
            context.getSharedPreferences("MainActivity", 0).edit().remove("location").commit();
        }
    }
    @Test public void switchingLocationRestoresRememberedCameraOnItsCurrentPage() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        ProfileStore store = new ProfileStore(context);
        assertTrue("Requires clean emulator profiles", store.load().isEmpty());
        assertTrue("Requires clean emulator drafts", context.getSharedPreferences("recorder-drafts", 0).getAll().isEmpty());
        Location first = new Location("Restore location A"); first.grid = 4;
        first.cameras.add(new Camera("Initial camera", "rtsp://127.0.0.1:9/sub", "rtsp://127.0.0.1:9/main"));
        Location second = new Location("Restore location B"); second.grid = 4;
        for (int i = 0; i < 11; i++) second.cameras.add(new Camera("Restored camera " + (i + 1), "rtsp://127.0.0.1:9/sub", "rtsp://127.0.0.1:9/main"));
        second.lastPage = 0;
        second.lastCameraId = second.cameras.get(8).id;
        ArrayList<Location> locations = new ArrayList<>(); locations.add(first); locations.add(second);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            store.save(locations);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                assertTrue(device.wait(Until.hasObject(By.descStartsWith("Initial camera,")), 5000));
                device.pressMenu();
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.tv_viewing_menu))), 3000));
                device.findObject(By.text(context.getString(R.string.tv_switch_location))).click();
                assertTrue(device.wait(Until.hasObject(By.text(second.name)), 3000));
                device.findObject(By.text(second.name)).click();
                assertTrue(device.wait(Until.hasObject(By.descStartsWith("Restored camera 9,")), 5000));
                scenario.onActivity(a -> {
                    assertEquals(second.id, ((Location) value(a, "current")).id);
                    assertEquals(8, value(a, "focusedCamera"));
                    assertEquals(2, value(a, "page"));
                });
            }
        } finally {
            store.save(new ArrayList<>());
            context.getSharedPreferences("recorder-drafts", 0).edit().clear().commit();
            context.getSharedPreferences("MainActivity", 0).edit().remove("location").commit();
        }
    }
    @Test public void idleGeometryRemoteSafetyAndTechnicianLifetime() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        ProfileStore store = new ProfileStore(context);
        assertTrue("Requires clean emulator profiles", store.load().isEmpty());
        assertTrue("Requires clean emulator drafts", context.getSharedPreferences("recorder-drafts", 0).getAll().isEmpty());
        Location location = new Location("บ้านทดสอบ"); location.grid = 4;
        for (int i = 0; i < 11; i++) location.cameras.add(new Camera("กล้อง " + (i + 1), "rtsp://127.0.0.1:9/sub", "rtsp://127.0.0.1:9/main"));
        ArrayList<Location> locations = new ArrayList<>(); locations.add(location); store.save(locations);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("กล้อง 1,")), 5000));
            Object[] original = new Object[1];
            scenario.onActivity(a -> {
                ViewGroup root = (ViewGroup) value(a, "root");
                assertEquals(0, root.getPaddingLeft()); assertEquals(0, root.getPaddingTop());
                ViewGroup grid = root.findViewWithTag("camera-grid");
                assertEquals(root.getWidth(), grid.getWidth()); assertEquals(root.getHeight(), grid.getHeight());
                ViewGroup row = (ViewGroup) grid.getChildAt(0);
                ViewGroup tile = (ViewGroup) row.getChildAt(0);
                assertEquals(0, tile.getPaddingLeft()); assertEquals(0, ((ViewGroup.MarginLayoutParams) tile.getLayoutParams()).leftMargin);
                original[0] = new ArrayList<>((ArrayList<?>) value(a, "streams"));
            });
            SystemClock.sleep(4400);
            scenario.onActivity(a -> {
                assertFalse((Boolean) value(a, "chromeVisible"));
                assertEquals(View.GONE, ((View) value(a, "viewingHelp")).getVisibility());
                assertEquals(original[0], value(a, "streams"));
            });
            screenshot(device, "idle");
            device.pressDPadRight();
            scenario.onActivity(a -> { assertTrue((Boolean) value(a, "chromeVisible")); assertEquals(original[0], value(a, "streams")); });
            device.pressMenu();
            assertTrue(device.wait(Until.hasObject(By.text("เมนูดูภาพ")), 3000));
            screenshot(device, "viewing-menu");
            assertFalse(device.hasObject(By.text("แก้ไขเครื่องบันทึก"))); assertFalse(device.hasObject(By.text("เพิ่มสถานที่")));
            device.pressBack();
            for (int i = 0; i < 8; i++) {
                device.pressMenu();
                assertTrue(device.wait(Until.hasObject(By.text("เมนูดูภาพ")), 3000));
                scenario.onActivity(a -> {
                    java.util.Collection<?> tracked = (java.util.Collection<?>) value(a, "dialogs");
                    assertEquals("Dismissed menus must be pruned without waiting for background", 1, tracked.size());
                    assertTrue(tracked.contains(value(a, "viewingMenu")));
                });
                device.pressBack();
            }
            hold(KeyEvent.KEYCODE_DPAD_CENTER, 1100);
            scenario.onActivity(a -> { assertFalse((Boolean) value(a, "managing")); assertNull(value(a, "recorderEditor")); });
            device.pressBack();
            device.pressDPadRight(); device.pressDPadRight(); device.pressDPadRight(); device.pressDPadDown();
            scenario.onActivity(a -> { assertEquals(10, value(a, "focusedCamera")); assertEquals(2, value(a, "page")); });
            device.pressDPadCenter(); device.pressBack();
            scenario.onActivity(a -> { assertEquals(10, value(a, "focusedCamera")); assertEquals(2, value(a, "page")); });
            hold(KeyEvent.KEYCODE_MENU, 3200);
            assertTrue(device.wait(Until.hasObject(By.text("เข้าสู่การตั้งค่า")), 3000));
            screenshot(device, "technician-entry");
            scenario.onActivity(a -> assertTrue(((AlertDialog) value(a, "technicianConfirmation")).getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus()));
            device.findObject(By.text("เข้าสู่การตั้งค่า")).click();
            assertTrue(device.wait(Until.hasObject(By.text("เพิ่มเครื่องบันทึก")), 3000));
            device.findObject(By.text("สถานที่")).click();
            device.wait(Until.findObject(By.text("ตั้งค่าสถานที่")), 3000).click();
            device.wait(Until.findObject(By.text("ลบสถานที่")), 3000).click();
            assertTrue(device.wait(Until.hasObject(By.text("ยกเลิก").focused(true)), 3000));
            device.findObject(By.text("ลบ")).click();
            assertTrue(device.wait(Until.hasObject(By.text("เพิ่มสถานที่")), 3000));
            scenario.onActivity(a -> {
                assertNull(value(a, "current")); assertTrue((Boolean) value(a, "technician"));
                assertTrue("Deleting the last location must stay in setup", (Boolean) value(a, "managing"));
            });
            screenshot(device, "technician-empty");
            device.findObject(By.text("ดูกล้อง")).click();
            hold(KeyEvent.KEYCODE_MENU, 3200);
            device.wait(Until.findObject(By.text("เข้าสู่การตั้งค่า")), 3000).click();
            assertTrue(device.wait(Until.hasObject(By.text("เพิ่มสถานที่")), 3000));
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> { assertFalse((Boolean) value(a, "technician")); assertFalse((Boolean) value(a, "managing")); assertNull(value(a, "recorderEditor")); });
        } finally { store.save(new ArrayList<>()); context.getSharedPreferences("recorder-drafts", 0).edit().clear().commit(); }
    }
}
