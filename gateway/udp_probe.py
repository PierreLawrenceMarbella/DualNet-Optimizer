"""Small authenticated UDP echo for round-trip loss diagnostics, not VPN transport."""
import asyncio
import hashlib
import hmac
import struct
import time

MAGIC = b"DNP1"
PAYLOAD_LENGTH = 60  # magic + timestamp millis + 16-byte random nonce + HMAC-SHA256


def valid_probe(data: bytes, token: str, now: float | None = None) -> bool:
    if len(data) != PAYLOAD_LENGTH or data[:4] != MAGIC:
        return False
    timestamp = struct.unpack("!q", data[4:12])[0] / 1000
    if abs((time.time() if now is None else now) - timestamp) > 30:
        return False
    return hmac.compare_digest(data[28:], hmac.new(token.encode(), data[:28], hashlib.sha256).digest())


class ProbeServer(asyncio.DatagramProtocol):
    def __init__(self, token: str):
        self.token = token
        self.transport = None
        self.window = 0
        self.count = 0

    def connection_made(self, transport): self.transport = transport

    def datagram_received(self, data, addr):
        # Global limit bounds work and reflection even with captured authenticated packets.
        window = int(time.monotonic())
        if window != self.window: self.window, self.count = window, 0
        self.count += 1
        if self.count <= 100 and valid_probe(data, self.token):
            self.transport.sendto(data, addr)  # Never amplify or disclose the token.
