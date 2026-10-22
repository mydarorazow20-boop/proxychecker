#!/usr/bin/env python3
"""Phase 05 honesty checks. Static: compiles and runs nothing.

Checks that the engine dependency was NOT added without verification, the placeholder
engine is intact, the lifecycle manager and its tests exist, the workflow has the required
steps, and the report states what was and was not executed.
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PKG = "app/src/main/java/com/example/proxyscanner"
results = []


def check(name, ok):
    results.append((name, bool(ok)))


def path(rel):
    return os.path.join(ROOT, rel)


def read(rel):
    with open(path(rel), encoding="utf-8") as fh:
        return fh.read()


# no unverified engine dependency, no binaries
gradle = read("app/build.gradle.kts") + read("gradle/libs.versions.toml") + read("settings.gradle.kts")
for bad in ("libxray", "xray", "sing-box", "libbox", ".aar", "jniLibs", "fileTree", "mavenLocal", "jitpack"):
    check(f"no '{bad}' in Gradle files", bad.lower() not in gradle.lower())
stray = []
for dirpath, dirs, names in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d != ".git"]
    for n in names:
        if n.endswith((".aar", ".so")):
            stray.append(n)
        if n.endswith(".jar") and n != "gradle-wrapper.jar":
            stray.append(n)
check("no .aar/.so/.jar binaries other than the official wrapper", not stray)
jar = path("gradle/wrapper/gradle-wrapper.jar")
if os.path.exists(jar):
    with open(jar, "rb") as fh:
        head = fh.read(2)
    check("gradle-wrapper.jar (if present) is a real ZIP > 20 KB", head == b"PK" and os.path.getsize(jar) > 20 * 1024)

# placeholder engine untouched and honest
engine = read(f"{PKG}/core/engine/UnavailableProxyEngine.kt")
check("UnavailableProxyEngine integrated=false", "integrated = false" in engine)
check("UnavailableProxyEngine never opens", "EngineOpenResult.Opened" not in engine)
check("UnavailableProxyEngine supports() false", "supports(config: ProxyConfig): Boolean = false" in engine)

# lifecycle manager
mgr = read(f"{PKG}/core/engine/EngineLifecycleManager.kt")
life = read(f"{PKG}/core/engine/EngineLifecycle.kt")
for needle in ("suspend fun initialize", "suspend fun startSession", "suspend fun stop", "val lastError", "val state"):
    check(f"manager declares '{needle}'", needle in mgr)
for needle in ("Uninitialized", "Initializing", "Ready", "Running", "Stopping", "Stopped", "Failed"):
    check(f"state '{needle}' declared", re.search(r"\b" + needle + r"\b", life) is not None)
check("manager redacts messages", "Redactor.redact" in mgr)
check("manager never constructs a session itself", mgr.count("EngineOpenResult.Opened(") == 1 and "EngineOpenResult.Opened(wrapped)" in mgr)
check("manager has no Secret.reveal", "reveal()" not in mgr)
tests = read("app/src/test/java/com/example/proxyscanner/core/engine/EngineLifecycleManagerTest.kt")
check("lifecycle tests declare >= 15 @Test", tests.count("@Test") >= 15)
check("test doubles are labelled as such", "Test double" in tests)
check("AppContainer wires the manager", "EngineLifecycleManager(engine)" in read(f"{PKG}/app/AppContainer.kt"))

# workflow
wf = read(".github/workflows/android-build.yml")
for needle in ("setup-java", "temurin", "java-version: '17'", "bootstrap-wrapper.sh", "setup-gradle",
               "setup-android", "testDebugUnitTest", "assembleDebug", "upload-artifact", "if-no-files-found: error"):
    check(f"workflow contains '{needle}'", needle in wf)
check("APK upload only on success", re.search(r"Upload debug APK\n\s+if: success\(\)", wf) is not None)

# bootstrap pin
boot = read("tools/bootstrap-wrapper.sh")
check("bootstrap checks pinned SHA-256", "PINNED_SHA256" in boot)
check("wrapper properties pin distributionSha256Sum",
      re.search(r"^distributionSha256Sum=[0-9a-f]{64}$", read("gradle/wrapper/gradle-wrapper.properties"), re.M) is not None)

# docs and report
adr6 = read("docs/adr/ADR-0006-engine-selection-pending.md")
st = re.search(r"^Status:\s*(.+)$", adr6, re.M)
check("ADR-0006 still not Accepted", bool(st) and "Accepted" not in st.group(1))
check("ADR-0009 present", os.path.isfile(path("docs/adr/ADR-0009-engine-integration-blocked.md")))
design = read("docs/01-technical-design.md")
rows = [l for l in design.splitlines() if re.match(r"\|\s*(VLESS|VMess|Shadowsocks|Trojan)\b", l)]
check("support table still says No for all four", len(rows) == 4 and all("**No**" in l for l in rows))
rep_path = path("PHASE-05-REPORT.md")
check("PHASE-05-REPORT.md present", os.path.isfile(rep_path))
if os.path.isfile(rep_path):
    rep = read("PHASE-05-REPORT.md")
    for needle in ("Selected engine", "NOT RUN", "Remaining blockers", "Files changed", "Licens"):
        check(f"report has '{needle}'", needle in rep)

failed = [r for r in results if not r[1]]
for name, ok in results:
    if not ok:
        print("FAIL ", name)
print(f"{len(results) - len(failed)} passed, {len(failed)} failed, {len(results)} checks total (static)")
sys.exit(1 if failed else 0)
