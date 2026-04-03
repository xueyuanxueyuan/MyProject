---
name: vfox-toolchain
description: Ensure correct SDK toolchain via vfox (JDK/Node/Maven) before building or running in the Gjj workspace. Use when the user mentions vfox, switching JDK, java@17.0.2+8, Maven builds, packaging/compiling, or toolchain/version mismatch.
---

# vfox Toolchain (Gjj)

## Quick rules
- **Before any build/package**: switch SDKs with vfox, then **verify versions**, then run build.
- **Backend default**: JDK 17 is required for Gjj backend services.

## Backend (Java) standard workflow
1. Switch JDK to the required version:
   - `vfox use java@17.0.2+8`
2. Verify:
   - `java -version` (must show 17.x)
   - `mvn -v` (must show Java 17)
3. Build with Maven:
   - Prefer module build: `mvn -pl <module> -DskipTests clean package`
   - Or reactor build: `mvn -DskipTests clean package`

## Node.js (when a frontend/tooling build is involved)
1. Switch Node.js if the project specifies a version:
   - `vfox use nodejs@<version>`
2. Verify:
   - `node -v`
   - `npm -v` / `pnpm -v` (as applicable)
3. Run the project build command.

## Failure handling checklist (toolchain-related)
- If compilation fails with “unsupported class version”, “release version”, or “invalid target release”:
  - Re-run `vfox use java@17.0.2+8`
  - Confirm `java -version` and `mvn -v`
  - Then rebuild.
- If different terminals show different Java versions:
  - Treat terminals as independent; switch/verify per terminal session.

## Output expectations
When applying this skill, always output:
- The exact vfox switch command(s) used
- The verification command(s)
- The build command(s)
- Any detected mismatch and the corrective action

