package com.dualnet.optimizer.vpn

import java.io.Closeable
import java.io.IOException
import java.net.Socket

/** Owns partially connected sockets so stopping the VPN also closes in-flight connect/TLS operations. */
class SocketRegistry : Closeable {
    private val sockets = mutableSetOf<Socket>()
    private var closed = false

    @Synchronized
    fun register(socket: Socket) {
        if (closed) {
            socket.close()
            throw IOException("VPN session is closed")
        }
        sockets.add(socket)
    }

    @Synchronized
    fun release(socket: Socket) { sockets.remove(socket) }

    @Synchronized
    override fun close() {
        closed = true
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
    }
}
