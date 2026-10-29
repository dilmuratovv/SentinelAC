#!/usr/bin/env python3
"""Verify one version-specific JAR on its exact hash-pinned Paper target."""
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
import urllib.request
from download_test_dependencies import download
from smoke_paper import run_case

parser = argparse.ArgumentParser()
parser.add_argument('--target', required=True)
parser.add_argument('--java', required=True)
parser.add_argument('--jar', type=Path)
parser.add_argument('--mode', choices=['native', 'via', 'both'], default='both')
parser.add_argument('--accept-eula', action='store_true')
parser.add_argument('--client-probes', action='store_true')
args = parser.parse_args()
if not args.accept_eula:
    parser.error('Accept the Minecraft EULA to run isolated test servers')
entry = next(x for x in json.loads(Path('tools/server-matrix.json').read_text())['servers'] if x['version'] == args.target)
jar = args.jar or Path(f'plugin/target/SentinelAC-2.6.0-paper{args.target}.jar')
folder = Path('target/test-dependencies'); folder.mkdir(parents=True, exist_ok=True)
artifacts = download(folder, keys={'viaversion','viabackwards'})
paper = folder / entry['filename']
if not paper.exists() or hashlib.sha256(paper.read_bytes()).hexdigest() != entry['sha256']:
    request = urllib.request.Request(entry['url'], headers={'User-Agent': 'SentinelAC-tests/2.6 (https://github.com/dilmuratovv/SentinelAC)'})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    assert hashlib.sha256(data).hexdigest() == entry['sha256'], 'Paper checksum mismatch'
    paper.write_bytes(data)
artifacts['paper'] = paper.resolve()
root = Path('target/matrix-smoke') / args.target; root.mkdir(parents=True, exist_ok=True)
root = root.resolve()
cases = []
for via in ([False, True] if args.mode == 'both' else [args.mode == 'via']):
    cases.append(run_case(root, args.java, artifacts, jar.resolve(), via, args.client_probes, args.target, root.parent))
report = {'target': args.target, 'paper_build': entry['build'], 'paper_channel': entry['channel'],
          'java': entry['java'], 'jar': jar.name, 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
          'native_server': args.mode in ('native', 'both'), 'via_server': args.mode in ('via', 'both'),
          'native_client_probes': any(case['mode'] == 'native' and case['client_probes'] for case in cases),
          'cases': cases,
          'translated_client_probes': args.client_probes and args.mode in ('via', 'both'), 'result': 'passed'}
suites = [ET.parse(path).getroot() for path in Path('plugin/target/surefire-reports').glob('TEST-*.xml')]
report['unit_tests'] = {key: sum(int(suite.get(key, 0)) for suite in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
assert report['unit_tests']['tests'] >= 77 and all(report['unit_tests'][key] == 0 for key in ['failures','errors','skipped'])
(root / 'verification.json').write_text(json.dumps(report, indent=2)+'\n')
print('TARGET PASSED: '+args.target, flush=True)
