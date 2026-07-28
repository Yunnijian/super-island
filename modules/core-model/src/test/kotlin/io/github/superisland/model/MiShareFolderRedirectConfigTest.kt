package io.github.superisland.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiShareFolderRedirectConfigTest {
    @Test
    fun acceptsOnlyTheObservedMiShareFolderActions() {
        assertTrue(
            MiShareFolderRedirectContract.isMiShareFolderIntentAction(
                MiShareFolderRedirectContract.OEM_NEW_FILE_EXPLORER_ACTION,
            ),
        )
        assertTrue(
            MiShareFolderRedirectContract.isMiShareFolderIntentAction(
                MiShareFolderRedirectContract.OEM_LEGACY_FILE_EXPLORER_ACTION,
            ),
        )
        assertFalse(MiShareFolderRedirectContract.isMiShareFolderIntentAction("android.intent.action.VIEW"))
    }

    @Test
    fun acceptsOnlyTheCanonicalMiShareReceiveDirectory() {
        assertTrue(MiShareFolderRedirectContract.isCanonicalReceiveDirectory("/sdcard/Download/MiShare"))
        assertTrue(MiShareFolderRedirectContract.isCanonicalReceiveDirectory("/storage/emulated/0/Download/MiShare"))
        assertTrue(MiShareFolderRedirectContract.isCanonicalReceiveDirectory("/storage/emulated/10/Download/MiShare/"))
        assertFalse(MiShareFolderRedirectContract.isCanonicalReceiveDirectory("/sdcard/Download"))
        assertFalse(MiShareFolderRedirectContract.isCanonicalReceiveDirectory("/sdcard/Download/MiShare/../other"))
        assertFalse(MiShareFolderRedirectContract.isCanonicalReceiveDirectory(null))
    }
}
