package com.dualnet.optimizer.network

import android.net.Network
import com.dualnet.optimizer.data.PacketProbeResult
import com.dualnet.optimizer.data.Preferences
import kotlinx.coroutines.*
import java.net.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Authenticated round-trip datagram loss to the optional DN1 gateway diagnostic port. */
object UdpDiagnostics {
    suspend fun probe(network: Network, config: Preferences): PacketProbeResult = withContext(Dispatchers.IO) {
        val destination = runCatching { network.getAllByName(config.serverHost).first() }.getOrNull()
            ?: return@withContext PacketProbeResult(0, 0, "Gateway DNS lookup failed")
        var sent = 0; var received = 0
        DatagramSocket(null).use { socket ->
            network.bindSocket(socket)
            socket.bind(InetSocketAddress(0)); socket.connect(destination, config.serverPort); socket.soTimeout = 800
            repeat(10) {
                ensureActive()
                val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val payload = ByteBuffer.allocate(28).put("DNP1".toByteArray()).putLong(System.currentTimeMillis()).put(nonce).array()
                val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(config.token.toByteArray(), "HmacSHA256")) }
                val packet = payload + mac.doFinal(payload)
                try {
                    socket.send(DatagramPacket(packet, packet.size)); sent++
                    val deadline = System.nanoTime() + 800_000_000
                    while (System.nanoTime() < deadline) {
                        val reply = DatagramPacket(ByteArray(128), 128)
                        socket.soTimeout = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1)
                        socket.receive(reply)
                        if (reply.length == packet.size && java.security.MessageDigest.isEqual(packet, reply.data.copyOf(reply.length))) { received++; break }
                    }
                } catch (_: java.io.IOException) { /* A timeout or ICMP error is a missing reply. */ }
                delay(100)
            }
        }
        PacketProbeResult(sent, received, if (received == 0) "No UDP replies. Check gateway firewall, token, and device clock." else null)
    }
}
