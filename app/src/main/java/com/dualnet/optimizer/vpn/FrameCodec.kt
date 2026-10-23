package com.dualnet.optimizer.vpn

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** DN1: unsigned big-endian 16-bit length, then payload. A zero length is a heartbeat. */
object FrameCodec {
    const val MAX_PACKET = 1500
    const val MAX_HANDSHAKE = 4096
    fun read(input: DataInputStream, maximum: Int = MAX_PACKET): ByteArray {
        val size = input.readUnsignedShort()
        if (size > maximum) throw IOException("Gateway frame exceeds negotiated limit")
        return ByteArray(size).also { input.readFully(it) }
    }
    fun write(output: DataOutputStream, payload: ByteArray) {
        require(payload.size <= MAX_HANDSHAKE)
        output.writeShort(payload.size); output.write(payload); output.flush()
    }
    fun validIpv4(packet: ByteArray): Boolean {
        if (packet.size < 20 || packet[0].toInt().ushr(4) and 15 != 4) return false
        val header = (packet[0].toInt() and 15) * 4
        val length = ((packet[2].toInt() and 255) shl 8) or (packet[3].toInt() and 255)
        return header >= 20 && header <= packet.size && length == packet.size
    }
}
