# ProxyScanner Phase 03 — URL Import

**Status: code and tests written, NOT compiled, NO test executed, NO APK.** This environment has no network, Gradle,
Android SDK or Kotlin compiler, and `gradle-wrapper.jar` is still absent (not fabricated; `tools/bootstrap-wrapper.sh` unchanged).

## What was added (reusing existing code)
- `RemoteFetcher` / `FetchResult` (contract) and `HttpsFetcher` (core, pure JVM): https only via the **existing** `UrlSyntaxValidator`
  on the first URL and on every redirect (http downgrade, user info, local/private literals rejected); host must resolve only to public
  addresses (`PublicAddressPolicy`); max 3 redirects; connect 10 s / read 15 s / total 30 s; body read in chunks and abandoned above
  `ImportLimits.maxBytes` (declared Content-Length checked first); cancellation closes the connection to abort a blocked read.
- `HttpTransport` interface + `UrlConnectionTransport` (app; `HttpURLConnection`, no new dependency, redirects disabled, platform TLS checks).
- `DefaultConfigImporter` now handles `ImportSource.RemoteUrl` through an optional `fetcher` and **reuses** its decoding/parsing path
  (same UTF-8/NUL/empty checks, same parser, same counts: imported, duplicates, unsupported, invalid). Without a fetcher it still returns `NOT_IMPLEMENTED`.
- `ScannerViewModel.onImportUrl()` / `cancelImport()` share one `finishImport` with file import. Only the host name is shown
  (`DisplayNames.hostOf`); path, query and URL are never stored in UI state or messages, and nothing is logged.
- UI: "Import from URL" button (enabled only for a valid URL and when idle), an "Import status" card with a Cancel button.
- Manifest: `INTERNET` permission (the only one). Version 0.3.0-phase3.

## Tests added (fake HTTP only, no live server): 41 new `@Test` methods, 137 declared in total
`HttpsFetcherTest` (22: success, 204, URL rules, private DNS, redirects incl. relative/http/private/too many, size limit at/over,
HTTP errors, connect/read timeout, total deadline, cancellation, no URL in results), `PublicAddressPolicyTest` (5),
`DefaultConfigImporterTest` (+9 remote: counts, base64, limit passed, failure mapping, empty, bad encoding, HTML/malformed, no leaks),
`ScannerViewModelTest` (+5: host-only label, failure message, invalid URL never imports, cancel, second import ignored).
HttpsFetcherTest uses `runBlocking` (real time) because the fetcher uses real worker threads and `withTimeout`.

## Checks actually executed
| Command | Result |
|---|---|
| `sh ./gradlew test`, `sh ./gradlew assembleDebug` | FAILED, exit 1: `gradle-wrapper.jar is missing` (environment, not a code error) |
| `curl https://services.gradle.org` | could not resolve host |
| `python3 tools/verify_phase01.py` (runs `verify_skeleton.py`) | 707 passed, 0 failed. Static text/structure checks only |
| Java translation of the address-policy rules on JDK 21 (javac) | 21/21 expectations matched, incl. IPv4-mapped `::ffff:10.0.0.1`, CGNAT, ULA; `URI.resolve` gave the redirect targets the tests expect. This validates the logic, not the Kotlin code |
| Kotlin compilation, 137 JVM tests, 4 instrumented tests | **NOT RUN** |

Verifier changes (deliberate): the manifest rule now allows exactly one permission, `INTERNET`; the "no network API" rule skips only `app/UrlConnectionTransport.kt`; the `java.net.Socket` pattern got a word boundary so `SocketTimeoutException` is allowed.

## Unverified risks / limits
- Kotlin never compiled; coroutine details (watcher closing the connection on cancel, `TimeoutCancellationException` mapping) and the new threaded tests are reasoned, not run.
- DNS rebinding is not fully excluded (checked address is not pinned); cancellation during a connect in progress takes effect at the connect timeout.
- Plain `http://` stays rejected (decision D-06 open). `Content-Encoding` gzip is decoded by the platform; the size limit counts decoded bytes.
- No UI test for the new button/status card (instrumented tests not run anyway); JDK 21 here vs pinned JDK 17.

## To build (network, JDK 17, Android SDK platform 35)
`sh tools/bootstrap-wrapper.sh` → set `ANDROID_HOME` → `./gradlew test` → `./gradlew assembleDebug` (APK: `app/build/outputs/apk/debug/app-debug.apk`).
Manual check once built: serve a text file of synthetic links over https and import it by URL; cancel a slow download.
