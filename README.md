# ProxyScanner – Phase 04 (engine evaluation, docs only); app code and version unchanged from Phase 03 (0.3.0-phase3)

Native Android app (Kotlin, Compose, Material 3) for testing proxy
configurations **you are authorized to use**: import → parse → real connection
through a protocol engine → latency and bounded throughput → sort → export.

**Phase 04 (engine evaluation) = documentation only.** `docs/02-engine-evaluation.md`, ADR-0006 and
`docs/07-dependency-license-inventory.md` now hold an evidence-tagged comparison and a recommendation
(Xray-core via libXray, **proposed, not integrated**). No source, Gradle or catalog file changed; no
engine, connection, latency or speed test exists; no protocol is supported. Build/test status is unchanged
(never compiled, `gradle-wrapper.jar` still absent). See `PHASE-04-REPORT.md`.

**Phase 03 (URL import) changes, on top of Phase 02:** the URL field now has an "Import from URL" button. The download
(`HttpsFetcher` over `UrlConnectionTransport`, the only class that opens a connection) accepts https:// only, validates
every redirect hop with the existing `UrlSyntaxValidator`, rejects hosts that resolve to private/local addresses, applies
connect/read/total timeouts and a hard size limit (`ImportLimits.maxBytes`, 5 MiB), supports cancellation, and hands the
text to the existing parser/importer. Only the host name is ever shown; URLs are never logged. Manifest: `INTERNET` is
the only permission. No proxy connectivity, latency/speed test or export was added. See `PHASE-03-REPORT.md`.

**Phase 02 (UI foundation) changes, on top of Phase 01:** the Home, Results and Settings screens and state-based
navigation already existed; Phase 02 adds an always-visible "engine unavailable" notice on Home, keyboard
(IME) padding so the URL field stays visible, an updated version/phase notice, and 4 instrumented Compose UI
tests (`app/src/androidTest`, **not run**: they need a device/emulator). No scanning, URL download or export was added.
See `PHASE-02-REPORT.md`.

**Phase 01 = app shell, local-file import and link parsing.** What works in code:
- Home, Scan Results and Settings screens (dark-first Material 3, state-based navigation, ADR-0007).
- Import through the system file picker (Storage Access Framework; no permissions) with
  success, cancel and error states (empty file, unreadable file, invalid UTF-8, binary data, too large, no usable entries).
- Conservative parser for `vless://`, `vmess://`, `ss://`, `trojan://` links (plain or Base64 list),
  de-duplication by fingerprint, redacted error messages. A parsed link only has a valid **format**;
  it is never reported as online or working.
- A validated URL input field (in Phase 01 nothing was downloaded; Phase 03 adds the download, see above).
- An honest scan placeholder: no engine is integrated, so Start Scan is disabled and the app says
  "Scanning engine will be integrated in a later phase." No latency, speed or success numbers are ever shown.

**Not in Phase 01:** proxy engine, real connections, probing, throughput, background scanning,
export, persistence, JSON/subscription formats, (remote URL fetching arrived in Phase 03). No protocol is claimed as supported
(`docs/01-technical-design.md`, section 2). Scope decisions: `docs/adr/ADR-0008-phase-01-scope-and-roadmap-mapping.md`.

## Build status – read this first
| Item | Status |
|---|---|
| Static checks (`tools/verify_phase01.py`, which also runs `tools/verify_skeleton.py`) | run in the authoring environment; see `PHASE-01-REPORT.md` |
| Unit tests (`:app:testDebugUnitTest`, 137 `@Test` methods declared) | **NOT RUN** – no Gradle/Android SDK/Kotlin compiler/network in the authoring environment |
| Instrumented UI tests (`:app:connectedDebugAndroidTest`, 4 tests) | **NOT RUN** – need wrapper, SDK and a device/emulator |
| Debug APK (`:app:assembleDebug`) | **NOT RUN** – no APK exists |
| Android lint (`:app:lintDebug`) | **NOT RUN** |
| `gradle/wrapper/gradle-wrapper.jar` | **NOT INCLUDED** – cannot be produced offline; generate it with step 2 below |

The Kotlin sources were written and reviewed without ever being compiled. Treat the project as
**unbuilt**: the first Gradle run may report compile errors or failing tests that must be fixed first (risk R-15).

