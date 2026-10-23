# Physical device and deployed gateway acceptance

These checks remain **unverified**. Record the phone model, OS/build, carrier, APK SHA-256, gateway commit, date, and observations when running them. Emulator transport and local protocol tests cannot replace these checks.

## Xiaomi 12T

1. Install the debug APK using the README instructions. Launch with all optional permissions denied. Navigate through all seven screens; unavailable SSID, phone, and usage values must be explained without a crash.
2. Grant Location/Phone access through Network Status and enable Location. Compare SSID, IP, gateway, DNS, signal, and exposed network technology with Android settings. Revoke permissions in Android settings and reopen the app; restricted values must disappear. Android may restart the process on revocation.
3. Grant Usage access, generate some traffic, and refresh Today/This Week/This Month. Compare against Android's accounting after its reporting delay. Revoke access and refresh; totals and per-app rows must become unavailable.
4. Start Wi-Fi Only, then disable Wi-Fi. The app must show a waiting/disconnected state, without silently claiming mobile is the selected connection. Re-enable Wi-Fi and check recovery. Repeat for Mobile Data Only. Confirm requests actually leave the selected interface using gateway/endpoint logs when available.
5. Start Smart Mode with both radios available. Run diagnostics and a single-network speed test. Record measured HTTPS latency and throughput; do not infer aggregation from two available radios. Disconnect the chosen network and check that app-controlled requests can use the remaining validated network.
6. Leave a monitoring session in the background and lock the screen. Check the ongoing notification, reconnect behavior, and the phone's battery restrictions. Test enabled/disabled Auto Start on Boot after reboot. Record any MIUI/HyperOS autostart configuration needed.

## Experimental DN1 gateway

Deploy the gateway using `gateway/README.md` with a publicly trusted certificate. Keep credentials out of screenshots, logs, commits, and this checklist.

1. Try a wrong token and a certificate/hostname mismatch. Neither must establish a TUN or show Connected. Cancel while connecting; the foreground service and pending sockets must close.
2. Connect with valid credentials and both physical radios. Confirm two authenticated paths in the app and in server logs. Check the server's per-path byte counters during traffic; two TCP connections alone are insufficient evidence that both carry data.
3. Confirm IPv4 internet access through the server's public egress IP, DNS resolution using the configured resolver, and TCP/UDP application traffic. Verify IPv6 does not escape from VPN-routed applications.
4. Disable each radio separately during transfer, then restore it. Confirm one-path fallback, reconnection, truthful path counts, and continued transfer where the application protocol supports recovery. Record losses and interruptions.
5. Select one app in App Routing, reconnect, and compare its egress with an excluded app. DualNet itself stays included for bonded speed tests. Restore the empty selection to route all apps and reconnect.
6. Run Network Booster with the UDP port open, then blocked. Compare reported round-trip loss with received authenticated probes. A blocked port must be reported as missing replies, not as measured one-way radio loss.
7. Stop/start the gateway and revoke Android VPN consent. Verify error states, service cleanup, and recovery. Test Connection Lock with both paths unavailable. Test Android always-on and lockdown across reboot only after a working gateway is established; verify the OS blocks traffic as configured.
8. Compare repeated Wi-Fi-only, mobile-only, and bonded speed tests under similar conditions. Record data cost, latency, jitter, loss, and throughput. Test representative streaming/download/game sessions without promising a universal improvement.

## Evidence

Keep a dated result for every check: PASS, FAIL with reproduction steps, or NOT RUN with the missing prerequisite. Keep sanitized screenshots and logs outside Git unless reviewed for network identifiers and credentials. Mark the corresponding project phase complete only when its acceptance requirements have actually passed.
