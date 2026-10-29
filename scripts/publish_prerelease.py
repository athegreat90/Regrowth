#!/usr/bin/env python3
"""Only used after all matrix checks pass; no pushes to a branch or upstream pages."""
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile


def gh(*args, check=True):
    return subprocess.run(["gh", *args], check=check, text=True, capture_output=True)


sha = os.environ["GITHUB_SHA"]
repo = os.environ["GITHUB_REPOSITORY"]
assert len(sha) == 40 and all(c in "0123456789abcdef" for c in sha)
tag = f"dev-{sha}"
assets = pathlib.Path("release-assets")
jars = sorted(assets.glob("*.jar"))
assert len(jars) == 3, f"Expected 3 validated target jars: {jars}"
for mc in ("26.1.2", "26.2", "26.3"):
    assert sum(f"neoforge-{mc}-" in p.name for p in jars) == 1
sums = "".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in jars)
checksums = assets / "SHA256SUMS"
checksums.write_text(sums)
existing = gh("api", f"repos/{repo}/releases/tags/{tag}", check=False)
release = json.loads(existing.stdout) if existing.returncode == 0 else None
# Distinguish missing releases from auth/service failures.
if existing.returncode:
    if "HTTP 404" not in existing.stderr:
        raise RuntimeError(existing.stderr)
ref = gh("api", f"repos/{repo}/git/ref/tags/{tag}", check=False)
if ref.returncode == 0:
    obj = json.loads(ref.stdout)["object"]
    assert obj["type"] == "commit" and obj["sha"] == sha, "Tag is not this immutable commit"
elif "HTTP 404" not in ref.stderr:
    raise RuntimeError(ref.stderr)
elif release:
    assert release["draft"] and release.get("target_commitish") == sha, "Existing release has no verifiable tag/commit identity"
if release and not release["draft"]:
    assert release["prerelease"], "Existing release is not a development prerelease"
    assert {p.name for p in jars} | {"SHA256SUMS"} == {a["name"] for a in release["assets"]}
    with tempfile.TemporaryDirectory() as temp:
        gh("release", "download", tag, "--repo", repo, "--dir", temp)
        for asset in [*jars, checksums]:
            assert hashlib.sha256((pathlib.Path(temp) / asset.name).read_bytes()).digest() == hashlib.sha256(asset.read_bytes()).digest()
    print(f"Already published and verified: {tag}")
else:
    with tempfile.TemporaryDirectory() as temp:
        notes = pathlib.Path(temp) / "notes.md"
        notes.write_text(
            f"Development prerelease for `{sha}`. Unofficial Regrowth continuation.\n\n"
            "Separate NeoForge artifacts: Minecraft 26.1.2, 26.2, 26.3; Java 25.\n"
            "Install external KotlinLangForge 2.16.0-k2.4.21-3.1+neoforge.\n"
            "Build, unit/compatibility tests and dedicated GameTests are required for all targets.\n"
            "These checks do not verify old-world upgrades or full original gameplay parity.\n"
            "Read docs/FEATURE_AUDIT.md and docs/WORLD_UPGRADE.md. Back up worlds.\n\n"
            f"Changes: https://github.com/{repo}/commit/{sha}\n"
        )
        if not release:
            gh("release", "create", tag, "--repo", repo, "--target", sha, "--draft", "--prerelease",
               "--title", f"Development {sha}", "--notes-file", str(notes))
        # A draft can be repaired only for the same commit checked above.
        gh("release", "upload", tag, "--repo", repo, "--clobber", *map(str, [*jars, checksums]))
        uploaded = json.loads(gh("api", f"repos/{repo}/releases/tags/{tag}").stdout)
        assert {a["name"] for a in uploaded["assets"]} == {p.name for p in [*jars, checksums]}
        gh("release", "edit", tag, "--repo", repo, "--draft=false", "--prerelease", "--latest=false", "--notes-file", str(notes))
    print(f"Published development prerelease {tag}")
