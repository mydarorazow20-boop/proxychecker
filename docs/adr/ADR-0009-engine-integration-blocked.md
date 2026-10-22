# ADR-0009: Engine integration blocked in Phase 05; lifecycle manager added

Status: **Accepted** (about Phase 05 scope only). ADR-0006 stays **Proposed**.

## Context
Phase 05 asked to integrate the engine recommended in `docs/02-engine-evaluation.md`
(Xray-core through libXray), but only if protocol support, license, Android
compatibility and integration method are adequately verified, and only with
dependencies obtained from legitimate repositories.

## What was checked on 2026-10-21 (evidence tags as in doc 02)
- Protocol support: still **L1 only** [V] (implementation packages exist upstream).
  **L3 (this project's own test through `ProxyEngine`) is zero** for all four protocols.
- License: libXray MIT, Xray-core MPL-2.0 [V, Phase 04]. Licenses of the transitive Go
  modules compiled into the binary are **not audited** [U].
- Android: libXray documents minimum API 21 [V] and a release note "support android 16kb
  pagesize" (v26.3.27) [V]. ABIs of the produced AAR are **[U]**.
- Obtaining the binary: libXray's documented route is building it yourself with
  `python3 build/main.py android` (needs git, Go, gomobile, an NDK) [V]. No Maven Central or
  Google Maven coordinate for libXray was found. The GitHub release pages list "Assets"
  (8 for v26.7.28, 7 for v26.7.11, 2 for older tags), but the asset names, ABIs and
  checksums could not be read from the pages available to the authoring session, so no
  prebuilt AAR can be verified [U]. An unofficial slimmed AndroidLibXrayLite build exists
  [T]; it is not affiliated with XTLS and is **not acceptable** as a dependency.
- Pin: Phase 04 recorded that the libXray README pins Xray-core v26.9.30. The release list
  read in Phase 05 shows v26.7.28 as the newest libXray release. These could not be
  reconciled, so **no exact tag can be pinned** [U].
- Toolchain in the authoring sandbox: no network, no Gradle, no Android SDK, no Go, no NDK,
  no Kotlin compiler. The AAR cannot be built there.

## Decision
1. **Add no engine dependency, no AAR, no native library, no repository.** Doing so would
   mean guessing a coordinate or committing an unverifiable binary.
2. Add an **engine-neutral lifecycle layer** (`EngineLifecycleManager`, `EngineState`,
   `EngineError`) around the existing `ProxyEngine`. It works with any implementation and
   with the honest `UnavailableProxyEngine`; it never produces a session by itself.
3. `UnavailableProxyEngine` stays the only engine. `integrated = false`,
   `supports() = false`, the support table in `docs/01` stays **No** for every protocol,
   and no ping, latency or speed value is produced.
4. Add the GitHub Actions workflow so the build and unit tests can be run on a machine that
   has network access.

## What unblocks the integration (not done here)
Someone with network access runs the spike gates in doc 02 section 5, starting with: build the
libXray AAR from an exact upstream tag (record Go, gomobile, NDK versions and the AAR's
SHA-256), audit the Go module licenses, measure size per ABI, then prove one protocol at L3
against an out-of-process server. Only then is a dependency added and ADR-0006 closed.

## Consequences
+ The seam for the engine is exercised and tested without claiming anything about it.
- The manager's `startSession` semantics follow the current per-config `open` contract; doc 02
  section 3.1 (one managed Xray instance per process, wave design) may still require a
  contract revision in the integration phase.
