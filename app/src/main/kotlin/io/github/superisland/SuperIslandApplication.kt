package io.github.superisland

import android.app.Application

/**
 * Application entry that owns process-lifetime Root/LSPosed environment controllers.
 */
class SuperIslandApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ResidentIslandHostConfigSync.start(this)
        IslandAppearanceConfigSync.start(this)
        MiShareFolderExtensionConfigSync.start(this)
        SmartCapsuleRuntimeStatusController.start(this)
        SmartCapsuleHostConfigSync.start(this)
        RuntimeEnvironmentController.start()
    }
}
