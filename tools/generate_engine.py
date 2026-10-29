#!/usr/bin/env python3
"""Compile a reproducible, source-based Sentinel port of pinned upstream modules.

The git submodules preserve the original source and legal notices. This generator
only writes target/; deliberate behavioral changes below fail if their anchors
change, so an upstream upgrade requires review rather than silent patch drift.
"""
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "plugin/target/generated-sources/engine"
RES = ROOT / "plugin/target/generated-resources/engine"
TESTS = ROOT / "plugin/target/generated-test-sources/engine"
LIBS = {
    "io.github.retrooper.packetevents": "io.github.retrooper.packetevents",
    "com.github.retrooper.packetevents": "com.github.retrooper.packetevents",
    "net.kyori": "kyori", "org.slf4j": "slf4j",
    "github.scarsz.configuralize": "configuralize",
    "com.github.puregero": "com.github.puregero", "alexh": "maps",
    "it.unimi.dsi.fastutil": "fastutil", "org.yaml.snakeyaml": "snakeyaml",
    "org.json": "json", "org.intellij": "intellij", "org.jetbrains": "jetbrains",
    "org.incendo": "incendo", "io.leangen.geantyref": "geantyref",
    "com.zaxxer": "zaxxer",
}

def brand(text):
    text = text.replace("ac/grim/grimac", "uz/sentinel/engine")
    text = text.replace("ac.grim.grimac", "uz.sentinel.engine")
    text = re.sub(r"\bBasicGrimPlugin\b", "BasicEnginePlugin", text)
    text = re.sub(r"\bGrimPlugin\b", "EnginePlugin", text)
    for old, new in (("GrimAC", "SentinelAC"), ("grimac", "sentinelac"),
                     ("GRIM", "SENTINEL"), ("Grim", "Sentinel"), ("grim", "sentinel")):
        text = text.replace(old, new)
    text = text.replace('commandBuilder("sentinel", "sentinelac")',
                        'commandBuilder("sac", "sentinel", "sentinelac")')
    text = text.replace("/sentinel ", "/sac ")
    text = text.replace('commandBuilder("gl")', 'commandBuilder("saclog")')
    text = re.sub(r"https?://(?:www\.)?(?:api\.|paste\.)?sentinel\.ac[^\s\"<>)]*",
                  "https://github.com/dilmuratovv/SentinelAC", text)
    for old, new in sorted(LIBS.items(), key=lambda pair: -len(pair[0])):
        text = re.sub(r"(?<![\w.])" + re.escape(old) + r"(?=\.|[;\"'/])",
                      "uz.sentinel.engine.libs." + new, text)
    return text

def replace_once(text, before, after):
    if text.count(before) != 1:
        raise ValueError(f"Patch anchor changed ({text.count(before)} matches): {before[:100]}")
    return text.replace(before, after, 1)

def java_brand(source):
    links = []
    def protect(match):
        links.append(match.group(0))
        return f"SOURCELINK{len(links)-1}END"
    lines = []
    for line in source.splitlines(keepends=True):
        if line.lstrip().startswith(("//", "*")):
            line = re.sub(r'https?://[^\s<>"\]]+', protect, line)
        lines.append(line)
    result = brand("".join(lines))
    for index, link in enumerate(links):
        result = result.replace(f"SOURCELINK{index}END", link)
    return result

