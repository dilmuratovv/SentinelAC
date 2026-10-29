#!/usr/bin/env python3
"""Check standalone layout, templates, legal notices and active branding."""
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

def utf8_constants(data):
    """Classfile UTF8 entries, excluding accidental letter sequences in opcodes."""
    size = int.from_bytes(data[8:10], 'big')
    cursor, index = 10, 1
    values = []
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
              11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < size:
        tag = data[cursor]; cursor += 1
        if tag == 1:
            length = int.from_bytes(data[cursor:cursor+2], 'big'); cursor += 2
            values.append(data[cursor:cursor+length]); cursor += length
        else:
            cursor += widths[tag]
            if tag in (5, 6): index += 1
        index += 1
    return values

path = Path(sys.argv[1] if len(sys.argv) > 1 else 'plugin/target/SentinelAC-2.6.0-paper26.2.jar')
with zipfile.ZipFile(path) as jar:
    names = jar.namelist()
    assert len(names) == len(set(names)), 'Duplicate archive entries'
    assert not any(name.startswith(('ac/grim/', 'org/bukkit/', 'io/papermc/')) for name in names), 'Bundled old engine/server classes'
    plugin = jar.read('plugin.yml').decode()
    target_properties = dict(line.split('=', 1) for line in jar.read('sentinel-target.properties').decode().splitlines() if '=' in line)
    target = target_properties['minecraft.version']
    java = int(target_properties['java.version'])
    assert target_properties['api.version'] == target
    assert f"api-version: '{target}'" in plugin
    assert f"version: '2.6.0-paper{target}'" in plugin
    assert path.name == f'SentinelAC-2.6.0-paper{target}.jar', 'Incorrect target filename'
    expected_major = java + 44
    assert '\ndepend:' not in plugin, 'Unexpected mandatory external engine'
    assert '${' not in plugin, 'Unfiltered plugin version'
    for required in ['uz/sentinel/ac/SentinelPlugin.class',
                     'uz/sentinel/engine/SentinelAPI.class',
                     'uz/sentinel/engine/player/SentinelPlayer.class',
                     'uz/sentinel/engine/libs/com/github/retrooper/packetevents/PacketEvents.class',
                     'META-INF/sentinel/codec-bindings.tsv', 'META-INF/LICENSE.txt', 'META-INF/NOTICE.txt']:
        assert required in names, 'Missing ' + required
    assert 'META-INF/licenses/runtime-LICENSE.txt' in names, 'Missing third-party copyright notice'
    for backend in ['sqlite', 'mysql', 'postgres', 'mongo', 'redis']:
        assert f'databases/{backend}/en.yml' in names, 'Missing backend defaults: ' + backend
    for resource in ['config/en.yml', 'config.yml', 'messages/en.yml', 'punishments/en.yml', 'database/en.yml', 'discord/en.yml']:
        assert resource in names, 'Missing template: ' + resource
    leftovers = []
    core_classes = 0
    for name in names:
        if name.endswith('/'):
            continue
        data = jar.read(name)
        if name.endswith('.class'):
            if name.startswith(('uz/sentinel/ac/', 'uz/sentinel/engine/')) and not name.startswith('uz/sentinel/engine/libs/'):
                core_classes += 1
                assert struct.unpack('>HH', data[4:8]) == (0, expected_major), 'Wrong target bytecode: ' + name
            assert int.from_bytes(data[6:8], 'big') <= expected_major, 'Bundled class requires newer Java: ' + name
        # Preserve attribution and third-party licenses verbatim.
        legal = name.startswith('META-INF/') and any(token in name.lower() for token in ['license', 'notice'])
        strings = utf8_constants(data) if name.endswith('.class') else [data]
        if not legal and (any(b'grim' in value.lower() for value in strings) or 'grim' in name.lower()):
            leftovers.append(name)
    assert core_classes >= 949, 'Engine/API source classes missing'
    assert not leftovers, 'Old active branding: ' + ', '.join(leftovers[:30])
print(f'PASS: standalone source engine, complete templates, Sentinel branding, Java {java}, Paper {target}; {core_classes} core classes')
print('SHA256 ' + hashlib.sha256(path.read_bytes()).hexdigest())
