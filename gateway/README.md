# DualNet DN1 gateway

This is an **experimental, single-device IPv4 multipath gateway**, not a commercial VPN service. The Android app only enables bonding after authenticated TLS paths connect. No public gateway or subscription is included.

## Requirements

- A Linux server you control with Python 3.11+, `/dev/net/tun`, `iproute2`, and `iptables`.
- A public DNS hostname and a publicly trusted TLS certificate covering it. The Android client validates both certificate trust and hostname. Self-signed certificates are rejected in the shipped app.
- Inbound **TCP 4433** for the tunnel and **UDP 4433** for packet-loss diagnostics, outbound internet access, IPv4 forwarding, and NAT.
- Wi-Fi and cellular internet on the phone. Carrier/OEM restrictions can prevent keeping both active.

## Install

1. Copy `gateway.py` and `udp_probe.py` to `/opt/dualnet/`. Create `/etc/dualnet/`.
2. Run `sudo bash setup-network.sh eth0`, replacing `eth0` with the actual internet-facing interface. This creates the `dualnet` service user and persistent `dualnet0` TUN interface. It adds narrowly scoped forwarding/NAT rules without flushing existing rules. The interface must use MTU 1400, matching the app default. If you change MTU in the app, change the Linux interface too.
3. Obtain a certificate for your hostname using your normal ACME certificate tooling. Place the full certificate chain and key at `/etc/dualnet/fullchain.pem` and `/etc/dualnet/privkey.pem`. Grant the `dualnet` group read access to these files; keep the key inaccessible to other users. Automate renewal and restart the gateway after renewal.
4. Generate a random token locally: `python3 -c 'import secrets; print(secrets.token_urlsafe(48))'`. Store it as `DUALNET_TOKEN=your_generated_token` in `/etc/dualnet/gateway.env`. Set file permissions to `0600` and root ownership; systemd reads it before dropping privileges. Do not commit it.
5. Install `dualnet-gateway.service` under `/etc/systemd/system/`. Run `sudo systemctl daemon-reload`, then `sudo systemctl enable --now dualnet-gateway`.
6. Allow TCP and UDP 4433 through the host and cloud firewalls. Persist the TUN configuration, IPv4 forwarding, and firewall rules with your distribution's tools before relying on reboot startup.
7. In the app, open **Tools → Advanced Settings**, enter the hostname, port 4433, and the same token. Select **Dual Network Mode**, enable both networks, tap the power ring, and accept Android's VPN consent dialog.
8. Verify the app says **2 encrypted paths** and server logs show both `wifi` and `mobile`. Transfer traffic, then inspect the gateway's per-path byte counts. Disconnect either network and verify the app reports **Single path fallback**. Always test with a noncritical connection before using other apps.

The service opens a persistent TUN owned by `dualnet`, so the running Python process does not need root. Initial network/firewall setup requires administrator privileges. This setup was not executed against a remote server during project creation.

## Protocol

Each path is a TCP connection inside TLS 1.2 or 1.3. Frames begin with a two-byte unsigned network-order payload length. The first frame (maximum 4096 bytes) contains UTF-8 JSON:

```json
{"protocol":"DN1","token":"<secret>","session":"<random UUID>","path":"wifi","mtu":1400}
```

Both paths must use the same session UUID and token, and different path labels (`wifi`, `mobile`). The server permits one device session and two paths. It compares tokens in constant time and returns:

```json
{"ok":true,"protocol":"DN1","address":"10.77.0.2"}
```

After authentication, nonempty frames contain complete IPv4 packets (maximum negotiated MTU); empty frames are heartbeats. Client source addresses and server destination addresses must match `10.77.0.2`. Authentication material is never logged. A session cannot be replaced while it has a live path.

The app protects each socket from its own VPN, binds it to the Wi-Fi or cellular `Network`, validates TLS, and then establishes the Android TUN. Packets are distributed across bounded per-path queues. The gateway writes uplink packets into its TUN; Linux routes and NATs them. Replies are read from the TUN and distributed over the connected paths. When one path fails, new packets use the remaining path; failed paths reconnect every few seconds. IPv6 is blocked for VPN-routed apps by not enabling that address family in `VpnService.Builder`.

## Scope and tradeoffs

Network Booster can send ten authenticated UDP diagnostics per available connection. A 60-byte probe contains `DNP1`, an eight-byte network-order timestamp in milliseconds, a 16-byte random nonce, and a 32-byte HMAC-SHA256 over those first 28 bytes using the token. The server accepts timestamps within 30 seconds and echoes identical bytes, capped at 100 packets/second. It never sends the token or carries application payloads over this UDP port. The app counts matching replies with an 800 ms timeout and reports round-trip packet loss. A blocked UDP port, wrong token, or inaccurate clock can appear as 100% loss. The UDP diagnostic endpoint is separate from the TLS VPN transport.

- This implements actual IP forwarding across both networks, including TCP and UDP application traffic. It is **not MPTCP, QUIC, or a production-grade congestion controller**.
- Independent TCP paths can reorder IP packets and introduce TCP-over-TCP head-of-line blocking. Throughput can be worse than a single connection; gaming ping can increase. There is no bandwidth or latency guarantee.
- Full queues drop packets. Standard TCP applications retransmit; UDP applications may lose datagrams during path failure. There is no FEC, UDP relay, multipath congestion estimation, or seamless session migration after process death.
- IPv4 only. Applications requiring IPv6-only destinations are unsupported through this gateway. DNS travels inside TLS to the gateway, then uses the configured resolver; it is not end-to-end DoH.
- A different server instance, subnet, interface, and token are needed for additional devices. This implementation does not isolate multiple users.
- Android always-on/lockdown is required for a system-enforced kill switch. The app's Connection Lock alone cannot keep a tunnel alive after Android terminates its process.
- A server sees egress destinations and unencrypted application protocols. Continue using HTTPS and a trusted server.

## Tests

```sh
cd gateway
python -m unittest discover -p 'test_*.py' -v
```

Protocol tests run on Windows or Linux with an in-memory packet sink. They exercise framing, authentication, packet validation, two-path forwarding, queue pressure, and fallback. They do not validate Linux NAT, a public certificate deployment, or physical Wi-Fi plus cellular radios. See `../docs/VALIDATION.md` for the actual verification record.
