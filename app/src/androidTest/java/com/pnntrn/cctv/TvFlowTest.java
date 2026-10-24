package com.pnntrn.cctv;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import androidx.media3.common.util.UnstableApi;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.ArrayList;
import static org.junit.Assert.*;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public class TvFlowTest {
    private void shot(UiDevice d,String name) {
        d.waitForIdle(); android.os.SystemClock.sleep(250);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"ui-"+name+".png");
        assertTrue(d.takeScreenshot(file));
    }
    @Test public void remoteNavigationRecorderPairingAndOrderPersistence() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProfileStore store=new ProfileStore(context);
        assertTrue("Fixtures require an emulator",android.os.Build.FINGERPRINT.contains("generic")
            || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        assertTrue("Refusing to overwrite saved locations",store.load().isEmpty());
        assertTrue("Refusing to overwrite saved drafts",context.getSharedPreferences("recorder-drafts",0).getAll().isEmpty());
        Location shop=new Location("Shop");Nvr recorder=new Nvr();recorder.name="Shop recorder";
        // Fixture uses loopback only. Screenshots never contain real credentials or camera feeds.
        recorder.host="127.0.0.1";recorder.port=9;recorder.count=16;recorder.password="demo";recorder.apply(shop);
        String[] movedId={shop.cameras.get(0).id}; int[] movedTo={1};
        ArrayList<Location> data=new ArrayList<>();data.add(shop);store.save(data);
        UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera ")),10000));
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera 1,").focused(true)),5000));
            shot(d,"grid");
            d.pressDPadCenter();assertTrue(d.wait(Until.hasObject(By.textContains("ย้อนกลับ ตารางกล้อง")),5000));
            shot(d,"fullscreen");d.pressBack();
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera ")),5000));
            // ViewingFlowTest covers physical entry. This integration fixture uses the real confirmed transition.
            scenario.onActivity(a->{try {var enter=MainActivity.class.getDeclaredMethod("enterTechnician");
                enter.setAccessible(true);enter.invoke(a);}catch(Exception e){throw new AssertionError(e);}});
            assertTrue(d.wait(Until.hasObject(By.text("แก้ไขเครื่องบันทึก")),5000));shot(d,"settings");
            d.findObject(By.text("ตรวจสอบอัปเดต")).click();
            assertTrue(d.wait(Until.hasObject(By.textContains("เวอร์ชันที่ติดตั้ง: CCTV")),5000));
            shot(d,"updates");d.pressBack();
            assertTrue(d.wait(Until.hasObject(By.text("แก้ไขเครื่องบันทึก")),5000));
            d.findObject(By.text("แก้ไขเครื่องบันทึก")).click();
            assertTrue(d.wait(Until.hasObject(By.text("ชื่อเครื่องบันทึก")),5000));shot(d,"recorder");d.pressBack();
            d.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("ละทิ้ง"))),5000).click();
            d.wait(Until.findObject(By.text("ขั้นสูง")),5000).click();
            d.wait(Until.findObject(By.text("ตั้งค่าผ่านโทรศัพท์")),5000).click();
            // Standard emulator LAN may not expose a private IPv4. Both the pairing dialog and
            // explicit network-unavailable feedback are valid; the HTTP protocol is unit-tested.
            var close = d.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("ปิดการจับคู่"))),5000);
            if(close != null) { d.pressBack(); }
            assertTrue(d.wait(Until.hasObject(By.text("แก้ไขเครื่องบันทึก")),5000));
            // Reordering now belongs exclusively to technician setup, using the same saved IDs.
            d.wait(Until.findObject(By.text("จัดเรียงและซ่อนช่องกล้อง")),5000).click();
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera 1,").focused(true)),5000));
            d.pressDPadCenter();d.pressDPadRight();d.pressDPadCenter();
            d.wait(Until.findObject(By.text("บันทึกการจัดเรียง")),5000).click();
            d.wait(Until.findObject(By.text("ดูกล้อง")),5000).click();
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera ")),5000));
            assertEquals(movedId[0],store.load().get(0).cameras.get(movedTo[0]).id);
        }
        try(ActivityScenario<MainActivity> again=ActivityScenario.launch(MainActivity.class)){
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera ")),5000));
            assertEquals(movedId[0],store.load().get(0).cameras.get(movedTo[0]).id);
        }
    }
}
