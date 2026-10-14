[CmdletBinding()]
param(
    [switch]$Release,
    [switch]$SkipChecks,
    [string]$SigningStore,
    [string]$SigningAlias
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.android-build'

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem (Join-Path $toolRoot 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to JDK 17.'
}
if (-not $env:ANDROID_HOME) {
    if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
    elseif (Test-Path (Join-Path $toolRoot 'sdk')) { $env:ANDROID_HOME = Join-Path $toolRoot 'sdk' }
}
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    throw 'Set ANDROID_HOME to Android SDK 35 with Build Tools 35.0.0.'
}
if (-not $env:GRADLE_USER_HOME -and (Test-Path (Join-Path $toolRoot 'gradle-cache'))) {
    $env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-cache'
}
if ($Release -and (-not $SigningStore -or -not $SigningAlias -or -not (Test-Path -LiteralPath $SigningStore))) {
    throw 'Release requires -SigningStore and -SigningAlias.'
}
if ($Release -and (-not $env:LEARNFPRC_STORE_PASSWORD -or -not $env:LEARNFPRC_KEY_PASSWORD)) {
    throw 'Set LEARNFPRC_STORE_PASSWORD and LEARNFPRC_KEY_PASSWORD locally; never commit passwords.'
}

$gradle = Join-Path $toolRoot 'gradle/gradle-8.14.3/bin/gradle.bat'
if (-not (Test-Path $gradle)) { $gradle = Join-Path $projectRoot 'android_app/gradlew.bat' }
$variant = if ($Release) { 'Release' } else { 'Debug' }
$tasks = @(":app:assemble$variant")
if (-not $SkipChecks) { $tasks = @((":app:test" + $variant + "UnitTest"), ":app:lint$variant", ":app:assemble$variant") }
Push-Location (Join-Path $projectRoot 'android_app')
try {
    & $gradle --no-daemon --console plain @tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }

$versionText = Get-Content (Join-Path $projectRoot 'android_app/app/build.gradle') -Raw
$version = [regex]::Match($versionText, "versionName\s+'([^']+)'").Groups[1].Value
if (-not $version) { throw 'Cannot read versionName.' }
$outputDir = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$apkName = if ($Release) { 'LearnFPRC-stable.apk' } else { "LearnFPRC-v$version-debug.apk" }
$apkPath = Join-Path $outputDir $apkName
$signer = Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/apksigner.bat'
if ($Release) {
    $unsignedApk = Join-Path $projectRoot 'android_app/app/build/outputs/apk/release/app-release-unsigned.apk'
    & $signer sign --ks $SigningStore --ks-key-alias $SigningAlias `
        --ks-pass env:LEARNFPRC_STORE_PASSWORD --key-pass env:LEARNFPRC_KEY_PASSWORD `
        --v4-signing-enabled false --out $apkPath $unsignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
} else {
    Copy-Item (Join-Path $projectRoot 'android_app/app/build/outputs/apk/debug/app-debug.apk') $apkPath
}
& $signer verify --verbose $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$hash = (Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText([IO.Path]::ChangeExtension($apkPath, '.sha256'), "$hash  $apkName`n", [Text.UTF8Encoding]::new($false))
Write-Output "Version: $version"
Write-Output "APK: $apkPath"
Write-Output "SHA256: $hash"
