#!/usr/bin/env bash
# Run once on a dedicated Linux gateway: sudo ./setup-network.sh eth0
# Creates only DualNet's interface/rules. It never flushes existing firewall rules.
set -euo pipefail
EGRESS="${1:?Pass the internet-facing interface, for example eth0}"
[[ "$EGRESS" =~ ^[a-zA-Z0-9_.:-]+$ ]] && [[ -d "/sys/class/net/$EGRESS" ]] || { echo "Invalid interface"; exit 1; }
id dualnet >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin dualnet
ip link show dualnet0 >/dev/null 2>&1 || ip tuntap add dev dualnet0 mode tun user dualnet
ip address replace 10.77.0.1/24 dev dualnet0
ip link set dualnet0 mtu 1400 up
sysctl -w net.ipv4.ip_forward=1
iptables -t nat -C POSTROUTING -s 10.77.0.2/32 -o "$EGRESS" -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -s 10.77.0.2/32 -o "$EGRESS" -j MASQUERADE
iptables -C FORWARD -i dualnet0 -o "$EGRESS" -s 10.77.0.2/32 -j ACCEPT 2>/dev/null || iptables -I FORWARD -i dualnet0 -o "$EGRESS" -s 10.77.0.2/32 -j ACCEPT
iptables -C FORWARD -i "$EGRESS" -o dualnet0 -d 10.77.0.2/32 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || iptables -I FORWARD -i "$EGRESS" -o dualnet0 -d 10.77.0.2/32 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
echo "Interface configured. Allow inbound TCP and UDP 4433 in your host/cloud firewall. Persist these settings using your distribution's network/firewall tooling."
