To do
=====

Unfinished work, one section each. Delete a section when the work lands.

Deferred packs
--------------

`base-content/_plox_update/` holds four packs the parser skips because of the
leading underscore: `nether_crops`, `weapons`, `ultimatefist` and `SurvivalTweaks`.
`farming/crops/_wheatOverride.json` is skipped the same way. Every archetype and
property they use is ported, so bringing one in is mostly a content job:

- **Cut its sprite sheet** into one PNG per sprite named `<definition>[_<meta>].png`
  under `textures/`. Until then the emitter cuts them at load (sixteen across, tile
  size from the width), which works but leaves the pack depending on a sheet.
- **Drop the `.xid` file and the legacy `id`s**, and move the directory out of
  `_plox_update/`. Keep the `tex` indices, as every live definition does.
- **Update the acceptance run.** `acceptance/rig/session.py` copies each deferred
  pack in as a pack of its own, and `acceptance/checks/counts.py` quotes baselines
  with all four present. A pack that goes live is already in `base-content/`, so
  the copy goes and the baselines move.

Per pack:

- **`nether_crops`** has the blaze tree (`spikey`, refuses bone meal, and draws
  vanilla's fire as its leaves through `LegacyTerrain`), the zombie pigmelon stem and
  the crimson sickle. The sickle yields about 1.2 nether wart per ripe plant, against
  2–4 from breaking it by hand, so its only advantage is the 2% ether wart. It wants
  tuning before it goes live.
- **`weapons`** has three swords and three bows. A bow has one sprite and no drawn
  pull frames, so it gets a flat item model and does not animate while drawn.
  Drawing the three frames, and emitting vanilla's `using_item`/`use_duration`
  model for a bow that ships them, would close that.
- **`ultimatefist`** is the one `transforms` tool.
- **`SurvivalTweaks`** nerfs the wooden and stone pickaxes through `override`, culls
  stone tools for flint ones, and holds `_campfire.json`, which is the dropped
  `furnace` archetype. If that file is ever un-hidden, the cookpot recipe's
  `campfire` will resolve to its block instead of vanilla's. The wheat override's
  `blockBounds` cannot be carried, because an override patches the vanilla block
  rather than replacing it.

**The 2013 format has traps** that live content no longer shows:

- `name:count:meta` is how an item reference reads, but a `spawns` key splits as
  `name:meta`, so `foo:1` is a count in one place and a subtype in the other.
- A metadata suffix on a block reference (`netherStalk:3`) widens to every state of
  the block, because metadata is a blockstate now. The log says so.
- `"8+"` in a stage map means 8 is the last stage, not that sixteen exist.
- A bare number where a block is expected is a 1.6 numeric id (`LegacyBlockIds`).
- `material` was matched loosely in 2013 (`plant` fell through to ground), so
  plant-ness comes from `walkthroughable`, not from the material.
- A sprite index with no `texFile` addresses vanilla's 1.5-era `terrain.png`.
  `LegacyTerrain` holds only index 31 (fire); add the next one when a pack needs it.
- A recipe's ingredients are `content`. `contents` is rejected, as it was in 1.6.

Steel
-----

`base-content/_wip/steel/` is unfinished in 2013's own terms, not just unported:

- No sprites: its `xie_steel.png` sheet never existed.
- `toolMaterial` and `armorMaterial` on the ingots were stubs in the 2013 engine,
  and no tool or armour item was ever defined. The numbers are all there is, so the
  tool and armour sets are design work.
- The ores declare `smeltsInto`, which no version of the engine read. `cooked` is
  the property.
- Three recipes write `contents` for `content`, and the `greySteelBlock` recipe
  names a `greySteel` that does not exist.
- There is no `purpleSteelBlock`, and no block declares `drops`, so each one drops
  nothing when mined.

Smaller gaps
------------

- **Drop entries are not checked for unread keys.** The unread-property report walks
  a definition's own properties but not the `PropertyMap` inside a drop entry, so
  `{"chance": 20, "amuont": 2}` loses the amount in silence. Nothing in the content
  declares anything there but `chance`, `amount` and `min`.
- **A buried fruit leaf never ripening is not asserted.** The acceptance run checks
  the neighbour rule but not exposure.
- **`becomesWhenDestroyed` has no live content.** The salt deposit was its only user
  until it became a layer that breaks to air, so the engine property, and the
  client-side prediction that stops it flickering, are now exercised by nothing.
  The acceptance run would need a throwaway pack in `xiecontent/` to cover it.
