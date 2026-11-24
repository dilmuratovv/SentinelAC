#!/usr/bin/env python3
"""Real cheat-client test bench (localhost only).

Runs a disposable Paper server with SentinelAC, a standing target player (tools/client/target.cjs) and a real
Minecraft client with a cheat client mod (for example Meteor Client) inside a virtual X display. Cheat modules are
toggled through the client's own chat commands and the "legit" baselines use real mouse/keyboard input through
XTEST, so every packet comes from the genuine client code. Results: which checks flagged, and kicks.

Needs: Xvfb, a Python with python-xlib and pillow (run this script with it), portablemc with the client installed.
Pass --accept-eula only after accepting the Minecraft EULA.
"""
import argparse
import json
import os
import queue
import random
import shutil
import socket
import subprocess
import threading
import time
from pathlib import Path

from Xlib import X, XK, display
from Xlib.ext import xtest

ROOT = Path(__file__).resolve().parent
PLAYER = 'Meteorbot'
NETWORK = {'rtt_ms': 0}  # simulated round trip of the game client (the human only sees what reached the client)
HUMAN_REACTION_MS = (170, 30)  # visual-motor reaction of a fast player: mean, standard deviation
W, H = 320, 240  # tiny window: the client renders on the CPU (no GPU in Xvfb), every pixel costs ticks
TARGET_POS = {'x': 0.5, 'z': 2.5}  # where the moving target is (player stands at 0.5, 0.5)
DEG_PER_PIXEL = 0.15  # mouseSensitivity 0.5 -> (0.5*0.6+0.2)^3*8*0.15
TARGET = 'Target'


class Server:
    def __init__(self, work, paper, java, plugins):
        self.dir = work / 'server'
        if self.dir.exists():
            shutil.rmtree(self.dir)
        (self.dir / 'plugins').mkdir(parents=True)
        for jar in plugins:
            shutil.copy2(jar, self.dir / 'plugins' / jar.name)
        (self.dir / 'eula.txt').write_text('eula=true\n')
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0))
            self.port = s.getsockname()[1]
        (self.dir / 'server.properties').write_text(
            f'server-ip=127.0.0.1\nserver-port={self.port}\nonline-mode=false\nwhite-list=false\n'
            'enforce-whitelist=false\nmax-players=5\nview-distance=3\nsimulation-distance=3\n'
            'generate-structures=false\nlevel-type=minecraft:flat\nspawn-protection=0\ndifficulty=peaceful\n'
            'spawn-monsters=false\npvp=true\nallow-flight=false\n'
            'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},'
            '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n')
        self.lines = queue.Queue()
        self.log = []
        trace = ['-Dsentinel.trace=' + str((work / 'trace').resolve())] if os.environ.get('SENTINEL_TRACE') else []
        self.process = subprocess.Popen([java, '-Xms384M', '-Xmx700M', *trace, '-jar', str(paper), '--nogui'], cwd=self.dir,
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, bufsize=1)
        threading.Thread(target=lambda: [self.lines.put(l) for l in self.process.stdout], daemon=True).start()

    def pump(self, until=None, seconds=0.0):
        deadline = time.monotonic() + seconds
        while True:
            try:
                line = self.lines.get(timeout=0.1)
                self.log.append(line)
                if until and until in line:
                    return line
            except queue.Empty:
                pass
            if time.monotonic() >= deadline:
                if until:
                    raise RuntimeError('server did not report: ' + until)
                return None

    def cmd(self, text):
        self.process.stdin.write(text + '\n')
        self.process.stdin.flush()

    def flags(self, since_ms, name=PLAYER):
        found = {}
        for path in (self.dir / 'plugins/SentinelAC/logs').glob('violations.*.log'):
            for record in path.read_text(errors='replace').splitlines():
                f = record.split('\t')
                if len(f) >= 5 and f[2] == name and f[0].isdigit() and int(f[0]) >= since_ms:
                    found.setdefault(f[3], []).append(f[4][:100])
        return found


