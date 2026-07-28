package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusNotificationRequestTest {
    @Test
    fun fallsBackToProgressForCompactFocusStatus() {
        val request = FocusNotificationRequest("设备状态", "电量: 80%", progress = 80)

        assertEquals("80%", request.shortStatus)
    }
}
