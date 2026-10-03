# Shardwatch — Design Plan

*A crystal-themed staff and moderation suite for Paper 1.21.11, with its own resource pack.*

This document is the blueprint. Everything in it is built in six stages (see the end of the file).
After each stage, `tools/check_pack.py` checks every texture and model against the
**Look & textures** list, and the result is written to `docs/stage-reports/`.

---

## 1. Naming

All features have their own names. Plain-English words are kept as aliases where they help staff learn the commands.

| Concept | Shardwatch name | Main command | Plain alias |
|---|---|---|---|
| Plugin / hub | **Shardwatch** | `/shardwatch` | `/sw` |
| Player report | **Flare** | `/flare <player> [reason]` | `/report` |
| Report queue | **Flare Board** | `/flares` | — |
| Warning | **Chip** | `/chip <player> <reason>` | `/warn` |
| Mute | **Hush** | `/hush <player> [duration] <reason>` / `/unhush` | `/mute`, `/unmute` |
| Kick | **Eject** | `/eject <player> <reason>` | `/kick2` |
| Ban / temp-ban | **Encase** | `/encase <player> [duration] <reason>` / `/unencase` | `/ban2`, `/unban2` |
| Freeze | **Petrify** | `/petrify <player>` | `/freeze` |
| Punishment history | **Ledger** | `/ledger <player>` | `/history` |
| Block log | **Echoes** | (Echo Lens tool or `/echo`) | `/inspect` |
| Rollback | **Rewind** (Timeglass) | `/rewind <who> <time> [radius]` | `/rollback` |
| X-ray alerts | **Glint** | `/glint` | `/xray` |
| Staff ranks | **Facets** | `/facet` | `/staffrank` |
| Staff chat | **Hum** | `/hum <message>` | `/sc` |
| Vanish | **Veil** | `/veil` | `/vanish` |
| Log viewer | **Shardscope** | `/scope [tab] [page] [search]` | `/logs` |
| Staff XP | **Lustre** | `/sw lustre` | — |
| Tool upgrades | **Refinements** | (GUI) | — |
| Cosmetic rewards | **Keepsakes** | (GUI) | — |
| Main GUI | **Crystal Console** | `/sw` | — |

> The plain aliases `/kick2` and `/ban2` don't shadow vanilla `/kick` and `/ban`. Server owners can remap them in `commands.yml`.

### Staff ranks (Facets)

| Weight | Facet | Role | Colour |
|---|---|---|---|
| 10 | **Shardling** | Helper: Flares, Chip, Hum, Shardscope (read) | `#7FE8E0` teal |
| 20 | **Prismkeeper** | Moderator: + Hush, Eject, Petrify, Echo Lens, Veil | `#F59AC8` pink |
| 30 | **Lumenwarden** | Senior mod: + Encase, Rewind, Glint, Ledger revoke | `#B7F2FF` ice |
| 40 | **Crownfacet** | Admin: everything, Facet management, reload | `#FFC7E6` rose-white |

Ranks are defined in `config.yml` (any number of them). Each rank grants a list of permissions through a
`PermissionAttachment`, so the plugin works **without** LuckPerms. A server using LuckPerms can turn
`facets.grant-permissions` off and just grant `shardwatch.facet.<id>`.

---

## 2. Architecture

