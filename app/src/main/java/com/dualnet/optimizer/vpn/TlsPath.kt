package com.dualnet.optimizer.vpn

import android.net.Network
import android.net.VpnService
import com.dualnet.optimizer.data.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.*
import java.net.*
import javax.net.ssl.*

/** TLS uses the platform trust store AND HTTPS hostname validation. Never accepts arbitrary certificates. */
class TlsPath private constructor(val network: Network, val label: String, private val socket: SSLSocket) : Closeable {
    val outgoing = Channel<ByteArray>(256)
    private val input = DataInputStream(BufferedInputStream(socket.inputStream))
    private val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
    suspend fun pump(deliver: (ByteArray) -> Unit) = coroutineScope {
        launch(Dispatchers.IO) { for (packet in outgoing) FrameCodec.write(output, packet) }
        launch { while (isActive) { delay(5000); outgoing.send(byteArrayOf()) } }
        withContext(Dispatchers.IO) {
            while (isActive) {
                val packet = FrameCodec.read(input)
                if (packet.isNotEmpty()) { check(FrameCodec.validIpv4(packet)) { "Invalid gateway packet" }; deliver(packet) }
            }
        }
    }
    override fun close() { outgoing.close(); runCatching { socket.close() } }
    companion object {
        suspend fun open(vpn: VpnService, network: Network, label: String, preferences: Preferences, session: String): TlsPath = withContext(Dispatchers.IO) {
            val raw = Socket()
            try {
                check(vpn.protect(raw)) { "Unable to protect tunnel socket" }
                network.bindSocket(raw)
                raw.tcpNoDelay = true; raw.soTimeout = 15000
                val address = network.getAllByName(preferences.serverHost).firstOrNull() ?: error("Gateway DNS lookup failed")
                raw.connect(InetSocketAddress(address, preferences.serverPort), 8000)
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, preferences.serverHost, preferences.serverPort, true) as SSLSocket
                try {
                    tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    tls.enabledProtocols = tls.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
                    tls.startHandshake()
                    val hello = JSONObject().put("protocol", "DN1").put("token", preferences.token).put("session", session).put("path", label).put("mtu", preferences.mtu).toString().toByteArray()
                    FrameCodec.write(DataOutputStream(tls.outputStream), hello)
                    val response = JSONObject(FrameCodec.read(DataInputStream(tls.inputStream), FrameCodec.MAX_HANDSHAKE).toString(Charsets.UTF_8))
                    check(response.optBoolean("ok") && response.optString("protocol") == "DN1" && response.optString("address") == "10.77.0.2") { "Gateway authentication or protocol negotiation failed" }
                    TlsPath(network, label, tls)
                } catch (e: Exception) { tls.close(); throw e }
            } catch (e: Exception) { raw.close(); throw e }
        }
    }
}
