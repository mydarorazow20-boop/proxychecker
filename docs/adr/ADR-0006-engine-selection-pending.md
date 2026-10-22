# ADR-0006: Protocol engine selection

Status: **Proposed (Phase 04 recommendation) – not accepted, nothing integrated**

## Context
`docs/02-engine-evaluation.md` (Phase 04, researched 2026-10-21) compares Xray-core
via libXray, sing-box (libbox), v2fly/v2ray-core, shadowsocks-rust, an own gomobile
wrapper, a packaged executable and an own Kotlin implementation, with evidence tags
[V]/[T]/[I]/[U]. No candidate was built or run; the sandbox has no network or
toolchain. No protocol has reached L3 (verified by this project's own test).

## Proposed decision
Use **Xray-core through libXray** (libXray MIT [V], Xray-core MPL-2.0 [V]; VLESS,
VMess, Trojan, Shadowsocks implementation packages present upstream [V]; Android
minimum API 21 [V]; in-process JSON API [V]) as the single engine. Generate Xray
JSON from `ProxyConfig`; never hand libXray share links (it cannot parse the legacy
VMess link format our parser accepts [V]).

Rejected for now: sing-box (GPL-3.0-or-later plus a name-use clause [V] would make the
app copyleft [I]); v2fly-core (no Android integration evidence); shadowsocks-rust
(one protocol only). Plan B: a thin own gomobile wrapper around Xray-core.

## Conditions (spike gates, see doc 02 section 5)
Reproducible pinned AAR build; license/NOTICE audit of Go dependencies; APK size
measured; wave-based session design proven against the one-managed-instance limit
[V]; L3 test per protocol using an out-of-process server; cancellation and resource
checks. If a gate fails, revisit Plan B or reopen this ADR.

## Consequences
+ One engine, permissive wrapper, broad transport coverage.
- libXray has few maintainers, no API stability guarantee and tracks only the latest
  Xray-core release [V]: pin exact tags, keep `ProxyEngine` as the seam.
- `ProxyEngine`/`ProxySession` timing and lifecycle semantics will likely need
  revision (batch/wave, `establishedInMs`); that change belongs to the integration
  phase and its own ADR.
- Hysteria2 stays undecided (Xray has a `hysteria` package [V], details unverified);
  TUIC is not available in Xray [V].
- `supports()` must stay false everywhere until L3 evidence exists, including for
  `TransportType.HTTP2`, which the bundled Core probably no longer supports [V/I].

## Phase 05 update (2026-10-21)
Still **Proposed**. The spike gates were not met: no pinned AAR could be obtained or built, no
Go-dependency license audit, no size measurement, no L3 test. See `ADR-0009-engine-integration-blocked.md`.
No dependency was added.