class LatencyProxy:
    """TCP proxy that makes the localhost connection behave like a real network: half the RTT in each direction,
    random jitter, and short lag spikes. Byte order is preserved (a chunk is never released before an earlier one)."""

    def __init__(self, target_port, rtt_ms, jitter_ms, spike_every_s=0.0, spike_ms=0):
        self.target_port, self.half, self.jitter = target_port, rtt_ms / 2000.0, jitter_ms / 2000.0
        self.spike_every, self.spike = spike_every_s, spike_ms / 1000.0
        self.listener = socket.socket()
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen(8)
        self.port = self.listener.getsockname()[1]
        self.rng = random.Random(17)
        threading.Thread(target=self._accept, daemon=True).start()

    def _extra(self):
        """Lag spike: for 1 s out of every spike_every seconds both directions get spike_ms more delay."""
        if self.spike_every <= 0:
            return 0.0
        return self.spike if (time.monotonic() % self.spike_every) < 1.0 else 0.0

    def _accept(self):
        while True:
            try:
                client, _ = self.listener.accept()
            except OSError:
                return
            server = socket.create_connection(('127.0.0.1', self.target_port))
            for a, b in ((client, server), (server, client)):
                a.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                q = queue.Queue()
                threading.Thread(target=self._read, args=(a, q), daemon=True).start()
                threading.Thread(target=self._write, args=(b, q), daemon=True).start()

    def _read(self, sock, q):
        last = 0.0
        while True:
            try:
                data = sock.recv(65536)
            except OSError:
                data = b''
            if not data:
                q.put((0.0, None))
                return
            due = time.monotonic() + max(0.0, self.half + self.rng.gauss(0, self.jitter)) + self._extra()
            last = max(last, due)  # TCP keeps order: never earlier than the previous chunk
            q.put((last, data))

    def _write(self, sock, q):
        while True:
            due, data = q.get()
            if data is None:
                try:
                    sock.shutdown(socket.SHUT_WR)
                except OSError:
                    pass
                return
            wait = due - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            try:
                sock.sendall(data)
            except OSError:
                return


