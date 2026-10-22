# ADR-0008: Phase 01 scope and how it maps to the Phase 0 roadmap

Status: Accepted (Phase 01)

## Context
`docs/03-roadmap.md` (Phase 0) planned "Phase 1 = parsing core" and "Phase 2 =
import". The Phase 01 brief that was actually requested is "Android App
Foundation": app shell, navigation, Storage Access Framework import, domain
models, ViewModel state, an honest scan placeholder AND a conservative parser for
VLESS, VMess, Shadowsocks and Trojan links with unit tests.

## Decision
The requested brief wins. Phase 01 therefore delivers the roadmap's parsing core
and the local-file half of its import phase, plus the UI shell. What is NOT done and
stays in later phases: remote URL fetching (`RemoteUrlPolicy` DNS/redirect checks,
INTERNET permission, D-06), JSON/subscription formats (D-09), any engine, probing,
orchestration, results beyond an empty state, export, persistence (D-07) and
editable settings.

Existing Phase 0 decisions are kept: package `com.example.proxyscanner`, one
`:app` module (ADR-0002), minSdk 24 (ADR-0004), the version catalog and pinned
toolchain, the `Secret`/`Redactor` rules (ADR-0005), and the replaceable engine
abstraction (ADR-0003).

Mapping of brief terms to existing names, to avoid parallel types:
- "ProxyProtocol" = `core.model.Protocol`
- "ProxyScannerEngine" = `core.engine.ProxyEngine` (sessions) plus
  `core.testing.ScanOrchestrator` (scan flow); `UnavailableProxyEngine` and
  `UnavailableScanOrchestrator` are the honest placeholders
- "ScanResult" = new `core.testing.ScanResult` (list of `ProxyTestResult` plus `ScanSummary`)
- "ScanStatus" = new `core.testing.ScanStatus` (Idle, InProgress, Complete, Cancelled, Failed)

## Consequences
Later phases need renumbering in `docs/03-roadmap.md` (done in Phase 01 as a
"status" column). The first real Gradle build is still outstanding (R-01).
