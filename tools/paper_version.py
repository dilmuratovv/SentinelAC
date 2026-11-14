#!/usr/bin/env python3
"""Prints the pinned Paper API version of a target from pom.xml: python3 tools/paper_version.py 26.3"""
import re
import sys
from pathlib import Path

pom = (Path(__file__).resolve().parents[1] / 'pom.xml').read_text()
match = re.search(r'<id>paper-' + re.escape(sys.argv[1]) + r'</id>.*?<paper\.version>([^<]+)</paper\.version>', pom, re.S)
if not match:
    sys.exit('unknown target ' + sys.argv[1])
print(match.group(1))
