
<img width="1919" height="768" alt="speedVaro_banner" src="https://github.com/user-attachments/assets/5dfa228f-ef10-4fee-9ed2-281c5764e282" />

# SpeedVaro – Hero Island Drop

A lightweight, self-contained **Varo / Speed-UHC / battle-royale** minigame for Paper servers. Players drop from a glass cage in the sky with elytras, farm in peace for a while, then fight inside a shrinking world border. Everything is controlled through in-game menus – no command memorising required.

> **Languages:** English by default, German included – set `language: de` in the config. Every message lives in `messages_en.yml` / `messages_de.yml` in the plugin folder, so you can adjust texts or add your own language file.

Built for **Paper 1.21+** (Java 21). No hard plugin dependencies – see requirements below.

---

## Features

* **Phase System:** Smooth progression from Lobby → Farm time → Fight (shrinking border) → Winner celebration. The live scoreboard tracks the current phase, countdown, border size, players alive, and your team.
* **Elytra Drop:** Rounds begin with a 5-second countdown. The glass cage dissolves, and every participant glides down with an unbreakable flight elytra to a landing spot of their choice. The elytra disappears upon landing.
* **Lives & Respawn (Blue Hearts):** Players start with a configurable number of respawns (default: 3). Dying with hearts left keeps your inventory: you hover as a spectator above the arena for 5 seconds, then glide down again. A death without hearts left is final.
* **Death Loot Chests:** On final death, the complete inventory (armor, offhand, exact durability, and enchantments) is secured in a double chest at the death spot with a floating 60-second timer. The chest can be opened but not broken, and vanishes with its contents when the timer ends.
* **Teams & Shared Backpack:** Create and join up to 8 color-coded teams via the menu. Team colors and `[Team X]` prefixes show in the tab list and above heads. Teammates cannot damage each other (though knockback/boosting remains active) and share a 27-slot **team backpack**. Sneak + right-click a teammate to view their inventory.
* **Farm-Time Quality of Life:**
  * No damage, no hunger, and no PvP during the farming phase.
  * Night vision for everyone from the drop until the fight starts – caves and nights are no obstacle.
  * Ores drop as ingots, and animals drop cooked food automatically (auto-smelter).
  * Whole trees fall when chopping a single log, and drops go straight into your inventory.
  * Wooden tools craft as iron; diamond tools craft pre-enchanted (*Efficiency IV, Unbreaking III*).
  * Virtual `/anvil` and `/enchant` tables (full 15-bookshelf power) available on the go if you carry the block.
* **No Hostile Mobs:** Zombies, creepers, phantoms and friends never spawn in the arena world while a round is prepared or running. Animals are unaffected, spawn eggs and `/summon` still work – no gamerules to set.
* **Uniform Food:** Animals drop cooked beef (plus leather and wool) to keep food management streamlined.
* **Cave Elevator:** A one-time use item given at the drop – right-click below Y=0 to teleport straight back to the surface.
* **Fair Arena Locations:** `/varosetup` searches for a random location 10,000–50,000 blocks out, filtering out oceans, rivers, deserts, and low elevations, never reuses an area, and pre-generates the surrounding chunks so nobody drops into unloaded terrain.
* **Better World Generation:** Extra diamond veins and much more sugar cane along shores in newly generated arena chunks (both configurable).
* **Safe Inventories:** Normal player inventories and XP are safely stored on disk when entering the arena world and fully restored upon leaving. Arena loot never leaves the arena.
* **Winner Celebration:** Title and fanfare, server-wide announcement, fireworks in team colour, configurable reward commands for every winner, and everyone returns to spawn after 10 seconds.
* **Update Notice:** Checks Modrinth once at startup and tells the console and admins when a newer version exists. Nothing is downloaded; `update-check: false` turns it off.
* **Locked Down:** Command blocking (shops, teleports, `/home`, `/back`), disabled Nether portals, and an invitation system with clickable **[Accept] / [Decline]** chat buttons.
* **Multilingual:** English and German out of the box; every message is editable and additional languages are a single YAML file away.

---

## How a round works

1. **Admin → Build arena.** A random location is chosen, the border is centred there and a 9×9 glass cage appears at Y=200. The lobby opens.
2. **Admin invites players** from a head list. Invited players click **[Accept]** in chat and are teleported into the cage. Players form teams in the menu.
3. **Admin → Start round.** Countdown, cage dissolves, everyone glides down. Farm time begins (default 20 min).
4. **Fight.** The border shrinks smoothly to its target size (default 500 blocks over 30 min). PvP is on.
5. **Last party standing wins** – a team, or a solo player without a team. Celebration, rewards, back to spawn.

---

## Commands

### Players

* `/varo` – Opens the main menu (join/leave, teams, backpack, spectate)
* `/varoaccept` / `/varocancel` – Accept or decline a game invitation
* `/varoleave` (`/leave`) – Leave the arena and restore your normal inventory
* `/varobackpack` (`/backpack`, `/bp`) – Open your team's shared chest
* `/varospec` (`/spec`) – Spectator menu to teleport to living players
* `/varoanvil` (`/anvil`) / `/varoenchant` (`/enchant`) – Open virtual workstations

