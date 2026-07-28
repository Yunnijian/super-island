package io.github.superisland.hook.systemui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import org.junit.Test;

public final class ModuleReportDeliveryPolicyTest {
    @Test
    public void blocksDeliveryOnlyWhileTheModuleIsForceStopped() {
        assertTrue(ModuleReportDeliveryPolicy.canDeliverForFlags(0));
        assertTrue(ModuleReportDeliveryPolicy.canDeliverForFlags(ApplicationInfo.FLAG_DEBUGGABLE));
        assertFalse(ModuleReportDeliveryPolicy.canDeliverForFlags(ApplicationInfo.FLAG_STOPPED));
        assertFalse(ModuleReportDeliveryPolicy.canDeliverForFlags(
                ApplicationInfo.FLAG_STOPPED | ApplicationInfo.FLAG_DEBUGGABLE));
    }
}