class Client:
    """The real game client in its own virtual display."""

    def __init__(self, args, port, number=91):
        self.args, self.port, self.number = args, port, number
        self.xvfb = subprocess.Popen(['Xvfb', f':{number}', '-screen', '0', f'{W}x{H}x24', '-nolisten', 'tcp'],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1.5)
        self.display = display.Display(f':{number}')
        self.process = None

    def launch(self):
        self.prepare_options()
        # The cheat client saves which modules were on (a kicked run never switched them off): start clean.
        for state in (self.args.mc / 'meteor-client').glob('modules.nbt*'):
            state.unlink()
        for state in (self.args.mc / 'LiquidBounce').glob('modules.json*'):
            state.unlink()
        env = dict(os.environ, DISPLAY=f':{self.number}', LIBGL_ALWAYS_SOFTWARE='1', XDG_SESSION_TYPE='x11')
        # Newer LWJGL/GLFW prefer Wayland when it is available: the game would open on the real desktop instead of
        # the virtual display (and receive no XTEST input). Hide the Wayland session from the client.
        env.pop('WAYLAND_DISPLAY', None)
        env['SDL_VIDEODRIVER'] = env['SDL_VIDEO_DRIVER'] = 'x11'  # 26.x creates its window through SDL
        log = open(self.args.work / 'client.log', 'a')
        self.process = subprocess.Popen([str(self.args.portablemc), '--main-dir', str(self.args.mc), 'start',
                                         self.args.client, '-u', PLAYER, '--resolution', f'{W}x{H}',
                                         '--join-server', '127.0.0.1', '--join-server-port', str(self.port),
                                         '--jvm', self.args.client_java, f'--jvm-arg=-Xmx{self.args.client_heap},-Xms256m'],
                                        env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)

    def prepare_options(self):
        options = self.args.mc / 'options.txt'
        wanted = {'pauseOnLostFocus': 'false', 'onboardAccessibility': 'false', 'skipMultiplayerWarning': 'true',
                  'joinedFirstServer': 'true', 'renderDistance': '2', 'simulationDistance': '5', 'maxFps': '20',
                  'tutorialStep': 'none', 'narrator': '0', 'chatVisibility': '0', 'autoJump': 'false',
                  'mouseSensitivity': '0.5', 'rawMouseInput': 'true', 'fullscreen': 'false',
                  # cheapest rendering possible
                  'graphicsMode': '0', 'renderClouds': '"false"', 'entityShadows': 'false', 'particles': '2',
                  'mipmapLevels': '0', 'ao': 'false', 'biomeBlendRadius': '0', 'enableVsync': 'false',
                  'guiScale': '1', 'entityDistanceScaling': '0.5', 'menuBackgroundBlurriness': '0',
                  'cutoutLeaves': 'false', 'chunkSectionFadeInTime': '0.0'}
        lines = options.read_text().splitlines() if options.exists() else []
        seen = set()
        out = []
        for line in lines:
            key = line.split(':', 1)[0]
            if key in wanted:
                out.append(f'{key}:{wanted[key]}')
                seen.add(key)
            else:
                out.append(line)
        out += [f'{k}:{v}' for k, v in wanted.items() if k not in seen]
        options.write_text('\n'.join(out) + '\n')

    def alive(self):
        return self.process is not None and self.process.poll() is None

    def stop(self):
        """Stops the launcher AND the game it started (they share a process group)."""
        if self.process is None:
            return
        import signal
        try:
            os.killpg(self.process.pid, signal.SIGTERM)
        except ProcessLookupError:
            return
        try:
            self.process.wait(20)
        except subprocess.TimeoutExpired:
            pass
        time.sleep(2)
        try:
            os.killpg(self.process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass

    # ---- input through XTEST, exactly like a person at a keyboard and mouse
    def _key(self, keysym, press):
        code = self.display.keysym_to_keycode(keysym)
        xtest.fake_input(self.display, X.KeyPress if press else X.KeyRelease, code)
        self.display.sync()

    def tap(self, name, hold=0.04):
        keysym = XK.string_to_keysym(name)
        self._key(keysym, True)
        time.sleep(hold)
        self._key(keysym, False)
        time.sleep(0.03)

    def hold(self, name, press):
        self._key(XK.string_to_keysym(name), press)

    def type(self, text):
        names = {'.': 'period', '-': 'minus', ' ': 'space', '/': 'slash', '_': 'underscore', ':': 'colon'}
        shift = XK.string_to_keysym('Shift_L')
        for ch in text:
            upper = ch.isupper() or ch in '_:'
            if upper:
                self._key(shift, True)
            self.tap(names.get(ch, ch.lower()), 0.02)
            if upper:
                self._key(shift, False)

    def chat(self, text):
        # No pointer warp here: while the game holds the mouse, any pointer jump turns the camera.
        self.tap('t')
        time.sleep(0.4)
        self.type(text)
        time.sleep(0.1)
        self.tap('Return')
        time.sleep(0.4)

    def center(self):
        xtest.fake_input(self.display, X.MotionNotify, x=W // 2, y=H // 2)
        self.display.sync()

    def button(self, number, press):
        xtest.fake_input(self.display, X.ButtonPress if press else X.ButtonRelease, number)
        self.display.sync()

    def click(self, hold=0.05):
        xtest.fake_input(self.display, X.ButtonPress, 1)
        self.display.sync()
        time.sleep(hold)
        xtest.fake_input(self.display, X.ButtonRelease, 1)
        self.display.sync()

    def right(self, hold=0.05):
        xtest.fake_input(self.display, X.ButtonPress, 3)
        self.display.sync()
        time.sleep(hold)
        xtest.fake_input(self.display, X.ButtonRelease, 3)
        self.display.sync()

    def move_mouse(self, dx, dy):
        """Relative mouse motion (the game captures the pointer; it reads deltas)."""
        xtest.fake_input(self.display, X.MotionNotify, True, x=int(dx), y=int(dy))
        self.display.sync()

    def screenshot(self, path):
        from PIL import Image
        root = self.display.screen().root
        raw = root.get_image(0, 0, W, H, X.ZPixmap, 0xffffffff)
        Image.frombytes('RGB', (W, H), raw.data, 'raw', 'BGRX').save(path)

    def close(self):
        self.stop()
        self.xvfb.terminate()


def jitter(client, rng, drift):
    """Small hand movements that keep returning to where the player aims (pixels of mouse motion)."""
    dx, dy = rng.gauss(-0.3 * drift[0], 2.0), rng.gauss(-0.3 * drift[1], 1.0)
    dx, dy = round(dx), round(dy)
    client.move_mouse(dx, dy)
    return (drift[0] + dx, drift[1] + dy)


def human_clicking(client, seconds, cps=8.0, spread_ms=30, wander=True):
    """Clicks at a human rhythm while the hand drifts a little, like someone fighting a target in front of them."""
    end = time.monotonic() + seconds
    rng = random.Random(7)
    drift = (0, 0)
    while time.monotonic() < end:
        client.click(rng.uniform(0.03, 0.07))
        if wander:
            drift = jitter(client, rng, drift)
        time.sleep(max(0.03, rng.gauss(1.0 / cps, spread_ms / 1000.0)))


def wander(client, seconds):
    """A cheater still moves the mouse; the aura overrides what is sent while it has a target."""
    end = time.monotonic() + seconds
    rng = random.Random(11)
    while time.monotonic() < end:
        client.move_mouse(rng.gauss(0, 6), rng.gauss(0, 3))
        time.sleep(0.05)


VIEW = {'trace': None}  # path of the player's trace file; the "eyes" of the human fighter


def current_view():
    """Latest yaw/pitch the server received from the player (the trace file), or None."""
    path = VIEW['trace']
    if path is None or not path.exists():
        return None
    with open(path, 'rb') as f:
        f.seek(0, 2)
        f.seek(max(0, f.tell() - 4096))
        for line in reversed(f.read().decode(errors='replace').splitlines()):
            parts = line.split('\t')
            if len(parts) > 4 and parts[2] == 'rot':
                return float(parts[3]), float(parts[4])
    return None


def human_cooldown(client, seconds, swing=True, interval=0.68, spread=0.09, aim_offset=0.0, aim=True):
    """A 1.9+ sword fighter: sees where he looks and where the target is, corrects with the real mouse (partial
    corrections, noise, whole mouse steps, ~100 ms reaction), and swings when the cooldown is about full."""
    import math
    end = time.monotonic() + seconds
    rng = random.Random(5)
    next_hit = time.monotonic() + 0.7
    history = []
    last_aim = 0.0
    while time.monotonic() < end:
        view = current_view() if time.monotonic() - last_aim >= 0.05 else None  # one correction per tick
        if view is not None:
            last_aim = time.monotonic()
        now = time.monotonic()
        history.append((now, TARGET_POS['x'], TARGET_POS['z']))
        # A person sees the target only after the server sent it (next tick, half the round trip) and needs a
        # visual-motor reaction on top: aim at where the target was that long ago.
        seen_at = now - (50 + NETWORK['rtt_ms'] / 2 + rng.gauss(*HUMAN_REACTION_MS)) / 1000.0
        tx, tz = history[0][1], history[0][2]
        for t, hx, hz in history:
            if t <= seen_at:
                tx, tz = hx, hz
        while len(history) > 200:
            history.pop(0)
        if view is not None and aim:
            yaw, pitch = view
            goal_yaw = math.degrees(math.atan2(tz - 0.5, tx - 0.5)) - 90 + aim_offset
            dist = math.hypot(tx - 0.5, tz - 0.5)
            goal_pitch = math.degrees(math.atan2(1.62 - 0.9, dist))  # eyes to the middle of the target
            ey = ((goal_yaw - yaw + 180) % 360) - 180
            ep = goal_pitch - pitch
            dx = round((0.35 * ey + rng.gauss(0, 0.4)) / DEG_PER_PIXEL)
            dy = round((0.35 * ep + rng.gauss(0, 0.3)) / DEG_PER_PIXEL)
            if dx or dy:
                client.move_mouse(dx, dy)
        if swing and time.monotonic() >= next_hit:
            client.click(rng.uniform(0.03, 0.07))
            next_hit = time.monotonic() + max(0.03, rng.gauss(interval, spread))
        time.sleep(0.05 if interval >= 0.2 else 0.01)


def manual_totem(client, server, seconds):
    """Pops a totem every 5 s and refills it like a person: the F key ~350 ms after the pop, then picks the next
    totem slot."""
    rng = random.Random(3)
    end = time.monotonic() + seconds
    slot = 1
    while time.monotonic() + 5 < end:
        time.sleep(2.5)
        server.cmd(f'damage {PLAYER} 1000 minecraft:generic')
        time.sleep(max(0.22, rng.gauss(0.35, 0.06)))
        client.tap('f')
        time.sleep(0.4)
        slot = min(9, slot + 1)
        client.tap(str(slot))
        time.sleep(2.0)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--paper', type=Path, required=True)
    p.add_argument('--java', required=True, help='server java')
    p.add_argument('--client-java', required=True)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--portablemc', type=Path, required=True)
    p.add_argument('--mc', type=Path, required=True, help='portablemc main dir with the cheat mod in mods/')
    p.add_argument('--client', default='fabric:1.21.11')
    p.add_argument('--cheat', choices=['meteor', 'liquidbounce'], default='meteor')
    p.add_argument('--client-heap', default='900m')
    p.add_argument('--plugin', type=Path, action='append', default=[], help='extra server plugin (e.g. ViaVersion)')
    p.add_argument('--rtt', type=int, default=0, help='simulated round-trip time for the game client (ms)')
    p.add_argument('--jitter', type=int, default=0, help='random variation of the round trip (ms, standard deviation)')
    p.add_argument('--spike-every', type=float, default=0.0, help='a 1 s lag spike every N seconds (0 = none)')
    p.add_argument('--spike', type=int, default=0, help='extra delay during a lag spike (ms)')
    p.add_argument('--protocol', default='1.21.11', help='target bot protocol version')
    p.add_argument('--work', type=Path, required=True)
    p.add_argument('--scenario', action='append')
    p.add_argument('--accept-eula', action='store_true')
    args = p.parse_args()
    if not args.accept_eula:
        p.error('accept the Minecraft EULA first')
    args.work.mkdir(parents=True, exist_ok=True)
    VIEW['trace'] = args.work / 'trace' / f'{PLAYER}.tsv'

    server = Server(args.work, args.paper.resolve(), args.java, [args.jar] + args.plugin)
    lb = args.cheat == 'liquidbounce'
    # module names in LiquidBounce (it toggles with ".toggle <Name>", no on/off argument)
    LB_NAMES = {'kill-aura': 'KillAura', 'auto-clicker': 'AutoClicker', 'auto-totem': 'Offhand', 'criticals': 'Criticals',
                'hitboxes': 'Hitbox', 'velocity': 'Velocity', 'flight': 'Fly', 'speed': 'Speed', 'no-fall': 'NoFall',
                'step': 'Step', 'spider': 'Spider', 'timer': 'Timer', 'crystal-aura': 'CrystalAura'}

    def toggle(module, on):
        if lb:
            client.chat(f'.toggle {LB_NAMES.get(module, module)}')
        else:
            client.chat(f'.toggle {module} {"on" if on else "off"}')
    server.pump('Done (', 240)
    server.cmd('fill -12 -63 -12 12 -61 12 minecraft:obsidian')
    target = subprocess.Popen(['node', str(ROOT / 'client/target.cjs'), str(server.port), TARGET, args.protocol],
                              stdin=subprocess.PIPE, stdout=open(args.work / 'target.log', 'w'),
                              stderr=subprocess.STDOUT, text=True)

    mover = {'on': False, 'home': (0.5, 2.5), 'retreat': False}

    def move_target():
        x, step, idle = 0.5, 0.15, 0
        z, zstep = 2.5, 0.4
        while True:
            time.sleep(0.25)
            if not mover['on']:
                idle += 1
                if idle % 4 == 0:
                    hx, hz = mover['home']
                    server.cmd(f'tp {TARGET} {hx} -60 {hz}')
                continue
            idle = 0
            if mover['on'] and mover['retreat']:
                # backs off and comes back (1.6 blocks/s), the case backtracking cheats exploit
                z += zstep
                if z > 3.6 or z < 1.8:
                    zstep = -zstep
                    z = min(3.6, max(1.8, z))
                server.cmd(f'tp {TARGET} 0.5 -60 {z:.2f}')
                TARGET_POS['x'], TARGET_POS['z'] = 0.5, z
                continue
            if mover['on']:
                x += step
                if x > 1.6 or x < -0.6:
                    step = -step
                server.cmd(f'tp {TARGET} {x:.2f} -60 {TARGET_POS["z"]}')
                TARGET_POS['x'] = x

    threading.Thread(target=move_target, daemon=True).start()

    def strafe(on):
        if target.poll() is not None:
            raise RuntimeError('target bot exited; see target.log')
        mover['on'] = on
    server.pump(TARGET + ' joined the game', 60)
    client_port = server.port
    NETWORK['rtt_ms'] = args.rtt
    if args.rtt or args.jitter or args.spike:
        client_port = LatencyProxy(server.port, args.rtt, args.jitter, args.spike_every, args.spike).port
    client = Client(args, client_port)
    results = {}

    def ensure_client():
        if client.alive() and not getattr(client, 'kicked', False):
            return
        client.stop()
        client.kicked = False
        client.launch()
        server.pump(PLAYER + ' joined the game', 300)
        server.pump(seconds=10)  # world load
        client.center()  # once, so keyboard focus is on the game window
        server.cmd(f'op {PLAYER}')  # cheat clients need no op; op only makes the console commands below quieter
        server.cmd(f'deop {PLAYER}')

    def place(away=False):
        mover['home'] = (0.5, 9.5) if away else (0.5, TARGET_POS['z'])
        server.cmd(f'tp {TARGET} {mover["home"][0]} -60 {mover["home"][1]} 180 0')
        TARGET_POS['x'] = 0.5
        if away:  # movement tests: open floor ahead (-Z), target out of the way
            server.cmd(f'tp {PLAYER} 0.5 -60 0.5 180 0')
        else:
            server.cmd(f'tp {PLAYER} 0.5 -60 0.5 facing entity {TARGET} eyes')
        server.pump(seconds=1.5)

    def scenario(name, seconds, setup=(), module=None, during=None, schedule=(), moving=False, away=False,
                 settings=(), target_z=2.5, retreat=False):
        TARGET_POS['z'] = target_z
        mover['retreat'] = retreat
        ensure_client()
        server.cmd(f'clear {PLAYER}')
        server.cmd(f'effect clear {PLAYER}')
        server.cmd(f'gamemode survival {PLAYER}')
        server.cmd('fill -4 -60 -8 4 -50 -2 minecraft:air')  # walls left by earlier movement tests
        if not lb:
            client.chat('.toggle all off')  # the cheat client remembers active modules between runs
        client.tap('1')  # first hotbar slot: setup items land there (earlier scenarios may have switched slots)
        # the target bot does not simulate knockback; without this the anticheat (rightly) kicks it for that
        server.cmd(f'attribute {TARGET} minecraft:knockback_resistance base set 1')
        server.cmd(f'effect give {TARGET} minecraft:regeneration infinite 4 true')
        server.cmd(f'effect give {TARGET} minecraft:instant_health 1 10 true')
        place(away)
        for c in setup:
            server.cmd(c.replace('@me', PLAYER))
        server.pump(seconds=1.0)
        strafe(moving)
        start_ms = int(time.time() * 1000)
        start_ns = time.monotonic_ns()  # same clock as the server's System.nanoTime on Linux (trace files)
        kicked_before = sum(1 for l in server.log if f'{PLAYER} lost connection' in l)
        human_cooldown(client, 6, swing=False)  # looks around with the mouse first, like any player
        if module:
            for setting in ([] if lb else settings):  # module settings a cheater would pick
                client.chat(f'.settings {module} {setting}')
            toggle(module, True)
        place(away)
        t0 = time.monotonic()
        plan = sorted(schedule)
        if during:
            during(client, server, seconds)
        mid_shot = False
        while time.monotonic() - t0 < seconds:
            if not mid_shot and time.monotonic() - t0 >= seconds / 2:
                mid_shot = True
                try:
                    client.screenshot(args.work / f'{name}-mid.png')
                except Exception:
                    pass
            while plan and plan[0][0] <= time.monotonic() - t0:
                server.cmd(plan.pop(0)[1].replace('@me', PLAYER))
            server.pump(seconds=0.2)
        if module and client.alive():
            toggle(module, False)
        strafe(False)
        server.pump(seconds=3)
        kicks = [l.strip()[:160] for l in server.log if f'{PLAYER} lost connection' in l][kicked_before:]
        client.kicked = bool(kicks)
        flagged = server.flags(start_ms)
        try:
            client.screenshot(args.work / f'{name}.png')
        except Exception:
            pass
        results[name] = {'window_ns': [start_ns, time.monotonic_ns()], 'flagged': {k: len(v) for k, v in flagged.items()},
                         'examples': {k: v[0] for k, v in flagged.items()}, 'kicked': kicks}
        print(f'{name}: flagged={sorted(flagged)} kicked={kicks}', flush=True)
        (args.work / 'realclient-results.json').write_text(json.dumps(results, indent=2) + '\n')

    S = {
        # baselines with real input, no cheat module
        'idle': dict(seconds=20),
        'legit-clicking': dict(seconds=30, during=lambda c, s, t: human_cooldown(c, t, interval=0.125, spread=0.03)),
        'legit-fastclick': dict(seconds=30, during=lambda c, s, t: human_cooldown(c, t, interval=0.077, spread=0.025)),
        'legit-walk': dict(seconds=20, away=True, during=lambda c, s, t: walk(c, t)),
        # combat modules
        'kill-aura': dict(seconds=40, module='kill-aura', moving=True, setup=['give @me minecraft:diamond_sword'],
                          during=lambda c, s, t: wander(c, t)),
        'kill-aura-fast': dict(seconds=40, module='kill-aura', moving=True, setup=['give @me minecraft:diamond_sword'],
                               settings=['custom-delay true', 'hit-delay 4'], during=lambda c, s, t: wander(c, t)),
        'kill-aura-norotate': dict(seconds=40, module='kill-aura', moving=True, setup=['give @me minecraft:diamond_sword'],
                                   settings=['rotate none'], during=lambda c, s, t: human_cooldown(c, t, swing=False)),
        'legit-sword': dict(seconds=40, moving=True, setup=['give @me minecraft:diamond_sword'],
                            during=lambda c, s, t: human_cooldown(c, t)),
        'auto-clicker': dict(seconds=30, module='auto-clicker', during=lambda c, s, t: human_cooldown(c, t, swing=False)),
        'criticals': dict(seconds=25, module='criticals', during=lambda c, s, t: human_cooldown(c, t, interval=0.6, spread=0.1)),
        # aims 11 degrees beside the target: a miss for a vanilla hitbox, a hit for an expanded one
        'hitboxes': dict(seconds=25, module='hitboxes', during=lambda c, s, t: human_cooldown(c, t, interval=0.6, spread=0.1, aim_offset=11)),
        'legit-offaim': dict(seconds=25, during=lambda c, s, t: human_cooldown(c, t, interval=0.6, spread=0.1, aim_offset=11)),
        # a person: holds a totem, sees the pop, presses F about 350 ms later (the bench knows when it pops)
        'legit-totem': dict(seconds=25, setup=['give @me minecraft:totem_of_undying 6',
                                               'item replace entity @me weapon.offhand with minecraft:totem_of_undying'],
                            during=lambda c, s, t: manual_totem(c, s, t)),
        # fast butterfly clicking (~17 CPS) with a person's timing spread
        'legit-butterfly': dict(seconds=25, during=lambda c, s, t: human_cooldown(c, t, interval=0.058, spread=0.022)),
        'auto-totem': dict(seconds=25, module='auto-totem',
                           setup=['give @me minecraft:totem_of_undying 6'],
                           schedule=[(3 + 4 * i, 'damage @me 1000 minecraft:generic') for i in range(4)]),
        'crystal-aura': dict(seconds=30, module='crystal-aura', settings=['pause-on-lag false', 'max-damage 36', 'anti-suicide false'],
                             setup=['give @me minecraft:end_crystal 64', 'give @me minecraft:obsidian 64',
                                    'effect give @me minecraft:resistance infinite 4 true',
                                    'item replace entity @me armor.head with minecraft:netherite_helmet[enchantments={"minecraft:blast_protection":4}]',
                                    'item replace entity @me armor.chest with minecraft:netherite_chestplate[enchantments={"minecraft:blast_protection":4}]',
                                    'item replace entity @me armor.legs with minecraft:netherite_leggings[enchantments={"minecraft:blast_protection":4}]',
                                    'item replace entity @me armor.feet with minecraft:netherite_boots[enchantments={"minecraft:blast_protection":4}]',
                                    ]),
        'velocity': dict(seconds=20, module='velocity',
                         schedule=[(2 + 1.5 * i, f'damage @me 1 minecraft:player_attack by {TARGET}') for i in range(10)]),
        # movement modules (the upstream engine's domain)
        'flight': dict(seconds=20, module='flight', away=True, during=lambda c, s, t: walk(c, t, jump=True)),
        'speed': dict(seconds=20, module='speed', away=True, during=lambda c, s, t: walk(c, t)),
        'no-fall': dict(seconds=20, module='no-fall', schedule=[(2 + 5 * i, 'tp @me 0.5 -40 0.5') for i in range(3)]),
        'step': dict(seconds=20, module='step', away=True, setup=['fill -1 -60 -4 2 -60 -4 minecraft:stone'], during=lambda c, s, t: walk(c, t)),
        'spider': dict(seconds=20, module='spider', away=True, setup=['fill -1 -60 -3 2 -54 -3 minecraft:stone'], during=lambda c, s, t: walk(c, t)),
        'timer-1.5': dict(seconds=20, module='timer', away=True, settings=['multiplier 1.5'], during=lambda c, s, t: walk(c, t)),
        'timer': dict(seconds=20, module='timer', away=True, settings=['multiplier 3'], during=lambda c, s, t: walk(c, t)),
    }

    def holding_attack(c, seconds):
        """Holds the attack button (clickers that only run while it is held) and aims like a person."""
        c.button(1, True)
        try:
            human_cooldown(c, seconds, swing=False)
        finally:
            c.button(1, False)

    S.update({
        # totem refills while running around, as in a real fight
        'auto-totem-moving': dict(seconds=25, module='auto-totem', away=True, setup=['give @me minecraft:totem_of_undying 6'],
                                  schedule=[(3 + 4 * i, 'damage @me 1000 minecraft:generic') for i in range(4)],
                                  during=lambda c, s, t: walk(c, t)),
        'legit-totem-moving': dict(seconds=25, away=True, setup=['give @me minecraft:totem_of_undying 6',
                                   'item replace entity @me weapon.offhand with minecraft:totem_of_undying'],
                                   during=lambda c, s, t: walking_manual_totem(c, s, t)),
        'lb-auto-clicker': dict(seconds=30, module='AutoClicker', during=lambda c, s, t: holding_attack(c, t)),
        # the cheat aims (camera), the player only clicks like a person
        'aimbot': dict(seconds=40, module='Aimbot', moving=True, setup=['give @me minecraft:diamond_sword'],
                       during=lambda c, s, t: human_cooldown(c, t, aim=False)),
        'aimbot-retreat': dict(seconds=45, module='Aimbot', moving=True, retreat=True, setup=['give @me minecraft:diamond_sword'],
                               during=lambda c, s, t: human_cooldown(c, t, aim=False)),
        # the player aims like a person, the cheat clicks when the crosshair is on the target
        'triggerbot': dict(seconds=40, module='TriggerBot', moving=True, setup=['give @me minecraft:diamond_sword'],
                           during=lambda c, s, t: human_cooldown(c, t, swing=False)),
        # target 3.6 blocks away: out of vanilla reach, inside the cheat's
        'reach': dict(seconds=30, module='Reach', target_z=4.1, setup=['give @me minecraft:diamond_sword'],
                      during=lambda c, s, t: human_cooldown(c, t)),
        'legit-far': dict(seconds=30, target_z=4.1, setup=['give @me minecraft:diamond_sword'],
                          during=lambda c, s, t: human_cooldown(c, t)),
        'backtrack-retreat': dict(seconds=60, module='Backtrack', moving=True, retreat=True,
                                  setup=['give @me minecraft:diamond_sword'], during=lambda c, s, t: human_cooldown(c, t)),
        'legit-retreat': dict(seconds=60, moving=True, retreat=True, setup=['give @me minecraft:diamond_sword'],
                              during=lambda c, s, t: human_cooldown(c, t)),
        'backtrack': dict(seconds=40, module='Backtrack', moving=True, setup=['give @me minecraft:diamond_sword'],
                          during=lambda c, s, t: human_cooldown(c, t)),
        'tickbase': dict(seconds=40, module='TickBase', moving=True, setup=['give @me minecraft:diamond_sword'],
                         during=lambda c, s, t: human_cooldown(c, t)),
        'keepsprint': dict(seconds=30, module='KeepSprint', setup=['give @me minecraft:diamond_sword'],
                           during=lambda c, s, t: sprint_fight(c, t)),
        'legit-sprintfight': dict(seconds=30, setup=['give @me minecraft:diamond_sword'],
                                  during=lambda c, s, t: sprint_fight(c, t)),
    })

    def sprint_fight(c, seconds):
        """Sprints at the target (W + Ctrl) while hitting it, like a person rushing in."""
        c.hold('Control_L', True)
        c.hold('w', True)
        try:
            human_cooldown(c, seconds)
        finally:
            c.hold('w', False)
            c.hold('Control_L', False)

    def walking_manual_totem(c, srv, seconds):
        c.hold('w', True)
        try:
            manual_totem(c, srv, seconds)
        finally:
            c.hold('w', False)

    def walk(c, seconds, jump=False):
        c.hold('w', True)
        end = time.monotonic() + seconds
        try:
            while time.monotonic() < end:
                if jump:
                    c.tap('space', 0.3)
                time.sleep(0.5)
        finally:
            c.hold('w', False)

    try:
        for name in args.scenario or list(S):
            scenario(name, **S[name])
    finally:
        client.close()
        target.kill()
        server.cmd('stop')
        try:
            server.process.wait(60)
        except subprocess.TimeoutExpired:
            server.process.kill()
        while not server.lines.empty():
            server.log.append(server.lines.get_nowait())
        (args.work / 'server-output.log').write_text(''.join(server.log))
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
