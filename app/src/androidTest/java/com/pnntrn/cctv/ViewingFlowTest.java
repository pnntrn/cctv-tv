package com.pnntrn.cctv;

import android.app.AlertDialog;
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
