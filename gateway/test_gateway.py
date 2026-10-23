import asyncio
import ipaddress
import json
import struct
import unittest
import uuid
from gateway import Gateway, Path, encode_frame, read_frame, valid_packet

TOKEN = "unit-test-token-" + "a" * 48


def packet(source="10.77.0.2", destination="1.1.1.1", body=b"hello"):
    return struct.pack("!BBHHHBBH4s4s", 0x45, 0, 20 + len(body), 0, 0, 64, 17, 0,
                       ipaddress.ip_address(source).packed, ipaddress.ip_address(destination).packed) + body


class Sink:
    def __init__(self): self.packets = []
    def write(self, data): self.packets.append(data)


class ProtocolTests(unittest.IsolatedAsyncioTestCase):
    async def test_fragmented_frame_and_oversized_rejection(self):
        reader = asyncio.StreamReader()
        reader.feed_data(b"\x00")
        task = asyncio.create_task(read_frame(reader))
        await asyncio.sleep(0)
        self.assertFalse(task.done())
        reader.feed_data(b"\x03abc")
        self.assertEqual(await task, b"abc")
        reader.feed_data(b"\xff\xff")
        with self.assertRaises(ValueError): await read_frame(reader)

    async def test_heartbeat_and_truncated_frame(self):
        reader = asyncio.StreamReader()
        reader.feed_data(encode_frame(b""))
        self.assertEqual(await read_frame(reader), b"")
        reader.feed_data(b"\x00\x05ab"); reader.feed_eof()
        with self.assertRaises(asyncio.IncompleteReadError): await read_frame(reader)

    async def test_two_paths_authenticated_bidirectional_forwarding(self):
        sink = Sink(); gateway = Gateway(TOKEN, sink)
        server = await asyncio.start_server(gateway.handle, "127.0.0.1", 0)
        port = server.sockets[0].getsockname()[1]
        session = str(uuid.uuid4())
        clients = []
        try:
            for label in ("wifi", "mobile"):
                reader, writer = await asyncio.open_connection("127.0.0.1", port)
                hello = {"protocol": "DN1", "token": TOKEN, "session": session, "path": label, "mtu": 1400}
                writer.write(encode_frame(json.dumps(hello).encode())); await writer.drain()
                self.assertTrue(json.loads(await read_frame(reader))["ok"])
                clients.append((reader, writer))
            outbound = packet()
            for _, writer in clients: writer.write(encode_frame(outbound)); await writer.drain()
            for _ in range(50):
                if len(sink.packets) == 2: break
                await asyncio.sleep(.01)
            self.assertEqual(sink.packets, [outbound, outbound])
            reply = packet("1.1.1.1", "10.77.0.2")
            gateway.downstream(reply); gateway.downstream(reply)
            for reader, _ in clients: self.assertEqual(await asyncio.wait_for(read_frame(reader), 2), reply)
            # Losing one path preserves the session on the other path.
            clients[0][1].close(); await clients[0][1].wait_closed()
            for _ in range(50):
                if len(gateway.paths) == 1: break
                await asyncio.sleep(.01)
            gateway.downstream(reply)
            self.assertEqual(await asyncio.wait_for(read_frame(clients[1][0]), 2), reply)
        finally:
            for _, writer in clients: writer.close(); await writer.wait_closed()
            server.close(); await server.wait_closed()

    async def test_rejects_invalid_auth_without_forwarding(self):
        sink = Sink(); gateway = Gateway(TOKEN, sink)
        hello = {"protocol": "DN1", "token": "incorrect", "session": str(uuid.uuid4()), "path": "wifi"}
        with self.assertRaises(ValueError): gateway.authenticate(json.dumps(hello).encode())
        self.assertEqual(sink.packets, [])

    async def test_session_isolation(self):
        gateway = Gateway(TOKEN, Sink())
        session = str(uuid.uuid4())
        hello = {"protocol": "DN1", "token": TOKEN, "session": session, "path": "wifi"}
        gateway.authenticate(json.dumps(hello).encode())
        gateway.paths["wifi"] = Path("wifi", None, 1400)
        hello["session"] = str(uuid.uuid4())
        with self.assertRaises(ValueError): gateway.authenticate(json.dumps(hello).encode())

    async def test_spoofed_and_truncated_packets_rejected(self):
        self.assertTrue(valid_packet(packet(), from_client=True))
        self.assertFalse(valid_packet(packet("10.77.0.3"), from_client=True))
        self.assertFalse(valid_packet(packet()[:-1], from_client=True))
        self.assertFalse(valid_packet(b"\x60" * 30, from_client=True))
        self.assertFalse(valid_packet(packet(), from_client=False))

    async def test_bounded_backpressure_uses_healthy_path(self):
        gateway = Gateway(TOKEN, Sink())
        first = Path("wifi", None, 1400); second = Path("mobile", None, 1400)
        for _ in range(first.queue.maxsize): first.queue.put_nowait(b"full")
        gateway.paths = {"wifi": first, "mobile": second}
        reply = packet("1.1.1.1", "10.77.0.2")
        gateway.downstream(reply)
        self.assertEqual(second.queue.get_nowait(), reply)
        self.assertEqual(first.queue.qsize(), 256)


if __name__ == "__main__": unittest.main()
