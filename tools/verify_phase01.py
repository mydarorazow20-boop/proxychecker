#!/usr/bin/env python3
"""Phase 01 structure and honesty checks.

This script does NOT compile anything and does NOT run unit tests. It verifies
that the expected files exist, that resource references resolve, that forbidden
APIs and permissions are absent, and that no fake-result code or fabricated
wrapper JAR is present. It also runs tools/verify_skeleton.py (Phase 0 checks).

Usage:
  python3 tools/verify_phase01.py            # check the project tree
  python3 tools/verify_phase01.py --zip FILE # additionally inspect a release ZIP
"""
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PKG = "app/src/main/java/com/example/proxyscanner"
TPKG = "app/src/test/java/com/example/proxyscanner"
results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))


def path(rel):
    return os.path.join(ROOT, rel)


def read(rel):
    with open(path(rel), encoding="utf-8") as fh:
        return fh.read()


def kt_files(base):
    out = []
    for dirpath, _, names in os.walk(path(base)):
        for n in names:
            if n.endswith(".kt"):
                out.append(os.path.join(dirpath, n))
    return sorted(out)


def strip_comments(src):
    src = re.sub(r"/\*[\s\S]*?\*/", "", src)
    return re.sub(r"(?m)^\s*//.*$", "", src)


# 1. required files -----------------------------------------------------------
required_main = [
    "app/MainActivity.kt", "app/AppContainer.kt", "app/ProxyScannerApp.kt",
    "app/ContentResolverDocumentReader.kt", "app/theme/Theme.kt",
    "core/model/ProxyConfig.kt", "core/model/Protocol.kt", "core/model/ImportResult.kt",
    "core/model/ProxyTestResult.kt", "core/testing/ScanState.kt",
    "core/testing/ScanAvailability.kt", "core/testing/UnavailableScanOrchestrator.kt",
    "core/engine/ProxyEngine.kt", "core/engine/UnavailableProxyEngine.kt",
    "core/parser/DefaultConfigParser.kt", "core/parser/VlessLinkParser.kt",
    "core/parser/VmessLinkParser.kt", "core/parser/ShadowsocksLinkParser.kt",
    "core/parser/TrojanLinkParser.kt", "core/parser/DefaultConfigNormalizer.kt",
    "core/parser/DefaultConfigValidator.kt", "core/parser/DefaultFormatDetector.kt",
    "core/importer/DefaultConfigImporter.kt", "core/importer/DocumentReader.kt",
    "core/importer/UrlSyntaxValidator.kt", "core/security/Secret.kt", "core/security/Redactor.kt",
    "feature/home/HomeScreen.kt", "feature/imports/ImportSection.kt",
    "feature/results/ResultsScreen.kt", "feature/settings/SettingsScreen.kt",
    "feature/scan/ScannerViewModel.kt", "feature/scan/ScannerUiState.kt",
]
for rel in required_main:
    check(f"main source present: {rel}", os.path.isfile(path(f"{PKG}/{rel}")))

required_tests = [
    "core/security/RedactorTest.kt", "core/parser/LowLevelParsingTest.kt",
    "core/parser/LinkParsersTest.kt", "core/parser/DefaultConfigParserTest.kt",
    "core/importer/DefaultConfigImporterTest.kt", "core/importer/UrlAndNamesTest.kt",
    "core/model/DomainModelsTest.kt", "feature/scan/ScannerViewModelTest.kt",
    "feature/scan/ImportIssuesTest.kt", "testutil/Fixtures.kt", "testutil/MainDispatcherRule.kt",
]
for rel in required_tests:
    check(f"test source present: {rel}", os.path.isfile(path(f"{TPKG}/{rel}")))

test_count = sum(len(re.findall(r"^\s*@Test\b", open(f, encoding="utf-8").read(), re.M))
                 for f in kt_files("app/src/test"))
check("at least 60 @Test methods declared (declared, not executed)", test_count >= 60, str(test_count))

