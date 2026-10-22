# ADR-0004: Tentative minSdk 24, compile/target 35, JDK 17, pinned toolchain

Status: Proposed – re-evaluate in the engine-integration phase

## Evidence
- Jetpack Compose requires at least API 21 (general knowledge, [U]).
- libXray's Android build documents a minimum of API 21 [V].
- Other engine candidates' minimum is not yet verified (D-01).

## Decision
- `minSdk = 24` for now. Reason: Network Security Configuration (needed for a
  per-domain cleartext policy, D-06) is available from API 24 [U – verify];
  API 24 also avoids legacy-device edge cases in TLS and file handling while
  staying below what the engine candidates require. Lowering to 21 remains
  possible if the engine supports it and the project wants wider reach.
- `compileSdk`/`targetSdk = 35`, JVM target 17.
- Toolchain pins in `gradle/libs.versions.toml`: AGP 8.7.3, Kotlin 2.0.21,
  Compose BOM 2024.12.01, Gradle 8.9. They are chosen to be mutually compatible,
  **not** to be the newest, and they were not resolved or built in Phase 0.

## Consequences
- Update the pins deliberately at the start of Phase 1 after a first green build.
- If the engine requires a higher minSdk, record it here and in the README.

## Toolchain compatibility evidence (added in Phase 0 fix-01)
See `docs/08-toolchain-and-verification.md`. In short: the Android developer
documentation for AGP 8.7 states minimum Gradle 8.9, JDK 17 and maximum API
level 35; the Kotlin documentation states that Kotlin 2.0.20-2.0.21 supports
Gradle 8.7-8.10 (with a caveat that applies only to Kotlin Multiplatform). The
pinned combination therefore stays inside documented ranges. It has still not
been built.