### Admins (`varo.admin`) – all also available as buttons in `/varo` → Admin

* `/varosetup [borderSize]` – Find a location, build the cage, and open the lobby
* `/varostart [farmMin] [targetSize] [shrinkSec]` – Start the countdown and drop
* `/varoreset` – Reset the arena world and send everyone home
* `/varosafenet <on|off>` – Auto-teleport joining players into the cage during the lobby phase
* `/varo reload` – Reload config and messages without restarting

The admin menu additionally offers a **Settings** page (border size, farm time, target size, shrink time, lives, countdown, chunk pre-generation, start time of day) and **Remove player** for participants who went offline and would otherwise block the win.

Aliases only work if no other plugin claims the same name; the `varo…` form always works. Note that `/enchant` shadows the vanilla operator command (`/minecraft:enchant` still works).

---

## Permissions

* `varo.admin` (default: op) – All admin commands and menus, joining without invitation. Includes `varo.invite`.
* `varo.invite` (default: false) – Only the *Invite players* button – for helpers who may invite but not run the round.

---

## Configuration

Generated automatically as `plugins/SpeedVaro/config.yml`. Fully customizable border sizes, timer durations, lives, rewards, blocked commands and language. The `defaults` section can also be edited in-game via the settings menu, and `/varo reload` applies config and message changes without a restart.

```yaml
language: en               # en or de; add messages_<code>.yml for more
arena-world: varo          # the only world /varosetup may build in
lobby-world: world         # where /leave, reset and the post-game return go

blocked-commands:          # blocked in the arena world during a round
  - shop
  - tpa
  - tpahere
  - tpaccept
  - tphere
  - back
  - spawn
  - warp
  - rtp
farm-blocked-commands:     # blocked only during farm time
  - ah
  - sethome
  - home

extra-diamond-veins-per-chunk: 2   # 0 = off
extra-sugar-cane: true

block-hostile-mobs: true   # no natural hostile spawns in the arena world (spawn eggs and /summon still work)
farm-night-vision: true    # night vision from the drop until the fight starts
update-check: true         # notify console and admins about new versions on Modrinth

win-rewards:               # console commands per winner, %player% is replaced
  - "eco give %player% 1000"
win-rewards-text: "&a+1000 Money"

defaults:
  border-size: 2000
  farm-minutes: 20
  target-size: 500
  shrink-minutes: 30
  lives: 3                 # respawns; 0 = classic Varo, one death and you're out
  countdown-seconds: 5
  preload-chunk-radius: 10
  start-time: 1000         # world time at build/drop, -1 = leave unchanged
```

### Translating

`messages_en.yml` and `messages_de.yml` are copied into `plugins/SpeedVaro/` on first start. To add a language, copy `messages_en.yml` to `messages_<code>.yml`, translate the values (keep the `{0}` placeholders and `&` colour codes), and set `language: <code>`. Any key missing from your file falls back to English.

---

## Requirements

**Required**
* **Paper 1.21 or newer** – tested on 1.21.1, 1.21.11 and 26.2 (Spigot is not supported – the plugin uses Paper's Adventure API)
* **Java 21**
* **A separate arena world.** SpeedVaro never builds in your main world; it needs its own world named as configured in `arena-world` (default `varo`). The plugin does not create worlds itself, so you need a world manager such as **[Multiverse-Core](https://modrinth.com/plugin/multiverse-core)**: `/mv create varo NORMAL`.

No gamerules or other world settings are needed – mob spawning and night vision are handled by the plugin.

**Optional – works with, not required**
* **A permissions plugin** (e.g. LuckPerms) to hand out `varo.admin` / `varo.invite` to non-ops
* **An economy or points plugin** (EssentialsX, PlayerPoints, …) if you want winner rewards – the plugin runs whatever console commands you put in `win-rewards`
* **Chunky** to pre-generate the whole arena area (SpeedVaro pre-generates only the centre)
* **TAB** – compatible, but its nametag feature must be disabled for the arena world so team prefixes show (`scoreboard-teams.disable-condition: '%world%=varo'`)

---

## Setup

1. Drop `SpeedVaro-x.y.z.jar` into `plugins/` and restart.
2. Create the arena world, e.g. `/mv create varo NORMAL`.
3. Set `lobby-world` in the config to your hub/spawn world (and `language` if you want German).
4. Optional: for a fully pre-generated arena, run Chunky on the coordinates printed by `/varosetup`.

---

## About this project

SpeedVaro was developed with AI assistance (Claude). The concept, gameplay rules, balancing, testing and every design decision are by the author; the code and this page were written together with an AI coding assistant. Screenshots are real in-game captures.

Source code and issue tracker: [github.com/HrKato4949/SpeedVaro](https://github.com/HrKato4949/SpeedVaro) · License: GPL-3.0
