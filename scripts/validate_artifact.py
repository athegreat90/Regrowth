#!/usr/bin/env python3
"""Validate target metadata, identifiers, notices and absence of embedded runtimes."""
import argparse
import json
import pathlib
import tomllib
import zipfile

p = argparse.ArgumentParser()
p.add_argument("module", choices=["mc2612", "mc262", "mc263"])
a = p.parse_args()
mc = {"mc2612": "26.1.2", "mc262": "26.2", "mc263": "26.3"}[a.module]
neo_min = {"mc2612": "26.1.2.125", "mc262": "26.2.0.93", "mc263": "26.3.0.99"}[a.module]
root = pathlib.Path(__file__).resolve().parents[1]
jars = list((root / a.module / "build/libs").glob(f"regrowth-kotlin-neoforge-{mc}-*.jar"))
assert len(jars) == 1, f"Expected one target jar, got {jars}"
with zipfile.ZipFile(jars[0]) as jar:
    names = set(jar.namelist())
    metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
    assert metadata["modLoader"] == "klf"
    assert metadata["loaderVersion"] == "[1,)"
    assert metadata["mods"][0]["modId"] == "regrowth"
    deps = metadata["dependencies"]["regrowth"]
    neo_deps = [d for d in deps if d["modId"] == "neoforge"]
    assert len(neo_deps) == 1, f"Expected one NeoForge dependency, got {neo_deps}"
    assert neo_deps[0]["type"] == "required"
    assert neo_deps[0]["versionRange"] == f"[{neo_min},)", neo_deps[0]
    assert any(d["modId"] == "minecraft" and d["versionRange"] == f"[{mc}]" for d in deps)
    assert not any(d["modId"] in ("fabric", "fabricloader", "kotlinforforge", "klf") for d in deps)
    assert not any(n.startswith(("kotlin/", "kotlinx/", "net/fabricmc/", "dev/nyon/")) for n in names)
    assert "META-INF/mods.toml" not in names
    assert "LICENSE" in names and "THIRD_PARTY_NOTICES.md" in names
    assert "com/mactso/regrowth/Regrowth.class" in names
    for name in ("rgroot", "offlimits", "saferhedges", "teardown"):
        assert f"data/regrowth/advancement/rgroot/{name}.json" in names
    for source in (root / "src/main/resources").rglob("*.json"):
        rel = source.relative_to(root / "src/main/resources").as_posix()
        assert json.loads(jar.read(rel)) == json.loads(source.read_text()), rel
print(f"Validated {jars[0].name}: target={mc}, klf provider, resources, MIT notices, no bundled Kotlin")
