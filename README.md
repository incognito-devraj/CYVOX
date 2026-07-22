# CYVOX

Compress Smarter. Store More.

CYVOX is a JavaFX desktop application for intelligent batch video compression on Windows. The project is being built milestone by milestone with a self-contained packaging target that bundles runtime dependencies and FFmpeg tooling.

## Current status

Milestone 1 is in progress:

- Maven project bootstrapped
- JavaFX application shell created
- Git repository initialized
- Build verification enabled

## Build

Use the Maven Wrapper once it has been generated:

```powershell
.\mvnw.cmd clean verify
```

Until then, the local bootstrap Maven binary in `.bootstrap/` can be used:

```powershell
.\.bootstrap\apache-maven-3.9.11\bin\mvn.cmd clean verify
```
