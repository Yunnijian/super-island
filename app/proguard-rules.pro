# libxposed loads this entry from META-INF/xposed/java_init.list.
-dontwarn io.github.libxposed.api.**
-keep,allowoptimization public class io.github.superisland.hook.systemui.SuperIslandXposedModule {
    public <init>();
    public void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
}

# libxposed invokes these objects after they cross the module/framework boundary. R8 cannot see
# that external interface dispatch and must not remove or merge the Hooker implementation method.
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain);
}

# Android components are discovered from the merged manifest; retain their public entry points.
-keep class io.github.superisland.SuperIslandApplication { *; }
-keep class io.github.superisland.MainActivity { *; }
-keep class io.github.superisland.SmartCapsuleReportProvider { *; }
-keep class io.github.superisland.SmartCapsuleXmsfAcceptanceReceiver { *; }
-keep class io.github.superisland.BatteryMonitorService { *; }
-keep class io.github.superisland.source.screenrecord.ScreenRecordingCaptureActivity { *; }
-keep class io.github.superisland.source.screenrecord.ScreenRecordingTileCaptureActivity { *; }
-keep class io.github.superisland.source.screenrecord.ScreenRecordingService { *; }
-keep class io.github.superisland.source.screenrecord.ScreenRecordingTileService { *; }
-keep class io.github.superisland.publisher.focus.FocusNotificationPublisher { *; }
