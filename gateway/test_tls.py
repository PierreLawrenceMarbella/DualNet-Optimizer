"""TLS integration uses a temporary local CA certificate, never the production trust store."""
import asyncio
import contextlib
import json
import os
import shutil
import ssl
import subprocess
import tempfile
import unittest
import uuid
from gateway import Gateway, encode_frame, read_frame
from test_gateway import Sink, TOKEN, packet

OPENSSL = shutil.which("openssl") or (r"C:\Program Files\Git\usr\bin\openssl.exe" if os.path.isfile(r"C:\Program Files\Git\usr\bin\openssl.exe") else None)


@unittest.skipUnless(OPENSSL, "OpenSSL is needed to generate temporary test certificates")
class TlsTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.cert = os.path.join(cls.temp.name, "cert.pem")
        cls.key = os.path.join(cls.temp.name, "key.pem")
        subprocess.run([OPENSSL, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", cls.key,
                        "-out", cls.cert, "-days", "1", "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost"],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    async def asyncSetUp(self):
        self.sink = Sink(); self.gateway = Gateway(TOKEN, self.sink)
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.minimum_version = ssl.TLSVersion.TLSv1_2
        tls.load_cert_chain(self.cert, self.key)
        self.server = await asyncio.start_server(self.gateway.handle, "127.0.0.1", 0, ssl=tls)
        self.port = self.server.sockets[0].getsockname()[1]
        self.client = ssl.create_default_context(cafile=self.cert)
        # TLS rejection intentionally resets sockets; suppress expected asyncio server diagnostics.
        self.previous_handler = asyncio.get_running_loop().get_exception_handler()
        asyncio.get_running_loop().set_exception_handler(lambda loop, context: None)

    async def asyncTearDown(self):
        self.server.close(); await self.server.wait_closed()
        asyncio.get_running_loop().set_exception_handler(self.previous_handler)

    async def test_two_tls_paths_carry_real_binary_frames(self):
        session = str(uuid.uuid4()); clients = []
        try:
            for label in ("wifi", "mobile"):
                reader, writer = await asyncio.open_connection("127.0.0.1", self.port, ssl=self.client, server_hostname="localhost")
                self.assertIn(writer.get_extra_info("ssl_object").version(), ("TLSv1.2", "TLSv1.3"))
                writer.write(encode_frame(json.dumps({"protocol": "DN1", "token": TOKEN, "session": session, "path": label}).encode()))
                await writer.drain(); self.assertTrue(json.loads(await read_frame(reader))["ok"])
                clients.append((reader, writer))
            for _, writer in clients: writer.write(encode_frame(packet())); await writer.drain()
            for _ in range(50):
                if len(self.sink.packets) == 2: break
                await asyncio.sleep(.01)
            self.assertEqual(len(self.sink.packets), 2)
            reply = packet("1.1.1.1", "10.77.0.2")
            self.gateway.downstream(reply); self.gateway.downstream(reply)
            for reader, _ in clients: self.assertEqual(await asyncio.wait_for(read_frame(reader), 2), reply)
        finally:
            for _, writer in clients:
                writer.close()
                with contextlib.suppress(Exception): await writer.wait_closed()

    async def test_wrong_hostname_is_rejected(self):
        with self.assertRaises(ssl.SSLCertVerificationError):
            await asyncio.open_connection("127.0.0.1", self.port, ssl=self.client, server_hostname="wrong.invalid")

    async def test_untrusted_certificate_is_rejected(self):
        with self.assertRaises(ssl.SSLCertVerificationError):
            await asyncio.open_connection("127.0.0.1", self.port, ssl=ssl.create_default_context(), server_hostname="localhost")


if __name__ == "__main__": unittest.main()
