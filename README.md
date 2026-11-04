Xie's Mod
=========

A revival of Xie's Farming Mod from 2011: new crops, fruit trees, foods and a
cookpot, aiming for a vanilla feel. Every block, item and recipe is defined in plain
JSON files, so you can change any of it, leave parts out, or add content of your own
in the same format without writing any Java.

[The base content](base-content/README.md) describes what comes with the mod and how
to play with it: seed splicing, crops, fruit trees, salt, the food recipes, the
cookpot and cheese.

Installing
----------

Needs Minecraft 26.1.2 with NeoForge 26.1.2.94 or later. Put the jar in `.minecraft/mods/` on
both the client and the server, and give both the same content: blocks and items
are registered from it, so a client and a server that disagree cannot connect. The
server refuses such a client with a "Channel of mod" message showing the content
each side has, one name and fingerprint per pack; the log prints each pack's
fingerprint as it loads. Only definition files count: `.loc` names and textures may
differ, the way resource packs may.

**The first launch unpacks the content** into `.minecraft/xiecontent/base-content/`
as plain JSON. Change a food value, growth rate or recipe there and restart the
game. To leave something out, delete its file or put an underscore at the start of
the file or folder name.

**Updating never overwrites that folder**, so your edits are safe. To take new content
after an update, move `.minecraft/xiecontent/base-content` somewhere else and relaunch. The mod unpacks a fresh copy, and you can bring your edits back over.

**Worldgen is a data pack of its own**, "Xie's Mod worldgen". It is on by default, in
new worlds and in existing ones the first time they load with the mod, and only
chunks generated afterwards are affected. To leave it out, untick it under Data
Packs when creating a world, or run `/datapack disable "xiesmod_generated_worldgen"`.
Every crop and tree can still be grown from spliced seeds, and you can get salt by
smelting dried kelp.

Making your own content
-----------------------

Each folder in `xiecontent/` is a content pack. Put yours beside `base-content/`
rather than inside it, and taking new base content will never touch it. A pack is
a folder of `.json` definition files, plus a `textures/` folder and optional `.loc`
files of display names. Files and folders starting with `_` are skipped.

Note that in 2013 the mod used `.xie` files, but they were always just JSON, and the
schema hasn't changed. The mod will still read them, and they should work. Let me know
if they don't, I'll be happy to help fix it.

Every launch writes what the packs produce to `xiecontent/_generated/`, as a
resource pack and a data pack side by side, so you can open it and see exactly what
a definition became. It also writes `xie-content.schema.json` beside the packs.
Point a definition file at it and any editor that understands JSON Schema will
complete property names, describe them on hover, and flag a typo before the game
does.

```json
{
  "$schema": "../xie-content.schema.json",
  "blocks": {
    "tomatoBush": {
      "name": "Tomato Bush",
      "type": "bush",
      "growsOn": "farmland,dirt,grass",
      "fruit": {
        "tomato": { "chance": 50, "amount": 2, "min": 1 }
      },
      "drops": {
        "tomatoSeeds": { "chance": { "0": 0, "1": 20, "2": 40, "3+": 60 }, "amount": 3 }
      }
    }
  },
  "items": {
    "tomato": { "name": "Tomato", "type": "food", "hunger": 4, "nourishment": 0.3 },
    "tomatoSeeds": { "name": "Tomato Seeds", "type": "seeds", "growsInto": "tomatoBush" }
  },
  "recipes": { "tomatoSeeds": "tomato" }
}
```

A file can have five sections: `blocks`, `items`, `recipes`, `aliases` and
`spawns`. The schema follows each definition's `type`, so it offers a bush
`growthRate` and a cookpot `cookTime` and neither the other's. It does not forbid
anything, because another pack's archetype may have properties it has never heard
of.

### Archetypes

A definition's `type` picks its archetype. With no `type` it is `basic`.

| block | what it is |
|---|---|
| `basic` | A plain block. `walkthroughable` makes it something you walk through that breaks when its support goes, and `becomesWhenDestroyed` leaves another block behind. |
| `plant` | Grows through stages on the blocks in `growsOn`, at `growthRate`, and is broken to harvest. `growthHeight` lets it grow taller than one block. |
| `bush` | A plant that is picked instead: a right-click on a ripe one gives its `fruit` and sets it back three stages. Breaking it gives its `drops`. |
| `crop` | A plant that grows faster in a tended field, the way vanilla wheat does. |
| `sapling` | A plant that grows into the tree named by `growsInto`. |
| `tree` | Leaves on a vanilla-shaped tree, whose `trunk` is the log. Leaves with `fruit` ripen on the outside of the canopy and are picked by right-click. |
| `log` | A log, which keeps a tree's leaves alive. |
| `stem` | A melon-style stem that fruits into the block named by `grows`. |
| `ripening` | A solid block that ages where it stands and becomes `growsInto`, with no light or soil needed. |
| `stove` | The cookpot: a cauldron that cooks `ingredients`, `proteins` and `flavourings` into meals by right-click. |

| item | what it is |
|---|---|
| `basic` | A plain item. |
| `food` | Eaten or, with `action: "drink"`, drunk. `hunger`, `nourishment`, `potionEffect`, and `drops` for what is left in your hand. |
| `seeds` | Plants the block named by `growsInto`. |
| `tool` | `material` is the tier, `blocksEffective` and `canHarvest` what it mines, and `transforms` turns one block into another on right-click. |
| `sword` | `damage`, `uses`, `enchantability`, and `canBlock` to raise it like a shield. |
| `bow` | `ammo` to fire something other than arrows, `ammoless`, `ammoConsumed` for shots that can't be picked up, `semiautomatic`, and extra `power`, `punch` and `flame`. |
| `stick` | Right-click a block listed in `blocks` to harvest it where it stands. |

