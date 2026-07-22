param(
    [switch]$Installer
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

.\mvnw.cmd clean package dependency:copy-dependencies "-DincludeScope=runtime"

$packageRoot = Join-Path $root "target\package"
$inputDir = Join-Path $packageRoot "input"
$appContent = Join-Path $packageRoot "app-content"
$distDir = Join-Path $root "dist"

Remove-Item -LiteralPath $packageRoot -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $distDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $inputDir, $appContent, $distDir | Out-Null

Copy-Item -LiteralPath "target\cyvox-0.1.0-SNAPSHOT.jar" -Destination (Join-Path $inputDir "cyvox.jar") -Force
Copy-Item -Path "target\dependency\*.jar" -Destination $inputDir -Force

$ffmpegDir = Join-Path $root "ffmpeg"
if (-not (Test-Path (Join-Path $ffmpegDir "ffmpeg.exe")) -or -not (Test-Path (Join-Path $ffmpegDir "ffprobe.exe"))) {
    throw "ffmpeg.exe and ffprobe.exe must exist in $ffmpegDir before packaging."
}
Copy-Item -LiteralPath $ffmpegDir -Destination (Join-Path $appContent "ffmpeg") -Recurse -Force
New-Item -ItemType Directory -Force -Path (Join-Path $appContent "logs"), (Join-Path $appContent "reports"), (Join-Path $appContent "temp") | Out-Null

$commonArgs = @(
    "--name", "CYVOX",
    "--app-version", "0.1.0",
    "--vendor", "CYVOX",
    "--description", "Intelligent batch video compression desktop application.",
    "--input", $inputDir,
    "--main-jar", "cyvox.jar",
    "--main-class", "com.cyvox.app.CyvoxLauncher",
    "--dest", $distDir,
    "--app-content", $appContent
)

jpackage --type app-image @commonArgs

if ($Installer) {
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
        throw "WiX Toolset is required to build CYVOX_Setup.exe. Install WiX and make sure candle.exe and light.exe are on PATH."
    }
    jpackage --type exe @commonArgs --win-shortcut --win-menu --win-per-user-install --win-dir-chooser
}

Write-Host "CYVOX package output written to $distDir"
