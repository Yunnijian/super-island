-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization public class io.github.superisland.hook.systemui.SuperIslandXposedModule {
    public <init>();
    public void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
}

-keep class * implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain);
}
