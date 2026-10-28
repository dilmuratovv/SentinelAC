<div align="center">

# 🛡️ SentinelAC — Next-Gen Universal Anticheat Engine

**An enterprise-grade, 0-Lag, 1:1 server-side physics simulation anticheat engine for Minecraft 1.16.5 – 26.x.**  
*Engineered by **azicoo** for Paper, Purpur, and multi-threaded Folia servers.*

[![GitHub Release](https://img.shields.io/badge/Release-v2.3.74--SENTINEL-FF416C?style=for-the-badge&logo=github)](https://github.com/dilmuratovv/SentinelAC)
[![Java Version](https://img.shields.io/badge/Java-21%20%7C%2025%2B-blue?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![Minecraft Compatibility](https://img.shields.io/badge/Minecraft-1.16.5%20--%2026.x-10B981?style=for-the-badge&logo=minecraft)](https://papermc.io/)
[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Folia-orange?style=for-the-badge)](https://papermc.io/software/folia)
[![Author](https://img.shields.io/badge/Author-azicoo-9333EA?style=for-the-badge)](https://github.com/dilmuratovv/SentinelAC)
[![License](https://img.shields.io/badge/License-GPL--3.0-lightgrey?style=for-the-badge)](LICENSE)

<br/>

[✨ Features](#-key-features) •
[📥 Installation](#-quick-start--installation) •
[⚙️ Configuration](#-configuration--punishments) •
[🎮 Commands & Permissions](#-commands--permissions) •
[🧩 PlaceholderAPI](#-placeholderapi-placeholders) •
[🛠️ Build from Source](#-building-from-source)

</div>

---

## 📖 Overview

**SentinelAC** is a high-performance, next-generation Minecraft anticheat engineered to eliminate unfair advantages, client-side hacks, and network exploits with mathematical precision. 

Unlike traditional heuristic anticheats that suffer from rubberbanding and false positives, **SentinelAC combines a 1:1 deterministic physics simulation engine with custom high-frequency exploit mitigations**. Every movement, click, block interaction, and combat event is validated against server-side world geometry and latency compensation models.

Whether you run a high-traffic PvP Anarchy server, an SMP realm, or a competitive minigame network, SentinelAC provides impenetrable defense with **sub-millisecond overhead**.

---

## ✨ Key Features

### ⚡ 1. 1:1 Server-Side Physics Simulation
* **Mathematical Accuracy**: Simulates complete client movement tick-by-tick based on official Mojang physics.
* **0-Lag & Zero Rubberbanding**: Reconstructs player velocity, friction, jump curves, and collision boxes with sub-millimeter precision.
* **Flawless Movement Protection**: Detects **Fly, Speed, NoSlow, Jesus, HighJump, Step, Timer, NoFall, and GroundSpoof** instantly without false flagging laggy players.

### ⚔️ 2. Advanced Combat & Reach Module
* **PvP-Optimized Raytracing**: Computes the exact 3D distance between the attacker’s eye vector and the nearest surface point on the target player's `BoundingBox`.
* **Zero-Allocation Scalar Math**: High-speed, garbage-collection-free math that never causes TPS drops during massive team fights.
* **Smart Latency Buffer**: Dynamically scales reach thresholds according to real-time round-trip latency (`player.getPing()`).

### 💎 3. FastCrystal & Respawn Anchor Limiter
* **Separate Rate Counters**: Limits crystal placement to **12/s** and crystal destruction to **14/s**, perfectly allowing legitimate fast-paced crystal PvP while crushing automated macro autocrystal clients.
* **Dual-Hand Deduplication**: Prevents ghost-flagging when switching or holding crystals in the offhand.
* **Anti-Anchor Macro**: Enforces strict right-click cooldowns on `RESPAWN_ANCHOR` blocks.

### 🔄 4. 0-Tick AutoTotem & InvMove Blocker
* **Reaction-Time Enforcement**: Flags and cancels any totem swap occurring within **<90ms** of a totem pop or critical damage event (human reaction threshold).
* **GUI-less InvMove Protection**: Detects players swapping totems or armor while actively sprinting, jumping, or moving without opening their inventory.
* **Legitimate Play Freedom**: Legitimate offhand swaps ('F' key) during standard gameplay are never obstructed.

### 🍎 5. FastEat & NoHunger Protection
* **Item Consumption Validation**: Validates the true 32-tick eating duration for all foods, golden apples, potions, milk buckets, and honey bottles (with fast kelp compensation).
* **Server-Side Exhaustion**: Blocks clients attempting to cancel hunger packets while sprinting across long distances. Automatically ignores gliding (Elytra), swimming, flying, and Trident riptiding.

### 🚫 6. ModSentinel (Client Hack Detector)
* **Plugin Channel Sniffer**: Proactively intercepts client handshakes and register packets to detect forbidden client modifications:
  * **Baritone, FreeCam, Meteor, Wurst, LiquidBounce, Tweakeroo, Xaero's WorldMap/Minimap radar**, and other illegal client mods.
* **Folia Regional Thread Safety**: Uses regional entity schedulers to safely kick illegal clients without deadlocking asynchronous server regions.

### ⚖️ 7. Automated Progressive Punishments (Max Protection)
* **Tiered Enforcement**: Fully configurable automated punishment pipeline:
  * **Low VL**: Instant staff alerts (`[alert]`) and persistent transaction logs (`[log]`).
  * **Medium VL**: Discord webhook logging (`[webhook]`) + automated kick with clean gradient kick message.
  * **High VL**: Automated temporary ban (3–14 days) for persistent offenders.

### 💾 8. Zero-Setup SQLite Datastore
* **Self-Contained Database**: Automatically stores player session history, violation offsets, and client brand metadata in a high-speed SQLite database (`data/history.v1.db`).
* **Zero Bloat**: Removed bulky, unused external database drivers. Network setups can optionally enable native MySQL.

---

## 📥 Quick Start & Installation

### Requirements
* **Server**: Paper, Purpur, or Folia **1.16.5 – 26.x** (Universal).
* **Java**: **Java 21** or **Java 25+** (64-bit recommended).
* **RAM**: 0 MB extra overhead (designed for extreme low memory footprint).

### Step-by-Step Setup
1. **Download**: Grab the latest release `SentinelAC-2.3.74.jar` from the [Releases](https://github.com/dilmuratovv/SentinelAC) section.
2. **Install**: Place `SentinelAC-2.3.74.jar` into your server’s `plugins/` directory.
3. **Restart**: Restart your server (or run `/sentinel reload` if hot-loading).
4. **Permissions**: Grant your administrative staff `sentinel.admin` and `sentinel.alerts`.

---

## ⚙️ Configuration & Punishments

All settings are organized across clean, human-readable YAML configuration files inside `plugins/SentinelAC/`:

### 1. `config.yml` (Engine & Modules)
```yaml
# ==============================================================================
#  SENTINEL AC — CUSTOM EXPANDED MODULES
# ==============================================================================
inventory:
  autototem:
    enabled: true
    min-swap-delay-ms: 90
    prevent-swap-while-moving: true
    cancel-illegal-swaps: true

combat:
  enabled: true
  max-reach-distance: 3.35
  cancel-impossible-hits: true

food:
  fast-eat:
    enabled: true
    min-consume-ticks: 27
  no-hunger:
    enabled: true
    enforce-server-exhaustion: true

exploits:
  crystal-limiter:
    enabled: true
    max-crystals-per-second: 12
    max-breaks-per-second: 14
    max-anchors-per-second: 8
```

### 2. `punishments/en.yml` (Automated Punishments)
```yaml
Punishments:
  Simulation:
    remove-violations-after: 300
    checks:
      - "Simulation"
      - "GroundSpoof"
      - "Timer"
      - "NoFall"
    commands:
      - "15:10 [alert]"
      - "1:1 [log]"
      - "25:25 [webhook]"
      - "35:0 kick %player% &#FF416C[SENTINEL AC]\n&#E2E8F0Noqonuniy harakat (Fly/Speed/Timer) aniqlandi!"
      - "80:0 ban %player% 7d &#FF416C[SENTINEL AC]\n&#E2E8F0Cheat/Hack tufayli 7 kunga bloklandingiz!"
```

---

## 🎮 Commands & Permissions

### Commands
| Command | Aliases | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/sentinel alerts` | `/sac alerts`, `/sentinelac alerts` | `sentinel.alerts` | Toggle real-time in-game staff alerts |
| `/sentinel reload` | `/sac reload` | `sentinel.admin` | Hot-reload all configurations and check modules |
| `/sentinel profile <player>` | `/sac profile` | `sentinel.profile` | View player ping, client brand, sensitivity & stats |
| `/sentinel perf` | `/sac perf` | `sentinel.performance` | Display physical simulation latency and TPS health |
| `/sentinel info` | `/sac info` | `sentinel.admin` | Display active modules, author, and engine version |
| `/sentinel history <player>` | `/sac history` | `sentinel.admin` | Query stored violation sessions from database |
| `/sentinel version` | `/sac version` | `sentinel.version` | Display current build version and status |

### Permissions
* `sentinel.admin` — Master administrative permission for all anticheat commands.
* `sentinel.alerts` — Allows staff to receive formatted on-screen violation alerts.
* `sentinel.alerts.enable-on-join` — Automatically enables violation alerts upon joining.
* `sentinel.exempt` — Exempts trusted players / staff members from all anticheat checks.
* `sentinel.performance` — Access to real-time engine performance metrics (`/sentinel perf`).
* `sentinel.profile` — Allows inspecting player client brand and network statistics.
* `sentinel.nosetback` — Disables movement setbacks for the player.
* `sentinel.nomodifypacket` — Prevents packet cancellation / modification for the player.

---

## 🧩 PlaceholderAPI Placeholders

SentinelAC natively integrates with **PlaceholderAPI** without any extra setup:

| Placeholder | Description | Example Output |
| :--- | :--- | :--- |
| `%sentinel_version%` | Current installed SentinelAC version | `2.3.74-SENTINEL` |
| `%sentinel_player%` | Name of the checked player | `azicoo` |
| `%sentinel_player_brand%` | Client brand detected on connection | `Vanilla`, `LunarClient`, `Fabric` |
| `%sentinel_player_ping%` | Real-time roundtrip ping in milliseconds | `42 ms` |
| `%sentinel_player_horizontal_sensitivity%` | Player horizontal mouse sensitivity | `100.0%` |
| `%sentinel_player_vertical_sensitivity%` | Player vertical mouse sensitivity | `100.0%` |

---

## 🛠️ Building from Source

### Prerequisites
* JDK 21 or JDK 25+
* Apache Maven 3.9+
* Git

### Build Instructions
```bash
# 1. Clone the repository
git clone https://github.com/dilmuratovv/SentinelAC.git
cd SentinelAC

# 2. Compile and package the shaded plugin
mvn clean package

# 3. Output artifact
# Located in: target/SentinelAC-2.3.74-SENTINEL.jar
```

---

## 🌐 Til va Mahalliylashtirish (Localization)

SentinelAC o'z ichida 13 ta xalqaro tillarni to'liq qo'llab-quvvatlaydi. Xususan, o'zbek tili uchun **chiroyli gradient ranglar (`&#FF416C`, `&#10B981`, `&#FFD200`) va tushunarli matnlar** tayyor holatda joylashtirilgan. Barcha xabarlarni `messages/en.yml` (yoki o'zingiz tanlagan til fayli) orqali osongina tahrirlashingiz mumkin.

---

## 👨‍💻 Author & Credits

* **Author / Lead Developer**: **`azicoo`**
* **Project Engine**: Hybrid Simulation Core + SentinelAC Specialized Modules
* **License**: Released under the terms of the **GNU General Public License v3.0** (GPL-3.0).

---

<div align="center">
  <sub>Built with passion for high-performance, cheat-free Minecraft servers.</sub>
</div>
