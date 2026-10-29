#!/usr/bin/env python3
"""Fail a release build if the companion accidentally bundles engine/server code."""
import struct
import sys
import zipfile
from pathlib import Path

path = Path(sys.argv[1] if len(sys.argv) > 1 else 'target/SentinelAC-2.4.0-paper26.2.jar')
with zipfile.ZipFile(path) as jar:
    names = jar.namelist()
    assert len(names) == len(set(names)), 'Duplicate archive entries'
    assert not any(name.startswith(('ac/grim/', 'org/bukkit/', 'io/papermc/')) for name in names), 'Bundled engine/server classes'
    assert not any(name.startswith(('punishments/', 'database/', 'databases/', 'config/')) for name in names), 'Stale engine resources'
    plugin = jar.read('plugin.yml').decode()
    assert "api-version: '26.2'" in plugin
    assert 'depend: [GrimAC]' in plugin
    assert '${' not in plugin, 'Unfiltered plugin version'
    for name in names:
        if name.endswith('.class'):
            assert name.startswith('uz/sentinel/ac/'), name
            assert struct.unpack('>HH', jar.read(name)[4:8]) == (0, 69), 'Expected non-preview Java 25 bytecode'
print(f'PASS: {path.name}; companion-only, Paper 26.2, Java 25, no stale engine templates')
