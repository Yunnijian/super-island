package io.github.superisland.design

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.LyricSourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

/** Installed source integrations shown by HyperLyric's basic source pages. */
data class LyricSupportApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val usage: String,
    val packageInfo: PackageInfo? = null,
    val lastUpdateTime: Long = 0L,
    val apiVersionName: String? = null,
    val apiVersionCode: String? = null,
    val providerAuthor: String? = null,
    val providerCategory: String? = null,
    val providerDescription: String? = null,
    val providerTags: List<String> = emptyList(),
)

data class LyricSupportSnapshot(
    val lyriconProviders: List<LyricSupportApp> = emptyList(),
    val superLyricApiApps: List<LyricSupportApp> = emptyList(),
    val superLyricHookApps: List<LyricSupportApp> = emptyList(),
    val lyriconCentralInstalled: Boolean = false,
    val superLyricInstalled: Boolean = false,
    val loaded: Boolean = false,
)

object LyricSupportCatalog {
    private const val SUPER_LYRIC_PACKAGE = "com.hchen.superlyric"
    private const val ICON_CACHE_SIZE_KB = 8 * 1024
    private val iconCache = object : LruCache<String, ImageBitmap>(ICON_CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    private val hookUsage = mapOf(
        "remix.myplayer" to "使用方法：开启“状态栏歌词”功能即可",
        "com.apple.android.music" to "使用方法：无需额外设置",
        "cn.aqzscn.stream_music" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "cn.wenyu.bodian" to "使用方法：开启“状态栏歌词”功能",
        "org.akanework.gramophone" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "com.heytap.music" to "使用方法：开启“蓝牙歌词”功能即可",
        "com.hiby.music" to "使用方法：未知",
        "com.hihonor.cloudmusic" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "com.huawei.music" to "使用方法：开启“蓝牙歌词”功能",
        "org.kde.kdeconnect_tp" to "使用方法：开启“蓝牙歌词”功能",
        "com.kugou.android" to "使用方法：无需额外设置",
        "com.kugou.android.lite" to "使用方法：无需额外设置\n3.0.1 和 5.1.5 版本支持逐字",
        "cn.kuwo.player" to "使用方法：开启“蓝牙歌词”功能",
        "com.lalilu.lmusic" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "cn.toside.music.mobile" to "使用方法：开启“桌面歌词”功能",
        "com.meizu.media.music" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "com.mimicry.mymusic" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "com.miui.player" to "使用方法：开启“车载蓝牙歌词”或“通知栏歌词”功能",
        "cmccwm.mobilemusic" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "fun.upup.musicfree" to "使用方法：开启“桌面歌词”功能",
        "com.netease.cloudmusic" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "com.oppo.music" to "使用方法：开启“蓝牙歌词”功能即可",
        "com.maxmpz.audioplayer" to "使用方法：未知",
        "com.xuncorp.qinalt.music" to "使用方法：开启“蓝牙歌词”功能",
        "com.luna.music" to "使用方法：开启“蓝牙歌词”功能",
        "com.tencent.qqmusic" to "使用方法：开启“状态栏歌词”功能",
        "com.r.rplayer" to "使用方法：开启“蓝牙歌词”功能",
        "com.salt.music" to "使用方法：无需额外设置",
        "com.xuncorp.suvine.music" to "使用方法：开启“魅族状态栏歌词”功能即可",
        "app.symfonik.music.player" to "使用方法：无需额外设置",
        "com.spotify.music" to "使用方法：无需额外设置",
    )

    private data class SuperLyricApiVersion(
        val name: String,
        val code: String,
    )

    suspend fun scan(context: Context): LyricSupportSnapshot = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val providers = mutableListOf<LyricSupportApp>()
        val apiApps = mutableListOf<LyricSupportApp>()
        val hookApps = mutableListOf<LyricSupportApp>()
        var centralInstalled = false
        var superLyricInstalled = false
        packages.forEach { info ->
            if (info.packageName == SUPER_LYRIC_PACKAGE) superLyricInstalled = true
            if (info.packageName.contains("lyricon", ignoreCase = true) && info.packageName != context.packageName) {
                centralInstalled = true
            }
            val appInfo = info.applicationInfo ?: return@forEach
            val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(info.packageName)
            val versionName = info.versionName ?: "未知"
            val versionCode = runCatching {
                @Suppress("DEPRECATION")
                info.longVersionCode
            }.getOrDefault(0L)
            val metadata = appInfo.metaData
            fun supportApp(
                usage: String,
                apiVersion: SuperLyricApiVersion? = null,
                providerAuthor: String? = null,
                providerCategory: String? = null,
                providerDescription: String? = null,
                providerTags: List<String> = emptyList(),
            ) = LyricSupportApp(
                packageName = info.packageName,
                label = label,
                versionName = versionName,
                versionCode = versionCode,
                usage = usage,
                packageInfo = info,
                lastUpdateTime = info.lastUpdateTime,
                apiVersionName = apiVersion?.name,
                apiVersionCode = apiVersion?.code,
                providerAuthor = providerAuthor,
                providerCategory = providerCategory,
                providerDescription = providerDescription,
                providerTags = providerTags,
            )
            if (isLyriconProvider(appInfo, metadata)) {
                providers += supportApp(
                    usage = "Lyricon 歌词提供器",
                    providerAuthor = metadata?.getString("lyricon_module_author"),
                    providerCategory = metadata?.getString("lyricon_module_category"),
                    providerDescription = metadata?.getString("lyricon_module_description"),
                    providerTags = readTags(pm, appInfo, metadata),
                )
            }
            superLyricApiVersion(appInfo, metadata)?.let { apiVersion ->
                apiApps += supportApp("使用方法：API 原生支持", apiVersion)
            }
            hookUsage[info.packageName]?.let { usage ->
                hookApps += supportApp(usage)
            }
        }
        LyricSupportSnapshot(
            lyriconProviders = providers.sortedBy { it.label.lowercase() },
            superLyricApiApps = apiApps.sortedBy { it.label.lowercase() },
            superLyricHookApps = hookApps.sortedBy { it.label.lowercase() },
            lyriconCentralInstalled = centralInstalled,
            superLyricInstalled = superLyricInstalled,
            loaded = true,
        )
    }

