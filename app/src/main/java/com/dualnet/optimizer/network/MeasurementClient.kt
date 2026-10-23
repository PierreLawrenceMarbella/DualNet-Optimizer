package com.dualnet.optimizer.network

import android.net.Network
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.utils.throughput
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Network.openConnection binds each request and its DNS lookup to the chosen path. */
class MeasurementClient {
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    fun cancel() { connections.forEach { it.disconnect() }; connections.clear() }
    private fun connection(network: Network?, url: String): HttpURLConnection {
        val endpoint = URL(url)
        return ((network?.openConnection(endpoint) ?: endpoint.openConnection()) as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000; useCaches = false; instanceFollowRedirects = false
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Accept-Encoding", "identity")
            connections.add(this)
        }
    }
    private fun close(connection: HttpURLConnection) { connections.remove(connection); connection.disconnect() }
    suspend fun probe(network: Network?, count: Int = 5): ProbeResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Double>()
        repeat(count) {
            ensureActive()
            val c = connection(network, "https://speed.cloudflare.com/__down?bytes=0&nonce=${System.nanoTime()}")
            try {
                val start = System.nanoTime()
                if (c.responseCode !in 200..299) error("Probe rejected")
                c.inputStream.use { it.readBytes() }
                samples += (System.nanoTime() - start) / 1e6
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* A failed application probe, not an ICMP loss measurement. */ }
            finally { close(c) }
            delay(120)
        }
        val sorted = samples.sorted()
        ProbeResult(sorted.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }, if (samples.size > 1) samples.zipWithNext { a, b -> abs(a - b) }.average() else null, count - samples.size, count)
    }
    suspend fun speedTest(network: Network?, route: String, update: (SpeedTestState) -> Unit) = withContext(Dispatchers.IO) {
        var state = SpeedTestState(running = true, phase = "Measuring latency…", route = route)
        update(state)
        val probe = probe(network)
        check(probe.latency != null) { "The test endpoint did not respond on this connection." }
        state = state.copy(ping = probe.latency, jitter = probe.jitter, phase = "Measuring download…")
        update(state)
        var total = 0L; var elapsed = 0L
        repeat(3) {
            ensureActive()
            val c = connection(network, "https://speed.cloudflare.com/__down?bytes=4000000&nonce=${System.nanoTime()}")
            try {
                val start = System.nanoTime()
                check(c.responseCode in 200..299) { "Download endpoint returned HTTP ${c.responseCode}." }
                var received = 0L; var lastUi = start
                c.inputStream.use { input ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer); if (read < 0) break
                        received += read
                        val now = System.nanoTime()
                        if (now - lastUi > 150_000_000) { update(state.copy(current = throughput(total + received, elapsed + now - start))); lastUi = now }
                        check(received <= 4_100_000) { "Unexpected download response size." }
                    }
                }
                check(received == 4_000_000L) { "The download was incomplete. Please retry." }
                total += received; elapsed += System.nanoTime() - start
            } finally { close(c) }
        }
        state = state.copy(download = throughput(total, elapsed), phase = "Measuring upload…", current = 0.0)
        update(state)
        total = 0; elapsed = 0
        val payload = ByteArray(32768).also { SecureRandom().nextBytes(it) }
        repeat(3) {
            ensureActive()
            val c = connection(network, "https://speed.cloudflare.com/__up")
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(4_000_000)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                val start = System.nanoTime(); var sent = 0; var lastUi = start
                c.outputStream.use { output ->
                    while (sent < 4_000_000) {
                        ensureActive()
                        val amount = minOf(payload.size, 4_000_000 - sent)
                        output.write(payload, 0, amount); sent += amount
                        val now = System.nanoTime()
                        if (now - lastUi > 150_000_000) { update(state.copy(current = throughput(total + sent, elapsed + now - start))); lastUi = now }
                    }
                }
                check(c.responseCode in 200..299) { "Upload endpoint returned HTTP ${c.responseCode}." }
                c.inputStream.use { it.readBytes() }
                total += sent; elapsed += System.nanoTime() - start
            } finally { close(c) }
        }
        val upload = throughput(total, elapsed)
        update(state.copy(running = false, phase = "Test complete", current = state.download ?: 0.0, upload = upload))
    }
}