def customize(name, text):
    if name == "SentinelACBukkitLoaderPlugin.java":
        text = replace_once(text, "public final class SentinelACBukkitLoaderPlugin",
                            "public class SentinelACBukkitLoaderPlugin")
        text = replace_once(text, "                new BukkitBStats(),\n", "")
        text = replace_once(text,
            'LogUtil.warn("CRITICAL: Failed to initialize Command Framework. " +\n'
            '                    "Sentinel will continue to run with no commands.", t);\n'
            '            return () -> {};',
            'throw new IllegalStateException("Failed to initialize SentinelAC commands", t);')
    elif name == "InitManager.java":
        text = replace_once(text, "                .add(new UpdateChecker())\n", "")
        text = replace_once(text, "                .add(new TerminatePacketEvents())", "                .add(SentinelAPI.INSTANCE.getDiscordManager())\n                .add(new TerminatePacketEvents())")
        for phase in ("load", "start"):
            text = replace_once(text, f'LogUtil.error("Failed to {phase} " + initable.getClass().getSimpleName(), e);',
                                f'throw new IllegalStateException("Failed to {phase} " + initable.getClass().getSimpleName(), e);')
    elif name == "CommandRegister.java":
        text = replace_once(text, 'LogUtil.error("Failed to register commands! Sentinel will run without command support.", t);',
                            'throw new IllegalStateException("Failed to register SentinelAC commands", t);')
    elif name == "CloudCommandService.java":
        text = replace_once(text, "        commandsRegistered = true;",
                            "        uz.sentinel.ac.commands.SentinelManagementCommand.register(commandManager);\n"
                            "        commandsRegistered = true;")
        text = text.replace('tokens[0].equals("sentinel") || tokens[0].equals("sentinelac")',
                            'tokens[0].equals("sac") || tokens[0].equals("sentinel") || tokens[0].equals("sentinelac")')
    elif name == "SentinelReload.java":
        text = replace_once(text, "SentinelAPI.INSTANCE.getExternalAPI().reloadAsync()",
                            "((uz.sentinel.ac.SentinelPlugin) uz.sentinel.engine.platform.bukkit.SentinelACBukkitLoaderPlugin.LOADER).reloadSettings()")
    elif name == "CommonSentinelArguments.java":
        text = re.sub(r"^.*SystemArgument<String> (?:API_URL|PASTE_URL) = .*\n", "", text, flags=re.M)
    elif name == "DiscordManager.java":
        text = replace_once(text, "implements StartableInitable, ReloadableInitable", "implements StartableInitable, ReloadableInitable, uz.sentinel.engine.manager.init.stop.StoppableInitable")
        text = replace_once(text,
            "private static final ConcurrentLinkedDeque<Pair<HttpRequest, CompletableFuture<Boolean>>> requests = new ConcurrentLinkedDeque<>();",
            "private static final uz.sentinel.ac.diagnostics.BoundedWebhookDispatcher requests = new uz.sentinel.ac.diagnostics.BoundedWebhookDispatcher(client, 256);")
        text = text.replace("    private static final AtomicBoolean sending = new AtomicBoolean();\n", "")
        text = text.replace("    private static long rateLimitedUntil;\n", "")
        text = replace_once(text, "CompletableFuture<Boolean> future = new CompletableFuture<>();\n\n        requests.add(new Pair<>(request, future));", "CompletableFuture<Boolean> future = requests.submit(request);")
        text = text[:text.index("    private static void tick() {")] + "    private static void tick() { requests.tick(); }\n    @Override public void stop() { requests.close(); }\n}\n"
        text = text.replace('validatedConfigURL("embed-footer-url", "https://github.com/dilmuratovv/SentinelAC")', 'validatedConfigURL("embed-footer-url", null)')
        text = text.replace('getStringElse("embed-image-url", defaultURL)', 'getStringElse(configPath, defaultURL)')
    elif name == "SimpleCollisionBox.java":
        text = replace_once(text,
            "Vector3dm invDir = new Vector3dm(1f / ray.direction().getX(), 1f / ray.direction().getY(), 1f / ray.direction().getZ());",
            "// Scalars avoid a temporary mutable vector for every ray without changing arithmetic.\n"
            "        double invX = 1f / ray.direction().getX();\n"
            "        double invY = 1f / ray.direction().getY();\n"
            "        double invZ = 1f / ray.direction().getZ();")
        for axis in "XYZ":
            text = text.replace(f"invDir.get{axis}()", f"inv{axis}")
    override = ROOT / "tools/engine-overrides" / name
    return brand(override.read_text()) if override.exists() else text

