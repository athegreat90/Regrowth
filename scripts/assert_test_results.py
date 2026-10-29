#!/usr/bin/env python3
import pathlib
import sys
import xml.etree.ElementTree as ET

# Runner can return zero even when no tests are discovered; reject that outcome.
for module in sys.argv[1:]:
    files = list(pathlib.Path(module, "build/test-results/test").glob("TEST-*.xml"))
    tests = failures = errors = skipped = 0
    for file in files:
        root = ET.parse(file).getroot()
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
    assert tests > 0 and failures == 0 and errors == 0 and skipped == 0, (module, tests, failures, errors, skipped)
    print(f"{module}: {tests} tests passed, 0 skipped")
