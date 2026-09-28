# CrystallineResonance

> This repo also contains **[InfiniteFireball](infinite-fireball/README.md)**, a separate plugin: a fire charge that never runs out and throws fireballs with adjustable size and damage, plus a totem-pop mode.

A Paper plugin for Minecraft Java **1.21.11**. Players dig up Amethyst, Copper and Quartz crystals in the Nether, craft them into spell tomes, and right-click the tomes to cast spells that cost mana. Mana regenerates over time.

| Spell | Crystal | What it does |
|---|---|---|
| **Frost Bolt** | Resonant Amethyst | A fast ice bolt that damages, slows and freezes the first thing it hits, and splashes nearby enemies. |
| **Inferno Wave** | Emberheart Quartz | An expanding arc of fire in front of you that burns and knocks back everything it passes. Fire-immune mobs are unharmed. |
| **Lightning Strike** | Galvanic Copper | Charges on the targeted spot, following a locked-on creature, then calls down lightning that hits everything nearby and jumps to more targets. |

## Features

- **Nether crystals.** Each crystal drops from specific Nether blocks at a configurable chance. Fortune raises the chance. By default Silk Touch doesn't drop crystals, and blocks placed by players never do, so crystals can't be farmed. Crystals also turn up in bastion, fortress and ruined portal chests.
- **Spell tomes.** Each tome is crafted from 4 crystals, a book and 4 flavor items. Recipes are fully configurable, and the plugin checks that the crystals are real ones, not vanilla amethyst, copper or quartz. Crystals and tomes can't be used up in vanilla crafting, smithing or enchanting.
- **Mana.** Mana regenerates per second after a short delay once you cast. It's shown as a colored bar in the action bar and saved on the player, so it survives relogs and restarts. You can't cast a spell without enough mana; you get an action-bar message and a sound instead.
- **Cooldowns.** Each spell has its own cooldown, and the action bar shows how long is left.
- **Custom sounds.** Every spell plays its own layered sounds on cast and on impact, plus travel and chain sounds for some spells. Each layer has its own volume, pitch and delay. You can use any vanilla or resource-pack sound.
- **Particle colors.** Each spell has configurable primary and secondary colors, used for colored dust and color-fading particles.
- **Fair combat.** Damage counts as coming from the caster, so kill credit, death messages, PvP rules, teams and protection plugins such as WorldGuard all work as normal. You can turn off damage to other players or to your own pets.
- **API.** Other plugins can listen for `SpellCastEvent`, which they can cancel or use to change a spell's mana cost.

## Installation

1. Build the jar (see below) or grab it from a release.
2. Drop `CrystallineResonance-1.0.0.jar` into your server's `plugins/` folder. The server must run **Paper 1.21.11** on **Java 21+**.
3. Start the server, edit `plugins/CrystallineResonance/config.yml`, and run `/cr reload`.

## Building

```bash
./gradlew build
# -> build/libs/CrystallineResonance-1.0.0.jar
```

The build compiles against `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` from `https://repo.papermc.io/repository/maven-public/`.

## Commands

Aliases: `/cr`, `/crystal`

| Command | Permission | Description |
|---|---|---|
| `/cr help` | none | Show help |
| `/cr mana` | `crystalline.command.mana` | Show your mana |
| `/cr mana <player>` | `crystalline.admin` | Show another player's mana |
| `/cr spells` | none | List spells, costs and which you can cast |
| `/cr give <player> crystal <amethyst\|copper\|quartz> [amount]` | `crystalline.admin` | Give crystals |
| `/cr give <player> tome <frost\|inferno\|lightning> [amount]` | `crystalline.admin` | Give spell tomes |
| `/cr setmana <player> <amount\|max>` | `crystalline.admin` | Set a player's mana |
| `/cr resetcooldowns <player>` | `crystalline.admin` | Clear a player's spell cooldowns |
| `/cr reload` | `crystalline.admin` | Reload `config.yml`, including recipes |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `crystalline.admin` | op | Admin commands |
| `crystalline.spell.*` | true | Cast every spell |
| `crystalline.spell.frost` | true | Cast Frost Bolt |
| `crystalline.spell.inferno` | true | Cast Inferno Wave |
| `crystalline.spell.lightning` | true | Cast Lightning Strike |
| `crystalline.craft.*` | true | Craft every tome |
| `crystalline.craft.frost` / `.inferno` / `.lightning` | true | Craft a specific tome |
| `crystalline.gather` | true | Find crystals while mining |
| `crystalline.command.mana` | true | `/cr mana` |
| `crystalline.bypass.cooldown` | false | Ignore cooldowns |
| `crystalline.bypass.mana` | false | Cast without spending mana |
| `crystalline.*` | false | Everything above |

To lock a spell behind a rank, negate its permission for the default group, e.g. `crystalline.spell.lightning: false`, and grant it to the rank.

## Default recipes

```
 S C S      Frost Bolt:        C = Amethyst Crystal, S = Snowball
 C B C      Inferno Wave:      C = Quartz Crystal,   S = Blaze Powder
 S C S      Lightning Strike:  C = Copper Crystal,   S = Redstone      (B = Book)
```

## Where crystals drop (defaults)

| Crystal | Blocks (chance per block) |
|---|---|
| Amethyst | Crying Obsidian 25%, Gilded Blackstone 20%, Blackstone 1%, Basalt 0.8% |
| Copper | Nether Gold Ore 6%, Magma Block 2%, Netherrack 0.2% |
| Quartz | Ancient Debris 50%, Nether Quartz Ore 8%, Glowstone 5% |

Each Fortune level multiplies these by `1 + level × fortune-bonus` (default +25% per level). In addition, 35% of bastion, fortress and ruined-portal chests hold 1 to 3 crystals.

## Configuration

Everything lives in [`config.yml`](src/main/resources/config.yml), and each setting is commented there. Highlights:

```yaml
mana:
  max: 100.0
  regen-per-second: 4.0
  regen-delay: 1.5            # seconds after a cast before regen resumes
  display:
    mode: SMART               # ALWAYS | HOLDING_TOME | SMART

spells:
  frost:
    mana-cost: 20.0
    cooldown: 1.5             # seconds
    particles:
      primary: "#A2D2FF"
      secondary: "#FFFFFF"
      size: 1.2
    sounds:
      cast:
        - "minecraft:entity.player.hurt_freeze 1.0 1.6"      # key volume pitch
        - "minecraft:block.amethyst_block.chime 1.0 1.2 2"   # ...optional delay in ticks
```

All text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html), so gradients, colors and hover text work in item names, lore and messages.

### Using your own sound files

Sound entries accept any sound key, so a server resource pack can add brand-new spell sounds:

```
assets/crystalline/sounds.json
assets/crystalline/sounds/spell/frost_cast.ogg
```

```json
{
  "spell.frost.cast": { "sounds": ["crystalline:spell/frost_cast"] }
}
```

```yaml
spells:
  frost:
    sounds:
      cast:
        - "crystalline:spell.frost.cast 1.0 1.0"
```

Players without the pack just won't hear that layer, so it's a good idea to keep a vanilla layer as well.

## Developer API

```java
@EventHandler
public void onCast(dev.crystalline.resonance.api.SpellCastEvent event) {
    if (event.getSpell() == SpellType.LIGHTNING && isInSafeZone(event.getPlayer())) {
        event.setCancelled(true);
    }
    event.setManaCost(event.getManaCost() * 0.5); // e.g. a mana-discount perk
}
```
