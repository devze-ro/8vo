package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class OctavoBootstrapTest {
    @Test
    public void nativeLibraryAndActivityBootstrap() {
        assertEquals("0.4.0-dev", OctavoNative.version());
        assertEquals("android", OctavoNative.platform());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity ->
                assertNotNull(activity.findViewById(R.id.octavo_surface)));
            scenario.recreate();
            scenario.onActivity(activity ->
                assertNotNull(activity.findViewById(R.id.octavo_surface)));
        }
    }
}
