# Phase Roadmap (Phases 1–8)

> **Status note (Phase 01, ADR-0008):** the requested Phase 01 ("Android App
> Foundation") covered roadmap phase 1 (parsing core, unit-tested but **not yet
> compiled or run**) and the local-file part of phase 2 (SAF import), plus the UI
> shell and navigation. Remote URL fetching, JSON formats and everything from
> phase 3 on are still open. **Phase 04 (engine evaluation, documentation only)** added a
> recommended engine (Xray-core via libXray, ADR-0006 *Proposed*) and a spike plan; no engine is
> integrated and no protocol is supported. Engine integration is the next engine step. The table below is the original plan.

Rules for every phase: do not claim a protocol is supported before its parser
and engine are integrated and tested; update the support table in
`01-technical-design.md`; keep earlier phases' tests passing; produce a ZIP of the
full project; report build and test results truthfully (including what could not
be run). A phase starts only when explicitly requested.

| Phase | Goal | Main deliverables | Exit criteria |
|---|---|---|---|
| 1 | Parsing core | `LinkParser` implementations for VLESS, VMess, Shadowsocks, Trojan share links; format detector (plain list, Base64 list); normalizer, validator, fingerprint, de-duplication; `ConfigParser` facade | JVM unit tests with synthetic fixtures (no real credentials) pass; error messages redacted; limits enforced |
| 2 | Import | SAF file import (TXT/JSON as defined by fixtures), `RemoteUrlPolicy` + bounded URL fetcher, import screen with error report, `INTERNET` permission, cleartext decision (D-06) | URL policy test table passes (private/loopback/redirect/rebinding cases); size caps tested; import works on a device |
| 3 | Engine spike and integration | Verified engine choice (ADR-0006), license inventory filled, AAR built, `ProxyEngine` implementation for the mandatory protocols that it actually handles, contract tests | Loopback test against a locally hosted server passes; support table updated only for what passed |
| 4 | Single-config testing | `ProbeClient`, `ProxyTester`, status classification, timings (connect vs latency), configurable test endpoint (D-05) | Classification matrix tests pass; a config is never `WORKING` without verified probe |
| 5 | Scan orchestration | Bounded-concurrency `ScanOrchestrator`, bounded download test, cancel button, progress, background policy (D-08) | Concurrency never exceeds the limit (tested with virtual time); cancel leaves no open sessions/ports |
| 6 | Results UI | Results list, status chips, filters, sorting by latency/throughput/status, detail view with redacted diagnostics | UI state tests; layouts checked on small and large phones |
| 7 | Export | TXT/JSON exporters, share sheet and Save As, credential warning | Exporter unit tests (golden files); export contains only selected statuses |
| 8 | Settings, hardening, release | Settings screen, SOCKS5 (if not done earlier), Hysteria2/TUIC decision per feasibility, R8 rules, signing, license screen, final QA | Release build passes lint and tests; inventory complete; known-issues list current |

Notes
- SOCKS5 is "later phase" in the brief; it is slotted into Phase 8 unless
  the chosen engine makes it trivial earlier.
- Hysteria2/TUIC are included only if Phase 3 evidence shows feasibility.
- Persistence (D-07) is decided before Phase 6 at the latest.
