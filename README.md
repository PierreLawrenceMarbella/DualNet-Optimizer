# DualNet Optimizer 1.0.0

Native Android application built with Kotlin, Jetpack Compose, Material 3, MVVM, StateFlow, Coroutines, and DataStore. The seven main screens follow the supplied dark navy, electric blue, and neon green reference. All runtime network values come from Android or actual requests; missing measurements are shown as unavailable.

## Requirements and build

Open this folder in Android Studio. Use **JDK 17** for Gradle (Settings → Build Tools → Gradle → Gradle JDK), install Android SDK Platform 36 and Build Tools 36.1.0, and let Gradle sync. The project pins AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, and Compose BOM 2025.10.01. Android 8.0/API 26 or later is supported; API 31+ supplies live interface counters.

```powershell
# Set this to your installed JDK 17; do not use a Java 8 runtime.
$env:JAVA_HOME = 'C:\path\to\jdk-17'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS/Linux, use `chmod +x gradlew` and `./gradlew` instead. Android Studio writes your machine-specific `local.properties`; it is excluded from source control.

APK output: `app/build/outputs/apk/debug/app-debug.apk`. To validate and create the delivery copy at `artifacts/DualNet-Optimizer-1.0.0-debug.apk`, run `./scripts/build.ps1 -JdkPath C:\path\to\jdk-17` in PowerShell. This is a debug-signed development APK, not a Play Store release.

## Install on Xiaomi 12T

1. Enable Developer options by tapping the OS/MIUI version repeatedly in Settings → About phone. Enable USB debugging in Developer options; Xiaomi may also require “Install via USB.” Menu names vary by MIUI/HyperOS release.
2. Connect the phone by USB and accept its debugging prompt. Run `adb devices`; the phone should be listed as `device`.
3. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or select the Xiaomi device in Android Studio and press Run. You can also copy the APK to the phone and permit installation from your file manager.
4. Start with Smart Mode. Enable Wi-Fi/mobile data in Android. Tap the power ring to start app-scoped selection. The ongoing notification keeps this user-requested session visible.
5. Open Network Status → Allow network details for optional SSID and phone details. Enable Location if Android hides the SSID. Open Usage → Open usage access to allow device/app usage statistics.
6. For dependable background operation, review the app's battery/autostart settings on your phone. Android/OEM restrictions can still stop services. Test actual reconnect behavior before relying on it.
7. Dual Network Mode needs the included [Linux gateway](gateway/README.md), a valid public TLS certificate, and an authentication token. Configure these in Tools → Advanced Settings, then accept Android's VPN permission. Never enable “Block connections without VPN” until the gateway works.

The Xiaomi 12T was not physically attached during development. Emulator verification does not establish physical radio, carrier, MIUI/HyperOS, gaming, or battery-management compatibility.

## What works and what the labels mean

| Feature | Implementation and scope |
|---|---|
| Home / live graphs | Android callbacks and per-interface RX/TX deltas on API 31+. Interface counters are estimates and may omit adjustments. No total-minus-cellular inference is used. |
| Smart Mode | HTTPS probes with a 20% switching margin select Wi-Fi/cellular for DualNet requests. Other apps retain Android routing. |
| Wi-Fi / Mobile Only | DualNet requests bind to the selected network. These modes do not globally force other applications onto a radio. |
| Dual Network | Experimental IPv4 VPN client and matching DN1 gateway use two separately bound TLS paths when available. A configured server is required. The Android VPN-to-public-gateway path and physical dual-radio bonding have not been verified end to end; the UI reports the actual authenticated path count. |
| Usage | `NetworkStatsManager` with special Usage access. Android can delay accounting, restrict totals, or expose only shared UID attribution. |
| Status | Available IP, gateway, DNS, SSID, signal and technology. Unknown values stay unknown; 5G NSA may be reported as LTE by Android. |
| Speed Test | Five HTTPS response-time probes, 12 MB download and 12 MB upload against Cloudflare. Wi-Fi, mobile, selected/system network, or active two-path VPN. Results are application-level throughput. |
| Network Booster | On-demand HTTPS latency/jitter/request failures, authenticated UDP round-trip packet loss to your configured gateway, live RX/TX, and mode selection. |
| App Routing | Installed launcher-app selection applied with `VpnService.Builder.addAllowedApplication`; empty selection routes all apps. DualNet remains included for tests. Reconnect to apply. |
| DNS | Configurable IPv4 resolver inside the VPN; shortcut to Android Private DNS. No system-wide DNS mutation outside VPN. |
| Connection Lock | Retry while the VPN service lives; Android VPN settings for OS-enforced always-on and lockdown. |
| Settings | DataStore preferences; Keystore-encrypted gateway token; boot receiver; measured speed in ongoing notification; fixed Dark theme / English locale. |

The application cannot silently enable radios, bypass carrier restrictions, guarantee lower ping, or guarantee faster downloads. UDP packet-loss diagnostics require the configured gateway's UDP port; they measure missing round-trip replies, not one-way loss. HTTPS request failures are labeled separately. An unreachable endpoint or firewall can also result in missing replies.

## Navigation

Home, Modes, Usage, and Tools form the bottom navigation. Tap a network card for Network Status, the live speed card for Speed Test, and the header gear for Settings. Tools also has direct Status and Speed Test shortcuts. All five tool cards open working settings/diagnostics pages.

## Permissions and privacy

- Internet/network state: network monitoring and requests. `CHANGE_NETWORK_STATE` requests cellular alongside Wi-Fi; it does not turn mobile data on.
- Optional Location + Phone: SSID and cellular details. No background location permission or location history is requested.
- Optional Usage access: device/app data totals. No installed-app list or usage history is uploaded.
- Notifications: connection status and measured speed. Android requires a foreground notification even if optional alerts are off.
- VPN consent: only for Dual Network Mode. Android can run only one user VPN at a time.
- Boot completed: starts the selected mode only when Auto Start is enabled and any required VPN consent already exists.
- No root access is required on the phone. No analytics/advertising SDKs. Credentials are encrypted at rest and excluded from backups.

Speed tests and Smart Mode diagnostics contact `speed.cloudflare.com`, which receives your public IP. A speed test transfers approximately 24 MB plus protocol overhead and latency probes. Review [Cloudflare's speed-test privacy information](https://speed.cloudflare.com/). Your own gateway handles VPN traffic. No gateway credentials are sent to Cloudflare.

## Architecture

```
app/src/main/java/com/dualnet/optimizer/
  ui/screens/       Seven screens and five functional tool pages
  ui/components/    Neon cards, power ring, graphs, gauge, segmented controls
  ui/theme/         Reference palette and typography
  navigation/       Compose navigation and bottom bar
  viewmodel/        State and user actions
  data/             Models, DataStore/Keystore, usage statistics
  network/          Network callbacks, traffic sampling, probes, speed tests
  vpn/              TLS framing, bound transport paths, Android TUN service
  services/         Notifications, foreground monitoring, boot handling
  utils/            Formatting, time windows, validation
gateway/            Python gateway, systemd example, setup script, tests
```

## Verification

The debug APK and unit, gateway, and Android emulator tests have passed. The emulator does not validate physical dual-radio aggregation, Linux NAT deployment, or Xiaomi 12T battery and carrier behavior.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
python -m unittest discover -s gateway -p 'test_*.py' -v
```

## Technical sources

- [Android VPN guide](https://developer.android.com/develop/connectivity/vpn): consent, protected sockets, TUN forwarding, per-app routing, always-on.
- [Network.bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.net.Socket)): selecting a physical transport.
- [NetworkStatsManager](https://developer.android.com/reference/android/app/usage/NetworkStatsManager): access restrictions, null subscriber IDs, delayed accounting.
- [TrafficStats](https://developer.android.com/reference/android/net/TrafficStats): per-interface counter availability and limitations.
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): declared `specialUse` service purpose. Distribution through Play requires its applicable foreground-service/VPN policy review; a sideload build is not store approval.
- [Cloudflare speed-test implementation](https://github.com/cloudflare/speedtest): HTTPS download/upload endpoints. DualNet uses its own small native measurement client, not Cloudflare's browser engine or quality score.
