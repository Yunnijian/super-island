package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEnvironmentTest {
    @Test
    fun fourStateMatrix() {
        assertEquals(
            "环境已就绪",
            snapshot(root = true, lsposed = true).statusLabel,
        )
        assertEquals(
            "已 Root · LSPosed 未激活",
            snapshot(root = true, lsposed = false).statusLabel,
        )
        assertEquals(
            "LSPosed 已激活 · 未检测到 Root",
            snapshot(root = false, lsposed = true).statusLabel,
        )
        assertEquals(
            "环境未满足",
            snapshot(root = false, lsposed = false).statusLabel,
        )
    }

    @Test
    fun environmentAcceptableRequiresBothSignals() {
        assertTrue(snapshot(root = true, lsposed = true).environmentAcceptable)
        assertFalse(snapshot(root = true, lsposed = false).environmentAcceptable)
        assertFalse(snapshot(root = false, lsposed = true).environmentAcceptable)
        assertFalse(snapshot(root = false, lsposed = false).environmentAcceptable)
    }

    @Test
    fun labelsRemainMinimal() {
        assertEquals("Root 权限可用", RuntimeEnvironmentLabels.rootDetail(true))
        assertEquals("未取得 Root 权限", RuntimeEnvironmentLabels.rootDetail(false))
        assertEquals("LSPosed 已激活", RuntimeEnvironmentLabels.lsposedDetail(true))
        assertEquals("LSPosed 未激活", RuntimeEnvironmentLabels.lsposedDetail(false))
    }

    private fun snapshot(
        root: Boolean,
        lsposed: Boolean,
    ): RuntimeEnvironmentSnapshot =
        RuntimeEnvironmentSnapshot(
            rootAvailable = root,
            rootDetail = RuntimeEnvironmentLabels.rootDetail(root),
            lsposedActive = lsposed,
            lsposedDetail = RuntimeEnvironmentLabels.lsposedDetail(lsposed),
        )
}
