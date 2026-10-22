#!/usr/bin/env python3
"""Phase 04 honesty checks (documentation-only phase). Static; compiles and runs nothing.

Checks that the engine evaluation exists with evidence tags, that no engine was
integrated (no engine dependency, no native library, placeholder engine intact),
that support claims were not made, and that licenses of candidates are recorded.
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as fh:
        return fh.read()


doc2 = read("docs/02-engine-evaluation.md")
adr6 = read("docs/adr/ADR-0006-engine-selection-pending.md")
inv = read("docs/07-dependency-license-inventory.md")
design = read("docs/01-technical-design.md")

# evaluation content
for tag in ("[V]", "[T]", "[I]", "[U]"):
    check(f"doc 02 uses evidence tag {tag}", tag in doc2)
for name in ("libXray", "Xray-core", "sing-box", "v2ray-core", "shadowsocks-rust"):
    check(f"doc 02 evaluates {name}", name in doc2)
for item in ("License", "Android", "Maintenance", "Integration", "APK size", "Offline"):
    check(f"doc 02 covers '{item}'", item.lower() in doc2.lower())
check("doc 02 states L3 is not reached by any protocol", "**no, none**" in doc2)
check("doc 02 has a recommendation section", "## 4. Recommendation" in doc2)
check("doc 02 says recommendation is not a support claim", "not a support claim" in doc2)

# ADR status stays honest
status = re.search(r"^Status:\s*(.+)$", adr6, re.M)
check("ADR-0006 status is Proposed (not Accepted)",
      bool(status) and "Proposed" in status.group(1) and "Accepted" not in status.group(1))

# support table unchanged: every protocol row still says No
rows = [l for l in design.splitlines() if re.match(r"\|\s*(VLESS|VMess|Shadowsocks|Trojan)\b", l)]
check("doc 01 support table lists the four protocols", len(rows) == 4)
for l in rows:
    check("doc 01 row says not supported: " + l.split("|")[1].strip(), "**No**" in l)

# no engine integrated
gradle = read("app/build.gradle.kts") + read("gradle/libs.versions.toml") + read("settings.gradle.kts")
for bad in ("libxray", "xray", "sing-box", "singbox", "libbox", ".aar", "jniLibs", "fileTree", "mavenLocal"):
    check(f"no '{bad}' in Gradle files", bad.lower() not in gradle.lower())
for dirpath, _, names in os.walk(ROOT):
    for n in names:
        if n.endswith((".aar", ".so", ".jar")):
            check(f"no binary artifact {n}", False)
check("no .aar/.so/.jar files in the tree", not any(not r[1] and r[0].startswith("no binary") for r in results))
engine = read("app/src/main/java/com/example/proxyscanner/core/engine/UnavailableProxyEngine.kt")
check("UnavailableProxyEngine still integrated=false", "integrated = false" in engine)
check("UnavailableProxyEngine supports() still false", "supports(config: ProxyConfig): Boolean = false" in engine)
_jar = os.path.join(ROOT, "gradle/wrapper/gradle-wrapper.jar")
if os.path.exists(_jar):
    with open(_jar, "rb") as _fh:
        _head = _fh.read(2)
    check("gradle-wrapper.jar (if present) is a real ZIP and larger than 20 KB",
          _head == b"PK" and os.path.getsize(_jar) > 20 * 1024)
else:
    check("gradle-wrapper.jar not fabricated", True)

# license inventory
check("inventory: libXray MIT", re.search(r"libXray[^\n]*\*\*MIT\*\*", inv) is not None)
check("inventory: Xray-core MPL-2.0", re.search(r"Xray-core[^\n]*MPL-2\.0", inv) is not None)
check("inventory: sing-box GPL-3.0-or-later", re.search(r"sing-box[^\n]*GPL-3\.0-or-later", inv) is not None)
check("inventory states nothing added to Gradle", "nothing added to Gradle" in inv)

# report
check("PHASE-04-REPORT.md present", os.path.isfile(os.path.join(ROOT, "PHASE-04-REPORT.md")))
if os.path.isfile(os.path.join(ROOT, "PHASE-04-REPORT.md")):
    rep = read("PHASE-04-REPORT.md")
    check("report says nothing was built or run", "NOT RUN" in rep)

failed = [r for r in results if not r[1]]
for name, ok, detail in results:
    if not ok:
        print(f"FAIL  {name}  {detail}")
print(f"{len(results) - len(failed)} passed, {len(failed)} failed, {len(results)} checks total (static)")
sys.exit(1 if failed else 0)
