#!/usr/bin/env python3
"""Checks that the JAR still links against a Paper version it was not compiled for.

The JAR is compiled once against the oldest supported API (Paper 1.21). It also bundles prebuilt libraries
(PacketEvents) whose version-specific code paths reference API that the oldest version does not have, so some
references are unresolvable even there. A reference counts as broken only when it resolves against the
baseline API but not against the target API:
    python3 tools/api_compat.py --jar plugin/target/SentinelAC-*-paper.jar \\
        --baseline-classpath <1.21 API + dependencies> --classpath <target API + dependencies>
Needs `javap` and a `java` new enough to load the classpath (JDK 25 for Paper 26.x).
"""
import argparse, os, re, subprocess, sys, tempfile, zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent
# Only API owned by the platform is probed; the JDK and our own packages are not.
OWNERS = re.compile(r'^(org/bukkit|io/papermc|net/kyori|com/destroystokyo|net/md_5|org/spigotmc|com/mojang/brigadier|com/google/common)')
MEMBER = re.compile(r'= (Methodref|InterfaceMethodref|Fieldref)\s+#\d+\.#\d+\s+// +(\S+?)\.(\S+?):(\S+)')
CLASS = re.compile(r'= Class\s+#\d+\s+// +(\S+)')

def references(jar: Path):
    classes, members = set(), set()
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(jar) as z:
        files = []
        for name in z.namelist():
            # Everything bundled is probed: the relocated engine libraries also call the Bukkit API.
            if name.endswith('.class') and not name.startswith('META-INF/versions/'):
                files.append(str(Path(z.extract(name, tmp))))
        def scan(batch):
            return subprocess.run(['javap', '-v', '-p', *batch], capture_output=True, text=True, check=True).stdout
        batches = [files[i:i + 60] for i in range(0, len(files), 60)]
        with ThreadPoolExecutor(max_workers=os.cpu_count() or 2) as pool:
            for out in pool.map(scan, batches):
                for kind, owner, name, desc in MEMBER.findall(out):
                    if OWNERS.match(owner):
                        members.add(('F' if kind == 'Fieldref' else 'M', owner, name.strip('"'), desc))
                classes.update(c for c in CLASS.findall(out) if OWNERS.match(c))
    return classes, members

def probe(java, refs_file, classpath):
    """Unresolvable references of this classpath, as a set of report lines."""
    out = subprocess.run([java, str(ROOT / 'ApiCompat.java'), str(refs_file), classpath], capture_output=True, text=True)
    if 'missing=' not in out.stdout:
        sys.exit('API probe failed:\n' + out.stdout + out.stderr)
    return {line.strip() for line in out.stdout.splitlines() if line.startswith(('MISSING', 'UNRESOLVED'))}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--jar', type=Path, required=True)
    ap.add_argument('--classpath', help='target API jar plus its dependencies, ":"-separated')
    ap.add_argument('--baseline-classpath', help='API the JAR was compiled against (oldest supported), plus dependencies')
    ap.add_argument('--extract-only', action='store_true', help='only write --refs-cache (done once, then reused by every target check)')
    ap.add_argument('--java', default='java')
    ap.add_argument('--refs-cache', type=Path, help='reuse (or create) the extracted references; saves minutes when checking many versions')
    args = ap.parse_args()
    if args.refs_cache and args.refs_cache.is_file():
        refs_file = args.refs_cache
    else:
        classes, members = references(args.jar)
        if not classes:
            sys.exit('no platform API references found; is this the right JAR?')
        refs_file = args.refs_cache or Path(tempfile.NamedTemporaryFile(suffix='.tsv', delete=False).name)
        with open(refs_file, 'w') as f:
            for c in sorted(classes):
                f.write(f'C\t{c}\n')
            for kind, owner, name, desc in sorted(members):
                f.write(f'{kind}\t{owner}\t{name}\t{desc}\n')
        print(f'{args.jar.name}: {len(classes)} classes, {len(members)} members', flush=True)
    if args.extract_only:
        return
    if not (args.classpath and args.baseline_classpath):
        ap.error('--classpath and --baseline-classpath are required unless --extract-only')
    baseline = probe(args.java, refs_file, args.baseline_classpath)
    target = probe(args.java, refs_file, args.classpath)
    broken = sorted(target - baseline)
    print(f'baseline API leaves {len(baseline)} references unresolved (bundled version-guarded code); target API leaves {len(target)}')
    for line in broken:
        print('BROKEN ' + line)
    print(f'newly broken={len(broken)}')
    sys.exit(1 if broken else 0)

if __name__ == '__main__':
    main()