```
shardwatch/                          Gradle subproject (root project keeps CrystallineResonance)
├─ build.gradle.kts                  Java 21, paper-api 1.21.11, `packZip` task (deterministic zip + .sha1)
├─ src/main/java/dev/shardwatch/
│  ├─ Shardwatch.java                plugin bootstrap, service registry, reload
│  ├─ config/        SwConfig        typed view over config.yml (re-read on /sw reload)
│  │                 Lang            MiniMessage messages with <prefix> and placeholders
│  ├─ storage/       Database        SQLite (JDBC), schema migrations, single writer thread
│  │                 Dao classes     PunishmentDao, FlareDao, EchoDao, ChatLogDao, AuditDao, GlintDao, ProgressDao
│  ├─ verdict/       VerdictService  Chip/Hush/Eject/Encase/Petrify, expiry, active caches
│  │                 VerdictListener login gate, chat gate, freeze movement lock
│  ├─ flare/         FlareService    create, cooldown, claim, resolve, notify staff
│  ├─ echo/          EchoRecorder    block place/break/explode/burn/bucket → batched queue
│  │                 RewindEngine    query → plan → apply N blocks/tick, undo stack
│  ├─ glint/         GlintTracker    per-player ore/stone counters, vein de-duplication, score, alerts
│  ├─ facet/         FacetService    ranks, permission attachments, Hum chat, Veil
│  ├─ scope/         ScopeViewer     "web page" written-book renderer + chat renderer
│  ├─ tool/          StaffTool enum  item factory (item_model, tooltip_style, custom_model_data tier)
│  │                 ToolListener    interactions for every tool
│  ├─ gui/           Menu framework  InventoryHolder menus, Button, Pager, custom font backgrounds
│  │                 menus/*         Console, FlareBoard, FlareCreate, Verdict, Ledger, Glint, Roster, Lustre, Settings
│  ├─ progress/      LustreService   points, levels, Refinements (tool tiers), Keepsakes (cosmetics)
│  ├─ fx/            Fx              particles (dust, transitions, custom item particles), sounds with vanilla fallback,
│  │                 Animations      ItemDisplay/TextDisplay animations (encase cage, rank halo, flare beacon, …)
│  ├─ pack/          PackService     optional per-join pack push using hash from config
│  └─ command/       one class per command + SwCommand hub (give, reload, kit, pack, lustre, menu)
├─ src/main/resources/ plugin.yml, config.yml, lang.yml
├─ resourcepack/                     generated, committed resource pack (zipped by CI)
├─ blockbench/                       .bbmodel source projects (open in Blockbench)
├─ tools/
│  ├─ pixelart.py                    shading engine: height fields → colour + LabPBR normal/specular
│  ├─ generate_pack.py               draws every texture, writes models/item defs/sounds/fonts/mcmeta
│  ├─ synth_sounds.py                synthesises every .ogg (bell partials, shimmer, whoosh, thud)
│  ├─ render_previews.py             software renderer → docs/previews/*.png
│  └─ check_pack.py                  checks every asset against the Look & textures list
└─ docs/   DESIGN.md, RESOURCE_PACK.md, stage-reports/, previews/
```

### Threading
* All SQL runs on one `ExecutorService` (single thread, so SQLite never sees concurrent writers).
* Echo events go into a `ConcurrentLinkedQueue` and are flushed in batches every second, inside one transaction.
* Reads that a command needs (Ledger, Shardscope, Rewind planning) run async, then hop back to the main thread
  with `Bukkit.getScheduler().runTask`.
* Rewind applies its plan on the main thread with a per-tick block budget (`rewind.blocks-per-tick`).

### Data model (SQLite, `plugins/Shardwatch/shardwatch.db`)

| Table | Columns |
|---|---|
| `verdicts` | id, type (CHIP/HUSH/EJECT/ENCASE/PETRIFY), target_uuid, target_name, actor_uuid, actor_name, reason, created, expires (0 = never), active, revoked_by, revoked_at, revoke_reason |
| `flares` | id, reporter_uuid, reporter_name, target_uuid, target_name, category, reason, world, x, y, z, created, status (OPEN/CLAIMED/RESOLVED/DISMISSED), handler_uuid, handler_name, resolution, closed_at |
| `echoes` | id, time, actor_uuid, actor_name, action (BREAK/PLACE/EXPLODE/BURN/BUCKET_EMPTY/BUCKET_FILL), world, x, y, z, old_data, new_data, rewound |
| `chat_log` | id, time, uuid, name, kind (CHAT/COMMAND/HUM), message |
| `audit` | id, time, actor_uuid, actor_name, action, detail |
| `glint` | id, time, uuid, name, ore, world, x, y, z, score |
| `progress` | uuid, name, facet, lustre, level, refinements (`tool:tier,…`), keepsakes (csv), active_keepsake, alerts_on, fx_on, veiled |

Indexes: `echoes(world,x,y,z)`, `echoes(time)`, `echoes(actor_name)`, `verdicts(target_uuid)`, `flares(status)`.

