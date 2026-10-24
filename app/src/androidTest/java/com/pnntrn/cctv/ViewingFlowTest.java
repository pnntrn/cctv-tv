package com.pnntrn.cctv;

import android.app.AlertDialog;
import android.app.Activity;
import android.app.Application;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
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
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
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
    private static Object value(Object owner, String name) {
        try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private enum InputWindow { ACTIVITY, VIEWING_MENU, TECHNICIAN_CONFIRMATION }
    private static String inputState(MainActivity activity) {
        StringBuilder state = new StringBuilder("activityFocus=").append(activity.hasWindowFocus())
                .append(", started=").append(value(activity, "started"))
                .append(", managing=").append(value(activity, "managing"))
                .append(", lifecycle=").append(ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity))
                .append(", finishing=").append(activity.isFinishing()).append(", destroyed=").append(activity.isDestroyed());
        for (String name : new String[]{"viewingMenu", "technicianConfirmation"}) {
            AlertDialog dialog = (AlertDialog) value(activity, name);
            state.append(", ").append(name).append("=").append(dialog == null ? "absent"
                    : "showing:" + dialog.isShowing() + "/focus:" + dialog.getWindow().getDecorView().hasWindowFocus());
        }
        java.util.Map<?, ?> presses = (java.util.Map<?, ?>) value(value(activity, "remote"), "presses");
        for (var entry : presses.entrySet()) {
            state.append(", ").append(entry.getKey()).append("=[started:").append(value(entry.getValue(), "started"))
                    .append("/held:").append(value(entry.getValue(), "held"))
                    .append("/pending:").append(value(entry.getValue(), "pendingRelease")).append("]");
        }
        return state.toString();
    }
    private static String timeoutState(ActivityScenario<MainActivity> scenario, String input) {
        StringBuilder state = new StringBuilder(input).append(", scenario=").append(scenario.getState());
        scenario.onActivity(activity -> {
            state.append(", trackedDialogs=[");
            for (Object tracked : new ArrayList<>((java.util.Collection<?>) value(activity, "dialogs"))) {
                AlertDialog dialog = (AlertDialog) tracked;
                state.append("{showing:").append(dialog.isShowing())
                        .append("/focus:").append(dialog.getWindow().getDecorView().hasWindowFocus())
                        .append("/title:").append(dialog.getWindow().getAttributes().getTitle())
                        .append("/flags:").append(Integer.toHexString(dialog.getWindow().getAttributes().flags)).append("}");
            }
            state.append("], activities=[");
            var monitor = ActivityLifecycleMonitorRegistry.getInstance();
            for (Stage stage : Stage.values()) for (Activity tracked : monitor.getActivitiesInStage(stage)) {
                state.append("{").append(tracked.getClass().getName()).append("/package:").append(tracked.getPackageName())
                        .append("/stage:").append(stage).append("/focus:").append(tracked.hasWindowFocus())
                        .append("/finishing:").append(tracked.isFinishing()).append("/destroyed:").append(tracked.isDestroyed()).append("}");
            }
            state.append("]");
        });
        state.append(", ").append(systemWindowState());
        return state.toString();
    }
    private static String systemWindowState() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var automation = instrumentation.getUiAutomation();
        var service = automation.getServiceInfo();
        int originalFlags = service.flags;
        StringBuilder state = new StringBuilder();
        try {
            service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(service);
            state.append("currentPackage=").append(UiDevice.getInstance(instrumentation).getCurrentPackageName());
            var activeRoot = automation.getRootInActiveWindow();
            if (activeRoot == null) state.append(", activeRoot=absent");
            else {
                state.append(", activeRoot={package:").append(activeRoot.getPackageName()).append("/class:")
                        .append(activeRoot.getClassName()).append("/window:").append(activeRoot.getWindowId()).append("}");
                activeRoot.recycle();
            }
            state.append(", systemWindows=[");
            for (var window : automation.getWindows()) {
                state.append("{id:").append(window.getId()).append("/title:").append(window.getTitle())
                        .append("/type:").append(window.getType()).append("/focus:").append(window.isFocused())
                        .append("/active:").append(window.isActive());
                var root = window.getRoot();
                state.append("/rootPackage:").append(root == null ? "absent" : root.getPackageName()).append("}");
                if (root != null) root.recycle();
                window.recycle();
            }
            return state.append("]").toString();
        } catch (RuntimeException failure) {
            return state.append("/windowDiagnosticsError:").append(failure).toString();
        } finally {
            service.flags = originalFlags;
            automation.setServiceInfo(service);
        }
    }
    private static void awaitInputFocus(ActivityScenario<MainActivity> scenario, InputWindow window) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = { false };
        String[] state = { "Activity callback not reached" };
        do {
            scenario.onActivity(activity -> {
                AlertDialog dialog = window == InputWindow.VIEWING_MENU ? (AlertDialog) value(activity, "viewingMenu")
                        : window == InputWindow.TECHNICIAN_CONFIRMATION ? (AlertDialog) value(activity, "technicianConfirmation") : null;
                ready[0] = window == InputWindow.ACTIVITY ? activity.hasWindowFocus()
                        && value(activity, "viewingMenu") == null && value(activity, "technicianConfirmation") == null
                        : dialog != null && dialog.isShowing() && dialog.getWindow().getDecorView().hasWindowFocus();
                state[0] = inputState(activity);
            });
            if (ready[0]) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Expected " + window + " input focus within 5000 ms; " + timeoutState(scenario, state[0]));
    }
    private static void awaitText(ActivityScenario<MainActivity> scenario, UiDevice device, String text,
            long timeout, String message) {
        if (device.wait(Until.hasObject(By.text(text)), timeout)) return;
        String[] state = { "Activity callback not reached" };
        scenario.onActivity(activity -> state[0] = inputState(activity));
        fail(message + "; " + timeoutState(scenario, state[0]));
    }
    private static void awaitState(ActivityScenario<MainActivity> scenario, Lifecycle.State expected) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            if (scenario.getState() == expected) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        String[] input = { "Activity callback not reached" };
        scenario.onActivity(activity -> input[0] = inputState(activity));
        fail("Expected lifecycle " + expected + " within 5000 ms; " + timeoutState(scenario, input[0]));
    }
    private static void homeAndResume(ActivityScenario<MainActivity> scenario, UiDevice device) {
        MainActivity[] original = new MainActivity[1];
        scenario.onActivity(activity -> original[0] = activity);
        assertTrue("Home must background the current app", device.pressHome());
        awaitState(scenario, Lifecycle.State.CREATED);
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        awaitState(scenario, Lifecycle.State.RESUMED);
        awaitInputFocus(scenario, InputWindow.ACTIVITY);
        scenario.onActivity(activity -> assertSame("Returning from Home must reuse the existing Activity", original[0], activity));
    }
    private static void hold(int key, long millis) {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        long down = SystemClock.uptimeMillis();
        instrumentation.sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, key, 0));
        SystemClock.sleep(millis);
        instrumentation.sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0));
    }
    private static long keyDown(int key) {
        long down = SystemClock.uptimeMillis();
        sendKey(down, KeyEvent.ACTION_DOWN, key, 0, 0);
        return down;
    }
    private static long keyDown(ActivityScenario<MainActivity> scenario, InputWindow window, int key) {
        awaitInputFocus(scenario, window);
        return keyDown(key);
    }
    private static void hold(ActivityScenario<MainActivity> scenario, InputWindow window, int key, long millis) {
        awaitInputFocus(scenario, window);
        hold(key, millis);
    }
    private static void sendKey(long down, int action, int key, int repeat, int flags) {
        InstrumentationRegistry.getInstrumentation().sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(),
                action, key, repeat, 0, -1, 0, flags));
    }
    @Test public void miBoxFirstSetupKeepsHeldKeysOutOfNewDialogs() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        ProfileStore store = new ProfileStore(context);
        assertTrue("Requires clean emulator profiles", store.load().isEmpty());
        assertTrue("Requires clean emulator drafts", context.getSharedPreferences("recorder-drafts", 0).getAll().isEmpty());
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(
                    R.string.tv_no_cameras_yet_hold_menu_for_3_seconds_to_set_up))), 5000));
            awaitInputFocus(scenario, InputWindow.ACTIVITY);
            device.waitForIdle();
            screenshot(device, "mi-box-empty");
            long ok = keyDown(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER);
            awaitText(scenario, device, context.getString(R.string.tv_viewing_menu), 2500,
                    "Holding OK must open viewing options without a Menu button");
            awaitInputFocus(scenario, InputWindow.VIEWING_MENU);
            sendKey(ok, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1, KeyEvent.FLAG_LONG_PRESS);
            sendKey(ok, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0);
            assertTrue("The held OK release must not select the first menu item",
                    device.hasObject(By.text(context.getString(R.string.tv_viewing_menu))));
            screenshot(device, "mi-box-menu");
            scenario.onActivity(a -> { assertFalse((Boolean) value(a, "managing")); assertNull(value(a, "recorderEditor")); });

            long cancelledBack = keyDown(scenario, InputWindow.VIEWING_MENU, KeyEvent.KEYCODE_BACK);
            sendKey(cancelledBack, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, KeyEvent.FLAG_CANCELED);
            SystemClock.sleep(3200);
            scenario.onActivity(a -> { assertNull(value(a, "technicianConfirmation")); assertNotNull(value(a, "viewingMenu")); });

            assertTrue("Viewing menu must offer Settings without a hidden Back gesture",
                    device.hasObject(By.text("ตั้งค่า")));
            for (int i = 0; i < 3; i++) device.pressDPadDown();
            scenario.onActivity(a -> assertEquals("ตั้งค่า", ((AlertDialog) value(a, "viewingMenu"))
                    .getListView().getSelectedItem()));
            device.pressDPadCenter();
            awaitText(scenario, device, context.getString(R.string.tv_enter_setup), 2500,
                    "Selecting Settings must request technician confirmation");
            awaitInputFocus(scenario, InputWindow.TECHNICIAN_CONFIRMATION);
            scenario.onActivity(a -> {
                AlertDialog confirmation = (AlertDialog) value(a, "technicianConfirmation");
                assertNotNull(confirmation);
                assertTrue(confirmation.isShowing());
                assertTrue(confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());
                assertFalse((Boolean) value(a, "technician"));
                assertFalse((Boolean) value(a, "managing"));
            });
            screenshot(device, "mi-box-technician");
            awaitInputFocus(scenario, InputWindow.TECHNICIAN_CONFIRMATION);
            device.pressDPadCenter();
            awaitInputFocus(scenario, InputWindow.ACTIVITY);
            scenario.onActivity(a -> { assertNull(value(a, "technicianConfirmation")); assertFalse((Boolean) value(a, "managing")); });

            hold(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER, 1100);
            awaitInputFocus(scenario, InputWindow.VIEWING_MENU);
            device.pressBack();
            awaitInputFocus(scenario, InputWindow.ACTIVITY);
            scenario.onActivity(a -> { assertNull(value(a, "viewingMenu")); assertFalse((Boolean) value(a, "technician")); });
            hold(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER, 1100);
            hold(scenario, InputWindow.VIEWING_MENU, KeyEvent.KEYCODE_BACK, 3200);
            awaitInputFocus(scenario, InputWindow.ACTIVITY);
            scenario.onActivity(a -> {
                assertNull("Long Back must close normally without setup", value(a, "technicianConfirmation"));
                assertNull(value(a, "viewingMenu")); assertFalse((Boolean) value(a, "technician"));
            });
            hold(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER, 1100);
            awaitInputFocus(scenario, InputWindow.VIEWING_MENU);
            for (int i = 0; i < 3; i++) device.pressDPadDown();
            device.pressDPadCenter();
            awaitInputFocus(scenario, InputWindow.TECHNICIAN_CONFIRMATION);
            device.pressDPadRight();
            scenario.onActivity(a -> assertTrue(((AlertDialog) value(a, "technicianConfirmation"))
                    .getButton(AlertDialog.BUTTON_POSITIVE).hasFocus()));
            device.pressDPadCenter();
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.tv_add_location))), 3000));
            scenario.onActivity(a -> { assertTrue((Boolean) value(a, "technician")); assertTrue((Boolean) value(a, "managing")); });
        } finally {
            store.save(new ArrayList<>());
            context.getSharedPreferences("recorder-drafts", 0).edit().clear().commit();
            context.getSharedPreferences("MainActivity", 0).edit().remove("location").commit();
        }
    }
    @Test public void miBoxShortOkFullscreenAndInterruptedHoldsStaySafe() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        ProfileStore store = new ProfileStore(context);
        assertTrue("Requires clean emulator profiles", store.load().isEmpty());
        assertTrue("Requires clean emulator drafts", context.getSharedPreferences("recorder-drafts", 0).getAll().isEmpty());
        Location location = new Location("Mi Box controls");
        location.cameras.add(new Camera("Remote camera 1", "rtsp://127.0.0.1:9/sub", "rtsp://127.0.0.1:9/main"));
        location.cameras.add(new Camera("Remote camera 2", "rtsp://127.0.0.1:9/sub", "rtsp://127.0.0.1:9/main"));
        ArrayList<Location> locations = new ArrayList<>(); locations.add(location); store.save(locations);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Remote camera 1,")), 5000));
            awaitInputFocus(scenario, InputWindow.ACTIVITY);
            device.pressDPadRight(); device.pressDPadCenter();
            scenario.onActivity(a -> {
                assertEquals(1, value(a, "fullscreen"));
                // Stop the unreachable loopback playback before checking stream-selection controls.
                for (Object stream : (ArrayList<?>) value(a, "streams")) ((StreamTile) stream).release();
                boolean sub = (Boolean) value(a, "fullSub");
                int token = (Integer) value(a, "fullscreenToken");
                long down = SystemClock.uptimeMillis();
                a.dispatchKeyEvent(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0));
                assertEquals("OK never changes SUB selection", sub, value(a, "fullSub"));
                a.dispatchKeyEvent(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 1));
                a.dispatchKeyEvent(new KeyEvent(down, down + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0));
                assertEquals(true, value(a, "fullSub"));
                assertEquals("Short OK reveals toolbar without restarting playback", token, value(a, "fullscreenToken"));
                assertTrue(((View)value(a,"layoutControl")).hasFocus());
                a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
                a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN));
            });
            Object[] original = new Object[2];
            scenario.onActivity(a -> {
                for (Object stream : (ArrayList<?>) value(a, "streams")) ((StreamTile) stream).release();
                original[0] = new ArrayList<>((ArrayList<?>) value(a, "streams"));
                original[1] = value(a, "fullSub");
            });
            hold(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_ENTER, 1100);
            awaitText(scenario, device, context.getString(R.string.tv_viewing_menu), 2500,
                    "Holding Enter in fullscreen must open viewing options");
            awaitInputFocus(scenario, InputWindow.VIEWING_MENU);
            scenario.onActivity(a -> {
                assertEquals(1, value(a, "fullscreen"));
                assertEquals(original[0], value(a, "streams"));
                assertEquals("Long OK must not toggle fullscreen stream", original[1], value(a, "fullSub"));
            });
            device.pressBack();
            awaitInputFocus(scenario, InputWindow.ACTIVITY);
            device.pressBack();
            scenario.onActivity(a -> { assertEquals(-1, value(a, "fullscreen")); assertEquals(1, value(a, "focusedCamera")); });

            long cancelledOk = keyDown(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER);
            sendKey(cancelledOk, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, KeyEvent.FLAG_CANCELED);
            SystemClock.sleep(1200);
            scenario.onActivity(a -> { assertNull(value(a, "viewingMenu")); assertEquals(-1, value(a, "fullscreen")); });

            long unfocusedOk = keyDown(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER);
            scenario.onActivity(a -> a.onWindowFocusChanged(false));
            SystemClock.sleep(1200);
            scenario.onActivity(a -> a.onWindowFocusChanged(true));
            sendKey(unfocusedOk, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0);
            scenario.onActivity(a -> { assertNull(value(a, "viewingMenu")); assertEquals(-1, value(a, "fullscreen")); });

            hold(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER, 1100);
            long unfocusedBack = keyDown(scenario, InputWindow.VIEWING_MENU, KeyEvent.KEYCODE_BACK);
            scenario.onActivity(a -> a.onWindowFocusChanged(false));
            SystemClock.sleep(3200);
            scenario.onActivity(a -> {
                a.onWindowFocusChanged(true);
                assertNull(value(a, "technicianConfirmation"));
                assertNotNull(value(a, "viewingMenu"));
            });
            sendKey(unfocusedBack, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0);
            awaitInputFocus(scenario, InputWindow.VIEWING_MENU);
            device.pressBack();

            hold(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER, 1100);
            long dismissedBack = keyDown(scenario, InputWindow.VIEWING_MENU, KeyEvent.KEYCODE_BACK);
            scenario.onActivity(a -> ((AlertDialog) value(a, "viewingMenu")).dismiss());
            SystemClock.sleep(3200);
            sendKey(dismissedBack, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0);
            scenario.onActivity(a -> { assertNull(value(a, "technicianConfirmation")); assertFalse((Boolean) value(a, "managing")); });

            long backgroundOk = keyDown(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER);
            homeAndResume(scenario, device);
            sendKey(backgroundOk, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0);
            SystemClock.sleep(1200);
            scenario.onActivity(a -> {
                assertNull(value(a, "viewingMenu")); assertNull(value(a, "technicianConfirmation"));
                assertEquals(-1, value(a, "fullscreen")); assertFalse((Boolean) value(a, "managing"));
                assertFalse((Boolean) value(a, "technician")); assertNull(value(a, "recorderEditor"));
                for (Object tracked : (java.util.Collection<?>) value(a, "dialogs"))
                    assertFalse("The old OK release must not open a popup", ((AlertDialog) tracked).isShowing());
            });

            hold(scenario, InputWindow.ACTIVITY, KeyEvent.KEYCODE_DPAD_CENTER, 1100);
            long backgroundBack = keyDown(scenario, InputWindow.VIEWING_MENU, KeyEvent.KEYCODE_BACK);
            homeAndResume(scenario, device);
            sendKey(backgroundBack, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0);
            SystemClock.sleep(3200);
            scenario.onActivity(a -> {
                assertNull(value(a, "viewingMenu")); assertNull(value(a, "technicianConfirmation"));
                assertFalse((Boolean) value(a, "technician")); assertFalse((Boolean) value(a, "managing"));
                assertNull(value(a, "recorderEditor"));
                for (Object tracked : (java.util.Collection<?>) value(a, "dialogs"))
                    assertFalse("The old Back release must not open a popup", ((AlertDialog) tracked).isShowing());
            });
        } finally {
            store.save(new ArrayList<>());
            context.getSharedPreferences("recorder-drafts", 0).edit().clear().commit();
            context.getSharedPreferences("MainActivity", 0).edit().remove("location").commit();
        }
    }
    @Test public void encryptedRecorderProfilesSurviveClosingAndRelaunching() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        ProfileStore store = new ProfileStore(context);
        assertTrue("Requires clean emulator profiles", store.load().isEmpty());
        Location location = new Location("Saved location"); location.grid = 4; location.tcp = true;
        Nvr recorder = new Nvr(); recorder.name = "Saved NVR"; recorder.host = "127.0.0.1";
        recorder.port = 9; recorder.username = "synthetic-user"; recorder.password = "synthetic-test-secret";
        recorder.count = 11; recorder.prefix = "Saved camera"; recorder.apply(location);
        location.cameras.get(8).name = "Saved custom camera";
        location.lastPage = 2; location.lastCameraId = location.cameras.get(8).id;
        ArrayList<Location> expected = new ArrayList<>(); expected.add(location);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            store.save(expected);
            for (int launch = 0; launch < 2; launch++) {
                try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                    assertTrue(device.wait(Until.hasObject(By.descStartsWith("Saved custom camera,")), 5000));
                    scenario.onActivity(a -> {
                        assertTrue((Boolean) value(a, "storageOkay"));
                        assertEquals(location.id, ((Location) value(a, "current")).id);
                        assertEquals(8, value(a, "focusedCamera")); assertEquals(2, value(a, "page"));
                    });
                    scenario.recreate();
                    scenario.onActivity(a -> assertEquals(location.id, ((Location) value(a, "current")).id));
                }
                Location saved = new ProfileStore(context).load().get(0);
                assertEquals(location.id, saved.id); assertEquals(location.name, saved.name);
                assertEquals(location.grid, saved.grid); assertEquals(location.tcp, saved.tcp);
                assertEquals(2, saved.lastPage); assertEquals(location.lastCameraId, saved.lastCameraId);
                assertEquals(1, saved.devices.size());
                assertEquals(ProfileStore.nvrJson(recorder, true).toString(), ProfileStore.nvrJson(saved.devices.get(0), true).toString());
                assertEquals(11, saved.cameras.size());
                for (int i = 0; i < 11; i++) {
                    Camera before = location.cameras.get(i), after = saved.cameras.get(i);
                    assertEquals(before.id, after.id); assertEquals(before.name, after.name);
                    assertEquals(before.deviceId, after.deviceId); assertEquals(before.channel, after.channel);
                    assertEquals(before.subUrl, after.subUrl); assertEquals(before.mainUrl, after.mainUrl);
                }
                byte[] encrypted = java.nio.file.Files.readAllBytes(new File(context.getFilesDir(), "locations.enc").toPath());
                assertEquals(1, encrypted[0]);
                String raw = new String(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1);
                assertFalse(raw.contains(recorder.password)); assertFalse(raw.contains(location.name));
            }
        } finally {
            store.save(new ArrayList<>());
            context.getSharedPreferences("MainActivity", 0).edit().remove("location").commit();
        }
    }
    @Test public void unreadableProfilesShowErrorAndAreNotOverwrittenOnClose() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        ProfileStore store = new ProfileStore(context);
        assertTrue("Requires clean emulator profiles", store.load().isEmpty());
        File file = new File(context.getFilesDir(), "locations.enc");
        byte[] unsupported = new byte[29]; unsupported[0] = 99;
        try {
            java.nio.file.Files.write(file.toPath(), unsupported);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.tv_cannot_open_saved_locations))), 5000));
                scenario.onActivity(a -> assertFalse((Boolean) value(a, "storageOkay")));
            }
            assertArrayEquals("Opening/closing unreadable profiles must preserve the original file",
                    unsupported, java.nio.file.Files.readAllBytes(file.toPath()));
        } finally {
            store.save(new ArrayList<>());
            context.getSharedPreferences("MainActivity", 0).edit().remove("location").commit();
        }
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
