# ADR-0003: Protocol engine behind a replaceable interface with loopback sessions

Status: Accepted (contract may be refined during the engine spike)

## Context
Engine candidates are native (Go) libraries with unstable wrapper APIs
(`docs/02-engine-evaluation.md`). The UI and test logic must not depend on any
one of them.

## Decision
`ProxyEngine.open(config)` returns a `ProxySession` that exposes an
authenticated loopback SOCKS5/HTTP endpoint. `core/testing` sends probe traffic
through that endpoint. A placeholder `UnavailableProxyEngine` is the only
implementation in Phase 0 and always reports `ENGINE_UNAVAILABLE`.

## Consequences
+ Probe code is engine-independent and testable with a fake loopback proxy.
+ Engines can be swapped or combined (possibly across a process boundary, D-11).
- One local listener per session: ports, authentication and cleanup must be
  handled carefully (R-08).
- If the chosen engine offers a better integration style, this ADR is superseded.