---

## 3. Features

### Stage 1 — Core (moderation with saved data)
* **Verdicts.** Chip, Hush (timed or permanent), Eject, Encase (timed or permanent), Petrify. Durations like `30m`, `2h`, `7d`, `1w2d`, `perm`.
  Encase is checked in `AsyncPlayerPreLoginEvent` with a custom disconnect screen. Hush is checked in `AsyncChatEvent` from an in-memory cache.
  Petrify locks movement (it keeps head rotation), blocks commands except an allow-list, and remembers petrified players across relogs.
  Optional broadcasts, silent flag `-s`, reason presets.
* **Ledger.** Full history per player, active vs. expired vs. revoked, revoke with a reason.
* **Flares.** Player reports with a per-player cooldown, a duplicate guard, location capture, staff alert with
  click-to-claim/teleport, and a status workflow.
* **Echoes + Rewind.** Logs block place, break, explosion (credited to whoever lit the TNT or creeper target when known), fire burn and buckets.
  `/rewind <player|#tnt|#fire|#creeper> <time> [radius]` restores the old states in batches. It only touches blocks that still match the
  logged state, so later legit builds are kept. `/rewind undo` reverts your last rewind, and `/rewind preview` shows ghost particles first.
* **Glint (x-ray).** Counts ore *veins* (connected ores mined within a short window count once) against stone mined. The score is weighted per ore and
  raised for low light and deep Y. When the score passes `glint.threshold`, staff with alerts on get a chat alert with [Teleport] [Ledger] [Spectate]. Every alert is saved.
* **Facets.** Config-defined ranks, permission attachments, coloured chat prefix (plus a glyph sigil from the pack), Hum staff chat, Veil vanish.
* **Shardscope.** A "web-style" log viewer drawn in a written book: a navbar of clickable tabs on every page
  (Overview · Blocks · Chat · Verdicts · Flares · Glint · Staff), table rows with hover tooltips (full detail, coordinates, time),
  click-to-teleport, ◀ ▶ pager links, and search (`/scope chat 1 griefer`). `/scope ... --chat` renders the same view in chat.

### Stage 2 — Content (staff tools)
| Tool | Use |
|---|---|
| **Echo Lens** | Left-click a block to see its history. Right-click a face to see the block placed against it. Shows a hologram and particles at the block. |
| **Timeglass** | Left-click and right-click set two corners. Shift + right-click opens Rewind presets for that area. The model drains while on cooldown. |
| **Verdict Gavel** | Hit a player to open the Verdict menu for them. Shift + right-click to Chip the last player you looked at. |
| **Petrify Prism** | Right-click a player to toggle Petrify, with a crystal shell animation. |
| **Veil Lantern** | Right-click to toggle Veil. The lantern is lit or unlit to match. |
| **Flare Compass** | Points at the oldest open Flare. Right-click teleports there and claims it. Shift + right-click cycles. |
| **Glint Monocle** | Right-click cycles through recent Glint suspects and spectates them. |
| **Lustre Shard** | A reward item. Right-click to turn it into Lustre (with a pickup sound and burst). |
| **Facet Sigils** (×4) | Rank badges. Display only, given on promotion. |

Tools are tagged in the PDC, can't be dropped, put in containers or used in crafting (configurable), and are removed from players who
lose the needed permission. `/sw kit` gives every tool your Facet allows.

### Stage 3 — GUI
A menu framework (InventoryHolder + click handler map + pager). Every icon uses a Shardwatch `item_model` and the
`shardwatch:crystal` tooltip style. Optionally, custom font glyphs draw a crystal background behind the chest grid.
Menus: **Crystal Console** (hub), **Flare Board** (filter by status, claim, resolve, dismiss, teleport), **Flare Create**
(categories for players), **Verdict** (type → duration presets → reason presets → confirm), **Ledger** (paged, revoke),
**Glint Watch**, **Facet Roster**, **Lustre & Refinements**, **Keepsakes**, **Settings** (alerts, FX, Veil, sigil).

