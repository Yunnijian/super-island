package io.github.superisland

import android.util.Log
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

data class XposedRuntimeStatus(
    val active: Boolean,
    val detail: String,
    val apiVersion: Int? = null,
)

/**
 * App-lifetime binder for the official libxposed service channel.
 *
 * A live service bind with API 102 and both runtime process scopes is the product-facing LSPosed
 * activation signal. Manager-package and filesystem heuristics are intentionally absent.
 */
object XposedRuntimeController {
    private const val TAG = "SuperIslandXposed"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"

    private val services = CopyOnWriteArraySet<XposedService>()
    private val listeners = CopyOnWriteArraySet<(XposedRuntimeStatus) -> Unit>()
    private var listenerRegistered = false

    private val serviceListener =
        object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                services += service
                removeRetiredSystemScope(service)
                Log.i(TAG, "libxposed service bound")
                publish()
            }

            override fun onServiceDied(service: XposedService) {
                services -= service
                Log.i(TAG, "libxposed service died")
                publish()
            }
        }

    @Synchronized
    fun start() {
        if (listenerRegistered) return
        XposedServiceHelper.registerListener(serviceListener)
        listenerRegistered = true
    }

    fun isActive(): Boolean = current().active

    fun current(): XposedRuntimeStatus =
        services
            .asSequence()
            .map(::inspect)
            .sortedByDescending(XposedRuntimeStatus::active)
            .firstOrNull()
            ?: XposedRuntimeStatus(
                active = false,
                detail = "LSPosed 模块未激活",
            )

    fun observe(listener: (XposedRuntimeStatus) -> Unit): () -> Unit {
        listeners += listener
        listener(current())
        return { listeners -= listener }
    }

    fun refresh() = publish()

    /**
     * Opens RemotePreferences only after API 102 and both runtime scopes have been verified.
     */
    fun remotePreferences(name: String): SharedPreferences? =
        services
            .asSequence()
            .firstOrNull { inspect(it).active }
            ?.let { service -> runCatching { service.getRemotePreferences(name) }.getOrNull() }

    private fun removeRetiredSystemScope(service: XposedService) {
        if (service.apiVersion < XposedService.API_102) return
        runCatching {
            if (RETIRED_SYSTEM_SCOPE in service.scope) {
                service.removeScope(listOf(RETIRED_SYSTEM_SCOPE))
                Log.i(TAG, "Removed retired Framework scope")
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not remove retired Framework scope", error)
        }
    }

    private fun inspect(service: XposedService): XposedRuntimeStatus =
        runCatching {
            when {
                service.apiVersion < XposedService.API_102 ->
                    XposedRuntimeStatus(
                        active = false,
                        detail = "LSPosed API 版本低于 102",
                        apiVersion = service.apiVersion,
                    )
                SYSTEM_UI_PACKAGE !in service.scope ->
                    XposedRuntimeStatus(
                        active = false,
                        detail = "模块作用域未包含 SystemUI",
                        apiVersion = service.apiVersion,
                    )
                XMSF_PACKAGE !in service.scope ->
                    XposedRuntimeStatus(
                        active = false,
                        detail = "模块作用域未包含 XMSF",
                        apiVersion = service.apiVersion,
                    )
                else ->
                    XposedRuntimeStatus(
                        active = true,
                        detail = "LSPosed 模块已激活",
                        apiVersion = service.apiVersion,
                    )
            }
        }.getOrElse {
            XposedRuntimeStatus(
                active = false,
                detail = "LSPosed 服务状态不可用",
            )
        }

    private fun publish() {
        val status = current()
        listeners.forEach { it(status) }
    }

    private const val RETIRED_SYSTEM_SCOPE = "system"
}
