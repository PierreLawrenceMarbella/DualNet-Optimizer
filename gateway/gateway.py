#!/usr/bin/env python3
"""DualNet DN1: a single-device, two-path, TLS-protected IPv4 TUN gateway.

Python 3.11+, Linux, CAP_NET_ADMIN, a configured TUN/NAT, and a public TLS
certificate are required. See README.md. No third-party Python dependencies.
"""
from __future__ import annotations

import argparse
import asyncio
import contextlib
import hmac
import ipaddress
import json
import logging
import os
import ssl
import struct
import time
import uuid
from dataclasses import dataclass, field
from typing import Protocol
from udp_probe import ProbeServer

MAX_PACKET = 1500
MAX_HANDSHAKE = 4096
CLIENT_ADDRESS = "10.77.0.2"
LOG = logging.getLogger("dualnet")


def encode_frame(payload: bytes) -> bytes:
    if len(payload) > MAX_HANDSHAKE:
        raise ValueError("Frame too large")
    return struct.pack("!H", len(payload)) + payload


async def read_frame(reader: asyncio.StreamReader, maximum: int = MAX_PACKET) -> bytes:
    size = struct.unpack("!H", await reader.readexactly(2))[0]
    if size > maximum:
        raise ValueError("Frame too large")
    return await reader.readexactly(size)


def valid_packet(packet: bytes, *, from_client: bool) -> bool:
    if len(packet) < 20 or packet[0] >> 4 != 4:
        return False
    ihl = (packet[0] & 15) * 4
    if ihl < 20 or ihl > len(packet) or struct.unpack("!H", packet[2:4])[0] != len(packet):
        return False
    address = packet[12:16] if from_client else packet[16:20]
    return address == ipaddress.IPv4Address(CLIENT_ADDRESS).packed


class PacketSink(Protocol):
    def write(self, packet: bytes) -> None: ...


class Tun:
    def __init__(self, name: str):
        import fcntl  # Linux only; protocol tests run on Windows too.
        self.fd = os.open("/dev/net/tun", os.O_RDWR | os.O_NONBLOCK)
        fcntl.ioctl(self.fd, 0x400454CA, struct.pack("16sH", name.encode(), 0x0001 | 0x1000))

    def write(self, packet: bytes) -> None:
        try:
            os.write(self.fd, packet)
        except BlockingIOError:
            LOG.warning("TUN output queue full; dropped packet")

    def close(self):
        os.close(self.fd)


@dataclass
class Path:
    label: str
    writer: asyncio.StreamWriter
    mtu: int
    queue: asyncio.Queue[bytes] = field(default_factory=lambda: asyncio.Queue(maxsize=256))
    uploaded: int = 0
    downloaded: int = 0


class Gateway:
    def __init__(self, token: str, sink: PacketSink):
        if len(token) < 32:
            raise ValueError("Use a random token of at least 32 characters")
        self.token = token
        self.sink = sink
        self.session: str | None = None
        self.paths: dict[str, Path] = {}
        self.last_detach = 0.0
        self.cursor = 0
        self.connections = 0

    def authenticate(self, payload: bytes) -> tuple[str, str, int]:
        hello = json.loads(payload)
        token = hello.get("token", "")
        if not isinstance(token, str) or not hmac.compare_digest(token.encode(), self.token.encode()):
            raise ValueError("Authentication failed")
        if hello.get("protocol") != "DN1" or hello.get("path") not in ("wifi", "mobile"):
            raise ValueError("Invalid protocol or path")
        session = str(uuid.UUID(hello["session"]))
        mtu = hello.get("mtu", 1400)
        if type(mtu) is not int or not 1280 <= mtu <= MAX_PACKET:
            raise ValueError("Invalid MTU")
        if self.session != session:
            if self.paths:
                raise ValueError("Gateway already in use")
            self.session = session
        return session, hello["path"], mtu

    async def handle(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        self.connections += 1
        path = None
        sender = None
        try:
            if self.connections > 8:
                raise ValueError("Too many connections")
            payload = await asyncio.wait_for(read_frame(reader, MAX_HANDSHAKE), 5)
            _, label, mtu = self.authenticate(payload)
            if label in self.paths:
                raise ValueError("Duplicate path")
            path = Path(label, writer, mtu)
            # No await between admission and reservation: duplicate paths cannot race.
            self.paths[label] = path
            writer.write(encode_frame(json.dumps({"ok": True, "protocol": "DN1", "address": CLIENT_ADDRESS}).encode()))
            await asyncio.wait_for(writer.drain(), 5)
            LOG.info("Authenticated %s path (%d active)", label, len(self.paths))
            sender = asyncio.create_task(self.send(path))
            while True:
                packet = await asyncio.wait_for(read_frame(reader, mtu), 20)
                if not packet:
                    continue
                if not valid_packet(packet, from_client=True):
                    raise ValueError("Invalid source packet")
                self.sink.write(packet)
                path.uploaded += len(packet)
        except (ValueError, KeyError, TypeError, UnicodeError, asyncio.IncompleteReadError, TimeoutError, ConnectionError, OSError):
            # Do not log tokens, payloads, user IPs, or unauthenticated JSON.
            pass
        finally:
            if sender:
                sender.cancel()
                with contextlib.suppress(asyncio.CancelledError):
                    await sender
            if path and self.paths.get(path.label) is path:
                del self.paths[path.label]
                self.last_detach = time.monotonic()
                LOG.info("Closed %s path; up=%d down=%d bytes", path.label, path.uploaded, path.downloaded)
            writer.close()
            with contextlib.suppress(Exception):
                await writer.wait_closed()
            self.connections -= 1

    async def send(self, path: Path):
        try:
            while True:
                try:
                    packet = await asyncio.wait_for(path.queue.get(), 5)
                except TimeoutError:
                    packet = b""  # Keepalive also makes dead paths detectable.
                path.writer.write(encode_frame(packet))
                await asyncio.wait_for(path.writer.drain(), 5)
                path.downloaded += len(packet)
        except (ConnectionError, OSError, TimeoutError):
            path.writer.close()

    def downstream(self, packet: bytes):
        if not valid_packet(packet, from_client=False):
            return
        paths = list(self.paths.values())
        for offset in range(len(paths)):
            path = paths[(self.cursor + offset) % len(paths)]
            if len(packet) <= path.mtu and not path.queue.full():
                path.queue.put_nowait(packet)
                self.cursor = (self.cursor + offset + 1) % len(paths)
                return


async def serve(args):
    token = os.environ.get("DUALNET_TOKEN", "")
    tun = Tun(args.tun)
    gateway = Gateway(token, tun)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.cert, args.key)
    loop = asyncio.get_running_loop()

    def read_tun():
        try:
            packet = os.read(tun.fd, 65535)
            gateway.downstream(packet)
        except BlockingIOError:
            pass

    loop.add_reader(tun.fd, read_tun)
    udp, _ = await loop.create_datagram_endpoint(lambda: ProbeServer(token), local_addr=(args.host, args.port))
    try:
        async with await asyncio.start_server(gateway.handle, args.host, args.port, ssl=tls, ssl_handshake_timeout=8, limit=8192) as server:
            LOG.info("DN1 gateway listening on port %d", args.port)
            await server.serve_forever()
    finally:
        udp.close()
        loop.remove_reader(tun.fd)
        tun.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=4433)
    parser.add_argument("--tun", default="dualnet0")
    parser.add_argument("--cert", required=True)
    parser.add_argument("--key", required=True)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    asyncio.run(serve(parser.parse_args()))
