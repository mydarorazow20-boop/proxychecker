# PHASE-01-REPORT – ProxyScanner, Android App Foundation

App version 0.1.0-phase1 (versionCode 2). Built on `ProxyScanner-Phase-00-FIXED.zip`.

## 1. Bottom line
- **No APK was built.** No Gradle, Kotlin compiler, Android SDK or network was available in the authoring environment.
- **`./gradlew test`: NOT RUN. `./gradlew assembleDebug`: NOT RUN. `:app:lintDebug`: NOT RUN.** `gradle-wrapper.jar` is not included and was not fabricated.
- 96 unit tests are written (declared with `@Test`) but **none has been executed**. No test result is claimed.
- All Kotlin was written and reviewed by hand without a compiler. Expect to fix some compile errors or failing tests on the first real build (risk R-15).
- What *was* run: Python static checks (`tools/verify_phase01.py`, which includes `tools/verify_skeleton.py`). Results are in section 5. They are not a compiler or test result.

## 2. Implemented (in code, unbuilt)
**App shell and navigation** – dark-first Material 3 UI; Home, Scan Results, Settings; navigation is one saved state value plus `BackHandler` (ADR-0007, no library). Layouts are single-column and scrollable for small phones.

**Home screen** – app info, "Import Configurations" button, https URL input with live validation, "Start Scan" button, scan status, results summary with a "View Results" button, Settings entry in the top bar, empty states and error cards.

**Import (SAF)** – `ActivityResultContracts.OpenDocument`; no permissions. `DefaultConfigImporter` reads at most 5 MiB (reader stops early), decodes strict UTF-8 (BOM removed), rejects empty, whitespace-only, NUL/binary/UTF-16 and invalid UTF-8 files, parses off the main thread, and treats "no usable configuration" as a **failure** with the parse report. Cancelling the picker changes nothing. Previously loaded configurations survive a failed import. File name is shown (sanitized). Coroutine cancellation is never swallowed.

**URL input** – `UrlSyntaxValidator`: https only, no user info, no spaces, host must contain a dot, no `localhost`, no IPv6 literals, no private/loopback/link-local/CGNAT/multicast IPv4 literals. **Nothing is fetched**; `RemoteUrl` imports return `NOT_IMPLEMENTED`.

**Parser** – plain link lists and Base64 link lists; `#`/`//` comment lines and blank lines skipped; limits for bytes, entries and line length. Link parsers: VLESS, VMess (Base64 JSON), Shadowsocks (SIP002 and legacy, no plugins), Trojan. Unsupported but well-formed things (JSON files, other schemes, kcp/quic transports, SS plugins, VMess URI-style links, VLESS non-`none` encryption) are counted as *unsupported*, never as parsed. Normalizer lower-cases hosts, computes a SHA-256 fingerprint (remark excluded) used for de-duplication. Messages are fixed text; no input, host, UUID or password is put into any message or log (there is no logging at all). Secrets are `Secret` values.

**Domain and state** – existing `Protocol` (= "ProxyProtocol"), `ProxyConfig`, `ImportResult` (extended codes) kept; new `ScanStatus` (Idle, InProgress, Complete, Cancelled, Failed), `ScanResult`, `ScanAvailability`; UI state `ImportUiState` (Empty, InProgress, Success, Failure, Cancelled) and `ScannerUiState`; one Activity-scoped `ScannerViewModel`.

**Honest scan placeholder** – `UnavailableProxyEngine` (`integrated = false`) and new `UnavailableScanOrchestrator` (only throws). Start Scan is enabled only when configurations exist **and** an engine is integrated **and** a test endpoint is configured; otherwise it is disabled with "Import at least one valid configuration…", "Scanning engine will be integrated in a later phase." or "A test endpoint must be configured…". The scan lifecycle code (progress, complete, cancel, failure) consumes `ScanEvent`s from the existing `ScanOrchestrator` contract and is exercised **only by unit tests with fakes**; the shipped app cannot reach it. The brief's "ProxyScannerEngine" is the existing `ProxyEngine` + `ScanOrchestrator` (ADR-0008); no duplicate interface was added.

