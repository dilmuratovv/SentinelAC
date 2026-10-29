#!/usr/bin/env python3
"""Verify the exact distribution used ONLY as a third-party library carrier."""
import hashlib
import json
from pathlib import Path
import sys

manifest = json.loads(Path(__file__).with_name('test-dependencies.json').read_text())['grim']
path = Path(sys.argv[1])
assert hashlib.new(manifest['hash'], path.read_bytes()).hexdigest() == manifest['checksum'], 'Runtime input checksum mismatch'
print('Verified pinned third-party library carrier checksum')
