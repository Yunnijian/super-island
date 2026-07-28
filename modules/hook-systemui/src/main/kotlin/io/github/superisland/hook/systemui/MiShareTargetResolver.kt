package io.github.superisland.hook.systemui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod

internal enum class MiShareTargetResolutionSource(
    val logLabel: String,
) {
    EXACT("exact"),
    CACHE("cache"),
    DEXKIT("dexkit"),
}

internal data class MiShareTargetResolution(
    val method: Method,
    val source: MiShareTargetResolutionSource,
)

internal object MiShareTargetResolver {
    private const val TARGET_CLASS = "com.miui.mishare.view.d"
    private const val TARGET_METHOD = "g"
    private const val TAP_RECEIVE_DATA_CLASS = "com.miui.mishare.tap.TapRecvData"
    private const val SEARCH_PACKAGE = "com.miui.mishare"
    private const val ANCHOR_START_TRANSFER = "com.android.fileexplorer.tapShare.START_TRANSFER"
    private const val ANCHOR_OPEN = "miui.intent.action.OPEN"
    private const val ANCHOR_EXPLORER_PATH = "explorer_path"

    fun resolve(
        classLoader: ClassLoader,
        applicationInfo: ApplicationInfo,
        versionCode: Long,
        forceSkipExact: Boolean,
    ): MiShareTargetResolution {
        if (!forceSkipExact) {
            runCatching { resolveExact(classLoader) }
                .getOrNull()
                ?.let { method ->
                    return MiShareTargetResolution(method, MiShareTargetResolutionSource.EXACT)
                }
        }

        val apkSet = MiShareApkSet.from(applicationInfo, versionCode)
        val cache = MiShareDescriptorCache(applicationInfo)
        cache.read(apkSet.identity)?.let { descriptor ->
            val cached = runCatching { resolveDescriptor(classLoader, descriptor) }.getOrNull()
            if (cached != null) {
                return MiShareTargetResolution(cached, MiShareTargetResolutionSource.CACHE)
            }
            cache.clear()
        }

        val descriptor = MiShareDexKitScanner.findUniqueDescriptor(apkSet.files.map(FilePath::from))
        val resolved = resolveDescriptor(classLoader, descriptor)
        cache.write(apkSet.identity, descriptor)
        return MiShareTargetResolution(resolved, MiShareTargetResolutionSource.DEXKIT)
    }

    private fun resolveExact(classLoader: ClassLoader): Method {
        val targetClass = Class.forName(TARGET_CLASS, false, classLoader)
        return targetClass.declaredMethods.singleOrNull { method ->
            method.name == TARGET_METHOD && hasTargetSignature(method)
        }?.apply { isAccessible = true }
            ?: error("Mi Share received-folder Intent builder signature changed")
    }

    internal fun resolveDescriptor(
        classLoader: ClassLoader,
        descriptor: String,
    ): Method {
        val dexMethod = DexMethod.deserialize(descriptor)
        require(
            dexMethod.className == SEARCH_PACKAGE ||
                dexMethod.className.startsWith("$SEARCH_PACKAGE."),
        ) { "Resolved Mi Share descriptor escaped the bounded package" }
        val method = dexMethod.getMethodInstance(classLoader, true)
        require(method.declaringClass.name == dexMethod.className) {
            "Resolved Mi Share descriptor changed declaring class"
        }
        require(hasTargetSignature(method)) { "Resolved Mi Share method signature is invalid" }
        method.isAccessible = true
        return method
    }

    internal fun hasTargetSignature(method: Method): Boolean {
        val parameters = method.parameterTypes
        return Modifier.isStatic(method.modifiers) &&
            method.returnType == Intent::class.java &&
            parameters.size == 4 &&
            Context::class.java.isAssignableFrom(parameters[0]) &&
            parameters[1] == Boolean::class.javaPrimitiveType &&
            parameters[2].name == TAP_RECEIVE_DATA_CLASS &&
            parameters[3] == Boolean::class.javaPrimitiveType
    }

    private data class FilePath(
        val value: String,
    ) {
        companion object {
            fun from(file: java.io.File): FilePath = FilePath(file.absolutePath)
        }
    }

    private object MiShareDexKitScanner {
        @Volatile
        private var nativeLoaded = false

        fun findUniqueDescriptor(apkPaths: List<FilePath>): String {
            require(apkPaths.isNotEmpty()) { "Mi Share APK set is empty" }
            ensureNativeLoaded()
            val descriptors =
                buildList {
                    apkPaths.forEach { apkPath ->
                        DexKitBridge.create(apkPath.value).use { bridge ->
                            addAll(
                                bridge.findMethod {
                                    searchPackages(SEARCH_PACKAGE)
                                    findFirst = false
                                    matcher {
                                        modifiers = Modifier.STATIC
                                        returnType = "android.content.Intent"
                                        paramTypes(
                                            "android.content.Context",
                                            "boolean",
                                            TAP_RECEIVE_DATA_CLASS,
                                            "boolean",
                                        )
                                        usingEqStrings(
                                            ANCHOR_START_TRANSFER,
                                            ANCHOR_OPEN,
                                            ANCHOR_EXPLORER_PATH,
                                        )
                                    }
                                }.map { method -> method.descriptor },
                            )
                        }
                    }
                }
            return requireUniqueDescriptor(descriptors)
        }

        private fun ensureNativeLoaded() {
            if (nativeLoaded) return
            synchronized(this) {
                if (nativeLoaded) return
                System.loadLibrary("dexkit")
                nativeLoaded = true
            }
        }
    }

    internal fun requireUniqueDescriptor(descriptors: List<String>): String =
        descriptors.singleOrNull()
            ?: error("Mi Share DexKit query expected one result, found ${descriptors.size}")
}
