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
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),10000));
            assertTrue(d.wait(Until.hasObject(By.descStartsWith("Camera 1,").focused(true)),5000));
            shot(d,"grid");
            d.pressDPadCenter();assertTrue(d.wait(Until.hasObject(By.textContains("Back grid")),5000));
            shot(d,"fullscreen");d.pressBack();
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),5000));
            d.findObject(By.text("Settings")).click();
            assertTrue(d.wait(Until.hasObject(By.text("Recorders")),5000));shot(d,"settings");
            d.findObject(By.text("Check for updates")).click();
            assertTrue(d.wait(Until.hasObject(By.textContains("Installed: CCTV")),5000));
            shot(d,"updates");d.pressBack();
            assertTrue(d.wait(Until.hasObject(By.text("Recorders")),5000));
            d.findObject(By.text("Recorders")).click();
            d.wait(Until.findObject(By.text("Shop recorder")),5000).click();
            assertTrue(d.wait(Until.hasObject(By.text("Recorder name")),5000));shot(d,"recorder");d.pressBack();
            d.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)Discard"))),5000).click();
            d.wait(Until.findObject(By.text("Set up from phone")),5000).click();
            // Standard emulator LAN may not expose a private IPv4. Both the pairing dialog and
            // explicit network-unavailable feedback are valid; the HTTP protocol is unit-tested.
            var close = d.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)close pairing"))),5000);
            if(close != null) { d.pressBack(); }
            assertTrue(d.wait(Until.hasObject(By.text("Recorders")),5000));
            d.findObject(By.text("‹ Live")).click();
            d.findObject(By.text("Arrange")).click();
            assertTrue(d.wait(Until.hasObject(By.text("Save order")),5000));
            var focused=d.findObject(By.descStartsWith("Camera ").focused(true));
            if(focused==null){d.pressDPadDown();focused=d.wait(Until.findObject(By.descStartsWith("Camera ").focused(true)),5000);}
            assertNotNull("Remote focus must reach a camera",focused);
            String description=focused.getContentDescription();
            int from=Integer.parseInt(description.substring(7,description.indexOf(',')))-1;
            movedId[0]=store.load().get(0).cameras.get(from).id;
            movedTo[0]=from+(from%2==0?1:-1);
            d.pressDPadCenter();
            if(from%2==0)d.pressDPadRight();else d.pressDPadLeft();
            assertTrue(d.wait(Until.hasObject(By.desc(description).focused(true)),5000));
            d.pressDPadCenter();
            d.findObject(By.text("Save order")).click();
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),5000));
            assertEquals(movedId[0],store.load().get(0).cameras.get(movedTo[0]).id);
        }
        try(ActivityScenario<MainActivity> again=ActivityScenario.launch(MainActivity.class)){
            assertTrue(d.wait(Until.hasObject(By.textContains("Substreams")),5000));
            assertEquals(movedId[0],store.load().get(0).cameras.get(movedTo[0]).id);
        }
    }
}
