package io.github.superisland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEnvironmentProbeTest {
    @Test
    fun rootPermissionRequiresUidZero() {
        assertTrue(
            RuntimeEnvironmentProbe.probeRootPermission {
                RuntimeEnvironmentProbe.CommandResult(exitCode = 0, stdout = "0\n")
            },
        )
    }

    @Test
    fun nonRootUidIsRejected() {
        assertFalse(
            RuntimeEnvironmentProbe.probeRootPermission {
                RuntimeEnvironmentProbe.CommandResult(exitCode = 0, stdout = "2000\n")
            },
        )
    }

    @Test
    fun deniedOrMissingSuIsRejected() {
        assertFalse(
            RuntimeEnvironmentProbe.probeRootPermission {
                RuntimeEnvironmentProbe.CommandResult(exitCode = 1, stdout = "Permission denied")
            },
        )
        assertFalse(
            RuntimeEnvironmentProbe.probeRootPermission {
                RuntimeEnvironmentProbe.CommandResult(exitCode = -1, stdout = "", timedOut = true)
            },
        )
    }

    @Test
    fun kernelSuVersionUsesItsOwnConciseVersionFormat() {
        assertEquals("KernelSU 3.2.5", RuntimeEnvironmentProbe.rootImplementationSummary("3.2.5:KernelSU\n"))
    }

    @Test
    fun lsposedDaemonVersionKeepsItsActualBuildNumber() {
        assertEquals(
            "2.1.0 (7769)",
            RuntimeEnvironmentProbe.lsposedVersionSummary(
                "I/LSPosedService  ] version 2.1.0 (7769)\n",
            ),
        )
    }
}
