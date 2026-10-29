#!/usr/bin/env python3
"""Publish only the hash-verified build; never overwrite a published release."""
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

config = json.loads(Path('release.json').read_text())
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

artifact = Path(config['artifact'])
digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
assert digest == config['sha256'], 'Artifact differs from the tested build'
checksums = Path('SHA256SUMS.txt')
checksums.write_text(digest + '  ' + artifact.name + '\n')
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
    for path in (artifact, checksums):
        for asset in release.get('assets', []):
            if asset['name'] == path.name:
                api('DELETE', '/releases/assets/' + str(asset['id']))
        upload = release['upload_url'].split('{', 1)[0] + '?name=' + urllib.parse.quote(path.name)
        api('POST', upload, raw=path.read_bytes(), content_type='application/octet-stream')
    release = api('PATCH', '/releases/' + str(release['id']), {'draft': False, 'make_latest': 'true'})
else:
    tag = api('GET', '/git/ref/tags/' + urllib.parse.quote(config['tag'], safe=''))
    assert tag['object']['sha'] == revision, 'Existing release targets a different commit'
    asset = next(item for item in release['assets'] if item['name'] == artifact.name)
    assert asset.get('digest') == 'sha256:' + digest, 'Published asset differs'
print('Published: ' + release['html_url'])

# Exact head leases and an ancestry check protect branches changed since review.
for branch, expected in config.get('cleanup_branches', {}).items():
    assert branch.startswith('codex/')
    ref_path = '/git/refs/heads/' + urllib.parse.quote(branch, safe='/')
    try:
        ref = api('GET', '/git/ref/heads/' + urllib.parse.quote(branch, safe='/'))
    except urllib.error.HTTPError as error:
        if error.code == 404:
            continue
        raise
    assert ref['object']['sha'] == expected, 'Work branch changed: ' + branch
    comparison = api('GET', '/compare/' + expected + '...' + revision)
    assert comparison['behind_by'] == 0, 'Unmerged changes: ' + branch
    api('DELETE', ref_path)
    print('Removed merged branch: ' + branch)
