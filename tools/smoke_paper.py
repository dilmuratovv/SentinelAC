#!/usr/bin/env python3
"""Isolated localhost Paper boot/command tests. Does not touch a production server.

Requires explicit --accept-eula: https://www.minecraft.net/eula .
The process uses an offline temporary test world and optional Via plugins.
"""
import argparse
import os
import json
from pathlib import Path
import queue
import shutil
import socket
import subprocess
import threading
import time
import urllib.parse
import zipfile
from download_test_dependencies import download

def run_case(root, java, artifacts, sentinel, via, client_probes=False, target=None, shared_root=None):
    if target is None:
        with zipfile.ZipFile(sentinel) as jar:
            metadata = dict(line.split('=', 1) for line in jar.read('sentinel-target.properties').decode().splitlines() if '=' in line)
            target = metadata['minecraft.version']
    directory = root / ('via' if via else 'native')
    if directory.exists():
        shutil.rmtree(directory)
    plugins = directory / 'plugins'
    plugins.mkdir(parents=True)
    for path in [sentinel] + ([artifacts['viaversion'], artifacts['viabackwards']] if via else []):
        shutil.copy2(path, plugins / path.name)
    for name in ['cache', 'libraries']:
        shared = (shared_root or root) / ('shared-' + name)
        shared.mkdir(exist_ok=True)
        (directory / name).symlink_to(shared, target_is_directory=True)
    (directory / 'eula.txt').write_text('eula=true\n')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    (directory / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n'
        'enable-status=true\nmax-players=5\nview-distance=2\nsimulation-distance=2\n'
        'generate-structures=false\nlevel-type=minecraft:flat\nspawn-protection=0\n'
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n')
    args = [java, '-Xms256M', '-Xmx1536M', '-Djavax.net.ssl.trustStore=' + os.environ.get('JAVA_TRUSTSTORE', java_truststore(java))]
    proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
    if proxy.hostname:
        args += [f'-Dhttps.proxyHost={proxy.hostname}', f'-Dhttps.proxyPort={proxy.port or 80}',
                 f'-Dhttp.proxyHost={proxy.hostname}', f'-Dhttp.proxyPort={proxy.port or 80}']
    args += ['-jar', str(artifacts['paper']), '--nogui']
    process = subprocess.Popen(args, cwd=directory, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, bufsize=1)
    lines = queue.Queue()
    def pump():
        for line in process.stdout: lines.put(line)
    threading.Thread(target=pump, daemon=True).start()
    captured = []
    def wait_for(text, seconds=150):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=0.25)
                captured.append(line)
                print(line, end='', flush=True)
                if text in line: return
            except queue.Empty:
                if process.poll() is not None: break
        raise RuntimeError('Server did not report: ' + text)
    def command(value):
        process.stdin.write(value + '\n'); process.stdin.flush()
    try:
        wait_for('Done (')
        command('sac info')
        wait_for('Channel policy=')
        command('sentinelac version')
        wait_for('Releases: https://github.com/dilmuratovv/SentinelAC')
        command('sentinel help')
        wait_for('/sac recent')
        command('sac reload')
        wait_for('Config has been reloaded.')
        config = plugins / 'SentinelAC/config.yml'
        original = config.read_text()
        config.write_text(original.replace('max-crystals-per-second: 12', 'max-crystals-per-second: 0'))
        command('sac reload')
        wait_for('Failed to reload config.')
        config.write_text(original)
        command('sac reload')
        wait_for('Config has been reloaded.')
        native_protocol = None
        if client_probes and not via:
            resolved = subprocess.run(['node', str(Path(__file__).with_name('client') / 'native_protocol.cjs'), str(port)], text=True, capture_output=True, timeout=12)
            if resolved.returncode:
                raise RuntimeError('Native protocol discovery failed: ' + resolved.stderr)
            native_protocol = json.loads(resolved.stdout)
            if target.startswith('1.21') and native_protocol['client'] is None:
                raise RuntimeError('No test codec for native legacy protocol: ' + str(native_protocol))
            print('Native protocol coverage: ' + json.dumps(native_protocol), flush=True)
        if client_probes and (via or native_protocol and native_protocol['client']):
            clients = '1.21.11,26.1' if via else native_protocol['client']
            movement_client = '26.1' if via else native_protocol['client']
            result = subprocess.run(['node', str(Path(__file__).with_name('client') / 'probe.cjs'), str(port), clients],
                                    text=True, capture_output=True, timeout=100)
            print(result.stdout, end='', flush=True)
            if result.returncode:
                raise RuntimeError('Protocol client checks failed: ' + result.stderr)
            result = subprocess.run(['node', str(Path(__file__).with_name('client') / 'movement.cjs'), str(port), movement_client],
                                    text=True, capture_output=True, timeout=25)
            print(result.stdout, end='', flush=True)
            if result.returncode:
                raise RuntimeError('Movement probe failed: ' + result.stderr)
            command('sac info')
            wait_for('Diagnostika: processed=')
            logs = '\n'.join(path.read_text() for path in (plugins / 'SentinelAC/logs').glob('violations.*.log'))
            if 'MovementProbe' not in logs or 'Simulation' not in logs:
                raise RuntimeError('Impossible movement did not produce a core Simulation finding')
        command('sac dump')
        wait_for('SentinelAC: diagnostic saved to ')
        command('sac perf')
        wait_for('Milliseconds per prediction')
        command('stop')
        process.wait(timeout=40)
        if process.returncode != 0: raise RuntimeError('Server exit code ' + str(process.returncode))
        text = ''.join(captured)
        if 'Sentinel ready: Paper ' + target + ',' not in text or 'Sentinel startup failed' in text:
            raise RuntimeError('Sentinel did not become ready')
        if any(word in text for word in ['Failed to start ', 'Failed to load ', 'Failed to register commands']):
            raise RuntimeError('Engine initializer reported a failure')
        print('PASS: Paper ' + target + ' ' + ('ViaVersion + ViaBackwards' if via else 'native') + ' startup/commands/reload', flush=True)
        return {'mode': 'via' if via else 'native', 'native_protocol': native_protocol, 'client_probes': client_probes and (via or bool(native_protocol and native_protocol['client']))}
    finally:
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
        while not lines.empty(): captured.append(lines.get_nowait())
        (directory / 'smoke-output.log').write_text(''.join(captured))

def java_truststore(java):
    system = Path('/etc/ssl/certs/java/cacerts')
    return str(system if system.exists() else Path(java).resolve().parent.parent / 'lib/security/cacerts')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--accept-eula', action='store_true')
    parser.add_argument('--java', default=shutil.which('java'))
    parser.add_argument('--sentinel', type=Path, default=Path('plugin/target/SentinelAC-2.6.0-paper26.2.jar'))
    parser.add_argument('--mode', choices=['native', 'via', 'both'], default='both')
    parser.add_argument('--client-probes', action='store_true', help='Requires npm ci --prefix tools/client')
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error('Read https://www.minecraft.net/eula and pass --accept-eula to run a test server')
    artifacts = download(Path('target/test-dependencies'))
    root = Path('target/smoke').resolve()
    root.mkdir(parents=True, exist_ok=True)
    for via in ([False, True] if args.mode == 'both' else [args.mode == 'via']):
        run_case(root, args.java, artifacts, args.sentinel.resolve(), via, args.client_probes)
