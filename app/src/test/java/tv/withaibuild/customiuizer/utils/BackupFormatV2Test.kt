package tv.withaibuild.customiuizer.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import tv.withaibuild.customiuizer.BuildConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatV2Test {

    @Test
    fun encodeDecodeRoundTripAllSupportedTypes() {
        val entries = linkedMapOf(
            "pref_key_bool" to true,
            "pref_key_int" to 42,
            "pref_key_long" to 1234567890123L,
            "pref_key_float" to 3.14f,
            "pref_key_string" to "hello world",
            "pref_key_set" to LinkedHashSet(listOf("com.a", "com.b")),
        )

        val encoded = BackupFormatV2.encode(entries)
        val decoded = BackupFormatV2.decode(encoded)

        assertEquals(entries.size, decoded.size)
        assertEquals(true, decoded["pref_key_bool"])
        assertEquals(42, decoded["pref_key_int"])
        assertEquals(1234567890123L, decoded["pref_key_long"])
        assertEquals(3.14f, decoded["pref_key_float"])
        assertEquals("hello world", decoded["pref_key_string"])
        @Suppress("UNCHECKED_CAST")
        val set = decoded["pref_key_set"] as Set<String>
        assertEquals(setOf("com.a", "com.b"), set)
    }

    @Test
    fun encodeIsDeterministic() {
        val entries = linkedMapOf(
            "pref_key_z" to "z",
            "pref_key_a" to "a",
            "pref_key_set" to LinkedHashSet(listOf("b", "a")),
        )

        val encoded1 = BackupFormatV2.encode(entries)
        val encoded2 = BackupFormatV2.encode(entries)

        assertArrayEquals(encoded1, encoded2)

        val decoded = BackupFormatV2.decode(encoded1)
        assertEquals(listOf("pref_key_a", "pref_key_set", "pref_key_z"), decoded.keys.toList())
        @Suppress("UNCHECKED_CAST")
        val set = decoded["pref_key_set"] as Set<String>
        assertEquals(listOf("a", "b"), set.toList())
    }

    @Test
    fun encodeHeaderLayoutIsBigEndian() {
        val entries = linkedMapOf("pref_key_bool" to true)
        val encoded = BackupFormatV2.encode(entries)

        val input = DataInputStream(ByteArrayInputStream(encoded))
        assertEquals(BackupFormatV2.MAGIC, input.readInt())
        assertEquals(BackupFormatV2.FORMAT_VERSION, input.readInt())
        assertEquals(BuildConfig.VERSION_CODE, input.readInt())
        assertEquals(1, input.readInt())
    }

    @Test
    fun decodeRejectsCrcHeaderMutation() {
        val entries = linkedMapOf("pref_key_test" to "value")
        val encoded = BackupFormatV2.encode(entries).copyOf()

        // Mutate a byte inside the CRC-covered header (not the magic).
        encoded[5] = (encoded[5].toInt() xor 1).toByte()

        try {
            BackupFormatV2.decode(encoded)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("CRC") == true)
        }
    }

    @Test
    fun decodeRejectsCrcFooterMutation() {
        val entries = linkedMapOf("pref_key_test" to "value")
        val encoded = BackupFormatV2.encode(entries).copyOf()

        // Mutate one of the final four CRC bytes.
        encoded[encoded.size - 1] = (encoded[encoded.size - 1].toInt() xor 1).toByte()

        try {
            BackupFormatV2.decode(encoded)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("CRC") == true)
        }
    }

    @Test
    fun decodeRejectsUnknownTypeTag() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(1)
        data.writeShort(4)
        data.write("test".toByteArray(StandardCharsets.UTF_8))
        data.writeByte(99)
        data.flush()

        try {
            BackupFormatV2.decode(payloadWithCrc(output.toByteArray()))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Unknown") == true)
        }
    }

    @Test
    fun decodeRejectsInvalidBooleanValue() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(1)
        data.writeShort(4)
        data.write("test".toByteArray(StandardCharsets.UTF_8))
        data.writeByte(BackupFormatV2.TYPE_BOOLEAN)
        data.writeByte(2)
        data.flush()

        try {
            BackupFormatV2.decode(payloadWithCrc(output.toByteArray()))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("boolean") == true)
        }
    }

    @Test
    fun decodeRejectsCorruptedPayload() {
        val entries = linkedMapOf("pref_key_test" to "value")
        val encoded = BackupFormatV2.encode(entries).copyOf()

        // Flip a bit in the middle of the payload.
        val idx = encoded.size / 2
        encoded[idx] = (encoded[idx].toInt() xor 1).toByte()

        try {
            BackupFormatV2.decode(encoded)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("CRC") == true)
        }
    }

    @Test
    fun decodeRejectsTruncatedFile() {
        val entries = linkedMapOf("pref_key_test" to "value")
        val encoded = BackupFormatV2.encode(entries)

        try {
            BackupFormatV2.decode(encoded.copyOfRange(0, 10))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("too short") == true)
        }
    }

    @Test
    fun decodeRejectsOversizedFile() {
        val tooBig = ByteArray(BackupFormatV2.MAX_FILE_SIZE.toInt() + 1)
        tooBig[0] = (BackupFormatV2.MAGIC ushr 24).toByte()
        tooBig[1] = (BackupFormatV2.MAGIC ushr 16).toByte()
        tooBig[2] = (BackupFormatV2.MAGIC ushr 8).toByte()
        tooBig[3] = BackupFormatV2.MAGIC.toByte()

        try {
            BackupFormatV2.decode(tooBig)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("too large") == true)
        }
    }

    @Test
    fun decodeRejectsUnknownMagic() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(0x12345678)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(0)
        data.flush()

        try {
            BackupFormatV2.decode(payloadWithCrc(output.toByteArray()))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("magic") == true)
        }
    }

    @Test
    fun decodeRejectsUnsupportedVersion() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(2)
        data.writeInt(0)
        data.writeInt(0)
        data.flush()

        try {
            BackupFormatV2.decode(payloadWithCrc(output.toByteArray()))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("version") == true)
        }
    }

    @Test
    fun decodeRejectsTrailingBytes() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(0)
        data.writeByte(99)
        data.flush()

        val payload = output.toByteArray()
        val crc = java.util.zip.CRC32()
        crc.update(payload, 0, payload.size)
        data.writeInt(crc.value.toInt())

        try {
            BackupFormatV2.decode(output.toByteArray())
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("trailing") == true)
        }
    }

    @Test
    fun decodeRejectsMalformedUtf8Key() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(1)
        data.writeShort(2)
        // Invalid multi-byte sequence: 0xC0 0x80 is an overlong encoding of U+0000.
        data.write(byteArrayOf(0xC0.toByte(), 0x80.toByte()))
        data.writeByte(BackupFormatV2.TYPE_STRING)
        data.writeInt(1)
        data.write(byteArrayOf('x'.code.toByte()))
        data.flush()

        try {
            BackupFormatV2.decode(payloadWithCrc(output.toByteArray()))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("UTF-8") == true)
        }
    }

    @Test
    fun decodeRejectsMalformedUtf8StringValue() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(1)
        data.writeShort(3)
        data.write("key".toByteArray(StandardCharsets.UTF_8))
        data.writeByte(BackupFormatV2.TYPE_STRING)
        data.writeInt(2)
        // Invalid multi-byte sequence.
        data.write(byteArrayOf(0xC0.toByte(), 0x80.toByte()))
        data.flush()

        try {
            BackupFormatV2.decode(payloadWithCrc(output.toByteArray()))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("UTF-8") == true)
        }
    }

    @Test
    fun decodeRejectsNegativeStringLength() {
        val entries = linkedMapOf("pref_key_test" to "value")
        val encoded = BackupFormatV2.encode(entries).copyOf()

        // Locate the value string length (after type tag 5, key, and type tag).
        // This is a fragile test byte offset, but it checks the negative-length branch.
        // It is simpler to construct a minimal malformed payload directly.
        val payload = ByteArrayOutputStream()
        val data = DataOutputStream(payload)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(1)
        data.writeShort(4)
        data.write("test".toByteArray(StandardCharsets.UTF_8))
        data.writeByte(BackupFormatV2.TYPE_STRING)
        data.writeInt(-1)
        data.flush()

        val crc = java.util.zip.CRC32()
        crc.update(payload.toByteArray(), 0, payload.size())
        data.writeInt(crc.value.toInt())

        try {
            BackupFormatV2.decode(payload.toByteArray())
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("negative") == true)
        }
    }

    @Test
    fun decodeRejectsKeyTooLong() {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeInt(BackupFormatV2.MAGIC)
        data.writeInt(BackupFormatV2.FORMAT_VERSION)
        data.writeInt(0)
        data.writeInt(1)
        data.writeShort(BackupFormatV2.MAX_KEY_BYTES + 1)
        data.write(ByteArray(BackupFormatV2.MAX_KEY_BYTES + 1))
        data.flush()

        try {
            BackupFormatV2.decode(payloadWithCrc(output.toByteArray()))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("key") == true)
        }
    }

    @Test
    fun encodeRejectsUnsupportedValueType() {
        try {
            BackupFormatV2.encode(linkedMapOf("key" to 1.5))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Unsupported") == true)
        }
    }

    @Test
    fun decodePreservesEmptyBackup() {
        val encoded = BackupFormatV2.encode(emptyMap())
        val decoded = BackupFormatV2.decode(encoded)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun encodeAcceptsMaxKeyLength() {
        val key = "k".repeat(BackupFormatV2.MAX_KEY_BYTES)
        val encoded = BackupFormatV2.encode(linkedMapOf(key to "v"))
        val decoded = BackupFormatV2.decode(encoded)
        assertEquals("v", decoded[key])
    }

    @Test
    fun encodeRejectsKeyTooLongByOne() {
        val key = "k".repeat(BackupFormatV2.MAX_KEY_BYTES + 1)
        try {
            BackupFormatV2.encode(linkedMapOf(key to "v"))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Key too long") == true)
        }
    }

    @Test
    fun encodeAcceptsMaxStringLength() {
        val value = "v".repeat(BackupFormatV2.MAX_STRING_BYTES)
        val encoded = BackupFormatV2.encode(linkedMapOf("key" to value))
        val decoded = BackupFormatV2.decode(encoded)
        assertEquals(value, decoded["key"])
    }

    @Test
    fun encodeRejectsStringTooLongByOne() {
        val value = "v".repeat(BackupFormatV2.MAX_STRING_BYTES + 1)
        try {
            BackupFormatV2.encode(linkedMapOf("key" to value))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("String too long") == true)
        }
    }

    @Test
    fun encodeAcceptsMaxSetItems() {
        val set = LinkedHashSet<String>().apply {
            repeat(BackupFormatV2.MAX_SET_ITEMS) { add("pkg$it") }
        }
        val encoded = BackupFormatV2.encode(linkedMapOf("key" to set))
        val decoded = BackupFormatV2.decode(encoded)
        @Suppress("UNCHECKED_CAST")
        val result = decoded["key"] as Set<String>
        assertEquals(BackupFormatV2.MAX_SET_ITEMS, result.size)
    }

    @Test
    fun encodeRejectsSetTooLargeByOne() {
        val set = LinkedHashSet<String>().apply {
            repeat(BackupFormatV2.MAX_SET_ITEMS + 1) { add("pkg$it") }
        }
        try {
            BackupFormatV2.encode(linkedMapOf("key" to set))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("StringSet too large") == true)
        }
    }

    @Test
    fun encodeAcceptsMaxEntryCount() {
        val entries = linkedMapOf<String, Any?>()
        repeat(BackupFormatV2.MAX_ENTRY_COUNT) { entries["pref_key_$it"] = true }
        val encoded = BackupFormatV2.encode(entries)
        val decoded = BackupFormatV2.decode(encoded)
        assertEquals(BackupFormatV2.MAX_ENTRY_COUNT, decoded.size)
    }

    @Test
    fun encodeRejectsEntryCountTooLargeByOne() {
        val entries = linkedMapOf<String, Any?>()
        repeat(BackupFormatV2.MAX_ENTRY_COUNT + 1) { entries["pref_key_$it"] = true }
        try {
            BackupFormatV2.encode(entries)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Entry count") == true)
        }
    }

    @Test
    fun encodeAcceptsExactMaxFileSize() {
        val (entries, _) = buildMaxFileSizeEntries()

        val encoded = BackupFormatV2.encode(entries)
        assertEquals(BackupFormatV2.MAX_FILE_SIZE.toInt(), encoded.size)
        val decoded = BackupFormatV2.decode(encoded)
        assertEquals(32, decoded.size)
        assertEquals(BackupFormatV2.MAX_STRING_BYTES, (decoded["k000"] as String).length)
    }

    @Test
    fun encodeRejectsMaxFileSizePlusOne() {
        val (entries, lastStringSize) = buildMaxFileSizeEntries()
        // Increase the last String by exactly one UTF-8 byte.
        entries["last_key"] = "b".repeat((lastStringSize + 1).toInt())

        try {
            BackupFormatV2.encode(entries)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("V2 encoded size") == true)
            assertTrue(e.message?.contains("exceeds") == true)
        }
    }

    private fun buildMaxFileSizeEntries(): Pair<MutableMap<String, Any?>, Long> {
        // 31 full-size string entries plus one smaller entry to hit MAX_FILE_SIZE exactly.
        val entries = linkedMapOf<String, Any?>()
        val fullString = "a".repeat(BackupFormatV2.MAX_STRING_BYTES)
        repeat(31) { entries["k" + it.toString().padStart(3, '0')] = fullString }
        val perEntryOverhead = 2L + 4L + 1L + 4L // key-length + key + type tag + string-length
        val lastKeyBytes = 8L
        val lastOverhead = 2L + lastKeyBytes + 1L + 4L
        val lastStringSize = BackupFormatV2.MAX_FILE_SIZE -
            20L - // header + CRC
            31L * (perEntryOverhead + BackupFormatV2.MAX_STRING_BYTES) -
            lastOverhead
        entries["last_key"] = "b".repeat(lastStringSize.toInt())
        return Pair(entries, lastStringSize)
    }

    private fun payloadWithCrc(payload: ByteArray): ByteArray {
        val crc = java.util.zip.CRC32()
        crc.update(payload, 0, payload.size)
        val result = ByteArray(payload.size + 4)
        System.arraycopy(payload, 0, result, 0, payload.size)
        val value = crc.value.toInt()
        result[payload.size + 0] = (value ushr 24).toByte()
        result[payload.size + 1] = (value ushr 16).toByte()
        result[payload.size + 2] = (value ushr 8).toByte()
        result[payload.size + 3] = value.toByte()
        return result
    }

    @Test
    fun encodeRejectsMalformedKeyUtf16() {
        try {
            BackupFormatV2.encode(linkedMapOf("k\uD800x" to "v"))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Malformed or unmappable") == true)
        }
    }

    @Test
    fun encodeRejectsMalformedStringValueUtf16() {
        try {
            BackupFormatV2.encode(linkedMapOf("key" to "value\uD800"))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Malformed or unmappable") == true)
        }
    }

    @Test
    fun encodeRejectsMalformedStringSetItemUtf16() {
        try {
            BackupFormatV2.encode(linkedMapOf("key" to LinkedHashSet(listOf("pkg\uDC00"))))
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Malformed or unmappable") == true)
        }
    }

    @Test
    fun encodeDecodeRoundTripValidNonBmp() {
        val key = "键\uD83D\uDE00"
        val value = "值\uD83D\uDE80"
        val set = LinkedHashSet(listOf("应用\uD83D\uDE00", "应用\uD83D\uDE80"))

        val entries = linkedMapOf(
            key to value,
            "nonbmp_set" to set
        )

        val encoded = BackupFormatV2.encode(entries)
        val decoded = BackupFormatV2.decode(encoded)

        assertEquals(value, decoded[key])
        @Suppress("UNCHECKED_CAST")
        val resultSet = decoded["nonbmp_set"] as Set<String>
        assertEquals(set, resultSet)
    }
}
