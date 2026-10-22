# ProxyScanner Phase 02 — App UI Foundation

**Status: code changes made, NOT compiled, NOT tested, NO APK.** The environment has no network, Gradle,
Android SDK or Kotlin compiler, and `gradle-wrapper.jar` is still absent (not fabricated).

## Finding
Phase 01 already contained the Phase 02 UI: Compose Home (title, Import Configurations, URL input, Start Scan,
results summary, Settings entry), Results and Settings screens, state-based navigation with BackHandler (ADR-0007),
a dark-only Material 3 theme, a `ScannerViewModel` + `ScannerUiState`, empty states and URL validation. Package name
(`com.example.proxyscanner`) and architecture are unchanged. I did not rewrite any of it.

## Changes (small)
| File | Change |
|---|---|
| `feature/home/HomeScreen.kt` | "Scanning engine will be integrated in a later phase." is now always shown while no engine is integrated (before, it appeared only after configs were loaded). Added `imePadding()` so the URL field is not hidden by the keyboard. |
| `res/values/strings.xml` | Home notice updated from "Phase 1" to "Phase 2: app UI foundation". |
| `app/build.gradle.kts` | versionName `0.2.0-phase2`, versionCode 3; `testInstrumentationRunner`; test-only deps: `ui-test-junit4` (BOM-managed), `androidx.test.ext:junit:1.2.1`, `androidx.test:runner:1.6.2`; `debugImplementation ui-test-manifest`. No runtime dependency added. |
| `app/src/androidTest/.../ProxyScannerAppUiTest.kt` (new) | 4 Compose UI tests: home controls + disabled Start Scan + engine message; URL validation message; Home→Results→Back (empty state); Home→Settings→Back. |
| `tools/verify_skeleton.py` | One regex now also accepts the `androidTest` source root (otherwise the new test failed the package check). |
| `README.md`, `MANIFEST.md` | Phase 02 note, build-status row, regenerated manifest. |

Not touched: parser, importer, engine/orchestrator placeholders, wrapper bootstrap script, `gradlew`, `gradlew.bat`.
No scanning, remote URL download or export was implemented; nothing fakes latency, speed or scan success.

## Checks actually executed
| Command | Result |
|---|---|
| `sh ./gradlew test` | FAILED, exit 1: `gradle-wrapper.jar is missing` (environment, not a code error) |
| `sh ./gradlew assembleDebug` | FAILED, exit 1: same reason |
| `curl https://services.gradle.org` | could not resolve host (no network) |
| `python3 tools/verify_phase01.py` (runs `verify_skeleton.py`) | 661 passed, 0 failed. Static checks only; nothing compiled or executed |
| Instrumented UI tests (4) / JVM unit tests (96) | NOT RUN |

## Unverified risks
- Kotlin/Compose code was never compiled; the new androidTest uses APIs by memory (e.g. `createAndroidComposeRule`, `assertIsNotEnabled`) and the test-dependency versions are not resolved.
- The URL test asserts the exact validator text "Plain http:// is not accepted. Use an https:// URL."
- Start Scan is disabled (not clickable) in this version, so the ViewModel's engine-unavailable failure path is only reachable by unit tests with fakes.
- JDK 21 here vs pinned JDK 17.

## To build (needs network, JDK 17, Android SDK platform 35)
1. `sh tools/bootstrap-wrapper.sh`  (official wrapper; verifies Gradle's SHA-256)
2. set `ANDROID_HOME`, accept licenses
3. `./gradlew test` · `./gradlew assembleDebug` · `./gradlew :app:connectedDebugAndroidTest` (device/emulator)

APK: none generated (would be `app/build/outputs/apk/debug/app-debug.apk`).