### Stage 4 — Progression
* **Lustre** is earned per action (resolve a Flare +12, Verdict +6, Rewind +10, handle a Glint alert +8, …). Daily caps stop farming.
* **Clarity levels** I–X (configurable thresholds). Levelling up plays a halo animation and grants Keepsakes.
* **Refinements**: spend Lustre to upgrade tools to tier II or III (Echo Lens range and page size, Timeglass max area, Flare Compass auto-claim,
  Petrify shell radius, …). The tier is stored in `custom_model_data` strings, and the item definition switches to a more ornate 3D model.
* **Keepsakes**: cosmetic auras (particle trails while Veiled or not), chat sigils, and Lustre Shards. Claimed in the GUI.
* Promotion helper: a Facet can require a minimum Clarity level. `/facet promote` checks it, and `facets.auto-promote` can promote automatically.

### Stage 5 — Polish
* `Fx` plays a **particle preset** + a **sound preset** for every ability, hit and pickup. Each sound preset has
  a custom `shardwatch:*` key and a vanilla fallback layer, so players without the pack still hear something.
* Custom particles: `Particle.ITEM` with an ItemStack whose `item_model` is `shardwatch:particle/*`. The break particles show
  our 64×64 crystal sprites without replacing any vanilla particle.
* Animations use `ItemDisplay` with interpolated transformations: Encase cage closing, Petrify shell growing, Flare beacon rising,
  Facet halo spinning, Rewind sweep, Lustre burst. They are all client-only visual entities, removed on disable.

### Stage 6 — Admin
`config.yml` covers every number, colour, sound, particle and message. `plugin.yml` has a full permission tree with
`shardwatch.*`, `/sw give <player> <tool> [tier] [amount]`, `/sw reload` (config, lang, ranks, menus), `/sw pack` (resend pack).
GitHub Actions builds both jars, zips the pack deterministically, computes SHA-1 and publishes the zip to a rolling `pack-latest` release.

---

## 4. Look & textures — implementation contract

| Requirement | How it is met | Checked by |
|---|---|---|
| Palette: clear pinks and teals, shiny faceted highlights | Every colour comes from 4 ramps (pink, teal, ice, silver/gold trim). Gems are drawn as planar facets with per-facet normals and a specular sparkle. | ≥ 85% of opaque pixels within ΔE of the palette, and pink **and** teal both present |
| 64×64, careful shading, highlights, outlines, consistent light | Height-field shading with one light vector `L = (-0.55, -0.65, 0.52)` (top-left-front), a 1 px darkened outline, rim light and a specular pass. | size = 64×64 (or 64×64·n for animation strips), outline coverage, highlight pixels present, light-direction test (top-left brighter than bottom-right) |
| Custom 3D models | Blockbench-format Java models (`elements` with real depth) plus `.bbmodel` projects. `display` for `gui`, `ground`, `fixed`, `head` and all four hand slots. | ≥ 3 elements, depth > 1 px on every axis, all display slots present |
| Animated textures + emissive | Glowing parts are separate overlay elements with `light_emission: 15`, textured with an animated strip + `.mcmeta` (interpolated). | `.mcmeta` exists, frames are 64×64, model has an element with `light_emission` |
| LabPBR | `_n.png` (RG normal, B AO, A height) and `_s.png` (R smoothness, G F0/metal IDs 230/231, B porosity/SSS, A emission) for every texture and frame. | files exist, same size as albedo, valid channel ranges |
| Particles & sounds | Custom item particles, `sounds.json` entries + `.ogg` for every ability, hit and pickup. | every config preset references an existing sound event and particle model |
| item_model, no vanilla overrides | `assets/shardwatch/items/*.json` only. Nothing in `assets/minecraft/`. Chat sigils and GUI backgrounds use the custom fonts `shardwatch:sigils` and `shardwatch:gui`. | no file under `assets/minecraft/` |

## 5. Build order
1. Core → 2. Content → 3. GUI → 4. Progression → 5. Polish → 6. Admin. Each stage is its own commit, followed by a stage report
([stage-1](stage-reports/stage-1.md) · [2](stage-reports/stage-2.md) · [3](stage-reports/stage-3.md) ·
[4](stage-reports/stage-4.md) · [5](stage-reports/stage-5.md) · [6](stage-reports/stage-6.md)).