## 3. File changes versus Phase 0
Modified: `README.md`, `app/build.gradle.kts` (version 0.1.0-phase1, test dependency), `gradle/libs.versions.toml` (+`kotlinx-coroutines-test`), `AndroidManifest.xml` (comment only; still no permissions), `strings.xml`, `themes.xml` (dark window theme), `AppContainer.kt`, `MainActivity.kt`, `Theme.kt`, `HomeScreen.kt`, `ImporterContracts.kt` (+codes, `ImportFailure.details`), `ImportResult.kt` (+parse error codes, KDoc), feature READMEs, `docs/01, 03, 05, 06, 08`, `tools/verify_skeleton.py` (proper literal stripping, ADR count `>= 6`).
Removed: `feature/home/HomeViewModel.kt` (replaced by `ScannerViewModel`).
Added main code: `app/{ProxyScannerApp,ContentResolverDocumentReader}.kt`; `core/parser/{LenientBase64,StrictUtf8,PercentDecoder,FlatJson,RawUri,LinkFields,BaseLinkParser,VlessLinkParser,VmessLinkParser,ShadowsocksLinkParser,TrojanLinkParser,DefaultFormatDetector,DefaultConfigNormalizer,DefaultConfigValidator,DefaultConfigParser}.kt`; `core/importer/{DocumentReader,DefaultConfigImporter,UrlSyntaxValidator,DisplayNames}.kt`; `core/testing/{ScanState,ScanAvailability,UnavailableScanOrchestrator}.kt`; `feature/imports/ImportSection.kt`; `feature/results/ResultsScreen.kt`; `feature/settings/SettingsScreen.kt`; `feature/scan/{ScannerViewModel,ScannerUiState,ImportIssues}.kt`; `feature/common/{SectionCard,Labels}.kt`.
Added tests (96 `@Test`): `LowLevelParsingTest` 11, `LinkParsersTest` 19, `DefaultConfigParserTest` 17, `DefaultConfigImporterTest` 14, `UrlAndNamesTest` 5, `DomainModelsTest` 6, `ImportIssuesTest` 2, `ScannerViewModelTest` 16, plus the existing `RedactorTest` 6; helpers `Fixtures`, `MainDispatcherRule`. Fixtures are synthetic (`.invalid` hosts, made-up UUIDs/passwords).
Added docs/tools: `ADR-0007`, `ADR-0008`, `tools/verify_phase01.py`, `PHASE-01-REPORT.md`; `MANIFEST.md` regenerated.

## 4. Tests actually run
None of the Kotlin unit tests. Covered by test code (not executed): valid schemes, malformed input, empty input, unsupported schemes, duplicates, Base64 lists, limits, URL validation, import failure modes, no-secret-in-messages checks, and ViewModel transitions (initial, import in progress/success/failure/cancel, scan placeholder, progress, complete, cancel, failure, import blocked while scanning).

## 5. Checks that were run (static only)
- `python3 tools/verify_skeleton.py` -> `821 passed, 0 failed, 821 checks total`
- `python3 tools/verify_phase01.py` -> `661 passed, 0 failed, 661 checks total (static checks only: nothing was compiled or executed; 96 @Test methods declared)`
- Checked: required files, no permissions, `allowBackup=false`, every `R.string.*` defined and used, no logging/network/persistence/TLS/random/sleep APIs in production code, no hard-coded latency/speed values in UI code, core has no Android/feature imports, imports of project symbols resolve, brackets balanced (strings, char literals and comments excluded), package names match directories, no build outputs, honest "NOT RUN" statements.
- `python3 tools/verify_phase01.py --zip ProxyScanner-Phase-01.zip` on the final archive -> 673 passed, 0 failed (the 12 extra checks inspect the ZIP: single top-level folder, key files present, no build outputs, caches, keystores or APKs, no `gradle-wrapper.jar`).
- NOT checked by any script: Kotlin syntax and types, Compose API signatures, test outcomes, resource merging, lint.

## 6. Known limitations
- Unbuilt and untested (above). Library versions and Compose API usage were not resolved against a real toolchain.
- No engine: nothing is connected, measured or exported. No protocol counts as "supported".
- Parser recognizes formats only; real-world link variants beyond the fixtures may be rejected or reported as unsupported.
- No JSON/subscription import, no remote fetch, no INTERNET permission, no persistence (data is lost when the process ends), no editable settings, English strings only, no launcher icon, always dark.
- `http://` URLs are rejected by the URL field (decision D-06 still open).
- Boundary rules between packages are checked by scripts, not by the compiler (ADR-0002).
- Termux/ARM64 builds usually need an ARM-native `aapt2` (R-12, unverified).

## 7. How to run the remaining checks locally
```
sh tools/bootstrap-wrapper.sh            # creates the official Gradle wrapper (needs network)
./gradlew --version                      # expect Gradle 8.9
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug             # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug
```
Send back the exact failing task and error text if anything fails; fixing compile or test drift is the first job of the next step.

## 8. Prerequisites for the next phase
1. A green `testDebugUnitTest` and `assembleDebug` on a machine with JDK 17, Android SDK 35 and network; record versions and results in `docs/08-toolchain-and-verification.md`.
2. Decide D-06 (cleartext), D-07 (persistence) and D-09 (formats) before remote import and results work.
3. For remote import: `INTERNET` permission, a bounded fetcher and a `RemoteUrlPolicy` implementation with DNS, redirect and size checks (test table in `docs/01`, section 9).
4. For scanning: engine selection and spike (ADR-0006, D-01/D-02/D-11), then `ProxyEngine`, `ProbeClient`, `ProxyTester` and `ScanOrchestrator` implementations and a configurable test endpoint (D-05).
5. Anonymized real-world link samples (no credentials) to extend the parser fixtures before any protocol is called supported.
