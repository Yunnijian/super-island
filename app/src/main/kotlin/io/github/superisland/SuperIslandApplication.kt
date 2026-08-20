package io.github.superisland

import android.app.Application

/**
 * Application entry that owns process-lifetime Root/LSPosed environment controllers.
 */
class SuperIslandApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // One-time migration for removed FocusLab lab.
        runCatching {
            getSharedPreferences("super-island-features", MODE_PRIVATE).edit().clear().apply()
        }
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)?.cancel(0x464F)
        }
        ResidentIslandHostConfigSync.start(this)
        IslandAppearanceConfigSync.start(this)
        MiShareFolderExtensionConfigSync.start(this)
        SmartCapsuleRuntimeStatusController.start(this)
        SmartCapsuleHostConfigSync.start(this)
        RuntimeEnvironmentController.start()
    }
}
