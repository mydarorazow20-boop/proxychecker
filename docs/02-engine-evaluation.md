# Protocol Engine Evaluation (Phase 04)

Date of research: 2026-10-21. Scope: choose, on paper, the engine for VLESS, VMess,
Shadowsocks and Trojan on Android. **Nothing was integrated, built or run in this
phase**; no source file of the app was changed.

## 1. Method, evidence tags, limits

The authoring sandbox has no network (DNS fails), no Gradle, no Android SDK, no Go,
no Rust. Facts come from pages fetched or searched through the web tool.

| Tag | Meaning |
|---|---|
| **[V]** | Read on the upstream project's own page (README, pkg.go.dev module page of the upstream module, LICENSE text) in this phase |
| **[T]** | Reported by a third party (package index, app store listing, downstream project's README) |
| **[I]** | My inference or plan; not a fact about the project |
| **[U]** | Unverified; general knowledge only |

**Levels of "supported"** (the brief asks not to claim support from documentation or
protocol names):

| Level | Meaning | Reached by any protocol? |
|---|---|---|
| L0 | Named in documentation | yes |
| L1 | An implementation package exists in the upstream source tree | yes, see 3.1 [V] |
| L2 | Used by a shipping Android client | engine yes [T]; per-protocol exposure [U] |
| L3 | Verified by this project's own test through `ProxyEngine` | **no, none** |

Only L3 permits the word "supported" in this project. The support table in
`01-technical-design.md` therefore stays "No" for every protocol.

## 2. Requirements taken from the existing project

From `core/engine/ProxyEngine.kt` (unchanged) and the Phase 01 parsers:
- `ProxyEngine.open(config, options)` returns a `ProxySession` exposing an
  authenticated loopback SOCKS5/HTTP endpoint; sessions may be open in parallel up
  to `ScanSettings.maxConcurrency` (default 4, max 16); `open` honors cancellation
  and the connect timeout; `supports()` only for verified combinations.
- Input is a normalized `ProxyConfig` (not a share link). Options the parsers can
  produce: VLESS (`encryption=none` only, flow, TLS or REALITY, transports tcp/ws/
  grpc/http2/httpupgrade); VMess (legacy `vmess://base64(JSON)` fields add, port, id,
  scy, net, type, host, path, tls, sni, alpn, fp; **no alterId field**); Shadowsocks
  (SIP002 and legacy forms; `plugin` links rejected); Trojan (TLS only).
- Engine must not log credentials; one process; ARM-first devices; low resource use.

## 3. Candidates

### 3.1 A. Xray-core through libXray (Go, gomobile AAR) — **recommended**

| Aspect | Evidence |
|---|---|
| Protocol implementations | Upstream module `github.com/xtls/xray-core` v1.260327.0 (published 2026-03-27, flagged on pkg.go.dev as not the latest) has packages `proxy/vless` (with `encryption`), `proxy/vmess` (with `aead`), `proxy/trojan`, `proxy/shadowsocks`, `proxy/shadowsocks_2022`, plus socks, http, hysteria, wireguard. **No tuic package.** [V] |
| Transports | `websocket`, `grpc`, `httpupgrade`, `splithttp`, `kcp`, `tcp`, `tls`, `reality`, `hysteria` packages exist [V]. libXray's notes say the bundled Core does not support the *removed HTTP/QUIC transports* [V]; our `TransportType.HTTP2` therefore probably has **no engine support** [I] and `supports()` must return false for it until the spike proves otherwise |
| License | Xray-core: MPL-2.0 (pkg.go.dev license field and README link to LICENSE) [V]. libXray: MIT [V]. libXray credits MetaCubeX age (BSD-3-Clause) [V]. Licenses of Xray-core's other Go dependencies: not audited [U] |
| Maintenance | libXray README pins Xray-core release tag `v26.9.30` (2026-09-30) [V]; libXray uses CalVer tags and mirrors SemVer tags [V]; 329 commits [V]. README warns: few maintainers, no API stability, only compatible with the latest Xray-core release [V]. Xray-core forked from v2fly-core [V] |
| Android / CPU | `python3 build/main.py android` (gomobile), **minimum Android API 21** [V]; requires git and Go [V] (NDK/gomobile: [U]). ABIs of the AAR: [U]. Xray-based v2rayNG ships separate arm64-v8a, armeabi-v7a, x86, x86_64 APKs [T] |
| Integration method | Single entrypoint `Invoke(requestJSON)` with `apiVersion: 3`; methods include `runXray`, `stopXray`, `testXray`, `pingBatch`, `getFreePorts`, `xrayVersion`, `getXrayState`; config is Xray JSON text, no file paths; request/response limited to 16 MiB; Kotlin/Java usage shown as `LibXray.invoke(...)` [V] |
| APK size | No measurement for this project. Indicator: Xray-based v2rayNG APKs are listed at roughly 21–33 MB [T] (includes its UI and extras [U]). Alpine's sing-box package (other engine) is 15.4–16.1 MiB packaged [T]. **Hypothesis to measure, not a fact:** Go runtime + Xray adds on the order of ten or more MB per ABI [I] |
| Offline build | Requires fetching Go modules and gomobile once; later builds can use the module cache / a committed AAR [I] |

**Per-protocol position (all L1 [V], none L3):**

| Protocol | Upstream implementation | Notes relevant to our parsers |
|---|---|---|
| VLESS | `proxy/vless`, `reality` transport [V] | Our parser accepts `encryption=none` only; flow values and `HTTP2` mapping to verify in spike [U] |
| VMess | `proxy/vmess` + `aead` [V] | **Legacy `vmess://base64(JSON)` links are not supported by libXray's own link parser** [V] → do not use libXray's converter; generate Xray JSON from `ProxyConfig`. v2rayNG's changelog states VMess nodes with alterId > 0 cannot be used [T]; our model has no alterId, so such nodes would be silently tested as alterId 0 [I] → follow-up in parser |
| Shadowsocks | `proxy/shadowsocks`, `shadowsocks_2022` [V] | Supported cipher list not read [U]; plugin links already rejected by our parser |
| Trojan | `proxy/trojan` [V] | TLS only, matches our parser |

**Fit to the `ProxyEngine` contract (findings, all [I] unless tagged):**
1. **One managed instance per process.** `pingBatch`/`testXray` reject an active
   `runXray` instance and take a lifecycle lock; Xray keeps DNS client and outbound
   manager in process-wide state [V]. `open(config)` per config therefore cannot map
   to one Xray instance per config. Workable design: one Xray instance per *wave* of
   up to K configs, each outbound paired with its own loopback SOCKS inbound
   (ports from `getFreePorts` [V]; they are candidates, not reservations [V]) and a
   routing rule inbound→outbound (standard Xray configuration, to be verified).
   Sessions become leases on a wave; a new wave means stop + start. The contract may
   need a batch-oriented method or an orchestrator that forms waves (ADR in spike).
2. **Connect timing.** A SOCKS inbound accepts immediately; the upstream handshake
   happens on first traffic. `ProxySession.establishedInMs` will measure instance
   start, not the proxy handshake. The design's "connect vs latency" split must be
   redefined (first request includes handshake) or cross-checked with `pingBatch`.
3. **`pingBatch` is not a substitute for our probes.** It tests at most five
   configs per call, concurrently, in one temporary instance, using a HEAD request
   and returning `delay` (10000 = error, 11000 = timeout) [V]. No body check, no
   throughput. Keep our own content-verified probes; use `pingBatch` at most as an
   optional cross-check.
4. **Errors.** libXray returns `success`/`error` strings [V]; mapping to
   `FailureCategory` will be heuristic; error text must pass `Redactor`.
5. **Cancellation.** `Invoke` is a blocking call; cancel by `stopXray` [V]; wrap in a
   dedicated dispatcher thread.
6. **Loopback authentication** (R-08) needs Xray's SOCKS inbound auth [U] — verify.
7. **No VpnService needed.** Socket-protect and DNS-protect APIs exist for VPN use
   [V]; this app does not tunnel device traffic [I].
8. **Single Go runtime** rule [V] is satisfied if this is the only Go library.
9. **Integration tests need an out-of-process server**: one process can host only one
   managed instance [V], so a loopback test server cannot run beside the client in
   the same app process [I]. Run the test server as a separate process (an Xray
   binary built from the same tag on the dev/CI host, or on the device).

### 3.2 B. sing-box (libbox)

| Aspect | Evidence |
|---|---|
| License | **GPL-3.0-or-later plus: "no derivative work may use the name or imply association with this application without prior consent"** — LICENSE text of v1.12.0-beta.13 [V]; distro metadata says "GPL-3.0-or-later with name use or association addition" [T] (Alpine, Arch). pkg.go.dev's automatic label "GPL-2.0" for that file is wrong against its own text [V] |
| Protocols | Broad (VMess, VLESS, Trojan, Shadowsocks, Hysteria2, TUIC, …) reported by downstream apps [T]; upstream docs not read |
| Android | `libbox` AAR via gomobile used by downstream apps, themselves GPL-3.0 [T]; ABIs/min API [U] |
| Size | Alpine packages: 15.4 MiB (armhf) / 16.1 MiB (s390x) packaged, 42–49 MiB installed (desktop CLI, not an AAR) [T] |
| Verdict | Same Go-runtime constraint class [I]. Copyleft would apply to the whole app if combined [I; not legal advice]. **Not recommended** unless the project chooses a GPL license. Keep only as contingency for TUIC (absent in Xray [V]) |

### 3.3 C. Other candidates

| Candidate | Evidence | Verdict |
|---|---|---|
| v2fly/v2ray-core | MIT [V]; Xray was forked from it [V]; a separate v2fly-core Android client exists [T]; no embedding/AAR evidence gathered | Not recommended: no Android integration evidence; Xray covers the four protocols [V] |
| shadowsocks-rust (Rust) | MIT [V]; Android builds with Rust targets for armv7, aarch64, i686, x86_64 [V via shadowsocks-android README]; Shadowsocks only | Cannot cover VLESS/VMess/Trojan; a second native engine only for one protocol is not worth it now. shadowsocks-android is GPL-3.0 [V] (an app, not a library to embed) |
| AndroidLibXrayLite (2dust, used by v2rayNG) | Listed among Xray wrappers on Xray-core's README [V]; license and API [U] | Possible Plan B wrapper; unverified |
| Thin own gomobile wrapper around Xray-core | [I] | **Plan B** if libXray's API churn or build proves unsuitable: expose only start-with-JSON/stop/free-ports; we own the Go toolchain step |
| Packaged Xray executable run as a child process | Xray README documents Android/arm64 and amd64 CGO builds with an NDK clang and `-checklinkname=0` (seen in a fork's README copy) [T]; exec feasibility on current Android [U] | Not first choice; gives crash and Go-runtime isolation at the cost of IPC/lifecycle work. Revisit if wave restarts inside the app process prove fragile |
| Own pure-Kotlin implementation | [I] | Rejected: large security-sensitive surface (VMess AEAD, REALITY), no justification |

## 4. Recommendation

Use **Xray-core via libXray (MIT AAR, MPL-2.0 core), built with libXray's own build
script, pinned to an exact libXray tag and the Xray-core tag it pins**, as the single
engine for VLESS, VMess, Shadowsocks and Trojan. Generate Xray JSON from
`ProxyConfig` ourselves; do not pass share links to libXray.

Why: it is the only candidate for which this phase verified, from upstream pages,
all four protocol implementations, a permissive wrapper license, Android API 21
support and a documented in-process JSON API. Why not sing-box: copyleft with a
name clause. Why not Shadowsocks-only or unevidenced engines: they cannot cover the
brief.

This is a **recommendation, not a support claim**: L3 is still zero. ADR-0006 is
"Proposed" and is closed only by the spike below. Plan B: thin own gomobile wrapper.

## 5. Spike plan for the integration phase (not started; needs network)

Gate checklist, each with an owner artifact and pass/fail recorded in ADR-0006:
1. **Build**: libXray AAR for arm64-v8a with pinned tags; record Go, gomobile, NDK
   versions; second build offline from cache.
2. **Licenses**: audit Xray-core/libXray `go.mod`/`go.sum`; fill `07-` with SPDX ids
   and NOTICE text; confirm MPL-2.0 obligations for an unmodified redistribution.
3. **Size**: measure APK delta per ABI; try arm64-v8a-only and ABI splits.
4. **Contract**: prove the wave design (item 1 above) with 8 configs and K=4; decide
   whether `ProxyEngine` needs a batch method; define `establishedInMs`.
5. **L3 per protocol**: out-of-process Xray test server on loopback for each of the
   four protocols; success and wrong-credential failure; only then set
   `verifiedProtocols`.
6. **Cancellation/leaks**: start/stop 100 waves; no leaked threads or ports.
7. **Resources**: memory and CPU on a low-end ARM device with 4 concurrent sessions.
8. **Hysteria2**: Xray has a `hysteria` package [V]; whether it is Hysteria 2 and how
   to configure it stays unverified; TUIC is out unless sing-box is accepted.

## 6. Follow-ups discovered (not done in this phase)
- Parser: flag VMess `aid` > 0 as unsupported instead of dropping it.
- Contract: possible batch/wave API and revised timing semantics (section 3.1, items 1–2).
- Model: confirm which transport and flow values the Xray JSON mapper can express; `HTTP2` links likely become `UNSUPPORTED` at engine level.

## 7. Phase 05 addendum (2026-10-21)
Integration was attempted only as far as verification allowed and then **blocked**; evidence and
reasoning are in `docs/adr/ADR-0009-engine-integration-blocked.md`. Summary: no legitimately
obtainable, verifiable libXray binary (no Maven coordinate found, release asset names/ABIs/checksums
unreadable), exact pin unresolved (README pin recorded in Phase 04 vs release list read in Phase 05),
Go-dependency licenses unaudited, L3 still zero. A new fact: libXray v26.3.27 notes 16 KB page-size
support for Android [V]. Section 5 gates are unchanged and still open.
