package com.dualnet.optimizer

import com.dualnet.optimizer.data.UsagePeriod
import com.dualnet.optimizer.utils.*
import com.dualnet.optimizer.vpn.FrameCodec
import java.io.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun throughputUsesElapsedNanosecondsAndDecimalMegabits() {
        assertEquals(8.0, throughput(1_000_000, 1_000_000_000), 0.00001)
        assertEquals(0.0, throughput(100, 0), 0.0)
        assertEquals(0.0, throughput(-1, 100), 0.0)
    }
    @Test fun periodUsesLocalMidnightAcrossDaylightSaving() {
        val now = ZonedDateTime.parse("2026-03-09T12:30:00-04:00[America/New_York]")
        assertEquals(ZonedDateTime.parse("2026-03-09T00:00:00-04:00[America/New_York]").toInstant().toEpochMilli(), periodStart(UsagePeriod.WEEK, now))
        assertEquals(ZonedDateTime.parse("2026-03-01T00:00:00-05:00[America/New_York]").toInstant().toEpochMilli(), periodStart(UsagePeriod.MONTH, now))
    }
    @Test fun sundayWeekStartsOnPreviousMonday() {
        val now = ZonedDateTime.parse("2026-10-25T23:00:00+08:00[Asia/Manila]")
        assertEquals(ZonedDateTime.parse("2026-10-19T00:00:00+08:00[Asia/Manila]").toInstant().toEpochMilli(), periodStart(UsagePeriod.WEEK, now))
    }
    @Test fun nullMeasurementsAreNotInventedZeroes() {
        assertEquals("—", speed(null)); assertEquals("—", bytes(null)); assertEquals("0 B", bytes(0))
    }
    @Test fun rejectsUrlOrInjectedHostAndMalformedIpv4() {
        assertTrue(validHost("vpn.example.com")); assertTrue(validHost("192.168.1.2"))
        listOf("https://vpn.example.com", "bad host", "../server", "a..com", "host:443").forEach { assertFalse(validHost(it)) }
        assertTrue(validIpv4("1.1.1.1")); assertFalse(validIpv4("1.1.1.999")); assertFalse(validIpv4("1.1.1")); assertFalse(validIpv4("-1.1.1.1"))
    }
    @Test fun framesPreserveBinaryPacketsAndHeartbeat() {
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        val payload = byteArrayOf(0, -1, 42, 0)
        FrameCodec.write(output, payload); FrameCodec.write(output, byteArrayOf())
        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertArrayEquals(payload, FrameCodec.read(input)); assertEquals(0, FrameCodec.read(input).size)
    }
    @Test(expected = IOException::class) fun oversizedFrameRejectedBeforeAllocatingPayload() { FrameCodec.read(DataInputStream(ByteArrayInputStream(byteArrayOf(-1, -1)))) }
    @Test(expected = EOFException::class) fun truncatedFrameRejected() { FrameCodec.read(DataInputStream(ByteArrayInputStream(byteArrayOf(0, 4, 1)))) }
    @Test fun ipv4HeaderLengthAndPayloadLengthValidated() {
        val packet = ByteArray(24); packet[0] = 0x45; packet[3] = 24
        assertTrue(FrameCodec.validIpv4(packet))
        packet[3] = 25; assertFalse(FrameCodec.validIpv4(packet))
        packet[3] = 24; packet[0] = 0x4f; assertFalse(FrameCodec.validIpv4(packet))
        packet[0] = 0x60; assertFalse(FrameCodec.validIpv4(packet))
    }
}
