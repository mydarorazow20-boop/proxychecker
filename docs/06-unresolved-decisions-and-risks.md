# Unresolved Decisions and Known Risks

## Unresolved decisions

| ID | Decision | Needed by | Notes |
|---|---|---|---|
| D-01 | Protocol engine | Integration phase | **Phase 04 recommendation: Xray-core via libXray** (ADR-0006 *Proposed*; `02-engine-evaluation.md`). Accepted only after the spike gates pass |
| D-02 | Licensing of this app | before release | Recommended engine: libXray MIT + Xray-core MPL-2.0 [V] → no copyleft on app code expected [I]; sing-box would force GPL-3.0-or-later [V/I]. Needs the Go-dependency audit (spike gate 2) |
| D-03 | Final `minSdk` | Phase 3 | Tentative 24 (ADR-0004) |
| D-04 | Real `applicationId` and package name | before release | `com.example.*` is a placeholder |
| D-05 | Default controlled test endpoint (user-configured only vs hosted by the project, how the bounded payload is served, HTTPS and body marker) | Phase 4 | No default is shipped; scanning stays disabled without one |
| D-06 | Cleartext HTTP for URL import (reject, or allow per explicit confirmation via network security config) | Phase 2 | Manifest has no INTERNET permission. Phase 01 URL field rejects `http://` (conservative default, revisit with this decision) |
| D-07 | Persistence of imported configs/results (memory only vs encrypted storage) | before Phase 6 | Credentials at rest need a deliberate design |
| D-08 | Background scanning (foreground service and its type, notification, battery) | Phase 5 | Check current Android foreground-service rules at that time |
| D-09 | Subscription/JSON formats to support | Phase 2 | Only formats with fixtures and parsers are claimed |
| D-10 | Hysteria2/TUIC inclusion | Integration phase / 8 | Xray has a `hysteria` package, details unverified; **no TUIC package in Xray** [V] → TUIC out unless sing-box is accepted |
| D-11 | Single-process vs separate-process engine | Integration phase | libXray: one Go runtime per process and one managed Xray instance per process [V]; in-process wave design preferred, packaged-executable alternative kept open |
| D-12 | DI approach (manual container vs framework) | Phase 5 | Manual until it hurts |
| D-13 | Navigation library | — | **Decided in Phase 01 (ADR-0007):** none; single-state navigation. Revisit when screens need arguments or deep links |
| D-14 | `ProxyEngine` contract revision for the one-managed-instance limit (batch/wave API, meaning of `establishedInMs`) | Integration phase | Doc 02 section 3.1 items 1–2; needs its own ADR |

## Known risks

| ID | Risk | Mitigation |
|---|---|---|
| R-01 | **Still open after Phase 01:** nothing (skeleton, UI, parser, tests) has been compiled; Gradle/AGP/Kotlin pins were never resolved | First action of Phase 1: run `./gradlew :app:testDebugUnitTest :app:assembleDebug`, fix any drift, record versions |
| R-02 | Gradle Wrapper JAR is not in the ZIP (authoring environment had no network); the shipped `gradlew`/`gradlew.bat` are labeled stopgaps | `sh tools/bootstrap-wrapper.sh` generates the official wrapper with checksum verification (README, step 2); first action after that: `./gradlew --version` |
| R-03 | Engine wrapper API instability (libXray: no API stability guarantee [V]) | Replaceable `ProxyEngine`, pinned versions, contract tests |
| R-04 | Two Go runtimes in one process are unsafe [V] | One engine per process; process boundary if two are needed |
| R-05 | Copyleft engine license may constrain app licensing | Verify before integrating (D-02) |
| R-06 | Credentials leaking via logs, errors, backups, clipboard, exports | `Secret`, `Redactor`, allowBackup=false, export warning, tests asserting no fixture secret appears in messages |
| R-07 | SSRF via user-supplied URLs (private network access, DNS rebinding, redirects) | `RemoteUrlPolicy` rules and test matrix |
| R-08 | Loopback proxy endpoint usable by other apps on the device | Random port + authentication, loopback bind only |
| R-09 | Misclassifying captive/intercepted responses as working | Content verification against the controlled endpoint |
| R-10 | Battery, data and thermal cost of scans | Hard caps on concurrency, payload size, duration |
| R-11 | Native library size and ABI coverage grow the APK | Measure in spike; ABI splits |
| R-12 | Building on low-resource or ARM64 hosts (for example Termux on a phone): the Android Gradle plugin's default `aapt2` is built for x86-64 Linux hosts, so such hosts usually need `android.aapt2FromMavenOverride` pointing to a native aapt2 build; Gradle itself needs enough RAM (`org.gradle.jvmargs`) | Documented workaround, not verified in this project; consider building on a PC/CI and only editing on the device |
| R-13 | Test endpoint availability/abuse: probes must not hammer third-party servers | User-configured endpoint, bounded attempts, no default public endpoint |
| R-14 | Dependency versions in the catalog are older than current releases | Deliberate update at Phase 1 after a green build |
| R-15 | Phase 01 Kotlin/Compose code and its 96 unit tests were written without a compiler; compile errors or failing tests are likely until the first build | Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` first, fix what the toolchain reports, record the results in `PHASE-01-REPORT.md` |
| R-16 | Hand-written Base64/JSON/URI helpers could diverge from real-world share links | Unit tests with synthetic fixtures; extend with anonymized real samples before claiming protocol support |
| R-17 | libXray runs one managed Xray instance per process; `open(config)` per config cannot map 1:1 to instances [V] | Wave design with one loopback inbound per outbound (to verify); possible contract change (D-14) |
| R-18 | libXray: few maintainers, no API stability, only compatible with the latest Xray-core [V] | Pin exact tags, commit AAR checksum, keep `ProxyEngine` seam, Plan B own gomobile wrapper |
| R-19 | VMess links with alterId > 0 are parsed without that field and would be tested as alterId 0, producing misleading FAILED results (current Xray-based clients reject such nodes [T]) | Parser follow-up: mark `aid` > 0 as unsupported (not done in Phase 04) |
| R-20 | `TransportType.HTTP2` likely unsupported by the bundled Core (HTTP/QUIC transports removed [V]) | `supports()` returns false for it until proven; UNSUPPORTED result, never a fake attempt |
| R-21 | MPL-2.0 redistribution duties and unaudited transitive Go licenses [V/U] | License audit and NOTICE file before the first release with the AAR |
| R-22 | Loopback integration tests cannot host server and client in one process [V/I] | Out-of-process Xray test server (dev/CI host or device) |
| R-23 | APK size impact of the Go runtime + Xray is unmeasured (indicators only [T], hypothesis [I]) | Measure in spike gate 3; ABI filter/splits |
