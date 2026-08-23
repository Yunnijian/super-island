package io.github.superisland.design

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import io.github.superisland.source.lyric.LyricIslandConfig
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
    private val hookPackages = setOf(
        "remix.myplayer", "com.apple.android.music", "cn.aqzscn.stream_music", "cn.wenyu.bodian",
        "org.akanework.gramophone", "com.heytap.music", "com.hiby.music", "com.hihonor.cloudmusic",
        "com.huawei.music", "org.kde.kdeconnect_tp", "com.kugou.android", "com.kugou.android.lite",
        "cn.kuwo.player", "com.lalilu.lmusic", "cn.toside.music.mobile", "com.meizu.media.music",
        "com.mimicry.mymusic", "com.miui.player", "cmccwm.mobilemusic", "fun.upup.musicfree",
        "com.netease.cloudmusic", "com.oppo.music", "com.maxmpz.audioplayer", "com.xuncorp.qinalt.music",
        "com.luna.music", "com.tencent.qqmusic", "com.r.rplayer", "com.salt.music",
        "com.xuncorp.suvine.music", "app.symfonik.music.player", "com.spotify.music",
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
            if (isLyriconProvider(appInfo, metadata)) {
                providers += LyricSupportApp(
                    packageName = info.packageName,
                    label = label,
                    versionName = versionName,
                    versionCode = versionCode,
                    usage = "Lyricon 歌词提供器",
                    providerAuthor = metadata?.getString("lyricon_module_author"),
                    providerCategory = metadata?.getString("lyricon_module_category"),
                    providerDescription = metadata?.getString("lyricon_module_description"),
                    providerTags = readTags(pm, appInfo, metadata),
                )
            }
            if (isSuperLyricApi(info.packageName, appInfo, metadata)) {
                apiApps += LyricSupportApp(info.packageName, label, versionName, versionCode, "SuperLyric API")
            }
            if (info.packageName in hookPackages) {
                hookApps += LyricSupportApp(info.packageName, label, versionName, versionCode, "SuperLyric Hook")
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

    private fun isSuperLyricApi(packageName: String, appInfo: ApplicationInfo, metadata: Bundle?): Boolean {
        if (metadata?.getBoolean("superlyricapi") != true || metadata.getBoolean("xposedmodule")) return false
        val source = appInfo.sourceDir ?: return true
        return runCatching {
            ZipFile(source).use { zip -> zip.entries().asSequence().none { it.name.startsWith("META-INF/xposed") } }
        }.getOrDefault(true)
    }

    private fun readTags(pm: PackageManager, appInfo: ApplicationInfo, metadata: Bundle?): List<String> {
        val resourceId = metadata?.getInt("lyricon_module_tags") ?: 0
        if (resourceId != 0) {
            return runCatching { pm.getResourcesForApplication(appInfo).getStringArray(resourceId).toList() }
                .getOrDefault(emptyList())
        }
        return metadata?.getString("lyricon_module_tags")?.let(::listOf).orEmpty()
    }
}

@Composable
fun rememberLyricSupportSnapshot(): LyricSupportSnapshot {
    val context = LocalContext.current.applicationContext
    val state by produceState(LyricSupportSnapshot(), context) {
        value = runCatching { LyricSupportCatalog.scan(context) }.getOrDefault(LyricSupportSnapshot(loaded = true))
    }
    return state
}

fun LyricSupportApp.displaySummary(): String = buildString {
    append("v").append(versionName).append(" (").append(versionCode).append(")")
    providerAuthor?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    providerCategory?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
}
