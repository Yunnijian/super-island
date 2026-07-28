package io.github.superisland.hook.systemui

import android.content.Context
import android.content.Intent
import com.miui.mishare.tap.TapRecvData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiShareTargetResolverTest {
    @Test
    fun `cache record round trips and matches only the exact APK identity`() {
        val identity =
            MiShareApkIdentity(
                packageName = "com.miui.mishare.connectivity",
                versionCode = 4_013_007L,
                apkSha256 = listOf(HASH_A, HASH_B),
            )
        val record =
            MiShareDescriptorCacheRecord(
                resolverSchema = MiShareDescriptorCache.RESOLVER_SCHEMA,
                packageName = identity.packageName,
                versionCode = identity.versionCode,
                apkSha256 = identity.apkSha256,
                descriptor = TARGET_DESCRIPTOR,
            )

        val decoded = MiShareDescriptorCacheCodec.decodeOrNull(MiShareDescriptorCacheCodec.encode(record))

        assertEquals(record, decoded)
        assertTrue(decoded!!.matches(identity))
        assertFalse(decoded.matches(identity.copy(versionCode = identity.versionCode + 1)))
        assertFalse(decoded.matches(identity.copy(apkSha256 = listOf(HASH_A, HASH_C))))
        assertFalse(decoded.copy(resolverSchema = 99).matches(identity))
    }

    @Test
    fun `corrupt unknown and malformed cache fields are rejected`() {
        val record =
            MiShareDescriptorCacheRecord(
                resolverSchema = MiShareDescriptorCache.RESOLVER_SCHEMA,
                packageName = "com.miui.mishare.connectivity",
                versionCode = 1L,
                apkSha256 = listOf(HASH_A),
                descriptor = TARGET_DESCRIPTOR,
            )
        val encoded = MiShareDescriptorCacheCodec.encode(record)

        assertNull(MiShareDescriptorCacheCodec.decodeOrNull(byteArrayOf(0, 1, 2, 3)))
        assertNull(
            MiShareDescriptorCacheCodec.decodeOrNull(
                encoded + "\nunexpected=true\n".toByteArray(),
            ),
        )
        assertNull(
            MiShareDescriptorCacheCodec.decodeOrNull(
                encoded.toString(Charsets.ISO_8859_1)
                    .replace(TARGET_DESCRIPTOR, "not-a-descriptor")
                    .toByteArray(Charsets.ISO_8859_1),
            ),
        )
    }

    @Test
    fun `DexKit result must be globally unique`() {
        assertEquals(
            TARGET_DESCRIPTOR,
            MiShareTargetResolver.requireUniqueDescriptor(listOf(TARGET_DESCRIPTOR)),
        )
        assertTrue(
            runCatching { MiShareTargetResolver.requireUniqueDescriptor(emptyList()) }.isFailure,
        )
        assertTrue(
            runCatching {
                MiShareTargetResolver.requireUniqueDescriptor(
                    listOf(TARGET_DESCRIPTOR, TARGET_DESCRIPTOR.replace("->g", "->h")),
                )
            }.isFailure,
        )
    }

    @Test
    fun `resolved method must keep the complete audited signature`() {
        val valid =
            ResolverSignatureFixtures::class.java.getDeclaredMethod(
                "valid",
                Context::class.java,
                Boolean::class.javaPrimitiveType,
                TapRecvData::class.java,
                Boolean::class.javaPrimitiveType,
            )
        val wrongReturn =
            ResolverSignatureFixtures::class.java.getDeclaredMethod(
                "wrongReturn",
                Context::class.java,
                Boolean::class.javaPrimitiveType,
                TapRecvData::class.java,
                Boolean::class.javaPrimitiveType,
            )
        val nonStatic =
            ResolverSignatureFixtures::class.java.getDeclaredMethod(
                "nonStatic",
                Context::class.java,
                Boolean::class.javaPrimitiveType,
                TapRecvData::class.java,
                Boolean::class.javaPrimitiveType,
            )

        assertTrue(MiShareTargetResolver.hasTargetSignature(valid))
        assertFalse(MiShareTargetResolver.hasTargetSignature(wrongReturn))
        assertFalse(MiShareTargetResolver.hasTargetSignature(nonStatic))
    }

    @Test
    fun `cached descriptor cannot escape the bounded Mi Share package`() {
        val descriptor =
            "Lio/github/superisland/hook/systemui/MiShareTargetResolverTest\$ResolverSignatureFixtures;" +
                "->valid(Landroid/content/Context;ZLcom/miui/mishare/tap/TapRecvData;Z)Landroid/content/Intent;"

        assertTrue(
            runCatching {
                MiShareTargetResolver.resolveDescriptor(javaClass.classLoader!!, descriptor)
            }.isFailure,
        )
    }

    private class ResolverSignatureFixtures {
        fun nonStatic(
            context: Context,
            first: Boolean,
            data: TapRecvData,
            second: Boolean,
        ): Intent = error("reflection only: $context $first $data $second")

        companion object {
            @JvmStatic
            fun valid(
                context: Context,
                first: Boolean,
                data: TapRecvData,
                second: Boolean,
            ): Intent = error("reflection only: $context $first $data $second")

            @JvmStatic
            fun wrongReturn(
                context: Context,
                first: Boolean,
                data: TapRecvData,
                second: Boolean,
            ): String = error("reflection only: $context $first $data $second")
        }
    }

    private companion object {
        const val TARGET_DESCRIPTOR =
            "Lcom/miui/mishare/view/d;->g(Landroid/content/Context;ZLcom/miui/mishare/tap/TapRecvData;Z)Landroid/content/Intent;"
        const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val HASH_C = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    }
}
