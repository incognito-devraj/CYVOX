# CYVOX

Compress Smarter. Store More.

CYVOX is a Java 21 + JavaFX desktop application for intelligent batch video compression on Windows. It scans folders recursively, reads video metadata with FFprobe, compresses batches with FFmpeg, supports pause/resume/cancel, and writes HTML/CSV/JSON reports after each completed batch.

## Status

The core desktop app is ready for local testing:

- Recursive video scanner and dashboard summary
- FFprobe metadata analysis
- Single-file and batch FFmpeg compression
- Pause, resume, and cancel controls
- Application logging in `logs/`
- Batch reports in `reports/`
- Windows app-image packaging with bundled FFmpeg runtime files

The optional `CYVOX_Setup.exe` installer requires WiX Toolset on Windows because `jpackage --type exe` depends on `candle.exe` and `light.exe`.

## Requirements

- Windows
- Java 21 JDK with `jpackage`
- Maven Wrapper from this repo
- `ffmpeg/ffmpeg.exe` and `ffmpeg/ffprobe.exe`

The local FFmpeg executables are intentionally not committed because they are large binary files. Put them in:

```text
ffmpeg/
  ffmpeg.exe
  ffprobe.exe
```

## Run From Source

```powershell
.\mvnw.cmd clean javafx:run
```

## Verify

```powershell
.\mvnw.cmd clean verify
```

## Build Portable Windows App

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1
```

After packaging, launch:

```powershell
.\dist\CYVOX\CYVOX.exe
```

## Build Installer

Install WiX Toolset first, make sure `candle.exe` and `light.exe` are available on `PATH`, then run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 -Installer
```
