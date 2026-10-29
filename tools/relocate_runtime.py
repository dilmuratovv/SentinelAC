#!/usr/bin/env python3
"""Finish namespace relocation of descriptor strings in third-party lambdas.

Maven Shade remaps class references, but FastUtil's serialized-lambda guards
also contain literal JVM method descriptors. Rewrite those UTF8 constants with
correct classfile lengths. This processes the library-only runtime, never the
engine: all engine/API classes are compiled from source in the plugin module.
"""
from pathlib import Path
import os
import struct
import sys
from zipfile import ZipFile, ZIP_DEFLATED

WIDTHS = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
          12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}

def relocate(data):
    assert data[:4] == b'\xca\xfe\xba\xbe'
    size = int.from_bytes(data[8:10], 'big')
    result = bytearray(data[:10])
    cursor, index = 10, 1
    while index < size:
        tag = data[cursor]; cursor += 1
        result.append(tag)
        if tag == 1:
            length = int.from_bytes(data[cursor:cursor+2], 'big'); cursor += 2
            value = data[cursor:cursor+length]; cursor += length
            value = value.replace(b'ac/grim/grimac/shaded', b'uz/sentinel/engine/libs')
            value = value.replace(b'ac.grim.grimac.shaded', b'uz.sentinel.engine.libs')
            result += struct.pack('>H', len(value)) + value
        else:
            length = WIDTHS[tag]; result += data[cursor:cursor+length]; cursor += length
            if tag in (5, 6): index += 1
        index += 1
    result += data[cursor:]
    return bytes(result)

path = Path(sys.argv[1])
temporary = path.with_suffix('.relocated.jar')
changed = 0
with ZipFile(path) as source, ZipFile(temporary, 'w', compression=ZIP_DEFLATED, compresslevel=9) as target:
    for info in source.infolist():
        data = source.read(info)
        if info.filename.startswith('META-INF/LICENSE'):
            # Preserve the carrier's SLF4J copyright/permission notice under a
            # unique path so the final plugin's own GPL file cannot shadow it.
            info.filename = 'META-INF/licenses/runtime-' + Path(info.filename).name
        if info.filename.endswith('.class'):
            assert info.filename.startswith(('uz/sentinel/engine/libs/', 'com/lmax/')), 'Runtime contains engine/server bytecode'
            transformed = relocate(data)
            changed += transformed != data
            data = transformed
        target.writestr(info, data)
os.replace(temporary, path)
print(f'Finished third-party lambda namespace relocation: {changed} classes')
