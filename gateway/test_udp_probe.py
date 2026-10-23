import asyncio
import hashlib
import hmac
import os
import struct
import time
import unittest
from udp_probe import ProbeServer, valid_probe

TOKEN = "test-token-" + "b" * 48

def probe(timestamp=None):
    data = b"DNP1" + struct.pack("!q", int((time.time() if timestamp is None else timestamp) * 1000)) + os.urandom(16)
    return data + hmac.new(TOKEN.encode(), data, hashlib.sha256).digest()


class UdpTests(unittest.IsolatedAsyncioTestCase):
    async def test_real_udp_echo_preserves_authenticated_probe(self):
        loop = asyncio.get_running_loop()
        server, _ = await loop.create_datagram_endpoint(lambda: ProbeServer(TOKEN), local_addr=("127.0.0.1", 0))
        result = loop.create_future()
        class Client(asyncio.DatagramProtocol):
            def datagram_received(self, data, addr):
                if not result.done(): result.set_result(data)
        client, _ = await loop.create_datagram_endpoint(Client, remote_addr=server.get_extra_info("sockname"))
        try:
            data = probe(); client.sendto(data)
            self.assertEqual(await asyncio.wait_for(result, 1), data)
        finally: client.close(); server.close()

    async def test_expired_forged_and_oversized_probes_rejected(self):
        self.assertTrue(valid_probe(probe(), TOKEN))
        self.assertFalse(valid_probe(probe(time.time() - 60), TOKEN))
        self.assertFalse(valid_probe(probe(), "wrong-token"))
        self.assertFalse(valid_probe(probe() + b"x", TOKEN))
        self.assertFalse(valid_probe(b"random", TOKEN))