for rel in ["README.md", "PHASE-01-REPORT.md", "docs/adr/ADR-0007-navigation-without-library.md",
            "docs/adr/ADR-0008-phase-01-scope-and-roadmap-mapping.md", "tools/bootstrap-wrapper.sh",
            "tools/verify_skeleton.py", "gradle/wrapper/gradle-wrapper.properties", "MANIFEST.md"]:
    check(f"file present: {rel}", os.path.isfile(path(rel)))

# 2. manifest: no permissions, no network ------------------------------------------
manifest = re.sub(r"<!--[\s\S]*?-->", "", read("app/src/main/AndroidManifest.xml"))
# Phase 3: INTERNET is the only permission allowed (URL import). Anything else is a failure.
declared = re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', manifest)
check("manifest declares no permission except INTERNET",
      declared == ["android.permission.INTERNET"] and manifest.count("<uses-permission") == 1, str(declared))
check("manifest keeps allowBackup=false", 'android:allowBackup="false"' in manifest)
check("manifest has no cleartext / networkSecurityConfig override",
      "usesCleartextTraffic" not in manifest and "networkSecurityConfig" not in manifest)

# 3. string resources referenced in Kotlin exist -----------------------------------
strings = set(re.findall(r'<string name="([^"]+)"', read("app/src/main/res/values/strings.xml")))
used = set()
for f in kt_files("app/src/main"):
    used.update(re.findall(r"R\.string\.([A-Za-z0-9_]+)", open(f, encoding="utf-8").read()))
missing = sorted(used - strings)
check("every R.string.* used in Kotlin is defined", not missing, ", ".join(missing))
unused = sorted(strings - used)
check("no unused string resources", not unused, ", ".join(unused))

# 4. forbidden APIs / patterns in production code -----------------------------------
forbidden = {
    r"android\.util\.Log": "Android logging (must not log configuration data)",
    r"\bprintln\s*\(": "println",
    r"\bprint\s*\(": "print",
    r"Thread\.sleep": "Thread.sleep",
    r"\bdelay\s*\(": "delay() (would simulate work)",
    r"kotlin\.random|java\.util\.Random|Math\.random|\bRandom\s*\(": "random numbers (fake results)",
    r"TrustManager|HostnameVerifier|SSLContext|setHostnameVerifier": "TLS verification code",
    r"READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|INTERNET": "storage/network permission",
    r"HttpURLConnection|OkHttp|openConnection|java\.net\.Socket\b|URL\(": "network access",
    r"SharedPreferences|DataStore|Room|openFileOutput|FileOutputStream": "persistence",
    r"firebase|analytics|admob|crashlytics": "tracking SDK",
}
for f in kt_files("app/src/main"):
    code = strip_comments(open(f, encoding="utf-8").read())
    rel = os.path.relpath(f, ROOT).replace(os.sep, "/")
    for pattern, label in forbidden.items():
        # Phase 3: the single HTTP transport class is the only file allowed to open a connection.
        if label == "network access" and rel.endswith("/app/UrlConnectionTransport.kt"):
            continue
        check(f"{rel}: no {label}", not re.search(pattern, code, re.I if "firebase" in pattern else 0))

# 5. no fake connectivity numbers in UI code ----------------------------------------
for f in kt_files(f"{PKG}/feature"):
    code = strip_comments(open(f, encoding="utf-8").read())
    rel = os.path.relpath(f, ROOT).replace(os.sep, "/")
    check(f"{rel}: no hard-coded latency/speed literals",
          not re.search(r"(?i)\b(latency|speed|mbps|kbps|ping)\w*\s*=\s*\d", code))

# 6. layering: core has no Android UI / feature imports ------------------------------
for f in kt_files(f"{PKG}/core"):
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, ROOT).replace(os.sep, "/")
    bad = re.findall(r"^import\s+(?:com\.example\.proxyscanner\.(?:feature|app)|androidx\.|android\.)[\w.]*", txt, re.M)
    check(f"{rel}: core imports no Android/feature/app code", not bad, "; ".join(bad))
