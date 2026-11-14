#!/usr/bin/env python3
"""Sums the surefire reports into unit-tests.json (uploaded by the build job, read by every target probe)."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

suites = [ET.parse(path).getroot() for path in Path('plugin/target/surefire-reports').glob('TEST-*.xml')]
summary = {key: sum(int(suite.get(key, 0)) for suite in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
Path(sys.argv[1] if len(sys.argv) > 1 else 'unit-tests.json').write_text(json.dumps(summary) + '\n')
print(summary)
