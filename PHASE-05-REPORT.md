# ProxyScanner Phase 05 — Engine integration foundation

**Outcome: engine integration BLOCKED (documented, not guessed). Lifecycle layer and CI workflow added.
Nothing was compiled, no unit test was run, no APK was built, no proxy connection was made.**

## Selected engine
**None integrated.** The Phase 04 recommendation (Xray-core through libXray) stays *Proposed* (ADR-0006).
`UnavailableProxyEngine` remains the only engine: `integrated = false`, `supports() = false`.

## Verified protocol support
| Protocol | Level reached |
|---|---|
| VLESS, VMess, Shadowsocks, Trojan | **L1** only: an implementation package exists upstream (read on upstream pages). **L3 (our own test through `ProxyEngine`) = 0.** |

So no protocol is claimed as supported; `docs/01` support table still says **No** for all four.

## Why the integration is blocked (details: `docs/adr/ADR-0009-engine-integration-blocked.md`)
- No Maven/Google Maven coordinate for libXray found. Its documented route is building the AAR locally
  (`python3 build/main.py android`; needs git, Go, gomobile, NDK).
- GitHub release pages show "Assets" counts but the asset names, ABIs and checksums were not readable, so no
  prebuilt AAR could be verified. An unofficial slimmed AndroidLibXrayLite build exists; rejected (unofficial binary).
- Exact tag pin unresolved: Phase 04 recorded a README pin of Xray-core v26.9.30; the release list read now shows
  libXray v26.7.28 as newest. Not reconciled.
- Go-module licenses unaudited; AAR ABIs unknown; no size measurement; no L3 test.
- Sandbox has no network, Go, NDK, Gradle, Android SDK or Kotlin compiler.
Hence **no dependency, AAR, native library or repository was added.**

## Licensing considerations
libXray MIT, Xray-core MPL-2.0 (file-level copyleft; notice/source obligations on redistribution), both from
upstream pages read in Phase 04; transitive Go dependencies **not audited** (must be done before any release);
sing-box remains rejected (GPL-3.0-or-later plus name clause). Not legal advice. Nothing new ships in the APK.
Workflow actions are referenced by major-version tag, not commit SHA; their licenses were not verified.

## What was implemented
- `core/engine/EngineLifecycleManager.kt`, `EngineLifecycle.kt`: engine-neutral lifecycle around `ProxyEngine`:
  `initialize()` (retryable after failure), `startSession()` (only when Ready/Running; unsupported configs rejected
  without calling the engine; sessions tracked; idempotent close), `stop()` (closes sessions then engine; runs to
  completion if cancelled; Stopped is terminal), `state` and `lastError` flows. Error text is redacted
  (`Redactor`) and length-limited; the manager never creates a session itself and never produces timings or results.
- `AppContainer`: exposes `engineLifecycle` (unused by the UI). UI and `ScannerViewModel` unchanged.
- `.github/workflows/android-build.yml`: static checks; JDK 17 (Temurin); official wrapper bootstrap only if
  `gradle-wrapper.jar` is absent (`tools/bootstrap-wrapper.sh`, checksum from Gradle plus a pinned SHA-256);
  `setup-gradle` (wrapper validation, cache); Android SDK + platform 35; `:app:testDebugUnitTest`;
  `:app:assembleDebug`; APK uploaded **only on success** (fails if no APK); test reports uploaded always; summary
  prints real step outcomes. Assumes the repository root is the project folder.
- Wrapper: `distributionSha256Sum` (Gradle 8.9, `d725d707…cecab`; matches two independent public sources, and the
  value Gradle publishes) added to `gradle-wrapper.properties`; `gradle-wrapper.jar` **not** present and not fabricated.

## Files changed
New: `core/engine/EngineLifecycle.kt`, `core/engine/EngineLifecycleManager.kt`,
`app/src/test/.../core/engine/EngineLifecycleManagerTest.kt` (21 tests, test doubles only),
`.github/workflows/android-build.yml`, `docs/adr/ADR-0009-engine-integration-blocked.md`, `tools/verify_phase05.py`,
`PHASE-05-REPORT.md`.
Modified: `app/.../AppContainer.kt` (+3 lines), `gradle/wrapper/gradle-wrapper.properties` (checksum line),
`tools/bootstrap-wrapper.sh` (pinned checksum check), `tools/verify_phase04.py` (jar check tolerates the real jar),
`docs/adr/ADR-0006-...md`, `docs/02-engine-evaluation.md`, `docs/07-dependency-license-inventory.md`, `README.md`, `MANIFEST.md`.

## Tests and checks actually executed
| Check | Result |
|---|---|
| `python3 tools/verify_phase01.py` / `verify_phase04.py` / `verify_phase05.py` (static text checks, not compiler/test results) | 731 / 43 / 53 passed, 0 failed |
| `sh -n tools/bootstrap-wrapper.sh`; run against a local mirror with a wrong archive | syntax OK; mismatch correctly rejected (exit 1) |
| `.github/workflows/android-build.yml` parsed with a YAML parser | parses |
| `unzip -t` on the delivered ZIP; verifiers re-run on the extracted copy; all 119 `MANIFEST.md` entries match size and SHA-256 | all OK |

## NOT RUN
Kotlin compilation, `EngineLifecycleManagerTest` and all other unit tests, `assembleDebug`, lint, instrumented tests,
the GitHub workflow itself, any engine build, any size measurement, any proxy connection, ping or speed test.
The Kotlin code and tests were written and reviewed by reading only; compile errors are possible.

## Build outcome
**Not built.** Blocked by the environment: no network (DNS fails for services.gradle.org, GitHub, Maven), no Gradle, no
Android SDK, no Kotlin compiler. `gradle-wrapper.jar` is still absent; the workflow creates it from the official
distribution on the runner. No APK exists. First real result will come from the first GitHub Actions run.

## Remaining blockers
1. First green build is unverified (also the Phase 0 dependency versions were never resolved).
2. Engine: build and pin the libXray AAR (record Go/gomobile/NDK versions, AAR SHA-256), audit Go licenses, measure size,
   then L3 per protocol against an out-of-process server (doc 02 section 5).
3. Possible `ProxyEngine` contract revision (one managed Xray instance per process).
4. Commit the official `gradle-wrapper.jar` (after `sh tools/bootstrap-wrapper.sh`) if you prefer not to bootstrap in CI.
5. Pin workflow actions to commit SHAs if desired.
