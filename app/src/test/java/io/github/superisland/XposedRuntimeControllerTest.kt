package io.github.superisland

import org.junit.Assert.assertFalse
import org.junit.Test

class XposedRuntimeControllerTest {
    @Test
    fun startsInactiveWithoutFrameworkBind() {
        // Unit tests run outside LSPosed; the official service channel is absent.
        assertFalse(XposedRuntimeController.isActive())
    }
}
