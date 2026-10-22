# ADR-0001: Native Android with Kotlin, Gradle Kotlin DSL and Jetpack Compose

Status: Accepted (Phase 0)

## Context
The app must run real proxy connections through an engine, use bounded
concurrency and cancellation, and work with command-line Gradle builds. A
web-only app or WebView shell cannot host native engine libraries or provide the
required control over sockets and lifecycle.

## Decision
Native Android: Kotlin, Gradle Kotlin DSL with a version catalog, Jetpack Compose
with Material 3, coroutines/Flow, ViewModel + repository-style layering.

## Consequences
+ First-class access to Storage Access Framework, share sheet, native libraries.
+ Compose keeps UI code small and testable.
- Requires JDK 17 and the Android SDK to build.
- Compose/AGP/Kotlin versions must be kept compatible (see ADR-0004, R-01).
