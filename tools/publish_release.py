#!/usr/bin/env python3
"""Publish a complete matrix atomically: all verified JARs first, then public release."""
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
import zipfile

config = json.loads(Path('release.json').read_text())
servers = json.loads(Path('tools/server-matrix.json').read_text())['servers']
repository = os.environ['GITHUB_REPOSITORY']
revision = os.environ['GITHUB_SHA']
assert os.environ['GITHUB_REF'] == 'refs/heads/main'
base = 'https://api.github.com/repos/' + repository
headers = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json',
           'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'SentinelAC-release'}

def api(method, path, body=None, raw=None, content_type=None):
    url = path if path.startswith('https://') else base + path
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    request_headers = dict(headers)
    if data is not None:
        request_headers['Content-Type'] = content_type or 'application/json'
    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    with urllib.request.urlopen(request, timeout=120) as response:
        result = response.read()
        return json.loads(result) if result else None

folder = Path(config['assets_directory'])
assets, reports, sums = [], [], []
for server in servers:
    target = server['version']
    path = folder / f'SentinelAC-{config["version"]}-paper{target}.jar'
    report = json.loads((folder / f'verification-paper{target}.json').read_text())
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    assert report['target'] == target and report['sha256'] == digest and report['jar'] == path.name
    assert report['paper_build'] == server['build'] and report['java'] == server['java']
    assert report['result'] == 'passed' and report['native_server'] and report['via_server'] and report['translated_client_probes']
    assert report['unit_tests']['tests'] >= 77
    assert all(report['unit_tests'][key] == 0 for key in ['failures','errors','skipped'])
    if target.startswith('1.21'):
        assert report['native_client_probes'], 'Native legacy protocol was not exercised'
    with zipfile.ZipFile(path) as jar:
        metadata = dict(line.split('=',1) for line in jar.read('sentinel-target.properties').decode().splitlines() if '=' in line)
        assert metadata['minecraft.version'] == target and int(metadata['java.version']) == server['java']
    assets.append(path); reports.append(report); sums.append(digest + '  ' + path.name)
assert len(list(folder.glob('*.jar'))) == len(servers), 'Unexpected or missing target JAR'
checksums = folder / 'SHA256SUMS.txt'; checksums.write_text('\n'.join(sums)+'\n')
verification = folder / 'VERIFICATION.json'
verification.write_text(json.dumps({'commit': revision, 'version': config['version'], 'targets': reports}, indent=2)+'\n')
assets += [checksums, verification]
try:
    release = api('GET', '/releases/tags/' + urllib.parse.quote(config['tag'], safe=''))
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
    release = api('POST', '/releases', {'tag_name': config['tag'], 'target_commitish': revision,
        'name': config['name'], 'body': Path(config['notes']).read_text(), 'draft': True,
        'prerelease': config['prerelease']})
if release['draft']:
    assert release['target_commitish'] == revision, 'Draft targets a different commit'
    for path in assets:
        for asset in release.get('assets', []):
            if asset['name'] == path.name:
                api('DELETE', '/releases/assets/' + str(asset['id']))
        upload = release['upload_url'].split('{', 1)[0] + '?name=' + urllib.parse.quote(path.name)
        uploaded = api('POST', upload, raw=path.read_bytes(), content_type='application/octet-stream')
        assert uploaded['state'] == 'uploaded'
        if uploaded.get('digest'):
            assert uploaded['digest'] == 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()
    release = api('PATCH', '/releases/' + str(release['id']), {'draft': False, 'make_latest': 'true'})
else:
    tag = api('GET', '/git/ref/tags/' + urllib.parse.quote(config['tag'], safe=''))
    assert tag['object']['sha'] == revision, 'Existing release targets a different commit'
    for path in assets:
        asset = next(item for item in release['assets'] if item['name'] == path.name)
        assert asset.get('digest') == 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()
print('Published complete matrix: ' + release['html_url'])

# Remove only this project's merged work branch; retain any later unmerged work.
for branch in config.get('cleanup_branches', []):
    assert branch.startswith('codex/')
    reference = '/git/ref/heads/' + urllib.parse.quote(branch, safe='/')
    try:
        ref = api('GET', reference)
    except urllib.error.HTTPError as error:
        if error.code == 404: continue
        raise
    comparison = api('GET', '/compare/' + ref['object']['sha'] + '...' + revision)
    if comparison['behind_by'] != 0:
        print('Preserved branch with unmerged work: ' + branch)
        continue
    api('DELETE', '/git/refs/heads/' + urllib.parse.quote(branch, safe='/'))
    print('Removed merged work branch: ' + branch)
