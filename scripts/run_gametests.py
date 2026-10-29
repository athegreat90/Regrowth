#!/usr/bin/env python3
"""Run only Regrowth tests and require a fresh nonempty successful report."""
import argparse
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument("module", choices=["mc2612", "mc262", "mc263"])
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
report = root / a.module / "run/regrowth-gametest.xml"
report.unlink(missing_ok=True)  # Only this generated test report, never world fixtures.
log = root / a.module / "build/gametest-console.log"
log.parent.mkdir(parents=True, exist_ok=True)
with log.open("w") as out:
    completed = subprocess.run(["bash", "gradlew", f":{a.module}:runGameTestServer"], cwd=root, stdout=out, stderr=subprocess.STDOUT)
print(log.read_text()[-16000:])
if completed.returncode:
    sys.exit(completed.returncode)
assert report.is_file(), "GameTest runner did not produce a fresh report"
suite = ET.parse(report).getroot()
cases = list(suite.iter("testcase"))
assert len(cases) == 5, f"Expected 5 Regrowth tests, got {len(cases)}"
assert not list(suite.iter("failure")) and not list(suite.iter("error")) and not list(suite.iter("skipped"))
assert all("regrowth" in str(c.attrib) for c in cases), "Report includes an unexpected namespace"
print(f"{a.module}: {len(cases)} dedicated-server GameTests passed")
