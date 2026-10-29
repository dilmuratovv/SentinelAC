#!/usr/bin/env python3
"""Download exact upstream test artifacts and verify their published hashes."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

def download(destination):
    manifest = json.loads(Path(__file__).with_name('test-dependencies.json').read_text())
    destination.mkdir(parents=True, exist_ok=True)
    result = {}
    for key, item in manifest.items():
        path = destination / item['filename']
        if not path.exists() or hashlib.new(item['hash'], path.read_bytes()).hexdigest() != item['checksum']:
            request = urllib.request.Request(item['url'], headers={'User-Agent': 'SentinelAC-development-tests/2.4'})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
            if hashlib.new(item['hash'], data).hexdigest() != item['checksum']:
                raise RuntimeError('Upstream checksum mismatch: ' + key)
            path.write_bytes(data)
        result[key] = path.resolve()
        print('Verified ' + path.name, flush=True)
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--destination', type=Path, default=Path('target/test-dependencies'))
    args = parser.parse_args()
    download(args.destination)