def main():
    for project, revision in {"engine": "f31ba923100d9a991939d7ea24fde541d6fa2929",
                              "api": "e9ae70ad6e13af0f3dab02ac849608443fb69f8a"}.items():
        path = ROOT / "upstream" / project
        actual = subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()
        if actual != revision:
            raise SystemExit(f"Unexpected {project} source revision: {actual}")
    for folder in (OUT, RES, TESTS):
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir(parents=True)
    source_roots = [
        "upstream/engine/common/src/main/java", "upstream/engine/bukkit/src/main/java",
        "upstream/api/src/main/java", "upstream/api/grim-internal/src/main/java",
        "upstream/api/grim-bukkit-internal/src/main/java",
    ]
    count = 0
    for root in source_roots:
        folder = ROOT / root
        if not folder.exists():
            raise SystemExit("Missing pinned source submodule: run git submodule update --init --recursive")
        for path in sorted(folder.rglob("*.java")):
            relative = brand(path.relative_to(folder).as_posix().replace("/", ".")).replace(".", "/")
            # Restore the Java extension after package path conversion.
            relative = relative.removesuffix("/java") + ".java"
            target = OUT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists():
                raise ValueError(f"Duplicate generated class {target}")
            target.write_text(customize(target.name, java_brand(path.read_text())), encoding="utf-8")
            count += 1
    # Keep the pinned engine's concurrency, ownership and verbose-schema tests.
    folder = ROOT / "upstream/engine/common/src/test/java"
    for path in sorted(folder.rglob("*.java")):
        relative = brand(path.relative_to(folder).as_posix().replace("/", ".")).replace(".", "/")
        target = TESTS / (relative.removesuffix("/java") + ".java")
        target.parent.mkdir(parents=True, exist_ok=True)
        source = brand(path.read_text()).replace('Path.of("src/main/java/uz/sentinel/engine/checks")',
                                               'Path.of("target/generated-sources/engine/uz/sentinel/engine/checks")')
        target.write_text(source)
    for root in ("upstream/engine/common/src/main/resources", "upstream/api/src/main/resources",
                 "upstream/api/grim-internal/src/main/resources", "upstream/api/grim-bukkit-internal/src/main/resources"):
        folder = ROOT / root
        if not folder.exists():
            continue
        for path in sorted(folder.rglob("*")):
            if path.is_file():
                target = RES / brand(path.relative_to(folder).as_posix())
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(brand(path.read_text()), encoding="utf-8")
    # One config contains both engine defaults and Sentinel policy settings.
    config = (RES / "config/en.yml").read_text()
    config = config[config.index("alerts:"):].replace("check-for-updates: true", "check-for-updates: false")
    custom = (ROOT / "plugin/src/main/resources/sentinel-defaults.yml").read_text()
    merged = "# SentinelAC configuration. Upstream attribution: META-INF/NOTICE.txt.\n" + config + "\n" + custom
    (RES / "config/en.yml").write_text(merged)
    (RES / "config.yml").write_text(merged)
    messages = RES / "messages/en.yml"
    text = messages.read_text().replace("Uploads a debug log", "Saves a local debug log")
    text = text.replace('    - "/sac help', '    - "/sac info &f- &7Build and policy status"\n'
                        '    - "/sac channels <player> &f- &7Advertised plugin channels"\n'
                        '    - "/sac recent <player> &f- &7Recent local findings"\n'
                        '    - "/sac help')
    messages.write_text(text)
    (RES / "sentinelac.properties").write_text(
        "build.version=2.5.0-paper26.2\ngit.commit=unknown\ngit.branch=standalone\n"
        "git.org=dilmuratovv\ngit.repo=SentinelAC\nbuild.shade_pe=true\n"
        "build.relocate=true\nbuild.release=false\ntimestamp=2026-10-29T00:00:00Z\n")
    legal = RES / "META-INF"
    legal.mkdir(exist_ok=True)
    (legal / "LICENSE.txt").write_text((ROOT / "LICENSE").read_text())
    notices = ["SentinelAC is a modified source fork of GrimAC and GrimAPI, licensed under GPLv3.\n",
               "GrimAC source: https://github.com/GrimAnticheat/Grim commit f31ba923100d9a991939d7ea24fde541d6fa2929\n",
               "GrimAPI source: https://github.com/GrimAnticheat/GrimAPI commit e9ae70ad6e13af0f3dab02ac849608443fb69f8a\n",
               "Thanks to DefineOutside and all upstream contributors.\n",
               "Modified source/build instructions: https://github.com/dilmuratovv/SentinelAC\n",
               "Changes include source namespaces, commands, integration, bounded diagnostics/webhooks, and scalar ray arithmetic.\n\n",
               (ROOT / "upstream/engine/common/src/main/resources/config/en.yml").read_text().split("alerts:")[0]]
    (legal / "NOTICE.txt").write_text("".join(notices))
    for project in ("engine", "api"):
        for path in (ROOT / "upstream" / project).glob("*LICENSE*"):
            shutil.copyfile(path, legal / f"upstream-{project}-{path.name}.txt")
    print(f"Generated {count} engine/API source files from pinned submodules")

if __name__ == "__main__":
    main()
