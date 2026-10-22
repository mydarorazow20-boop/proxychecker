# Proxy Scanner – Technical Design (Phase 0)

Status: Phase 0 design, updated in Phase 01. Parsing and local-file import exist in
code (never compiled or run so far); engine, probing, scanning and export are plans.
Where text says "will", it is a plan.

## 1. Objective and scope

Native Android app that
1. imports proxy configuration files or user-supplied URLs,
2. parses supported configuration formats,
3. validates connections through a protocol engine,
4. measures latency and download throughput separately,
5. sorts results,
6. exports successful configurations to files.

Intended use: testing configurations the user is authorized to use.

Non-goals: a VPN/TUN client, routing rules, a proxy server, traffic capture,
background scraping of public proxy lists, WebView-based UI, simulated results.

## 2. Honesty rule: what "supported" means

A protocol is **supported** only when (a) its parser exists, (b) a connection
engine that handles it is integrated, and (c) both are covered by passing tests.
The enum `Protocol` lists what the data model can *represent*; it is not a
support claim. Current status:

| Protocol | Parser | Engine | Supported? |
|----------|--------|--------|------------|
| VLESS | link syntax parser (Phase 01; unit tests written, **not yet run**) | none | **No** |
| VMess | link syntax parser (JSON-in-Base64 form; Phase 01; tests not yet run) | none | **No** |
| Shadowsocks | link syntax parser (SIP002 and legacy; no plugins; Phase 01; tests not yet run) | none | **No** |
| Trojan | link syntax parser (Phase 01; tests not yet run) | none | **No** |
| SOCKS5 | not started (later phase) | none | **No** |
| Hysteria2 / TUIC | not started (feasibility first) | none | **No** |

A parsed link only means the text has a valid format. It is never described in the
UI as online or working. Phase 01 UI shows: no engine integrated, no verified
protocols, and "Scanning engine will be integrated in a later phase."

`UnavailableProxyEngine` returns `ENGINE_UNAVAILABLE`; it can never succeed.

## 3. Architecture overview

```
 UI (Compose, feature/*)
        │  state / events
 ViewModels (coroutines, StateFlow)
        │
 core/testing  ── ScanOrchestrator ── ProxyTester ── ProbeClient
        │                                 │
 core/importer ── core/parser        core/engine (ProxyEngine ⇄ ProxySession)
        │                                 │
 core/model (pure data)  ◄── core/export  core/security (Secret, Redactor)
```

Dependency direction: `feature → core/*`; `core/testing → core/engine, core/model`;
`core/importer → core/parser, core/model`; `core/model` and `core/security`
depend on nothing else in the project. `core/*` must not import Android UI
classes (keeps it unit-testable on the JVM). Android-specific code
(ContentResolver, VpnService-free engine glue) is isolated behind interfaces.

Data flow of one scan: import → parse → normalize → validate → dedupe →
(`ProxyConfig` list) → orchestrator (bounded concurrency) → per config:
engine session → latency probes → optional bounded download → classification →
`ProxyTestResult` → results screen → export.

## 4. Packages

See `docs/05-module-structure.md`. Phase 0 uses **one Gradle module (`:app`)**
with strictly separated packages (ADR-0002). Refinement versus the brief:
`feature/import` is named `feature/imports` because `import` is a reserved
Kotlin keyword and cannot be used as a package segment.

## 5. Data models (skeletons in `core/model`)

- `ProxyConfig`: protocol, address, port, `Credentials` (all secrets wrapped in
  `Secret`), `TlsOptions`, `TransportOptions`, remark, optional `original`
  (a `Secret`). `fingerprint` is a hash of normalized fields (de-duplication,
  result correlation). `safeLabel()` yields protocol, port and a fingerprint
  prefix – no host, no credentials – for logs.
- `ImportResult`: configs, duplicates, unsupported entries, parse errors, stats.
- `ProxyTestResult`: status, `TestTimings`, `ConnectivityResult`,
  `ThroughputResult`, `FailureCategory`, start/finish timestamps, redacted detail.
  Its constructor **enforces** that `WORKING` needs a verified probe response and a
  measured latency, and that `FAILED`/`UNSUPPORTED` carry a failure category.
- `ScanSettings`: timeouts, concurrency, probe attempts, download limits,
  cancellation behavior, optional `ProbeEndpoint`. All values are range-checked
  with absolute maxima (16 concurrent tests, 25 MiB download, 60 s per timeout).
  There is no "unlimited" setting.

## 6. Engine abstraction (`core/engine`)

`ProxyEngine`: `initialize()` → `open(config, options)` (returns a
`ProxySession` or a categorized failure) → `close()`. A session exposes a
**loopback endpoint** (SOCKS5 or HTTP) through which the app sends probe
traffic, plus the time it took to establish. Contract highlights (documented in
KDoc): cancellation-aware, enforces connect timeout, idempotent close, never
logs credentials, `supports()` true only for verified combinations, loopback
bound with random port and authentication so other apps on the device cannot
use the proxy.

Why a loopback endpoint: Go-based engines (the main candidates, see
`02-engine-evaluation.md`) naturally expose local SOCKS/HTTP inbounds, and it
keeps `core/testing` independent of the engine. If a chosen engine offers a
direct dial API instead, a `ProxySession` adapter can wrap it; the contract
may be revised in the engine-integration phase (record in an ADR).

