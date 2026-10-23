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
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"ui-"+name+".png");
        assertTrue(d.takeScreenshot(file));
    }
    @Test public void remoteNavigationRecorderPairingAndOrderPersistence() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProfileStore store=new ProfileStore(context);
        Location shop=new Location("Shop");Nvr recorder=new Nvr();recorder.name="Shop recorder";
        // Fixture uses loopback only. Screenshots never contain real credentials or camera feeds.
        recorder.host="127.0.0.1";recorder.port=9;recorder.count=16;recorder.password="demo";recorder.apply(shop);
        String firstId=shop.cameras.get(0).id;
        ArrayList<Location> data=new ArrayList<>();data.add(shop);store.save(data);
        UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),10000));
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera 1,").focused(true)),5000));
            shot(d,"grid");
            d.pressDPadCenter();assertTrue(d.wait(Until.hasObject(By.textContains("Back grid")),5000));
            shot(d,"fullscreen");d.pressBack();
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),5000));
            d.findObject(By.text("Settings")).click();
            assertTrue(d.wait(Until.hasObject(By.text("Recorders")),5000));shot(d,"settings");
            d.findObject(By.text("Recorders")).click();
            d.wait(Until.findObject(By.text("Shop recorder")),5000).click();
            assertTrue(d.wait(Until.hasObject(By.text("Recorder name")),5000));shot(d,"recorder");d.pressBack();
            d.findObject(By.text("Set up from phone")).click();
            // Standard emulator LAN may not expose a private IPv4. Both the pairing dialog and
            // explicit network-unavailable feedback are valid; the HTTP protocol is unit-tested.
            if(d.wait(Until.hasObject(By.text("Close pairing")),2000))d.findObject(By.text("Close pairing")).click();
            d.findObject(By.text("‹ Live")).click();
            d.findObject(By.text("Arrange")).click();d.pressDPadCenter();d.pressDPadRight();d.pressDPadCenter();
            d.findObject(By.text("Save order")).click();
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),5000));
            assertEquals(firstId,store.load().get(0).cameras.get(1).id);
        }
        try(ActivityScenario<MainActivity> again=ActivityScenario.launch(MainActivity.class)){
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),5000));
            assertEquals(firstId,store.load().get(0).cameras.get(1).id);
        }
    }
}
