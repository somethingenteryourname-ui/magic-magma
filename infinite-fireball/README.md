# InfiniteFireball

A Paper plugin for Minecraft Java **1.21.11**. `/fireball give` gives you the **Infinite Fire Charge**. Right-click it to throw a fireball. It is never used up.

You choose how big the explosion is and how much damage it does. There is also a **totem mode**: anything the fireball hits has a Totem of Undying popped, or dies if it isn't holding one, and its armor takes no durability damage.

## Installation

1. Get `InfiniteFireball-1.0.0.jar`: open the latest successful run on the repo's **Actions** tab and download the **InfiniteFireball** artifact, or build it yourself (see below).
2. Put the jar in your server's `plugins/` folder. The server must run **Paper 1.21.11** (or a Paper fork such as Purpur) on **Java 21+**.
3. Restart the server, then run `/fireball give`.

## Commands

Aliases: `/fb`, `/infinitefireball`. All commands need `infinitefireball.admin` (ops have it by default).

| Command | What it does |
|---|---|
| `/fireball give [player]` | Give the Infinite Fire Charge to yourself or another player |
| `/fireball size <0.1-20>` | Explosion size. Ghast fireball = 1, TNT = 4, charged creeper = 6 |
| `/fireball damage <hearts>` | Damage to every creature in the blast, in hearts (armor still reduces it) |
| `/fireball totem <on\|off>` | Totem mode: every hit pops a totem, or kills if there's no totem. Armor takes no damage |
| `/fireball blocks <on\|off>` | Whether the explosion breaks blocks (off by default) |
| `/fireball fire <on\|off>` | Whether the explosion sets fire (off by default) |
| `/fireball speed <0.1-5>` | How fast the fireball flies |
| `/fireball cooldown <seconds>` | Minimum time between throws (default 0.25) |
| `/fireball hurtself <on\|off>` | Whether your own fireballs can hurt you (off by default) |
| `/fireball settings` | Show the current settings |
| `/fireball reload` | Reload `config.yml` |

For on/off settings you can leave off the `on`/`off` to flip the current value. Every change is saved to `plugins/InfiniteFireball/config.yml`, so it survives restarts. Changes apply to fireballs thrown afterwards.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `infinitefireball.admin` | op | Use `/fireball` |
| `infinitefireball.use` | true | Throw fireballs with the Infinite Fire Charge |

## How totem mode works

With `/fireball totem on`, every creature hit by the fireball (the direct hit or the blast) gets one finishing hit that:

- **pops a Totem of Undying** if it holds one in either hand (normal totem effects, one totem per fireball);
- **kills it** if it doesn't;
- goes through armor, shields, Protection and Resistance, **without damaging armor or shields** (the explosion's own damage is cancelled, and the finishing hit is a type of damage that never touches armor).

Things that still stop it:

- Players in creative or spectator mode.
- Areas where PvP is off, or protection plugins such as WorldGuard that block the damage.
- Your own blast doesn't affect you unless you turn on `/fireball hurtself`. **Keep it off with totem mode**, or throwing at a nearby wall will pop your own totem.

When a player is killed this way, the chat message says `<victim> was obliterated by <killer>'s fireball`. You can change it in `config.yml`.

## Other details

- The item can't be put in dispensers or used in crafting. Right-clicking with it always throws a fireball, even when you're looking at a block.
- By default, other players can't punch your fireballs back at you. Set `can-be-deflected: true` in the config to allow it.
- Fireballs disappear after 10 seconds if they haven't hit anything (`max-flight-seconds`).
- Breaking blocks also needs the `mobGriefing` game rule to be on (it is by default).
- Setting fire without breaking blocks lights fire on the ground around the blast.

## Building

The jar is built together with the rest of the repo:

```bash
./gradlew build
# -> infinite-fireball/build/libs/InfiniteFireball-1.0.0.jar
```
