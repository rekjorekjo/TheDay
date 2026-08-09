$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$moduleRoot = Join-Path $repoRoot "glass_flutter"
$gradleProperties = Join-Path $repoRoot "gradle.properties"

function Set-GradleProperty {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    if (-not (Test-Path $Path)) {
        New-Item -ItemType File -Path $Path -Force | Out-Null
    }

    $content = Get-Content $Path -Raw
    $escapedName = [Regex]::Escape($Name)
    $pattern = "(?m)^$escapedName=.*$"
    $line = "$Name=$Value"

    if ($content -match $pattern) {
        $content = [Regex]::Replace($content, $pattern, $line)
    } else {
        if ($content.Length -gt 0 -and -not $content.EndsWith("`n")) {
            $content += "`r`n"
        }
        $content += "$line`r`n"
    }

    Set-Content -Path $Path -Value $content -Encoding UTF8 -NoNewline
}

# Flutter 3.44 add-to-app hosts on AGP 9 must opt out of both built-in Kotlin
# and the new Android DSL while the host still uses the legacy Kotlin Gradle plugin.
Set-GradleProperty -Path $gradleProperties -Name "android.builtInKotlin" -Value "false"
Set-GradleProperty -Path $gradleProperties -Name "android.newDsl" -Value "false"

# Flutter add-to-app adds another Android sub-build and native configuration work.
# The old 2 GiB daemon heap can run out of memory while Flutter configures CMake.
# Prefer a roomy daemon and bounded concurrency; app runtime memory is unaffected.
Set-GradleProperty -Path $gradleProperties -Name "org.gradle.jvmargs" -Value "-Xmx6g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8"
Set-GradleProperty -Path $gradleProperties -Name "org.gradle.parallel" -Value "false"
Set-GradleProperty -Path $gradleProperties -Name "org.gradle.workers.max" -Value "4"
Write-Host "Gradle build memory: 6 GiB heap, 1 GiB metaspace; parallel project execution disabled."

if (-not (Get-Command flutter -ErrorAction SilentlyContinue)) {
    throw "Flutter SDK was not found on PATH. Install the current Flutter stable SDK (3.44 or newer), reopen PowerShell, then run this script again."
}

Write-Host "Flutter SDK:"
flutter --version

$metadata = Join-Path $moduleRoot ".metadata"
if (-not (Test-Path $metadata)) {
    $bootstrap = Join-Path ([System.IO.Path]::GetTempPath()) ("theday-glass-bootstrap-" + [guid]::NewGuid().ToString("N"))
    try {
        Write-Host "Bootstrapping Flutter module metadata..."
        flutter create --no-pub --template module --org io.github.thedayapp --project-name glass_flutter $bootstrap
        Copy-Item (Join-Path $bootstrap ".metadata") $metadata -Force
    } finally {
        if (Test-Path $bootstrap) {
            Remove-Item $bootstrap -Recurse -Force
        }
    }
}

Push-Location $moduleRoot
try {
    flutter pub get
    flutter analyze
} finally {
    Pop-Location
}

$includeScript = Join-Path $moduleRoot ".android\include_flutter.groovy"
if (-not (Test-Path $includeScript)) {
    throw "Flutter did not generate .android/include_flutter.groovy. Run flutter doctor and try again."
}

Write-Host ""
Write-Host "The Day Glass Flutter module is ready."
Write-Host "Return to Android Studio, Sync Project with Gradle Files, select glassDebug, and run."
