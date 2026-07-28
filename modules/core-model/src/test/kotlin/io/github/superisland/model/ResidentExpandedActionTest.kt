package io.github.superisland.model

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentExpandedActionTest {
    @Test
    fun roundTripPreservesOrderUnicodeLabelsTypesAndLaunchPackage() {
        val actions =
            listOf(
                ResidentExpandedAction(
                    id = "refresh-now",
                    label = "立即刷新",
                    type = ResidentExpandedActionType.REFRESH_NOW,
                ),
                ResidentExpandedAction(
                    id = "launch_scene",
                    label = "打开 Scene",
                    type = ResidentExpandedActionType.LAUNCH_APP,
                    targetPackage = "com.omarea.vtools",
                ),
                ResidentExpandedAction(
                    id = "battery.settings",
                    label = "电池设置",
                    type = ResidentExpandedActionType.OPEN_BATTERY_SETTINGS,
                ),
            )

        val encoded = ResidentExpandedActionCodec.encode(actions)

        assertTrue(encoded.startsWith("RA1\n"))
        assertEquals(actions, ResidentExpandedActionCodec.decodeOrEmpty(encoded))
        assertEquals("RA1", ResidentExpandedActionCodec.encode(emptyList()))
        assertEquals(emptyList<ResidentExpandedAction>(), ResidentExpandedActionCodec.decodeOrEmpty("RA1"))
        assertEquals(emptyList<ResidentExpandedAction>(), ResidentExpandedActionCodec.decodeOrEmpty(null))
    }

    @Test
    fun labelLimitCountsUnicodeCodePointsInsteadOfUtf16CodeUnits() {
        val eightEmoji = "😀".repeat(8)
        val nineEmoji = "😀".repeat(9)

        assertTrue(action(label = eightEmoji).isValid())
        assertFalse(action(label = nineEmoji).isValid())
        assertFalse(action(label = "刷新\n岛").isValid())
        assertFalse(action(label = "   ").isValid())
        assertFalse(action(label = "\uD83D").isValid())
    }

    @Test
    fun validatesStableIdsAndStrictLaunchPackageOwnership() {
        assertTrue(action(id = "550e8400-e29b-41d4-a716-446655440000").isValid())
        assertFalse(action(id = "contains space").isValid())
        assertFalse(action(id = "x".repeat(ResidentExpandedAction.MAX_ID_LENGTH + 1)).isValid())
        assertTrue(
            action(
                type = ResidentExpandedActionType.LAUNCH_APP,
                targetPackage = "com.example.app_2",
            ).isValid(),
        )
        assertFalse(action(type = ResidentExpandedActionType.LAUNCH_APP).isValid())
        assertFalse(
            action(
                type = ResidentExpandedActionType.LAUNCH_APP,
                targetPackage = "not-a-package",
            ).isValid(),
        )
        assertFalse(action(targetPackage = "com.example.unexpected").isValid())
        assertTrue(
            action(
                type = ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT,
                targetPackage = ResidentKnownShortcut.ALIPAY_PAY.id,
                label = "付款",
            ).isValid(),
        )
        assertFalse(
            action(
                type = ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT,
                targetPackage = "unknown_shortcut",
            ).isValid(),
        )
        assertFalse(
            action(
                type = ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT,
                targetPackage = "com.eg.android.AlipayGphone",
            ).isValid(),
        )
    }

    @Test
    fun knownShortcutCatalogContainsOnlyTheAuditedFixedEntries() {
        assertEquals(
            listOf(
                "alipay_pay",
                "alipay_scan",
                "alipay_collect",
                "alipay_shortcut_settings",
                "wechat_pay",
                "wechat_scan",
                "wechat_my_qr_code",
            ),
            ResidentKnownShortcut.entries.map(ResidentKnownShortcut::id),
        )
        assertEquals(
            setOf("com.eg.android.AlipayGphone", "com.tencent.mm"),
            ResidentKnownShortcut.entries.map(ResidentKnownShortcut::packageName).toSet(),
        )
    }

    @Test
    fun knownShortcutRoundTripAndFixedSlots() {
        val actions =
            listOf(
                ResidentExpandedAction(
                    id = ResidentExpandedAction.slotId(0),
                    label = "付款",
                    type = ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT,
                    targetPackage = ResidentKnownShortcut.ALIPAY_PAY.id,
                ),
                ResidentExpandedAction(
                    id = ResidentExpandedAction.slotId(2),
                    label = "扫一扫",
                    type = ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT,
                    targetPackage = ResidentKnownShortcut.WECHAT_SCAN.id,
                ),
            )
        val encoded = ResidentExpandedActionCodec.encode(actions)
        assertEquals(actions, ResidentExpandedActionCodec.decodeOrEmpty(encoded))

        val slots = ResidentExpandedAction.toSlots(actions)
        assertEquals(3, slots.size)
        assertEquals(actions[0], slots[0])
        assertEquals(null, slots[1])
        assertEquals(actions[1], slots[2])
        assertEquals(actions, ResidentExpandedAction.fromSlots(slots))
    }

    @Test
    fun encoderRejectsInvalidDuplicateAndOversizedActionSets() {
        assertTrue(runCatching { ResidentExpandedActionCodec.encode(listOf(action(label = ""))) }.isFailure)
        assertTrue(
            runCatching {
                ResidentExpandedActionCodec.encode(
                    listOf(action(id = "same"), action(id = "same", label = "设置")),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                ResidentExpandedActionCodec.encode(
                    (1..4).map { index -> action(id = "action-$index") },
                )
            }.isFailure,
        )
    }

    @Test
    fun decoderFailsClosedForMalformedNonCanonicalAndSemanticallyInvalidPayloads() {
        val valid = ResidentExpandedActionCodec.encode(listOf(action()))
        val record = valid.substringAfter('\n')
        val fields = record.split(':')
        val duplicate = "RA1\n$record\n$record"
        val unknownType = fields.toMutableList().also { it[2] = field("UNKNOWN") }.joinToString(":")
        val packageOnRefresh = fields.toMutableList().also { it[3] = field("com.example.app") }.joinToString(":")
        val invalidUtf8 = fields.toMutableList().also { it[1] = "_w" }.joinToString(":")

        listOf(
            "",
            "RA2",
            "$valid\n",
            valid.replaceFirst(fields[0], "${fields[0]}="),
            valid.replaceFirst(fields[0], "%${fields[0]}"),
            "RA1\n$record:extra",
            "RA1\n$unknownType",
            "RA1\n$packageOnRefresh",
            "RA1\n$invalidUtf8",
            duplicate,
            "x".repeat(2_049),
        ).forEach { malformed ->
            assertEquals(
                "Payload should fail closed: ${malformed.take(32)}",
                emptyList<ResidentExpandedAction>(),
                ResidentExpandedActionCodec.decodeOrEmpty(malformed),
            )
        }
    }

    private fun action(
        id: String = "refresh",
        label: String = "刷新",
        type: ResidentExpandedActionType = ResidentExpandedActionType.REFRESH_NOW,
        targetPackage: String? = null,
    ) = ResidentExpandedAction(id, label, type, targetPackage)

    private fun field(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}
