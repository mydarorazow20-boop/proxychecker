# Module / Package Structure

Layout (Phase 0, extended in Phase 01): one Gradle module `:app`, base package `com.example.proxyscanner`
(placeholder, change before release).

```
app/src/main/java/com/example/proxyscanner/
├─ app/                 Android entry point (MainActivity), AppContainer, ProxyScannerApp
│                       (state-based navigation, ADR-0007), ContentResolverDocumentReader, theme/
├─ core/
│  ├─ model/            ProxyConfig, ImportResult, ProxyTestResult, ScanSettings, Protocol
│  ├─ parser/           FormatDetector, LinkParser (vless/vmess/ss/trojan), ConfigNormalizer,
│  │                    ConfigValidator, ConfigParser (+ Base64/percent/flat-JSON helpers)
│  ├─ importer/         ImportSource, ConfigImporter (DefaultConfigImporter), DocumentReader,
│  │                    UrlSyntaxValidator (format check only), RemoteUrlPolicy (contract only)
│  ├─ engine/           ProxyEngine, ProxySession, UnavailableProxyEngine
│  ├─ testing/          ProbeClient, ProxyTester, ScanOrchestrator, ScanEvent, ScanStatus,
│  │                    ScanResult, ScanAvailability, UnavailableScanOrchestrator
│  ├─ export/           ConfigExporter, ExportRequest/Outcome
│  └─ security/         Secret, Redactor
└─ feature/
   ├─ home/             main screen (import, URL input, scan controls, results summary)
   ├─ imports/          picker button, import report, URL input section
   ├─ scan/             ScannerViewModel + UI state (shared by all screens)
   ├─ results/          results screen (empty state / real results only)
   ├─ settings/         read-only information in Phase 01
   └─ common/           small shared composables and labels
```

Rules
1. `core/model` and `core/security` import nothing from other project packages
   (model may import `Secret`).
2. `core/*` contains no Compose or Activity code.
3. `feature/*` may import `core/*`; `core/*` never imports `feature/*`.
4. Only `core/engine` implementations may touch engine-native APIs.
5. Only `app/AppContainer` wires implementations to interfaces.

When to split into Gradle modules: when build time or boundary violations justify
it (ADR-0002). The package layout maps 1:1 to future modules
(`:core:model`, `:core:engine`, …) so the split is mechanical.
