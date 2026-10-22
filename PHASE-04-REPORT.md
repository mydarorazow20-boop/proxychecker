# ProxyScanner Phase 04 — Proxy Engine Evaluation (documentation only)

**Status: evaluation written; no engine integrated; no code changed; nothing compiled or run.**
Network from the sandbox: none (DNS fails). Facts were gathered with the web tool and are tagged
**[V]** upstream page read, **[T]** third party, **[I]** my inference, **[U]** unverified.

## Recommendation (proposed, not accepted)
**Xray-core through libXray** (Go, gomobile AAR), pinned tags, Xray JSON generated from `ProxyConfig`.
Plan B: a thin own gomobile wrapper around Xray-core. ADR-0006 stays **Proposed** until the spike gates pass.

| | Xray-core + libXray | sing-box (libbox) | v2fly/v2ray-core | shadowsocks-rust |
|---|---|---|---|---|
| License | libXray **MIT**, Xray-core **MPL-2.0** [V] | **GPL-3.0-or-later + name-use clause** [V] | MIT [V] | MIT [T] |
| VLESS/VMess/Trojan/SS | all four implementation packages upstream [V] | reported [T] | not examined | SS only |
| Android/ARM | libXray min API 21, gomobile [V]; Xray-based v2rayNG ships 4 ABIs [T] | libbox AAR in GPL apps [T] | no evidence | Rust targets for 4 ABIs [V] |
| Maintenance | pinned to Xray tag v26.9.30 [V]; few maintainers, no API stability [V] | active [T] | not examined | active [T] |
| Verdict | **recommended** | rejected unless app goes GPL | no evidence | cannot cover brief |

## Genuine support (honest levels)
L1 (implementation package exists upstream) **[V] for VLESS, VMess, Trojan, Shadowsocks**. L3 (verified by this
project's own test through `ProxyEngine`) is **zero**. The support table in `docs/01` still says **No** for all four;
`UnavailableProxyEngine` is unchanged and reports `integrated = false`.

## Findings that affect the design (details: `docs/02-engine-evaluation.md`)
- libXray allows **one managed Xray instance per process** [V] → `open(config)` per config needs a wave design
  (one instance, one loopback inbound per outbound) or a contract change. Recorded as D-14, R-17.
- libXray's own link parser **does not support the legacy `vmess://base64(JSON)` format** [V] → generate Xray JSON
  from `ProxyConfig` instead of passing links.
- **VMess alterId > 0**: our parser has no such field, so such nodes would be tested as 0 (clients reject them [T]).
  Parser follow-up R-19 (not fixed here).
- `TransportType.HTTP2` probably has no engine support (HTTP/QUIC transports removed [V]) → R-20.
- `pingBatch` (≤5 configs, HEAD, delay only [V]) does not replace content-verified probes or throughput tests.
- Integration tests need an **out-of-process** Xray server (one managed instance per process) → R-22.
- TUIC: no package in Xray [V]. Hysteria: a `hysteria` package exists [V]; version/config unverified.
- APK size: **not measured**. Indicators only: Xray-based v2rayNG APKs ≈ 21–33 MB [T]; hypothesis that Go + Xray
  adds ≥ ~10 MB per ABI [I]. Spike gate 3.

## Files changed (docs and tools only)
`docs/02-engine-evaluation.md` (rewritten), `docs/adr/ADR-0006-engine-selection-pending.md`,
`docs/07-dependency-license-inventory.md` (engine candidates with SPDX and evidence; missing test dependencies added),
`docs/06-unresolved-decisions-and-risks.md` (D-01/02/10/11 updated, D-14, R-17..R-23), `docs/03-roadmap.md` (note),
`README.md` (Phase 04 paragraph), `tools/verify_phase04.py` (new), `PHASE-04-REPORT.md` (new), `BUILD-REPORT.md` (moved from the Phase 03 ZIP root into the project, content unchanged), `MANIFEST.md`.
**Unchanged:** every file under `app/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
`gradlew*`, `tools/bootstrap-wrapper.sh` (byte-compared with the Phase 03 ZIP; see below). No dependency added.

## Checks executed
| Check | Result |
|---|---|
| `python3 tools/verify_phase04.py` (docs honesty, no engine/dependency/binary added, placeholder intact) | 43 passed, 0 failed (static) |
| `python3 tools/verify_phase01.py` (includes `verify_skeleton.py`) | 707 passed, 0 failed (static only) |
| Byte comparison of `app/`, `gradle/`, root Gradle files, `gradlew*`, `tools/bootstrap-wrapper.sh` vs Phase 03 ZIP | identical |
| ZIP integrity (`unzip -t`); verifiers re-run on the extracted ZIP; every `MANIFEST.md` entry (112 files) matches size and SHA-256 | all OK |

## NOT RUN
Kotlin compilation, `./gradlew test`, `assembleDebug`, lint, instrumented tests, any engine build, any size
measurement, any connection. Reasons: no network, no Gradle, no Android SDK, no Go/Rust toolchain.
`gradle/wrapper/gradle-wrapper.jar` is still **absent** and was not fabricated (`sh tools/bootstrap-wrapper.sh`
creates the official one on a networked machine).

## Limits of this evaluation
Sources were web snapshots, not a local clone: licenses of Xray-core's transitive Go modules, the AAR's ABIs, SS cipher
coverage, Hysteria 2 details, flow values and the `HTTP2` mapping are **[U]**. Licensing remarks are not legal advice.

## Next (only when requested)
Engine-integration spike with the gates in `docs/02-engine-evaluation.md` section 5: pinned AAR build, license audit,
size, wave design, per-protocol L3 tests with an out-of-process server, cancellation and resource checks.
