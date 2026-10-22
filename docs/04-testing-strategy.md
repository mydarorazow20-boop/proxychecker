# Testing Strategy

## 1. Principles
- Tests use synthetic fixtures only (documentation-reserved hosts such as
  `host.invalid`, all-zero UUIDs). Real proxy configurations never enter the
  repository (`.gitignore` covers a private samples folder).
- Network tests run against servers started by the test itself on loopback.
  Tests never contact third-party hosts.
- A phase's tests must keep passing in later phases.
- If a test class cannot be run in the current environment, the phase report says so.

## 2. Test levels

| Level | Tooling | Covers |
|---|---|---|
| JVM unit tests (`app/src/test`) | JUnit 4 (present); `kotlinx-coroutines-test` added when first needed (new dependency → justify in ADR/inventory) | models and their invariants, parsers, normalizer, validator, redaction, URL policy, exporters, status classification, orchestrator (virtual time) |
| Local integration tests | JDK `com.sun.net.httpserver` as controlled test endpoint (no new dependency) | `ProbeClient`, bounded download, content verification, timeouts |
| Engine contract tests | Same contract suite run against every `ProxyEngine` | initialize/open/close, cancellation, timeout, no credential leakage in messages |
| Instrumented tests (`androidTest`) | AndroidX test, added with the first UI/engine phase | SAF import, engine native library loading on device ABIs, share/export intents |
| Manual checklist | Device | scan/cancel UX, rotation, process death, low-memory device |

## 3. Status classification rules (initial proposal – thresholds to be tuned)

Inputs: engine open result, `probeAttempts`, `probeSuccesses` with content
verification, optional download result, `ScanSettings`.

| Status | Rule |
|---|---|
| PENDING | Queued, not started. |
| TESTING | Started, not finished. |
| WORKING | Session opened **and** a majority of probe attempts (≥ ⌈attempts/2⌉) returned the expected status **and** passed the content check **and** latency measured **and**, if the download test is enabled, the bounded download completed or hit its limit with throughput ≥ `minBitsPerSecond`. |
| LIMITED_CONNECTIVITY | Session opened and at least one probe reached the endpoint, but WORKING is not met: minority of probes OK, content mismatch (possible interception/captive response), download failed/interrupted/too slow, or the download test could not run. |
| FAILED | Session could not be opened, or no probe succeeded. Always carries a `FailureCategory`. |
| UNSUPPORTED | No integrated engine handles this protocol/transport/option. No connection attempt was made. Carries `UNSUPPORTED_PROTOCOL` or `ENGINE_UNAVAILABLE`. |
| CANCELLED | The user cancelled before the config finished. Never mixed with FAILED. |

Per-config overall timeout → FAILED with `PER_CONFIG_TIMEOUT`.
TCP/TLS success without a verified HTTP probe response is at most FAILED/LIMITED,
never WORKING. The `ProxyTestResult` constructor already rejects WORKING without
verified connectivity and measured latency.

## 4. Test matrices

**Redaction** (`RedactorTest`, present): proxy links, subscription URL tokens,
key=value pairs, standalone UUIDs, unchanged plain text, `Secret.toString()`.
To add in later phases: errors thrown by parsers and engines never contain
fixture credentials (assert by scanning messages for the fixture secrets).

**Parser (Phase 1)**: per protocol – minimal valid, all options, missing field,
bad port, bad Base64, over-long line, duplicate with different remark, unknown
scheme, mixed list; limits (`maxEntries`, `maxBytes`).

**URL policy (Phase 2)**: `file://`, `ftp://`, `javascript:`, `http://user@host`,
`localhost`, `127.0.0.1`, `[::1]`, `10.x`, `172.16–31.x`, `192.168.x`,
`169.254.x`, `fc00::/7`, `::ffff:127.0.0.1`, decimal/hex/octal IPv4 spellings,
host resolving to mixed public+private, redirect to private address, redirect
loop, oversize body, slow body.

**Probe/throughput (Phases 4–5)**: expected status, wrong status, wrong body
marker, timeout, connection reset mid-body, limit by bytes, limit by duration,
zero-length body.

**Orchestrator (Phase 5)**: observed maximum parallelism ≤ `maxConcurrency`;
cancel mid-scan closes every session; one failing config does not stop others;
`CancellationException` propagates.

**Export (Phase 7)**: TXT contains only selected statuses and original links;
JSON has separate `configs` and `results` arrays linked by fingerprint; golden files.

## 5. What exists in Phase 0 and what was run
- `RedactorTest` (6 tests) is written. **It was not executed in the Phase 0
  environment** (no Gradle, Android SDK or Kotlin compiler available; network off).
- `tools/verify_skeleton.py` is a static consistency check (see README); it is not
  a compiler and does not replace `./gradlew test`.
