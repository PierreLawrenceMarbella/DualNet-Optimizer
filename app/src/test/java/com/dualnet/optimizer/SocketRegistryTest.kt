package com.dualnet.optimizer

import com.dualnet.optimizer.vpn.SocketRegistry
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SocketRegistryTest {
    @Test fun shutdownInterruptsBlockedSocketRead() {
        val registry = SocketRegistry()
        ServerSocket(0).use { server ->
            Socket("127.0.0.1", server.localPort).use { client ->
                server.accept().use {
                    registry.register(client)
                    val reading = CountDownLatch(1); val stopped = CountDownLatch(1)
                    val worker = Thread {
                        reading.countDown()
                        try { client.getInputStream().read() } catch (_: IOException) { } finally { stopped.countDown() }
                    }.apply { isDaemon = true; start() }
                    assertTrue(reading.await(2, TimeUnit.SECONDS))
                    registry.close()
                    assertTrue("Disconnect must unblock transport I/O", stopped.await(2, TimeUnit.SECONDS))
                    worker.join(1000)
                    assertTrue(client.isClosed)
                }
            }
        }
    }
    @Test fun socketFinishingLateCannotEscapeShutdown() {
        val registry = SocketRegistry(); registry.close()
        val late = Socket()
        try { registry.register(late); fail("A closed VPN must reject new sockets") } catch (_: IOException) { assertTrue(late.isClosed) }
    }
    @Test fun releasingFinishedPathLeavesOtherPathsOwned() {
        val registry = SocketRegistry()
        val finished = Socket(); val active = Socket()
        registry.register(finished); registry.register(active)
        finished.close(); registry.release(finished)
        registry.close(); registry.close()
        assertTrue(active.isClosed)
    }
}
