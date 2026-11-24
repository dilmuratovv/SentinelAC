#!/usr/bin/env python3
"""Run the combat behaviour probes (tools/client/combat.cjs) against a disposable localhost Paper server.

Each scenario imitates a cheat or a person doing the same thing slowly; the result lists which Sentinel checks
flagged the probe player (from plugins/SentinelAC/logs). Pass --accept-eula only after accepting the Minecraft EULA.

  python3 tools/combat_probe.py --paper paper.jar --java /path/to/java --jar SentinelAC.jar \
      --via ViaVersion.jar --via ViaBackwards.jar --work /tmp/probe --accept-eula
"""
import argparse
import json
import queue
import shutil
import socket
import subprocess
import threading
import time
from pathlib import Path

CLIENT = Path(__file__).with_name('client') / 'combat.cjs'
SENTINEL = ('AutoClickerA', 'AutoClickerB', 'KillAuraA', 'KillAuraB', 'AutoTotemA', 'CrystalAuraA')
# scenario -> (checks that must flag, checks that must stay silent)
EXPECT = {
    'clicker25': ({'AutoClickerA'}, set()),
    'clicker10': ({'AutoClickerB'}, {'AutoClickerA'}),
    'human': (set(), set(SENTINEL)),
    'aura': ({'KillAuraA'}, set()),
    'switcher': (set(), set(SENTINEL)),
    'autototem': ({'AutoTotemA'}, set()),
    'manualtotem': (set(), set(SENTINEL)),
    'crystalaura': ({'CrystalAuraA'}, set()),
    'crystalhuman': (set(), set(SENTINEL)),
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--paper', type=Path, required=True)
    parser.add_argument('--java', required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--via', type=Path, action='append', default=[])
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--client-version', default='26.1')
    parser.add_argument('--scenario', action='append')
    parser.add_argument('--accept-eula', action='store_true')
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error('Accept the Minecraft EULA to run an isolated test server')

    server = args.work / 'server'
    if server.exists():
        shutil.rmtree(server)
    (server / 'plugins').mkdir(parents=True)
    for jar in [args.jar] + args.via:
        shutil.copy2(jar, server / 'plugins' / jar.name)
    (server / 'eula.txt').write_text('eula=true\n')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    (server / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nwhite-list=false\nenforce-whitelist=false\nmax-players=5\nview-distance=3\n'
        'simulation-distance=3\ngenerate-structures=false\nlevel-type=minecraft:flat\nspawn-protection=0\n'
        'difficulty=peaceful\nspawn-monsters=false\n'
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n')

    process = subprocess.Popen([args.java, '-Xms512M', '-Xmx2G', '-jar', str(args.paper.resolve()), '--nogui'],
                               cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, bufsize=1)
    lines = queue.Queue()
    log = []
    threading.Thread(target=lambda: [lines.put(line) for line in process.stdout], daemon=True).start()

    def drain(until=None, seconds=0.0):
        deadline = time.monotonic() + seconds
        while True:
            timeout = max(0.0, deadline - time.monotonic())
            try:
                line = lines.get(timeout=min(0.25, timeout) if timeout else 0.01)
            except queue.Empty:
                if time.monotonic() >= deadline:
                    if until:
                        raise RuntimeError('Server did not report: ' + until)
                    return
                continue
            log.append(line)
            if until and until in line:
                return

    def command(text):
        process.stdin.write(text + '\n')
        process.stdin.flush()

    results = {}
    try:
        drain('Done (', 240)
        drain(seconds=2)
        command('fill -8 -63 -8 8 -61 8 minecraft:obsidian')
        drain(seconds=1)
        for scenario in args.scenario or list(EXPECT):
            name = ('P' + scenario)[:16]
            bot = subprocess.Popen(['node', str(CLIENT), str(port), scenario, args.client_version],
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            try:
                drain(name + ' joined the game', 40)
            except RuntimeError:
                bot.kill()
                raise RuntimeError(name + ' did not join; client output:\n' + bot.stdout.read())
            drain(seconds=3)  # let the client finish loading
            command(f'tp {name} 0.5 -60 0.5 0 10')
            setup = {'clicker25': 'one', 'clicker10': 'one', 'human': 'one', 'aura': 'two', 'switcher': 'two',
                     'autototem': 'totem', 'manualtotem': 'totem', 'crystalaura': 'crystal',
                     'crystalhuman': 'crystal'}[scenario]
            villager = '{NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b}'
            if setup == 'one':
                command(f'summon minecraft:villager 0.5 -60 2.5 {villager}')
            elif setup == 'two':
                command(f'summon minecraft:villager 3.5 -60 0.5 {villager}')
                command(f'summon minecraft:villager -2.5 -60 0.5 {villager}')
            elif setup == 'totem':
                command(f'clear {name}')
                command(f'item replace entity {name} weapon.offhand with minecraft:totem_of_undying')
                command(f'give {name} minecraft:totem_of_undying 9')
            elif setup == 'crystal':
                command(f'effect give {name} minecraft:resistance infinite 4 true')
            drain(seconds=2)
            bot.stdin.write('go\n')
            bot.stdin.flush()
            start = time.monotonic()
            schedule = []
            if setup == 'totem':
                schedule = [(2.0 + 3.0 * i, f'damage {name} 1000 minecraft:generic') for i in range(3)]
            elif setup == 'crystal':
                schedule = [(1.0 + 1.4 * i, 'summon minecraft:end_crystal 0.5 -60 2.5 {ShowBottom:0b,Invulnerable:1b}') for i in range(10)]
            while bot.poll() is None:
                elapsed = time.monotonic() - start
                while schedule and schedule[0][0] <= elapsed:
                    command(schedule.pop(0)[1])
                drain(seconds=0.1)
                if elapsed > 60:
                    bot.kill()
            output = bot.stdout.read()
            report = next((json.loads(line[7:]) for line in output.splitlines() if line.startswith('REPORT ')), {})
            command('kill @e[type=minecraft:villager]')
            command('kill @e[type=minecraft:end_crystal]')
            drain(seconds=1.5)
            flagged = {}
            for path in (server / 'plugins/SentinelAC/logs').glob('violations.*.log'):
                for record in path.read_text().splitlines():
                    fields = record.split('\t')
                    if len(fields) >= 5 and fields[2] == name:
                        flagged.setdefault(fields[3], fields[4][:120])
            must, silent = EXPECT[scenario]
            ok = must <= set(flagged) and not (silent & set(flagged))
            results[scenario] = {'ok': ok, 'flagged': flagged, 'report': report}
            print(f"{'PASS' if ok else 'FAIL'}: {scenario} flagged={sorted(flagged)} report={report}", flush=True)
        command('stop')
        process.wait(timeout=60)
    finally:
        if process.poll() is None:
            process.kill()
        while not lines.empty():
            log.append(lines.get_nowait())
        (args.work / 'server-output.log').write_text(''.join(log))
        (args.work / 'combat-results.json').write_text(json.dumps(results, indent=2) + '\n')
    if not all(r['ok'] for r in results.values()):
        raise SystemExit('Some combat probes failed; see combat-results.json')
    print('ALL COMBAT PROBES PASSED', flush=True)


if __name__ == '__main__':
    main()