## 7. Test workflow (what each step means)

1. **Import** local file or fetch a URL (bounded, validated).
2. **Parse** supported formats; unknown schemes become `UnsupportedEntry`.
3. **Normalize and de-duplicate** by fingerprint.
4. **Validate schema** (required fields, port range, option combinations).
5. **Establish a real connection** via `ProxyEngine.open` → `connectMs`.
6. **Probe a controlled HTTP endpoint through the proxy** and check status code
   and, if configured, a body marker.
7. **Latency**: median of N small probes *after* the session exists → `latencyMs`
   and `firstByteMs`; independent of `connectMs`.
8. **Throughput**: bounded download (max bytes and max duration) → bits/s.
9. **Record** errors, cancellation, `startedAt`/`finishedAt`.
10. **Sort and export** successful configurations.

TCP/TLS success alone is never enough for `WORKING`. Exact status rules are in
`04-testing-strategy.md`.

Statuses: `PENDING`, `TESTING`, `WORKING`, `LIMITED_CONNECTIVITY`, `FAILED`,
`UNSUPPORTED`, `CANCELLED`.

## 8. Concurrency, bounds and cancellation

- Bounded concurrency: a semaphore/worker pool sized by `maxConcurrency`
  (default 4, absolute max 16).
- Per-step timeouts (`connectTimeoutMs`, `probeTimeoutMs`) plus an overall
  `perConfigTimeoutMs` (→ `PER_CONFIG_TIMEOUT`).
- Download test: `maxBytes` and `maxDurationMs`; the reader stops as soon as
  either is reached. No continuous or repeated large downloads.
- Cancel button cancels the scan's coroutine scope; sessions are closed in
  `finally` blocks; `CancellationException` is never swallowed. What happens to
  partial results is governed by `CancellationBehavior`.
- Whether scans may continue while the app is backgrounded (foreground service)
  is an open decision (D-08).

## 9. Import, URL handling and export

**Local files**: Storage Access Framework (`ACTION_OPEN_DOCUMENT`) – no storage
permission needed. Read with a size cap (`ImportLimits.maxBytes`, default 5 MiB),
entry cap and line-length cap.

**Formats planned**: TXT with one link per line; Base64 link lists; JSON
(structure to be defined per supported subscription format in Phase 2; no format
is claimed before it has a parser and fixtures).

**Remote URLs** (policy contract `RemoteUrlPolicy`):
- schemes: `https` always; `http` only if the user explicitly confirms (open
  decision D-06); everything else rejected;
- no userinfo in URLs;
- resolve the host and require *all* addresses to be public; reject loopback,
  link-local, RFC 1918, unique-local IPv6, multicast, unspecified, and
  IPv4-mapped IPv6 forms of those;
- connect to the address that was checked (prevents DNS rebinding);
  re-validate on every redirect, limit redirects (e.g. 3);
- response size cap and timeouts; no cookies; no credentials forwarded;
- error messages never contain the full URL or query string.

**Export** (`ConfigExporter`): TXT = original links of successful configs, one
per line; JSON = `configs` and `results` as separate arrays linked by
fingerprint. Delivery via Android share sheet or `ACTION_CREATE_DOCUMENT`.
Exports contain credentials by design; the UI must warn before sharing.

## 10. Security and privacy

- `Secret` wraps UUIDs, passwords, tokens, REALITY keys and original links; its
  `toString()` is `[REDACTED]`.
- `Redactor.redact()` removes `scheme://…` tokens, credential-like `key=value`
  pairs and standalone UUIDs from free text; unit-tested (`RedactorTest`).
- Rules: never log credentials or full URLs; error/diagnostic strings are built
  from enum codes plus redacted text; host names are not logged by default.
- `android:allowBackup="false"` so imported configurations are not copied into
  device backups.
- No analytics, no third-party network calls except the user's configured test
  endpoint and user-supplied import URLs.
- Persistence is **not decided** (D-07). Until it is, data lives in memory.
- Loopback proxy endpoints use random ports and authentication.

## 11. Failure taxonomy

`FailureCategory` (in `core/model/ProxyTestResult.kt`) separates configuration
problems (`INVALID_CONFIG`, `UNSUPPORTED_PROTOCOL`), engine problems
(`ENGINE_UNAVAILABLE`, `ENGINE_ERROR`), network/handshake problems
(`DNS_FAILURE`, `CONNECT_TIMEOUT`, `CONNECTION_REFUSED`,
`TLS_HANDSHAKE_FAILURE`, `AUTHENTICATION_FAILURE`, `PROTOCOL_ERROR`), probe
problems (`PROBE_TIMEOUT`, `PROBE_HTTP_ERROR`, `PROBE_CONTENT_MISMATCH`),
throughput problems (`THROUGHPUT_TOO_LOW`, `DOWNLOAD_INTERRUPTED`) and control
flow (`PER_CONFIG_TIMEOUT`, `CANCELLED`, `UNKNOWN`).

## 12. Android baseline

Kotlin, Gradle Kotlin DSL, Jetpack Compose, Material 3, coroutines, ViewModel.
`minSdk = 24` (tentative), `compileSdk/targetSdk = 35`, JVM 17. See ADR-0004 for
the reasoning and what must be re-checked once an engine is chosen.
