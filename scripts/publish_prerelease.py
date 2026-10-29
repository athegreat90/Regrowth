#!/usr/bin/env python3
"""Only used after all matrix checks pass; no pushes to a branch or upstream pages."""
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile
import urllib.parse
import urllib.request


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


def find_release():
    existing = gh("api", f"repos/{repo}/releases/tags/{tag}", check=False)
    if existing.returncode == 0:
        return json.loads(existing.stdout)
    # Drafts are absent from the by-tag endpoint. Only fall back on a real 404;
    # auth/service failures must never be treated as permission to create one.
    if "HTTP 404" not in existing.stderr:
        raise RuntimeError(existing.stderr)
    listing = gh("api", "--paginate", f"repos/{repo}/releases", "--jq", ".[] | @json").stdout
    releases = [json.loads(line) for line in listing.splitlines() if line.strip()]
    matches = [r for r in releases if r["tag_name"] == tag]
    assert len(matches) <= 1, "Multiple releases have this immutable tag"
    return matches[0] if matches else None


release = find_release()
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
            payload = pathlib.Path(temp) / "release.json"
            payload.write_text(json.dumps({
                "tag_name": tag, "target_commitish": sha, "draft": True,
                "prerelease": True, "name": f"Development {sha}", "body": notes.read_text(),
            }))
            # Creation returns the authoritative ID even before discovery indexes
            # expose the draft. Never rediscover a just-created release by tag/list.
            release = json.loads(gh("api", "--method", "POST", f"repos/{repo}/releases",
                                    "--input", str(payload)).stdout)
        assert release["draft"] and release["tag_name"] == tag
        assert release["target_commitish"] == sha, "Draft targets another commit"
        endpoint = f"repos/{repo}/releases/{release['id']}"
        # Use the ID throughout: tag-based CLI helpers also rediscover drafts.
        for asset in release.get("assets", []):
            gh("api", "--method", "DELETE", f"repos/{repo}/releases/assets/{asset['id']}")
        for asset in [*jars, checksums]:
            name = urllib.parse.quote(asset.name, safe="")
            request = urllib.request.Request(
                f"https://uploads.github.com/{endpoint}/assets?name={name}",
                data=asset.read_bytes(), method="POST", headers={
                    "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
                    "Content-Type": "application/octet-stream",
                    "Accept": "application/vnd.github+json",
                })
            with urllib.request.urlopen(request, timeout=120) as response:
                response.read()
        uploaded = json.loads(gh("api", endpoint).stdout)
        expected = {p.name: "sha256:" + hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in [*jars, checksums]}
        assert {a["name"]: a["digest"] for a in uploaded["assets"]} == expected, "Uploaded bytes differ"
        gh("api", "--method", "PATCH", endpoint, "--field", "draft=false",
           "--field", "prerelease=true", "--raw-field", "make_latest=false")
    print(f"Published development prerelease {tag}")
