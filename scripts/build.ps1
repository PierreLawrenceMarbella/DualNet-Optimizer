param([string]$JdkPath = $env:JAVA_HOME, [switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
if (-not $JdkPath -or -not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin\java.exe'))) {
    throw 'Pass -JdkPath pointing to a JDK 17 installation, or set JAVA_HOME.'
}
$env:JAVA_HOME = $JdkPath
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
    if ($DeviceTests) { $tasks += ':app:connectedDebugAndroidTest' }
    & .\gradlew.bat @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
    New-Item -ItemType Directory -Force -Path artifacts | Out-Null
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination artifacts\DualNet-Optimizer-1.0.0-debug.apk
    Get-FileHash -Algorithm SHA256 -LiteralPath artifacts\DualNet-Optimizer-1.0.0-debug.apk
} finally { Pop-Location }