    private fun isLyriconProvider(appInfo: ApplicationInfo, metadata: Bundle?): Boolean {
        val system = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return !system && metadata?.getBoolean("lyricon_module") == true
    }

    private fun superLyricApiVersion(
        appInfo: ApplicationInfo,
        metadata: Bundle?,
    ): SuperLyricApiVersion? {
        if (metadata?.getBoolean("superlyricapi") != true || metadata.getBoolean("xposedmodule")) return null
        val source = appInfo.sourceDir
        if (!source.isNullOrBlank() && hasXposedModule(source)) return null
        return SuperLyricApiVersion(
            name = metadata.getFloat("superlyricapi_version_name").toString(),
            code = metadata.getInt("superlyricapi_version_code").toString(),
        )
    }

    private fun hasXposedModule(apkPath: String): Boolean = runCatching {
        ZipFile(apkPath).use { zip -> zip.entries().asSequence().any { it.name.startsWith("META-INF/xposed") } }
    }.getOrDefault(false)

    fun cachedIcon(app: LyricSupportApp, targetSizePx: Int): ImageBitmap? =
        iconCache.get(iconCacheKey(app, targetSizePx))

    suspend fun loadIcon(
        context: Context,
        app: LyricSupportApp,
        targetSizePx: Int,
    ): ImageBitmap? {
        cachedIcon(app, targetSizePx)?.let { return it }
        return withContext(Dispatchers.IO) {
            cachedIcon(app, targetSizePx)?.let { return@withContext it }
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
                    .apply { prepareToDraw() }
                    .asImageBitmap()
                    .also { iconCache.put(iconCacheKey(app, targetSizePx), it) }
            }.getOrNull()
        }
    }

    private fun readTags(pm: PackageManager, appInfo: ApplicationInfo, metadata: Bundle?): List<String> {
        val resourceId = metadata?.getInt("lyricon_module_tags") ?: 0
        if (resourceId != 0) {
            return runCatching { pm.getResourcesForApplication(appInfo).getStringArray(resourceId).toList() }
                .getOrDefault(emptyList())
        }
        return metadata?.getString("lyricon_module_tags")?.let(::listOf).orEmpty()
    }

    private fun iconCacheKey(app: LyricSupportApp, targetSizePx: Int): String =
        "${app.packageName}:${app.lastUpdateTime}:$targetSizePx"
}

@Composable
fun rememberLyricSupportSnapshot(): LyricSupportSnapshot {
    val context = LocalContext.current.applicationContext
    val state by produceState(LyricSupportSnapshot(), context) {
        value = runCatching { LyricSupportCatalog.scan(context) }.getOrDefault(LyricSupportSnapshot(loaded = true))
    }
    return state
}

@Composable
fun rememberLyricSupportAppIcon(app: LyricSupportApp): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val targetSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val cacheKey = "${app.packageName}:${app.lastUpdateTime}:$targetSizePx"
    val icon by produceState(
        initialValue = LyricSupportCatalog.cachedIcon(app, targetSizePx),
        key1 = cacheKey,
    ) {
        if (value == null) value = LyricSupportCatalog.loadIcon(context, app, targetSizePx)
    }
    return icon
}

fun LyricSupportApp.displaySummary(): String = buildString {
    append("v").append(apiVersionName ?: versionName).append(" (").append(apiVersionCode ?: versionCode).append(")")
    providerAuthor?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    providerCategory?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
}

fun LyricSourceMode.sourcePromptSummary(): String = when (this) {
    LyricSourceMode.SUPER_LYRIC ->
        "只需要安装并启用 SuperLyric。首次注入后重启手机。SuperLyric 不支持“显示下一句歌词”和“AI 翻译”。如果没有歌词，先检查它是否向音乐软件传出了歌词，以及版本是否兼容。"
    LyricSourceMode.LYRIC_INFO ->
        "建议安装 LyricInfo 模块，将歌词写入系统 MediaSession 元数据；如果音乐软件自身能够输出歌词元数据，则可以不安装。"
    LyricSourceMode.LYRICON,
    LyricSourceMode.MEDIA_FALLBACK,
    ->
        "HyperLyric 6.0 及之后的版本需要同时安装 Lyricon Central 和对应音乐软件的 LyricProvider。启用相关模块后，重启系统界面和音乐软件。如果没有歌词，先检查歌词提供器与音乐软件版本是否兼容，再确认是否完成重启。"
}