## Prerequisites
- **JDK 17** (AGP 8.7's documented minimum and default). Newer JDKs are not
  verified for this project.
- **Android SDK** with platform **android-35** (AGP 8.7 supports API 35 at most).
  AGP's default SDK Build Tools are 34.0.0; Gradle can download missing SDK
  components if the licenses are accepted. Point to the SDK with `ANDROID_HOME`
  or a `local.properties` file containing `sdk.dir=/path/to/Android/sdk`
  (the file is git-ignored). Accept licenses: `sdkmanager --licenses`.
- **Gradle 8.9** (pinned in `gradle/wrapper/gradle-wrapper.properties`; the
  minimum for AGP 8.7). Kotlin 2.0.21 is documented as supported with
  Gradle 8.7–8.10.
- **Network access on the first build** (Gradle distribution, Maven Central,
  Google Maven). Afterwards `--offline` works once everything is cached.
- ~2 GB free RAM for Gradle (`org.gradle.jvmargs=-Xmx2048m` in `gradle.properties`;
  lower it on small machines).

Pinned toolchain (details and sources: `docs/08-toolchain-and-verification.md`):
Gradle 8.9 · AGP 8.7.3 · Kotlin 2.0.21 (Compose compiler plugin of the same
version) · Compose BOM 2024.12.01 · compileSdk/targetSdk 35 · minSdk 24 · JVM target 17.

## 1. Unpack
The ZIP contains one folder, `ProxyScanner/`.

## 2. Gradle Wrapper (one-time)
The wrapper JAR is not in the ZIP. `gradlew`/`gradlew.bat` here are labeled
stopgap launchers that fail with a message while the JAR is missing. Create the
official wrapper (JAR, `gradlew`, `gradlew.bat`, properties) with network access:

```
sh tools/bootstrap-wrapper.sh
```
The script downloads Gradle 8.9 and its published `.sha256`, refuses to continue on
a checksum mismatch, runs `gradle wrapper` from that distribution in an empty
temporary folder, and copies the generated official files into the project
(also pinning the distribution checksum in `gradle-wrapper.properties`).
Needs `curl`, `unzip`, `sha256sum` or `shasum`, and JDK 17+. On Windows run it from
Git Bash/WSL, or run `gradle wrapper --gradle-version 8.9 --distribution-type bin`
with a locally installed Gradle 8.9.

Alternative without the script: `gradle wrapper --gradle-version 8.9
--distribution-type bin` with any local Gradle.

Check: `./gradlew --version` should report Gradle 8.9.

## 3. Build, test, lint
```
./gradlew :app:testDebugUnitTest      # 137 tests declared (parser, importer, URL, HTTPS fetcher, address policy, models, ViewModel, redaction)
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:lintDebug              # Android lint
python3 tools/verify_phase01.py       # static structure/honesty checks (no Gradle needed)
python3 tools/verify_skeleton.py      # Phase 0 static checks (also run by the line above)
```
Expected outputs (standard AGP locations, not yet observed for this project):
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`

Install on a device/emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
Expected launch result (not yet observed): a dark screen titled "ProxyScanner" with cards
"Import configurations", "Configuration URL", "Scan" and "Results". Start Scan is disabled and says
"Import at least one valid configuration to enable scanning."; after importing a valid file it stays disabled with
"Scanning engine will be integrated in a later phase." Try the import with a UTF-8 text file containing
synthetic links such as `trojan://secret@host.example:443#test` (do not commit real configurations).

If something fails, report the exact task and error text: Repair any drift first (risks R-01 and R-15).

## Known limitations
- Never built or run by the author (see table above).
- Parser/importer exist but no engine, probing, scanning or export: no protocol is supported.
- JSON/subscription files and VMess URI-style links are reported as unsupported; SS/VLESS/Trojan options outside the
  modelled set (plugins, kcp/quic transports, non-`none` VLESS encryption) are reported as unsupported.
- URL import: https only; every hop is checked for private/local addresses, but the checked address is not pinned for the connection (the platform resolves again), so DNS rebinding is not fully excluded. Cancellation closes the connection; a connect that is still in progress ends at the connect timeout.
- Imported configurations are kept in memory only and are lost when the process ends.
- No launcher icon; UI strings are English-only.
- Lint warnings are expected, for example no launcher icon and dependency
  versions older than current releases; they are not errors.
- `INTERNET` is the only permission, used for URL import. Plain http:// is not accepted (decision D-06 remains open).
- `applicationId`/package `com.example.proxyscanner` is a placeholder.
- Dependency pins are conservative, not the newest (ADR-0004, risk R-14).
- Building on ARM64/Termux typically needs an ARM-native `aapt2` via
  `android.aapt2FromMavenOverride` (documented workaround, not verified here, R-12).

## Layout
`docs/` (design, engine evaluation, roadmap, testing strategy, structure, risks,
license inventory, toolchain/verification, ADRs 0001-0008), `PHASE-01-REPORT.md` · `app/` (Kotlin sources, tests,
resources) · `gradle/` (version catalog, wrapper properties) · `tools/`
(`verify_phase01.py`, `verify_skeleton.py`, `generate_manifest.py`, `bootstrap-wrapper.sh`) ·
`MANIFEST.md` (every file with size and SHA-256).

## Next
First: a real build and test run on a machine with network and the Android SDK. Then the next phase starts only
when requested (`docs/03-roadmap.md`, ADR-0008): engine selection and integration.

## Phase 05 — engine integration foundation
No engine is integrated: the recommended Xray-core/libXray could not be verified or obtained from a
legitimate repository (`docs/adr/ADR-0009-engine-integration-blocked.md`). Added instead:
`core/engine/EngineLifecycleManager.kt` (initialize / start session / stop / error reporting, engine-neutral)
and `.github/workflows/android-build.yml`. Unit tests, `assembleDebug` and every proxy connection: **NOT RUN**
in the authoring sandbox. See `PHASE-05-REPORT.md`.