for f in kt_files(f"{PKG}/feature"):
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, ROOT).replace(os.sep, "/")
    check(f"{rel}: feature does not import app package",
          not re.search(r"^import\s+com\.example\.proxyscanner\.app\.", txt, re.M))
vm = open(path(f"{PKG}/feature/scan/ScannerViewModel.kt"), encoding="utf-8").read()
check("ScannerViewModel imports no Android UI/Compose types", not re.search(r"^import\s+(android\.|androidx\.compose)", vm, re.M))

# 7. honesty: engine placeholder, wrapper jar, README claims -----------------------------
engine = read(f"{PKG}/core/engine/UnavailableProxyEngine.kt")
check("UnavailableProxyEngine reports integrated = false", "integrated = false" in engine)
check("UnavailableProxyEngine never returns Opened", "EngineOpenResult.Opened" not in strip_comments(engine))
check("scan placeholder message present in strings",
      "Scanning engine will be integrated in a later phase." in read("app/src/main/res/values/strings.xml"))
jar = path("gradle/wrapper/gradle-wrapper.jar")
if os.path.isfile(jar):
    with open(jar, "rb") as fh:
        head = fh.read(2)
    check("gradle-wrapper.jar (if present) is a real ZIP and larger than 20 KB",
          head == b"PK" and os.path.getsize(jar) > 20_000)
else:
    print("NOTE  gradle-wrapper.jar is absent (documented; run tools/bootstrap-wrapper.sh)")
for rel in ("README.md", "PHASE-01-REPORT.md"):
    text = read(rel)
    check(f"{rel} contains an honest build-status statement", "NOT RUN" in text)
    check(f"{rel} does not claim a built APK", not re.search(r"(?i)apk (was|has been) (built|generated) successfully", text))
check("no build output directories in tree",
      not any(os.path.isdir(path(d)) for d in ("build", "app/build", ".gradle")))
check("no local.properties in tree", not os.path.exists(path("local.properties")))

# 8. Phase 0 checks ---------------------------------------------------------------------
proc = subprocess.run([sys.executable, path("tools/verify_skeleton.py")], capture_output=True, text=True)
check("tools/verify_skeleton.py passes", proc.returncode == 0, proc.stdout.strip().splitlines()[-1] if proc.stdout.strip() else proc.stderr[-200:])

# 9. optional ZIP inspection ---------------------------------------------------------------
if "--zip" in sys.argv:
    zpath = sys.argv[sys.argv.index("--zip") + 1]
    with zipfile.ZipFile(zpath) as z:
        names = z.namelist()
        check("ZIP integrity (testzip)", z.testzip() is None)
    top = {n.split("/")[0] for n in names}
    check("ZIP has a single top-level folder", len(top) == 1, ",".join(sorted(top)))
    for rel in ["README.md", "PHASE-01-REPORT.md", "gradlew", "gradlew.bat", "settings.gradle.kts",
                "app/build.gradle.kts", "gradle/libs.versions.toml", "tools/verify_phase01.py",
                "app/src/main/AndroidManifest.xml"]:
        check(f"ZIP contains {rel}", any(n.endswith("/" + rel) for n in names))
    bad = [n for n in names if re.search(r"(^|/)(build|\.gradle|\.idea)/|local\.properties|\.keystore$|\.jks$|__pycache__|\.apk$", n)]
    check("ZIP has no build outputs, caches, keystores or APKs", not bad, ", ".join(bad[:5]))

failed = [r for r in results if not r[1]]
for name, ok, detail in results:
    if not ok:
        print(f"FAIL  {name}  {detail}")
print(f"{len(results) - len(failed)} passed, {len(failed)} failed, {len(results)} checks total "
      f"(static checks only: nothing was compiled or executed; {test_count} @Test methods declared)")
sys.exit(1 if failed else 0)
