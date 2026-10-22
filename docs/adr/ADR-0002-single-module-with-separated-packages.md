# ADR-0002: One Gradle module with strictly separated packages

Status: Accepted (Phase 0)

## Context
The brief lists app, core/*, feature/* as "modules or clearly separated packages"
and allows refinement. Multi-module Gradle adds configuration, slower
configuration time and more files to keep consistent, while the contracts are not
yet exercised by real code.

## Decision
Single `:app` module. Boundaries are enforced by package layout and the rules in
`docs/05-module-structure.md`. The `feature/import` package is named
`feature/imports` (reserved Kotlin keyword). No DI framework and no navigation
library until a second screen or a real need exists (D-12, D-13); wiring is a
manual `AppContainer`.

## Consequences
+ Smaller Gradle surface, faster builds, fewer moving parts to verify.
- Boundary violations are not compiler-enforced; review and (later) a lint/test
  that checks imports must catch them.
- Splitting into modules later is mechanical (packages map 1:1).
