# Validation record

Checkpoint recorded 2026-10-23, Asia/Manila. Results are for this development environment, not a physical Xiaomi 12T.

| Check | Recorded result | Evidence |
|---|---|---|
| Debug APK + instrumented test APK | Build succeeded | `artifacts/device-build.log` |
| Android lint | 0 errors, 10 warnings at checkpoint | `app/build/reports/lint-results-debug.html` |
| JVM tests | 9 passed | `app/build/test-results/testDebugUnitTest/` |
| Python gateway suite | 12 passed | `artifacts/gateway-tests.log` |
| Android instrumentation | 7 passed | `artifacts/instrumentation-final.log` |
| Real speed transfer | HTTPS latency ~122 ms, download ~11.0 Mbps, upload ~9.5 Mbps | Android log tag `DualNetTest` |
| Screenshot capture | Needs rerun after presentation-wait fix | `NavigationTest.kt` and `artifacts/screenshots/` |

The emulator runs Android 16.1 at 1080×2400, density 420. Both emulated transports ultimately share host connectivity; they do not validate physical Wi-Fi/cellular aggregation. Lint warnings concern dependency upgrades and backup configuration. No warning baseline or disabled error checks were used.

Local gateway tests use an in-memory packet sink instead of a Linux TUN. TLS tests generate temporary certificates trusted only by their test client and also verify that normal trust/hostname failures are rejected. Production Android certificate validation is not weakened for those tests.

Not yet verified: deployed Linux NAT, Android VPN packet forwarding end to end, physical multipath performance, Xiaomi radio/battery behavior, game compatibility, always-on lockdown across restart, and a full permissions/disconnection matrix. See `PROJECT_PROGRESS.md` for the current follow-up list.
