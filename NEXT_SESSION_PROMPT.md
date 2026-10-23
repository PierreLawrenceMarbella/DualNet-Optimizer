# Continue DualNet Optimizer

Continue the existing native Android project in this folder. Do not restart, redesign, replace it with a website, or repeat already-passing checks without a relevant change.

1. Read `PROJECT_PROGRESS.md`, `docs/VALIDATION.md`, `README.md`, and `gateway/README.md`; inspect `git status`, recent commits, and the sanitized remote URL.
2. Preserve the user's original seven-screen image reference and honest networking requirements. Representative figures belong only in Compose previews. Smart Mode affects app-controlled traffic; arbitrary-app bonding requires the experimental DN1 VPN and its compatible gateway.
3. Keep the tested build combination: Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK 36, Build Tools 36.1.0, JDK 17. Set JAVA_HOME explicitly; do not use default Java 8. The host has limited RAM.
4. Read the remaining-work list and continue the earliest actionable item. Final screenshot capture, permission/offline checks, resource cleanup, artifact packaging, and physical/public-gateway validation must be distinguished.
5. Before committing/pushing, scan staged files for secrets, exclude local paths/caches/APKs/logs/private keys, verify the exact repository destination and remote history, and avoid force pushes. If no remote is configured, ask for the intended URL while continuing local work. Do not invent a repository or claim a push happened.
6. Update `PROJECT_PROGRESS.md` and this file after significant milestones. Record exact test results and outstanding limitations. Do not mark phases complete without verification.

Last checkpoint: debug APK builds/installs; 9 JVM, 12 Python, and 7 Android tests passed. Real HTTPS speed transfer succeeded. Screenshot test timing has been corrected and needs a rerun. There is no physical Xiaomi 12T or deployed public TLS gateway available in the recorded session. No Git remote was present when the continuation request arrived; the user was asked for its URL.

Useful commands (PowerShell):

```powershell
$env:JAVA_HOME = 'C:\Users\pierr\.jdks\jbr-17.0.14'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
python -m unittest discover -s gateway -p 'test_*.py' -v
python scripts/package_source.py
```

For direct instrumentation, install the main and test APKs and run `adb shell am instrument -w -r com.dualnet.optimizer.test/androidx.test.runner.AndroidJUnitRunner`. Tests transfer about 24 MB and should run on a test device. The screenshot files are at `/sdcard/Android/data/com.dualnet.optimizer/files/screenshots/`.