Any item may also declare `cooked` (what it becomes in a furnace, smoker and
campfire), `fuel` (a burn time), and `container` (what is left behind when it is
eaten or used in a recipe, like the bowl a salad comes in).

### Names

A definition is referred to by its key, `tomatoBush`. A definition with `names`
becomes one block or item per name, referred to as `name:count:subtype`, so
`fruitJuice:1:2` is one of the third fruit juice. Vanilla things go by their
modern id in camel case (`driedKelpBlock`) or by their 1.6 name (`beefCooked`).
Ids are made from display names, so `"Apple Seeds"` is `xiesmod:apple_seeds`.

### Drops

A drop's `amount` is not a stack size but a number of independent rolls, each
giving one item at `chance` percent. A `chance` keyed by growth stage is how a
plant gives more as it ripens: the tomato seeds above come at 20% a roll from stage
1 and 60% from stage 3, and `"3+"` means stage 3 and every stage after it. `min`
makes that many of the rolls certain wherever the chance is above zero, so each pick
above gives one tomato for certain and a second half the time.

A bush's `fruit` is what a right-click picks, and its `drops` are what breaking it
leaves, which is the place for its seeds. When a plant drops its own seed, keep
chance times amount below one at the early stages, so breaking what you just
planted gives back less than you put in.

### Recipes

The key is the product, with an optional `:count`:

- **Shapeless:** a string of ingredients, `"cornKernels:2": "cornCob"`.
- **Shaped:** `{"pattern": "XXX,YYY, Z ", "content": "sugar,cocoabeans,milk"}`.
  Rows are separated by commas, and each distinct character takes the next
  ingredient in `content`, in order of first appearance. A space is an empty slot.
- **Smelting:** `{"smelt": "milkBucket"}`. An item's own `cooked` property is the
  shorter way, and also gives smoker and campfire recipes when the result is food.
- **Several recipes for one product:** give each its own key and name the product
  with `"product"`, as in `{"product": "mayo", "content": "lemon,egg,oil"}`.
- **Removing vanilla recipes:** `{"cull": "redDye"}` removes every recipe that
  makes red dye, except this mod's own.

An **alias** is a group of interchangeable ingredients. `"aliases": {"protein":
"beefCooked,tofu"}` lets a recipe say `$protein`, and becomes the item tag
`#xiesmod:protein`. Aliases with the same name in different files are merged.

### Worldgen

A `spawns` entry places a block in the world: `block`, `biomes`, `rarity` (one
attempt in so many chunks), `clusterSize`, `clump` for a solid patch rather than a
scatter, and `spawnsOn`, `spawnsIn`, `spawnsUnder` and `spawnsNear` for what it
needs around it. Biomes are words such as `forest`, `savanna` or `swamp`, which map
to the common biome tags, so other mods' biomes are included. `!birchforest` leaves
a word's biomes out. A `tree` block spawns as its whole tree.

### Changing vanilla

`override` names a vanilla item or block that a definition speaks for. Nothing new
is registered: an item's properties are patched onto the vanilla item, and a
block's `drops` replace its loot table.

### Textures

Sprites go in `textures/blocks/` and `textures/items/`, one PNG each, named after
the definition. A block with growth stages has one per stage (`tomatoBush_0.png`,
`tomatoBush_1.png`, …), and a definition with `names` one per subtype
(`fruitJuice_2.png`). A block with both uses `<definition>_<subtype>_<stage>.png`.
An animated sprite takes a `.png.mcmeta` beside it, as in vanilla.

### What the log tells you

The log says how many blocks, items and recipes each pack produced, and names
anything that was not emitted: a recipe or drop naming something that doesn't
exist, a declaration that couldn't be read, and properties that nothing reads,
which is usually a typo.

Building from source
--------------------

Needs JDK 25. The Gradle wrapper is committed:

```bash
./gradlew :common:test
```

```bash
./gradlew :neoforge:runClient
```

| Path | What it is |
|---|---|
| `common/` | The content model and parser, with no Minecraft dependency, so it builds and tests in seconds. |
| `neoforge/` | The mod: registration, and the generated packs. |
| `base-content/` | The content that ships with the mod, bundled into the jar. |
| `acceptance/` | The acceptance run, which drives a real client and server. See [TESTING.md](TESTING.md). |
| `mod/` | The 2013 source, kept for reference and not built. |

Platform versions live in `gradle.properties`; bump `minecraft_version`,
`minecraft_version_range` and `neo_version` together. [TODO.md](TODO.md) lists
unfinished work, and [AGENTS.md](AGENTS.md) is for coding agents.

History
-------

Xie's Mod started in 2011 as a port of Zenith's discontinued sammich mod. It grew
into separate Farming, Cooking, Hunger and Food Stacking mods, then became one
engine that read all its content from text files, so players could write their own
packs, and some did. The last release was for Minecraft 1.6.4 in November 2013.

Thirteen years later vanilla still doesn't have sandwiches, so it has been ported to
modern Minecraft and NeoForge, with the content reworked to fit how the game plays
now: the Cooking Mod's stove is a cauldron-like cookpot, and fruit is picked from the
leaves instead of waiting for it to drop.

Licence
-------

The code is [MIT](LICENSE). The content in `base-content/`, textures included, is
[CC BY 4.0](base-content/LICENSE). The better textures were contributed by Thistle. The less good ones are by Xie.
