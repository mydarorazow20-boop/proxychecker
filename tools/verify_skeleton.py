#!/usr/bin/env python3
"""Static consistency checks (Phase 0 skeleton, still valid in Phase 1).

This is NOT a compiler. It catches mismatches between Gradle files, the version
catalog, the manifest, resources and Kotlin sources. It does not replace
`./gradlew :app:testDebugUnitTest :app:assembleDebug`.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
BASE_PKG = "com.example.proxyscanner"
results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))


def strip_kotlin_literals(src):
    """Removes comments, string literals (incl. raw strings) and char literals."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        two = src[i:i + 2]
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
        elif c == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
            i += 1
        elif c == "'":
            i += 1
            while i < n and src[i] != "'":
                i += 2 if src[i] == "\\" else 1
            i += 1
        elif two == "//":
            while i < n and src[i] != "\n":
                i += 1
        elif two == "/*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as fh:
        return fh.read()


# 1. settings.gradle.kts includes existing modules
settings = read("settings.gradle.kts")
for mod in re.findall(r'include\("(:[^"]+)"\)', settings):
    path = mod.strip(":").replace(":", "/")
    check(f"module {mod} has build.gradle.kts",
          os.path.isfile(os.path.join(ROOT, path, "build.gradle.kts")))

# 2. version catalog consistency
toml = read("gradle/libs.versions.toml")
sections = {}
current = None
for line in toml.splitlines():
    line = line.strip()
    m = re.match(r"\[(\w+)\]", line)
    if m:
        current = m.group(1)
        sections[current] = []
        continue
    if current and line and not line.startswith("#"):
        sections[current].append(line)
versions = {l.split("=")[0].strip() for l in sections.get("versions", [])}
libs = {l.split("=")[0].strip() for l in sections.get("libraries", [])}
plugins = {l.split("=")[0].strip() for l in sections.get("plugins", [])}
for sec in ("libraries", "plugins"):
    for l in sections.get(sec, []):
        for ref in re.findall(r'version\.ref\s*=\s*"([^"]+)"', l):
            check(f"catalog version.ref '{ref}' defined", ref in versions)

# 3. every libs.* alias used in build files exists in the catalog
used = set()
for rel in ("build.gradle.kts", "app/build.gradle.kts"):
    txt = re.sub(r"//.*", "", read(rel))
    for alias in re.findall(r"libs\.plugins\.([A-Za-z0-9_.]+)", txt):
        used.add(("plugins", alias))
    for alias in re.findall(r"libs\.(?!plugins)([A-Za-z][A-Za-z0-9_.]*)", txt):
        used.add(("libraries", alias))
for kind, alias in sorted(used):
    key = alias.replace(".", "-")
    pool = plugins if kind == "plugins" else libs
    check(f"alias libs.{'plugins.' if kind == 'plugins' else ''}{alias} exists", key in pool)
unused = {l for l in libs if not any(k == "libraries" and a.replace('.', '-') == l for k, a in used)}
check("no unused library entries in catalog", not unused, ", ".join(sorted(unused)))

# 4. manifest well-formed; activity class exists; resources referenced exist
manifest_path = "app/src/main/AndroidManifest.xml"
try:
    tree = ET.parse(os.path.join(ROOT, manifest_path))
    check("AndroidManifest.xml well-formed", True)
    ns = "{http://schemas.android.com/apk/res/android}"
    for act in tree.getroot().iter("activity"):
        name = act.get(ns + "name")
        full = BASE_PKG + name if name.startswith(".") else name
        rel = "app/src/main/java/" + full.replace(".", "/") + ".kt"
        check(f"activity {full} has source file", os.path.isfile(os.path.join(ROOT, rel)))
    manifest_text = read(manifest_path)
    check("manifest has android:allowBackup=\"false\"", 'android:allowBackup="false"' in manifest_text)
    no_comments = re.sub(r"<!--[\s\S]*?-->", "", manifest_text)
    # Phase 3: INTERNET only (URL import).
    check("manifest declares only the INTERNET permission",
          re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', no_comments) == ["android.permission.INTERNET"]
          and no_comments.count("<uses-permission") == 1)
except ET.ParseError as exc:
    check("AndroidManifest.xml well-formed", False, str(exc))

res_strings = set(re.findall(r'<string name="([^"]+)"', read("app/src/main/res/values/strings.xml")))
res_styles = set(re.findall(r'<style name="([^"]+)"', read("app/src/main/res/values/themes.xml")))
for rel in ("app/src/main/AndroidManifest.xml",):
    for kind, name in re.findall(r"@(string|style)/([A-Za-z0-9_.]+)", read(rel)):
        pool = res_strings if kind == "string" else {s.replace(".", "_") for s in res_styles}
        check(f"@{kind}/{name} defined", name.replace(".", "_") in pool or name in pool)
for xml in ("strings.xml", "themes.xml"):
    try:
        ET.parse(os.path.join(ROOT, "app/src/main/res/values", xml))
        check(f"{xml} well-formed", True)
    except ET.ParseError as exc:
        check(f"{xml} well-formed", False, str(exc))

# 5. Kotlin sources: package matches directory, imports of project symbols resolve,
#    brackets balanced
kt_files = []
for dirpath, _, filenames in os.walk(os.path.join(ROOT, "app/src")):
    for fn in filenames:
        if fn.endswith(".kt"):
            kt_files.append(os.path.join(dirpath, fn))

