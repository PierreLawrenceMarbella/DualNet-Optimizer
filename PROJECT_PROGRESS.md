# DualNet Optimizer — project progress

Updated: 2026-10-23 (Asia/Manila). Continue this implementation; do not recreate the project.

## Current checkpoint

- Native Kotlin/Compose Android Studio project, version 1.0.0, minimum API 26, target/compile API 36.
- Debug APK built and installed on the Android 16.1 emulator. Last build and lint invocation succeeded.
- 9 JVM unit tests, 12 Python gateway/protocol/TLS/UDP tests, and 7 Android instrumentation tests passed.
- The instrumented speed test transferred actual data through Cloudflare: approximately 122 ms HTTPS response time, 11.0 Mbps download and 9.5 Mbps upload in that run. These are emulator observations, not product performance promises.
- Git is initialized on `main`; no remote is configured. The intended GitHub URL has been requested. A push must wait for a verified destination.

## Implemented

- All seven main screens: Home, Connection Mode, Data Usage, Network Status, Speed Test, Tools, Settings; bottom navigation and functional tool subpages.
- Reference navy/blue/green palette, custom launcher logo, glowing power ring, measured traffic graphs, animated gauge, mode borders, and switches. Android status bar and splash use the dark theme.
- Real connectivity callbacks, optional SSID/telephony permissions, per-interface throughput on API 31+, actual usage accounting with special Usage access, and explicit unavailable states.
- Smart Mode probes and selects a network for DualNet-controlled traffic. Wi-Fi/Mobile modes bind individual app requests. No false device-wide optimization claims.
- Real HTTPS latency/download/upload tests with cancellation and explicit data-cost information.
- Foreground connection monitoring, measured-speed notification, optional result notification, DataStore preferences, Keystore-encrypted credentials, and boot receiver.
- Experimental DN1 IPv4 VPN with separately protected/bound Wi-Fi and cellular TLS sockets, authentication before TUN establishment, bounded packet queues, fallback/reconnection, app allowlisting, configurable DNS/MTU, and always-on settings access.
- Matching single-device Linux TUN/NAT gateway, systemd/setup examples, and authenticated UDP round-trip packet-loss diagnostics.
- README includes build/install instructions for Xiaomi 12T, permission/privacy behavior, technical sources, and limitations. Gateway instructions are in `gateway/README.md`.

## Verified

- JVM: framing, oversized/truncated packets, IPv4 validation, throughput calculation, timezone-aware accounting periods, validation, missing-value formatting.
- Gateway: two-path forwarding, path fallback, authentication and session isolation, malformed packets, bounded backpressure, actual local TLS transport, certificate/hostname rejection, authenticated UDP echo and invalid probe rejection.
- Emulator: all main screens and tool subpages reachable; one selected mode; no fake connection without a gateway; foreground Wi-Fi selection starts/stops; missing gateway rejected; real speed transfer completes; preferences survive DataStore reads and token is absent from plaintext storage; denied usage access shows a limitation.
- Lint: no errors. Warnings are pinned dependency updates and an older-Android backup XML configuration item still to address.

## Remaining work

1. Capture and inspect all seven final screenshots. The navigation assertions pass, but some UIAutomation captures showed the preceding frame. A 700 ms presentation wait has been added to the test helper and the test APK rebuilt; rerun the screenshot test.
2. Verify usage-access granted, permission grant/denial/revocation, offline recovery, Wi-Fi/cellular changes, and background behavior on the emulator; fix any resulting defects.
3. Finish the older-Android backup exclusion XML, rerun affected checks, copy the final APK and build a portable source ZIP.
4. Review VPN lifecycle/resource cleanup and add meaningful regression coverage for defects found.
5. Verify the actual Android TUN → public TLS gateway → Linux NAT → internet path, both physical radios, path failure/recovery, app routing, DNS, and UDP diagnostics end to end. A deployed gateway with a valid public certificate and a real Android device are still required. Local protocol tests do not establish those results.
6. Xiaomi 12T acceptance: installation, MIUI/HyperOS battery/autostart behavior, cellular/5G reporting, reboot, always-on/lockdown, gaming/streaming, and real performance remain unverified.
7. Configure the user-provided GitHub remote, inspect its refs/history, and push commits without overwriting remote work.

## Phase status

| Original phase | Status |
|---|---|
| 1 — UI recreation | Implemented; navigation verified; final screenshot review pending. No pixel-perfect claim. |
| 2 — Android networking | Implemented; core emulator checks passed; additional permission/offline tests pending. |
| 3 — Dual network | Implemented experimental client/server and local protocol tests; deployed gateway and physical dual-radio validation pending. |
| 4 — Testing/APK | APK build/install verified on emulator; physical Xiaomi installation and complete acceptance matrix pending. |

Do not mark a phase complete merely because its code exists. Keep unsupported features and unverified hardware/server behavior explicit.

## Working environment

- Project: `C:\Users\pierr\OneDrive\Documents\Coding Projects\DualNet Optimizer`.
- Local JDK 17: `C:\Users\pierr\.jdks\jbr-17.0.14`; default PATH Java is Java 8 and must not be used for Gradle.
- SDK: `C:\Users\pierr\AppData\Local\Android\Sdk`; emulator `Medium_Phone_API_36.1`, usually `emulator-5554`.
- Gradle must remain **8.13**, AGP **8.13.2**, Kotlin **2.2.21**. An external edit changed the wrapper to 9.7.1 once; it was restored because that version is incompatible with this AGP.
- Memory: 8 GB host. Gradle uses 1536 MB, two workers, Kotlin compilation in process. Do not stop unrelated user processes.
- Reports/logs/screenshots are local under `artifacts/` and `app/build/`, excluded from Git. Source packaging excludes local SDK paths, caches, artifacts, and private keys.
