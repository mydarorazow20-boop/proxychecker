# ADR-0007: Navigation with a single state value, no navigation library

Status: Accepted (Phase 01). Resolves open decision D-13 for the current scope.

## Context
ADR-0002 deferred a navigation library until a second screen exists. Phase 01 adds
three destinations: Home, Scan Results and Settings. The flow is flat (Home is the
only hub), there are no arguments, no deep links and no nested graphs.

## Decision
`app/ProxyScannerApp.kt` keeps the current destination in one `rememberSaveable`
enum (`Screen`) and switches on it. `BackHandler` returns to Home from the other two
screens. State shared by the screens lives in one Activity-scoped `ScannerViewModel`.

## Consequences
+ No extra dependency, no extra Gradle surface, trivially testable state.
- No back stack beyond "Home", no deep links, no per-destination ViewModel scoping.
- Revisit (Navigation-Compose or similar) when a screen needs arguments, a nested
  flow (for example a result detail view in the results phase) or deep links.
