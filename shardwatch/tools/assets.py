"""
The asset list every stage is checked against. This is the "Look & textures" contract in code form.

kind:
  tool      handheld 3D item (extruded relief model, handheld display, glow overlay, tiers)
  badge     rank sigil item (3D, glow)
  icon      GUI icon (3D plaque, glow)
  particle  sprite used through Particle.ITEM (3D chip, glow)
  sprite    GUI sprite (tooltip nine-slice); 64x64, no model
  font      font bitmap (chat sigils, menu backgrounds); exempt from the 64x64 rule, see README
"""
from dataclasses import dataclass, field


@dataclass
class Asset:
    id: str
    kind: str
    stage: int
    title: str
    tiers: int = 1
    states: list[str] = field(default_factory=list)


TOOLS = [
    Asset('echo_lens', 'tool', 2, 'Echo Lens — block-history inspector', tiers=3),
    Asset('timeglass', 'tool', 2, 'Timeglass — rewind wand (drains on cooldown)', tiers=3, states=['drained']),
    Asset('verdict_gavel', 'tool', 2, 'Verdict Gavel — opens the Verdict menu on hit', tiers=3),
    Asset('petrify_prism', 'tool', 2, 'Petrify Prism — freeze toggle', tiers=3),
    Asset('veil_lantern', 'tool', 2, 'Veil Lantern — vanish toggle (lit/unlit)', tiers=3, states=['unlit']),
    Asset('flare_compass', 'tool', 2, 'Flare Compass — points at the oldest open Flare', tiers=3),
    Asset('glint_monocle', 'tool', 2, 'Glint Monocle — cycles through x-ray suspects', tiers=3),
    Asset('lustre_shard', 'tool', 2, 'Lustre Shard — reward item, redeem for Lustre'),
]

BADGES = [
    Asset('sigil_shardling', 'badge', 2, 'Shardling sigil (teal, one shard)'),
    Asset('sigil_prismkeeper', 'badge', 2, 'Prismkeeper sigil (pink, twin prisms)'),
    Asset('sigil_lumenwarden', 'badge', 2, 'Lumenwarden sigil (ice, crowned eye)'),
    Asset('sigil_crownfacet', 'badge', 2, 'Crownfacet sigil (rose-gold crown of gems)'),
]

ICONS = [Asset('icon_' + i, 'icon', 3, t) for i, t in [
    ('flare', 'Flare (report)'), ('flare_hacking', 'Flare category: unfair client'),
    ('flare_griefing', 'Flare category: griefing'), ('flare_chat', 'Flare category: chat abuse'),
    ('flare_exploit', 'Flare category: exploit'), ('flare_other', 'Flare category: other'),
    ('chip', 'Verdict: Chip'), ('hush', 'Verdict: Hush'), ('eject', 'Verdict: Eject'),
    ('encase', 'Verdict: Encase'), ('petrify', 'Verdict: Petrify'), ('ledger', 'Ledger'),
    ('rewind', 'Rewind'), ('glint', 'Glint'), ('facet', 'Facets / roster'), ('scope', 'Shardscope'),
    ('lustre', 'Lustre'), ('refine', 'Refinement (upgrade)'), ('keepsake', 'Keepsake (reward)'),
    ('locked', 'Locked'), ('confirm', 'Confirm'), ('cancel', 'Cancel'), ('prev', 'Previous page'),
    ('next', 'Next page'), ('close', 'Close'), ('filter', 'Filter'), ('info', 'Info'),
    ('settings', 'Settings'), ('clock', 'Duration'), ('pane', 'Background filler'),
]]

PARTICLES = [
    Asset('particle_shard', 'particle', 5, 'Crystal shard particle (pink)'),
    Asset('particle_spark', 'particle', 5, 'Spark particle (teal)'),
    Asset('particle_mote', 'particle', 5, 'Glowing mote particle (ice)'),
]

SPRITES = [
    Asset('tooltip/crystal_background', 'sprite', 3, 'Tooltip background (nine-slice)'),
    Asset('tooltip/crystal_frame', 'sprite', 3, 'Tooltip frame (nine-slice)'),
]

FONTS = [
    Asset('font/menu_6', 'font', 3, 'Six-row menu background glyph'),
    Asset('font/menu_3', 'font', 3, 'Three-row menu background glyph'),
    Asset('font/sigils', 'font', 4, 'Chat sigil glyphs (ranks + Keepsake sigils)'),
]

MODEL_ASSETS = TOOLS + BADGES + ICONS + PARTICLES
ALL = MODEL_ASSETS + SPRITES + FONTS

# Sound events (assets/shardwatch/sounds.json) — one for every ability, hit, pickup and UI action.
SOUNDS = {
    5: [
        'ui.click', 'ui.open', 'ui.page', 'ui.deny', 'ui.confirm',
        'flare.send', 'flare.alert', 'flare.claim', 'flare.resolve',
        'verdict.chip', 'verdict.hush', 'verdict.eject', 'verdict.encase', 'verdict.petrify', 'verdict.release',
        'gavel.hit', 'echo.inspect', 'rewind.start', 'rewind.tick', 'rewind.done',
        'glint.alert', 'veil.on', 'veil.off', 'compass.warp', 'monocle.focus',
        'lustre.pickup', 'lustre.levelup', 'refine.upgrade', 'keepsake.claim', 'facet.promote',
        'tool.equip', 'hum.ping',
    ],
}

DISPLAY_SLOTS = ['thirdperson_righthand', 'thirdperson_lefthand', 'firstperson_righthand', 'firstperson_lefthand',
                 'gui', 'head', 'ground', 'fixed']
