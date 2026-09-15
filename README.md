# SpeedVaro – Hero Island Drop

A lightweight, self-contained **Varo / Speed-UHC / battle-royale** minigame for Paper servers. Players drop from a glass cage in the sky with elytras, farm in peace for a while, then fight inside a shrinking world border. Everything is controlled through in-game menus – no command memorising required.

> **Languages:** English by default, German included – set `language: de` in the config. Every message lives in `messages_en.yml` / `messages_de.yml` in the plugin folder, so you can adjust texts or add your own language file.

Built for **Paper 1.21+** (Java 21). No hard plugin dependencies – see requirements below.

---

## Requirements

**Required**
- **Paper 1.21.4 or newer** (Spigot is not supported – the plugin uses Paper's Adventure API)
- **Java 21**
- **A separate arena world.** SpeedVaro never builds in your main world; it needs its own world named as configured in `arena-world` (default `varo`). The plugin does not create worlds itself, so you need a world manager such as **[Multiverse-Core](https://modrinth.com/plugin/multiverse-core)**: `/mv create varo NORMAL`.

**Optional – works with, not required**
- **A permissions plugin** (e.g. LuckPerms) to hand out `varo.admin` / `varo.invite` to non-ops
- **An economy or points plugin** (EssentialsX, PlayerPoints, …) if you want winner rewards – the plugin runs whatever console commands you put in `win-rewards`
- **Chunky** to pre-generate the whole arena area (SpeedVaro pre-generates only the centre)
- **TAB** – compatible, but its nametag feature must be disabled for the arena world so team prefixes show (`scoreboard-teams.disable-condition: '%world%=varo'`)

---

## Features

**Phase system** – Lobby → Farm time → Fight (shrinking border) → Winner celebration. The scoreboard shows the current phase, countdown, live border size, players alive and your team.

**Elytra drop** – The round starts with a 5-second countdown. The glass cage dissolves and every participant glides down with an unbreakable flight elytra to the landing spot of their choice. The elytra can't be removed, dropped or kept, and disappears on landing.

**Lives (blue hearts)** – Every player starts with a configurable number of respawns (default 3). Dying with a heart left keeps your inventory: you hover as a spectator above the arena for 5 seconds, then glide down again. Dying with no hearts left is final.

**Loot chest** – On a final death the whole inventory (armour and offhand included, exact durability and enchantments) goes into a double chest at the death spot with a floating 60-second timer. The chest can be opened but not broken, and vanishes with its contents when the timer ends.

**Teams** – Up to 8 colour-coded teams, created and joined in the menu. Team colour and `[Team X]` prefix above the head and in the tab list. Teammates never damage each other but still knock each other back ("boosting"). Shared 27-slot **team backpack**, and sneak + right-click on a teammate opens their inventory.

**Farm-time quality of life** – No damage, no hunger, no PvP. Ores drop as ingots and animals drop cooked food (auto-smelter). Whole trees fall with one log. Drops go straight into your inventory. Wooden tools craft as iron, diamond tools craft enchanted (Efficiency IV, Unbreaking III). `/anvil` and `/enchant` open a virtual anvil or a full-power (15-bookshelf) enchanting table if you carry the block.

**Uniform food** – Animals only drop cooked beef (plus leather and wool), so nobody hauls around five kinds of meat.

**Cave elevator** – Every player gets a one-time item at the drop: right-click below Y=0 to teleport straight up to the surface.

**Fair arena locations** – `/varosetup` searches for a random spot 10 000–50 000 blocks out, rejecting oceans, rivers, deserts, frozen biomes and anything below sea level, and never reuses an area. The surrounding chunks are pre-generated so nobody drops into unloaded terrain.

**Better world generation** – Extra diamond veins and much more sugar cane along shores in newly generated chunks of the arena world (both configurable).

**Safe inventories** – Entering the arena world stores your normal inventory and XP on disk; leaving restores them. Arena loot never leaves the arena.

**Winner celebration** – Title and fanfare, server-wide announcement, fireworks in team colour, configurable reward commands for every winner, and everyone returns to spawn after 10 seconds.

**Locked down** – Configurable command blocking (shops, teleports, `/back`, `/home` …), no Nether portals, death-chest protection, and an invitation system with clickable **[Accept] / [Decline]** chat buttons so only invited players can join.

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

| Command | Aliases | Description |
|---|---|---|
| `/varo` | | Opens the main menu (join/leave, teams, backpack, spectate) |
| `/varoaccept` | | Accept an invitation and enter the lobby |
| `/varocancel` | | Decline an invitation |
| `/varoleave` | `/leave` | Leave the arena world and get your normal inventory back |
| `/varobackpack` | `/backpack`, `/bp` | Open your team's shared chest |
| `/varospec` | `/spec` | Spectator menu – teleport to living players (spectators only) |
| `/varoanvil` | `/anvil` | Open an anvil if you carry one |
| `/varoenchant` | `/enchant` | Open an enchanting table (15-bookshelf power) if you carry one |

### Admins (`varo.admin`) – all also available as buttons in `/varo` → Admin

| Command | Description |
|---|---|
| `/varosetup [borderSize]` | Find a location, build the cage, open the lobby |
| `/varostart [farmMin] [targetSize] [shrinkSec]` | Countdown, drop, start the round |
| `/varoreset` | Restore the arena world (border to max, safe spawn, send everyone home) |
| `/varosafenet <on\|off>` | Auto-teleport joining players into the cage while the lobby is open |
| `/varo reload` | Reload `config.yml` without a restart |

The admin menu additionally offers a **Settings** page (border size, farm time, target size, shrink time, lives, countdown, chunk pre-generation, start time of day) and **Remove player** for participants who went offline and would otherwise block the win.

Aliases only work if no other plugin claims the same name; the `varo…` form always works. Note that `/enchant` shadows the vanilla operator command (`/minecraft:enchant` still works).

---

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `varo.admin` | op | All admin commands and menus, joining without invitation. Includes `varo.invite`. |
| `varo.invite` | false | Only the *Invite players* button – for helpers who may invite but not run the round |

---

## Configuration

Generated as `plugins/SpeedVaro/config.yml`. The `defaults` section can also be edited in-game via the settings menu.

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

`/varo reload` applies config and message changes. New keys are added to an existing config automatically; existing lists are never overwritten.

### Translating

`messages_en.yml` and `messages_de.yml` are copied into `plugins/SpeedVaro/` on first start. To add a language, copy `messages_en.yml` to `messages_<code>.yml`, translate the values (keep the `{0}` placeholders and `&` colour codes), and set `language: <code>`. Any key missing from your file falls back to English.

---

## Setup

1. Drop `SpeedVaro-x.y.z.jar` into `plugins/` and restart.
2. Create a dedicated arena world, e.g. with Multiverse: `/mv create varo NORMAL`. The plugin refuses to build anywhere else, so your survival and hub worlds are never touched by the border.
3. Set `lobby-world` in the config to your hub/spawn world.
4. Optional: for a fully pre-generated arena, run Chunky on the coordinates printed by `/varosetup`.

### Compatibility notes

- **TAB** (or other tab-list/nametag plugins): team prefixes above the head only show if the plugin leaves the arena world alone – for TAB set `scoreboard-teams.disable-condition: '%world%=varo'`.
- **EssentialsSpawn**: respawn handling is compatible; the plugin overrides the respawn location for arena deaths.
- Works with any economy/points plugin through the `win-rewards` console commands.

---

## Building

Maven, Java 21: `mvn clean package` → `target/SpeedVaro-<version>.jar`.

## License

GPL-3.0 – see `LICENSE`.