decl_re = re.compile(
    r"^\s*(?:@\w+\s+)*(?:(?:public|internal|private|data|sealed|enum|annotation|value|abstract|open|inline)\s+)*"
    r"(?:class|interface|object|fun|typealias|val)\s+(?:<[^>]+>\s*)?([A-Za-z_][A-Za-z0-9_]*)",
    re.M,
)
declared = {}
for f in kt_files:
    txt = open(f, encoding="utf-8").read()
    pm = re.search(r"^package\s+([\w.]+)", txt, re.M)
    rel = os.path.relpath(f, ROOT)
    if not pm:
        check(f"{rel}: has package", False)
        continue
    pkg = pm.group(1)
    sub = os.path.relpath(os.path.dirname(f), ROOT).replace(os.sep, "/")
    sub = re.sub(r"^app/src/(main|test|androidTest)/java/", "", sub).replace("/", ".")
    check(f"{rel}: package matches directory", pkg == sub, f"{pkg} vs {sub}")
    names = set(decl_re.findall(txt))
    declared.setdefault(pkg, set()).update(names)
    # bracket balance on code only (strings, char literals and comments removed)
    stripped = strip_kotlin_literals(txt)
    for o, c in ("{}", "()", "[]"):
        check(f"{rel}: balanced {o}{c}", stripped.count(o) == stripped.count(c),
              f"{stripped.count(o)} vs {stripped.count(c)}")

for f in kt_files:
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, ROOT)
    for imp in re.findall(r"^import\s+([\w.]+)", txt, re.M):
        if imp.startswith(BASE_PKG + "."):
            pkg, _, name = imp.rpartition(".")
            if pkg == BASE_PKG:
                ok = name == "R"
            else:
                ok = name in declared.get(pkg, set())
            check(f"{rel}: import {imp.replace(BASE_PKG + '.', '')} resolves", ok)

# 6. layering rule: core/* must not import feature/* or Compose/Activity
for f in kt_files:
    rel = os.path.relpath(f, ROOT).replace(os.sep, "/")
    if "/core/" in rel and "/src/main/" in rel:
        txt = open(f, encoding="utf-8").read()
        bad = re.findall(r"^import\s+(?:" + re.escape(BASE_PKG) + r"\.feature|androidx\.compose|android\.app|androidx\.activity)[\w.]*", txt, re.M)
        check(f"{rel}: respects layering", not bad, "; ".join(bad))

# 7. docs: no placeholder phrases the brief forbids
forbidden = ["same as above", "implement later", "rest of file"]
for dirpath, _, filenames in os.walk(ROOT):
    for fn in filenames:
        if fn.endswith((".md", ".kt", ".kts")):
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, ROOT)
            if rel.replace(os.sep, "/") == "tools/verify_skeleton.py":
                continue
            low = open(full, encoding="utf-8").read().lower()
            for phrase in forbidden:
                check(f"{rel}: no '{phrase}'", phrase not in low)

# 8. required deliverables present
required = [
    "README.md", "docs/01-technical-design.md", "docs/02-engine-evaluation.md",
    "docs/03-roadmap.md", "docs/04-testing-strategy.md", "docs/05-module-structure.md",
    "docs/06-unresolved-decisions-and-risks.md", "docs/07-dependency-license-inventory.md",
    "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.properties",
]
for rel in required:
    check(f"deliverable present: {rel}", os.path.isfile(os.path.join(ROOT, rel)))
check("ADRs present (at least 6)", len([f for f in os.listdir(os.path.join(ROOT, "docs/adr")) if f.startswith("ADR-")]) >= 6)
jar = os.path.join(ROOT, "gradle/wrapper/gradle-wrapper.jar")
if os.path.isfile(jar):
    with open(jar, "rb") as fh:
        head = fh.read(4)
    check("gradle-wrapper.jar is a ZIP/JAR (PK header)", head[:2] == b"PK")
else:
    print("NOTE  gradle-wrapper.jar is absent (documented limitation; see README step 2)")
check("tools/bootstrap-wrapper.sh present", os.path.isfile(os.path.join(ROOT, "tools/bootstrap-wrapper.sh")))
check("docs/08 present", os.path.isfile(os.path.join(ROOT, "docs/08-toolchain-and-verification.md")))
props = read("gradle/wrapper/gradle-wrapper.properties")
gv = re.search(r"gradle-(\d+)\.(\d+)(?:\.(\d+))?-bin\.zip", props)
check("wrapper pins a Gradle >= 8.9 (minimum for AGP 8.7)",
      bool(gv) and (int(gv.group(1)), int(gv.group(2))) >= (8, 9))
agp = re.search(r'^agp\s*=\s*"(\d+)\.(\d+)', toml, re.M)
check("AGP pin is 8.7.x (matches documented Gradle/API-level evidence)",
      bool(agp) and (agp.group(1), agp.group(2)) == ("8", "7"))
app_gradle = read("app/build.gradle.kts")
check("compileSdk <= 35 (AGP 8.7 maximum)", int(re.search(r"compileSdk\s*=\s*(\d+)", app_gradle).group(1)) <= 35)
check("README states unit tests / APK / lint NOT RUN honestly",
      "NOT RUN" in read("README.md"))

failed = [r for r in results if not r[1]]
for name, ok, detail in results:
    if not ok:
        print(f"FAIL  {name}  {detail}")
print(f"{len(results) - len(failed)} passed, {len(failed)} failed, {len(results)} checks total")
sys.exit(1 if failed else 0)
