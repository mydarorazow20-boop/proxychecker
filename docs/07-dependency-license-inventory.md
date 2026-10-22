# Dependency and License Inventory

Rules: every new dependency needs a reason, an SPDX license id, a source URL,
a pinned version and an owner phase. Licenses below marked "to confirm" come from
general knowledge and must be confirmed against the artifact's POM/LICENSE when
the dependency is first resolved. Nothing here has been resolved by a build yet.
Phase 04 verified licenses of *engine candidates* only (below); entries for the
existing Gradle dependencies remain "to confirm".

## Template

| Name | Group:Artifact / repo | Version (pinned) | SPDX license | License verified? (date, source) | Why needed | Native code / ABIs | Attribution required | Offline-build notes | Added in phase |
|---|---|---|---|---|---|---|---|---|---|

## Current entries (Phase 0 skeleton)

| Name | Group:Artifact | Version | SPDX | Verified? | Why needed | Native | Attribution | Added |
|---|---|---|---|---|---|---|---|---|
| Android Gradle Plugin | com.android.application | 8.7.3 | Apache-2.0 (to confirm) | no | build | no | notice in third-party screen (if shipped) | 0 |
| Kotlin Gradle plugin + stdlib | org.jetbrains.kotlin | 2.0.21 | Apache-2.0 (to confirm) | no | language | no | yes | 0 |
| Compose BOM | androidx.compose:compose-bom | 2024.12.01 | Apache-2.0 (to confirm) | no | UI | no | yes | 0 |
| Compose UI | androidx.compose.ui:ui | via BOM | Apache-2.0 (to confirm) | no | UI | no | yes | 0 |
| Material 3 | androidx.compose.material3:material3 | via BOM | Apache-2.0 (to confirm) | no | Material 3 components | no | yes | 0 |
| Activity Compose | androidx.activity:activity-compose | 1.9.3 | Apache-2.0 (to confirm) | no | `setContent`, `ComponentActivity` | no | yes | 0 |
| Lifecycle ViewModel KTX | androidx.lifecycle:lifecycle-viewmodel-ktx | 2.8.7 | Apache-2.0 (to confirm) | no | ViewModel | no | yes | 0 |
| kotlinx.coroutines Android | org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.9.0 | Apache-2.0 (to confirm) | no | coroutines, Flow, Main dispatcher | no | yes | 0 |
| JUnit 4 (test only) | junit:junit | 4.13.2 | EPL-1.0 (to confirm) | no | unit tests | no | not shipped | 0 |
| kotlinx.coroutines test (test only) | org.jetbrains.kotlinx:kotlinx-coroutines-test | 1.9.0 | Apache-2.0 (to confirm) | no | ViewModel/coroutine unit tests | no | not shipped | 1 |
| Compose UI test, AndroidX test runner/ext-junit, ui-test-manifest (androidTest/debug only) | androidx.compose.ui:ui-test-junit4, androidx.test:runner 1.6.2, androidx.test.ext:junit 1.2.1, androidx.compose.ui:ui-test-manifest | via BOM / as listed | Apache-2.0 (to confirm) | no | instrumented UI tests (not run so far) | no | not shipped in release | 2 |

## Engine candidates (Phase 04) – NOT adopted, nothing added to Gradle

Evidence tags as in `02-engine-evaluation.md`: [V] upstream page read 2026-10-21,
[T] third party, [I] inference, [U] unverified.

| Candidate | Source | License (evidence) | What would ship | Obligations (inference, not legal advice) | Status |
|---|---|---|---|---|---|
| **libXray** (wrapper, recommended) | github.com/XTLS/libXray | **MIT** [V: repository page, LICENSE]; credits MetaCubeX age BSD-3-Clause [V] | gomobile AAR (native Go library, ABIs [U]) | keep MIT notice; list BSD-3 credit [I] | proposed, spike pending |
| **Xray-core** (engine inside libXray) | github.com/XTLS/Xray-core | **MPL-2.0** [V: pkg.go.dev module page v1.260327.0, README link to LICENSE] | compiled into the AAR | MPL-2.0 file-level copyleft: provide notice and source for covered files when redistributing; changes to those files must be published [I] | proposed, spike pending |
| Xray-core transitive Go modules | `go.mod`/`go.sum` of the pinned tags | not audited [U] | compiled into the AAR | full SPDX list and NOTICE text required before release [I] | open (spike gate 2) |
| sing-box / libbox | github.com/SagerNet/sing-box | **GPL-3.0-or-later + clause forbidding derivative works from using the name or implying association** [V: LICENSE text v1.12.0-beta.13; T: Alpine, Arch metadata] | gomobile AAR | strong copyleft would cover the combined app [I] | rejected unless app goes GPL |
| v2fly/v2ray-core | github.com/v2fly/v2ray-core | MIT [V: pkg.go.dev, openSUSE spec] | – | – | not evaluated further |
| shadowsocks-rust | github.com/shadowsocks/shadowsocks-rust | MIT [T: shadowsocks-android README list; SUSE package page] | Rust native library (SS only) | – | not suitable (one protocol) |
| shadowsocks-android | github.com/shadowsocks/shadowsocks-android | GPL-3.0 [T: README, JitPack page] | an app, not an embeddable engine | – | not used |
| AndroidLibXrayLite | github.com/2dust/AndroidLibXrayLite | [U] | – | – | possible Plan B, unverified |

## Build-time tools for the engine (not shipped in the APK)

| Tool | Needed for | License | Evidence |
|---|---|---|---|
| Go toolchain | building libXray | [U] | libXray README: depends on git and go [V] |
| gomobile / gobind | Android AAR binding | [U] | libXray README: Android builds use gomobile [V] |
| Android NDK | cgo cross-compile | [U] | version to be recorded in the spike |
| Python 3 | libXray `build/main.py` | [U] | README usage [V] |

Rule: none of these may be added to the Gradle build or the repository until the
spike gates in `02-engine-evaluation.md` are accepted in ADR-0006.

## Phase 05
No dependency was added (see `adr/ADR-0009-engine-integration-blocked.md`). Build/CI tooling
introduced in `.github/workflows/android-build.yml` is not shipped in the APK: GitHub Actions
`actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v6`,
`android-actions/setup-android@v3`, `actions/upload-artifact@v4` (referenced by major-version
tag, not by commit SHA; licenses not verified), Temurin JDK 17, Gradle 8.9 (Apache-2.0, to confirm).
