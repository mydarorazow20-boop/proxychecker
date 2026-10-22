# Toolchain Compatibility and Verification Report (Phase 0 fix-01)

## 1. Pinned toolchain and compatibility evidence

| Component | Pin | Evidence |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Android Developers, AGP 8.7.0 release notes: max supported API level 35; minimum Gradle 8.9 (default 8.9); SDK Build Tools 34.0.0; JDK 17. 8.7.1–8.7.3 are patch releases listed on the same page. |
| Gradle | 8.9 | Equals AGP 8.7's minimum (above). Kotlin docs ("Configure a Gradle project"): Kotlin 2.0.20–2.0.21 are fully compatible with Gradle up to 8.6, and Gradle 8.7–8.10 are also supported, with one exception that concerns the Kotlin Multiplatform plugin (not used here). |
| Kotlin / Compose compiler plugin | 2.0.21 | Compose compiler Gradle plugin is versioned with Kotlin (same version used for both aliases in `libs.versions.toml`). Gradle's compatibility page states Gradle is tested with Kotlin 2.0.0 and newer. |
| JDK | 17 | AGP 8.7 minimum and default. Gradle docs: Gradle runs on JVM 17+. Newer JDKs (such as 21) are not verified for this project. |
| compileSdk / targetSdk | 35 | Within AGP 8.7's maximum API level 35. |
| Compose BOM, activity-compose, lifecycle, coroutines | 2024.12.01 / 1.9.3 / 2.8.7 / 1.9.0 | **Not verified** against current documentation in this fix; chosen as a mutually consistent late-2024 set. First real build will show any problem. |

Sources read during this fix (web search snapshots, October 2026): developer.android.com
AGP 8.7.0 release notes; kotlinlang.org "Configure a Gradle project"; docs.gradle.org
compatibility page. Newer releases exist (AGP 9.x requires Gradle 9.x and a newer
Kotlin Gradle plugin), and **no upgrade was made**: that would change the whole
toolchain and cannot be verified without a build.

## 2. Gradle Wrapper
Required files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
`gradle/wrapper/gradle-wrapper.properties`.

| File | State in this ZIP |
|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | present, pins `gradle-8.9-bin.zip` |
| `gradlew`, `gradlew.bat` | present but **stopgap launchers**, labeled as such; not the official scripts |
| `gradle/wrapper/gradle-wrapper.jar` | **absent** |

Why: the JAR is a binary released by Gradle. The authoring environment had no
network (DNS resolution to services.gradle.org, Maven Central, Google Maven and
GitHub failed), no Gradle installation, no copy of the JAR anywhere on disk, and no
package mirror. A hand-made substitute would be a fabrication, so none was made.
`tools/bootstrap-wrapper.sh` produces the official set on any machine with network:
it verifies the download against the checksum published by Gradle and generates the
files with Gradle's own `wrapper` task. Its control flow (checksum accepted /
mismatch rejected / files copied) was tested against a local test double, not
against the real Gradle download.

## 3. Environment limits in the authoring environment
- Network: none (DNS failure for all hosts tried).
- Java: OpenJDK 21 runtime with `jdk.compiler` module; no `javac` launcher.
- Gradle: not installed. Kotlin compiler: not installed. Android SDK: not installed
  (`ANDROID_HOME` unset); no `aapt2`, no NDK.

## 4. Results

| Check | Result |
|---|---|
| `tools/verify_skeleton.py` static consistency (Gradle/catalog/manifest/resources/imports/layering/doc phrases) | **PASSED** (see final run in the delivery report) |
| Manual compile review of every Kotlin file (declarations, imports, smart casts, value-class usage, Compose APIs, lifecycle factory signature, range types) | no problem found; **not a compiler result** |
| `RedactorTest` expectations evaluated by an equivalent Java-regex emulation run on JDK 21 (8 assertions mirroring the 6 tests' regex cases) | 8/8 passed; validates the regex logic only, not the Kotlin code or JUnit run |
| `./gradlew :app:testDebugUnitTest` | **NOT RUN** (no Gradle, SDK, network) |
| `./gradlew :app:assembleDebug` | **NOT RUN** |
| `./gradlew :app:lintDebug` | **NOT RUN** |
| Install/launch on a device or emulator | **NOT RUN** |

## 5. Remaining blockers (all need a machine with network and the Android SDK)
1. Obtain the official wrapper: `sh tools/bootstrap-wrapper.sh`.
2. Provide JDK 17 and Android SDK platform 35; accept SDK licenses.
3. Run the three Gradle tasks and fix whatever the real toolchain reports.
4. Record the observed Gradle/AGP/Kotlin versions and the three results in this file.

## 6. Phase 01 update
The environment used for Phase 01 had the same limits as Phase 0 (no network, no
Gradle, no Android SDK, no Kotlin compiler; JDK 21 runtime only). Therefore:
- `./gradlew test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`: **NOT RUN**.
- No APK was built. The Gradle Wrapper JAR is still absent (not fabricated).
- Static checks only: `python3 tools/verify_phase01.py` (runs `tools/verify_skeleton.py` too).
  They check file layout, resource references, imports of project symbols, bracket
  balance, forbidden APIs and permissions. They are **not** a compiler result.
- Toolchain pins were not changed (risk R-01/R-14 remain). One test-only dependency
  was added: `kotlinx-coroutines-test` (same version as coroutines 1.9.0), needed to run
  ViewModel tests on the JVM.
Full details and the exact results of the static checks: `PHASE-01-REPORT.md`.
