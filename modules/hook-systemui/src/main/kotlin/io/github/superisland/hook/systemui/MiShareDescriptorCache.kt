package io.github.superisland.hook.systemui

import android.content.pm.ApplicationInfo
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

internal data class MiShareApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val apkSha256: List<String>,
)

internal data class MiShareApkSet(
    val identity: MiShareApkIdentity,
    val files: List<File>,
) {
    companion object {
        fun from(
            applicationInfo: ApplicationInfo,
            versionCode: Long,
        ): MiShareApkSet {
            val packageName = applicationInfo.packageName.orEmpty().trim()
            require(packageName.isNotEmpty()) { "Mi Share package name is missing" }
            require(versionCode >= 0L) { "Mi Share version code is invalid" }
            val basePath = applicationInfo.sourceDir.orEmpty().trim()
            require(basePath.isNotEmpty()) { "Mi Share base APK path is missing" }
            val orderedPaths =
                listOf(basePath) +
                    applicationInfo.splitSourceDirs.orEmpty()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .sorted()
            require(orderedPaths.distinct().size == orderedPaths.size) {
                "Mi Share APK paths contain duplicates"
            }
            val files = orderedPaths.map(::File)
            require(files.all { file -> file.isFile && file.canRead() }) {
                "Mi Share APK set is incomplete or unreadable"
            }
            return MiShareApkSet(
                identity =
                    MiShareApkIdentity(
                        packageName = packageName,
                        versionCode = versionCode,
                        apkSha256 = files.map(::sha256),
                    ),
                files = files,
            )
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
    }
}

internal data class MiShareDescriptorCacheRecord(
    val resolverSchema: Int,
    val packageName: String,
    val versionCode: Long,
    val apkSha256: List<String>,
    val descriptor: String,
) {
    fun matches(identity: MiShareApkIdentity): Boolean =
        resolverSchema == MiShareDescriptorCache.RESOLVER_SCHEMA &&
            packageName == identity.packageName &&
            versionCode == identity.versionCode &&
            apkSha256 == identity.apkSha256
}

/** A strict Properties codec keeps cache parsing structured without introducing another JSON stack. */
internal object MiShareDescriptorCacheCodec {
    private const val MAX_CACHE_BYTES = 32 * 1_024
    private const val MAX_APK_COUNT = 64
    private const val MAX_DESCRIPTOR_LENGTH = 4_096
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    private val DESCRIPTOR_PATTERN = Regex("L[^;\\s]+;->[^\\s(]+\\([^)]*\\).+")

    fun encode(record: MiShareDescriptorCacheRecord): ByteArray {
        require(record.resolverSchema > 0)
        require(record.packageName.isNotBlank())
        require(record.versionCode >= 0L)
        require(record.apkSha256.isNotEmpty() && record.apkSha256.size <= MAX_APK_COUNT)
        require(record.apkSha256.all(SHA256_PATTERN::matches))
        require(record.descriptor.length <= MAX_DESCRIPTOR_LENGTH)
        require(DESCRIPTOR_PATTERN.matches(record.descriptor))
        val properties =
            Properties().apply {
                setProperty(KEY_SCHEMA, record.resolverSchema.toString())
                setProperty(KEY_PACKAGE, record.packageName)
                setProperty(KEY_VERSION, record.versionCode.toString())
                setProperty(KEY_APK_COUNT, record.apkSha256.size.toString())
                setProperty(KEY_DESCRIPTOR, record.descriptor)
                record.apkSha256.forEachIndexed { index, hash ->
                    setProperty(apkHashKey(index), hash)
                }
            }
        return ByteArrayOutputStream().use { output ->
            properties.store(output, null)
            output.toByteArray().also { bytes -> require(bytes.size <= MAX_CACHE_BYTES) }
        }
    }

    fun decodeOrNull(bytes: ByteArray): MiShareDescriptorCacheRecord? =
        runCatching {
            require(bytes.isNotEmpty() && bytes.size <= MAX_CACHE_BYTES)
            val properties = Properties().apply { load(ByteArrayInputStream(bytes)) }
            val apkCount = properties.getProperty(KEY_APK_COUNT)?.toIntOrNull()
            require(apkCount != null && apkCount in 1..MAX_APK_COUNT)
            val expectedKeys =
                buildSet {
                    add(KEY_SCHEMA)
                    add(KEY_PACKAGE)
                    add(KEY_VERSION)
                    add(KEY_APK_COUNT)
                    add(KEY_DESCRIPTOR)
                    repeat(apkCount) { index -> add(apkHashKey(index)) }
                }
            require(properties.stringPropertyNames() == expectedKeys)
            val resolverSchema = properties.getProperty(KEY_SCHEMA)?.toIntOrNull()
            val packageName = properties.getProperty(KEY_PACKAGE).orEmpty()
            val versionCode = properties.getProperty(KEY_VERSION)?.toLongOrNull()
            val descriptor = properties.getProperty(KEY_DESCRIPTOR).orEmpty()
            val hashes = List(apkCount) { index -> properties.getProperty(apkHashKey(index)).orEmpty() }
            require(resolverSchema != null && resolverSchema > 0)
            require(packageName.isNotBlank())
            require(versionCode != null && versionCode >= 0L)
            require(hashes.all(SHA256_PATTERN::matches))
            require(descriptor.length <= MAX_DESCRIPTOR_LENGTH)
            require(DESCRIPTOR_PATTERN.matches(descriptor))
            MiShareDescriptorCacheRecord(
                resolverSchema = resolverSchema,
                packageName = packageName,
                versionCode = versionCode,
                apkSha256 = hashes,
                descriptor = descriptor,
            )
        }.getOrNull()

    private fun apkHashKey(index: Int): String = "apk.$index.sha256"

    private const val KEY_SCHEMA = "resolverSchema"
    private const val KEY_PACKAGE = "packageName"
    private const val KEY_VERSION = "versionCode"
    private const val KEY_APK_COUNT = "apkCount"
    private const val KEY_DESCRIPTOR = "descriptor"
}

internal class MiShareDescriptorCache(applicationInfo: ApplicationInfo) {
    private val atomicFile =
        AtomicFile(
            File(
                requireNotNull(applicationInfo.dataDir) { "Mi Share data directory is missing" },
                "cache/superisland_dexkit/mishare-resolver.properties",
            ),
        )

    fun read(identity: MiShareApkIdentity): String? {
        val bytes =
            try {
                atomicFile.openRead().use(::readBounded)
            } catch (_: FileNotFoundException) {
                return null
            } catch (_: Throwable) {
                atomicFile.delete()
                return null
            }
        val record = MiShareDescriptorCacheCodec.decodeOrNull(bytes)
        if (record == null || !record.matches(identity)) {
            atomicFile.delete()
            return null
        }
        return record.descriptor
    }

    fun write(
        identity: MiShareApkIdentity,
        descriptor: String,
    ): Boolean {
        val bytes =
            runCatching {
                MiShareDescriptorCacheCodec.encode(
                    MiShareDescriptorCacheRecord(
                        resolverSchema = RESOLVER_SCHEMA,
                        packageName = identity.packageName,
                        versionCode = identity.versionCode,
                        apkSha256 = identity.apkSha256,
                        descriptor = descriptor,
                    ),
                )
            }.getOrNull() ?: return false
        atomicFile.baseFile.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs()) return false
        }
        var output: FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
            true
        } catch (_: Throwable) {
            output?.let { stream -> runCatching { atomicFile.failWrite(stream) } }
            false
        }
    }

    fun clear() {
        atomicFile.delete()
    }

    private fun readBounded(input: FileInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1_024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 32 * 1_024) { "Mi Share resolver cache is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val RESOLVER_SCHEMA = 1
    }
}
