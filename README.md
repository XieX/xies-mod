xies-mod
========

Xie's content engine for Minecraft. Define blocks, items and recipes in plain text
files rather than in Java.

Currently being ported from Minecraft 1.6.2 (2013) to NeoForge.

Layout
------

| Path       | What it is |
|------------|------------|
| `common/`  | The content model and parser. No Minecraft dependency at all, so it compiles and tests in seconds. |
| `neoforge/`| The mod itself: entry point, registration, pack emission. Compiles `common/`'s sources into the jar. |
| `mod/`     | The original 1.6.2 source tree, kept for reference. Not part of the build. |
| `acceptance/` | The acceptance run: one command that starts a server, joins a client and asserts the behaviours a datapack cannot show. |

Content lives in a separate repository so that packs can be written without using
any of this one's content. The parser expects it beside this checkout at
`../mod-content`, and at runtime reads packs from `<gamedir>/xiecontent/`.

Building
--------

Needs **JDK 25** (Minecraft 26.1+ ships Java 25 to players, so mods target it).

The Gradle wrapper jar is not committed yet. Generate it once with a system
Gradle 9.2.1, or let your IDE do it when it imports the project:

```bash
gradle wrapper --gradle-version 9.2.1
```

Then:

```bash
./gradlew :common:test
```

```bash
./gradlew :neoforge:runClient
```

Target platform versions live in `gradle.properties`. NeoForge for Minecraft 26.2
is still beta, so the build targets the current stable 26.1.2 line; bump
`minecraft_version`, `minecraft_version_range` and `neo_version` together.

Verifying
---------

Two commands, and between them they cover both halves of what this mod does.

```bash
./gradlew :common:test
```

The parser and the content model, in seconds, with no Minecraft anywhere near it.

```bash
python acceptance/run.py
```

The **acceptance run**: it starts the dedicated server, joins a real client to it,
and asserts the behaviours only a running game can show — a stem fruiting, a tree
shedding, a sapling growing, a canopy that stays up on a trunk this mod invented,
bone meal being refused, the sickle, the Ultimate Fist, the bows, a craft remainder,
a block that comes away with its soil, and the culled recipes. It prints pass or fail per assertion and exits non-zero on any
failure, so it can be left alone. Add `--list` to see the checks, `--only <name>`
to run one, `--no-client` for the subset rcon alone can answer.

It needs Python 3 and a Windows desktop whose foreground it can take, because a
`useOn` is a real right-click at a real window and there is no command for it. It
arranges what it needs — the five deferred packs, its own flat world, and
`pauseOnLostFocus` — and puts all of it back afterwards. See
[TESTING.md](TESTING.md) for what each check is measuring, why it is measured that
way, and what driving Minecraft from a script turns out to cost.

Format
------

A content pack is a directory of `.json` definition files, plus optional `.loc` display
names and a `textures/` tree. Files and directories starting with `_` are skipped.
The `.xie` extension the format wore for its first decade is the same JSON under
another name, and still loads; there is no reason to rename a working pack.

Every launch writes `xie-content.schema.json` into `<gamedir>/xiecontent/`, beside
the packs. Point a definition file at it and any editor that understands JSON
Schema will complete property names, describe them on hover and flag a typo before
the game does:

```json
{
  "$schema": "../xie-content.schema.json",
  "blocks": {
    "tomatoBush": {
      "name": "Tomato Bush",
      "type": "bush",
      "growsOn": "farmland,dirt,grass",
      "drops": {
        "tomato": { "chance": { "0-3": 0, "4+": 50 }, "amount": 2 }
      }
    }
  },
  "items": {
    "tomato": { "name": "Tomato", "type": "food", "hunger": 4, "nourishment": 0.3 }
  },
  "recipes": { "tomatoSeeds": "tomato" }
}
```

`.xid` files assigning numeric block and item ids are still parsed, but the ids are
ignored — Minecraft dropped numeric ids in 1.7.

Port status
-----------

- [x] Gradle build targeting NeoForge
- [x] Content model and parser, with tests over the real content repo
- [x] Registrar: definitions to `DeferredRegister` (every block and item archetype the content uses)
- [x] Emitter: generated resource pack (blockstates, models, textures, lang)
- [x] Emitter: generated datapack (loot tables from `drops`, recipes, alias tags)
- [x] Multi-variant definitions (`names`) as one registered object per subtype
- [x] Perennial harvest on `bush`, following vanilla's sweet berry bush
- [x] Annual `crop` with vanilla's farmland-adjacency growth bonus, and `growthHeight`
- [x] `basic` blocks — `becomesWhenDestroyed`, `walkthroughable`, `textureBase`
- [x] Worldgen from `spawns` — configured features, placed features, biome modifiers
- [x] Fruit trees — `sapling` and `tree`, with `sheds` and `shedSaturation`
- [x] Food as data components — `container`, `action`, `potionEffect` and a food's `drops`
- [x] `cooked` as furnace, smoker and campfire recipes; `fuel` as furnace burn times
- [x] `cull` recipes, as a pass over the recipe JSON as it loads
- [x] `tool` and `sword` as data components — `damage`, `uses`, `enchantability`,
      `canBlock`, and `blocksEffective` as a generated block tag
- [x] Vanilla overrides — `override` as components patched onto a vanilla item and a
      loot table written at a vanilla block's own id
- [x] `bow` — `ammo`, `ammoless`, `ammoConsumed`, `semiautomatic`, `power`, `punch`
      and `flame`, as the port's first item class
- [x] `transforms` — the one `tool` property that is behaviour rather than
      description, replacing the block you right-click
- [x] `log` and `stem` — a rotated pillar and a fruiting stem, both of which turned
      out to be vanilla's own blocks
- [x] `stick` — right-click a listed block and harvest it where it stands
- [x] `seed`, which is `seeds` spelt in the singular and always was
- [x] `spikey` and `fertilisable` — a block that hurts what stands in it, and a plant
      that refuses bone meal
- [x] Every property the content declares and nothing reads, counted and named in the log
- [x] An acceptance run — one command that drives a real client into the dedicated
      server and asserts the behaviours only a running game can show
- [x] `stove` — the cookpot, a cauldron over a campfire that turns water, any of a
      dozen ingredients and a seasoning into soup or stew, fed by right-click. The
      port's first block entity, rebuilt from the mod posting rather than from a file

Seventeen distinct archetypes appear across the content repo, deferred packs included,
and sixteen of them register. The one that does not is `furnace`, which is content
this port dropped rather than an archetype it cannot build — see the table below. See
[PORTING.md](PORTING.md) for the full breakdown and the version-specific gotchas
worth not rediscovering.

Content superseded by vanilla
-----------------------------

Minecraft has gained equivalents to several things this mod added in 2013, so they
have been dropped rather than shipped as worse duplicates:

| removed | vanilla equivalent |
|---------|--------------------|
| `campfire`, `ashpile` | Campfire (1.14) |
| `fertiliser`, `potash` | Composter and bone meal (1.14) |
| `pumpkinPiece`, `pumpkinCooked` | Pumpkin pie |
| `glowberryBush` | Glow berries on cave vines (1.17) |
| `melon` override | Vanilla melon mechanics |
| `foragingStick` | Superseded by chest loot and villager trades |

The sibling Food Stacking and Hunger mods are obsolete outright — food stacks to 64
in vanilla, and hunger has been a core mechanic since Beta 1.8.

Vanilla has still never added fruit trees, salt, tofu, or sandwiches.

The generated pack is written to `<gamedir>/xiecontent/_generated/` on every launch
rather than served from memory, so you can open it and see exactly what a
definition file produced. One directory is registered twice, as a resource pack and as a
datapack, so `assets/` and `data/` sit side by side in it.

The three growing archetypes share one class, as they did in 2013. `plant` is both
an archetype in its own right and the base `bush` and `crop` extend, and it holds
everything that does not depend on which of the three you are: the growth stage
property, the per-stage collision boxes, where the block may be planted, how tall it
stacks, and bone meal. A bush is a plant that regrows when you pick it instead of
being destroyed; a crop is a plant that grows faster on well-tended farmland. That
is the whole difference, and it is one overridden method each.

Fruit trees are the mod's most distinctive content — vanilla still has none — and
they cost less code than anything else in it. The 2013 engine carried three tree
generators, and all three were Minecraft's own oak, spruce and pine copied out of
vanilla, so a `tree` is emitted as a `minecraft:tree` configured feature and vanilla
places it, for worldgen and for a growing sapling alike. Leaf decay is vanilla's too.
What is left is the part vanilla has no equivalent for: a planted tree drops its
fruit on the ground, from one leaf just above the trunk, and stops once eight of them
are lying there.

Worldgen is the clearest case of the port deleting code rather than moving it. In
2013 a generator ran on every chunk populate and hand-placed blocks; now a `spawns`
entry is three JSON files — what to place, where to place it, and which biomes to
add it to — and vanilla's own generator runs them. The engine has no worldgen code
at runtime at all. It is also what finally makes the wild shrubs and the salt
deposits reachable: they have no seed and no item, so generating in the world is the
only way they were ever meant to be found.

The one thing that did not survive the translation is biome names. 1.6 had no biome
ids, so a pack said `"biomes": "swamp"` and the engine matched that against the
biome's display name, which caught other mods' swamps by accident. Biome tags are
the modern form of that accident, so a biome word maps to a `c:` tag wherever one
exists — a mod that tags its own swamp gets wild tomatoes without either side
knowing about the other. Two words have no modern counterpart at all: `marsh` and
`shrubland` were biomes from the mod packs of the day, and nothing tags anything as
either.

Aliases are the clearest win from the port. `$salady` used to be expanded into the
cartesian product of concrete recipes at load time, turning one sammich recipe with
three alias slots into hundreds of registered ones. It is now a single item tag and
the recipe stays a single recipe.

Multi-variant definitions are the other place the two versions of Minecraft
disagree structurally. A 1.6 id carried up to sixteen subtypes told apart by a
damage value, declared in one go with a `names` list and referred to as
`fruitSeed:1:2`. Damage-value subtypes are gone, so each entry in `names` becomes
its own registered object. Its id comes from its own display name rather than from
`fruitSeed_2`, because the content already agrees with itself on those names: the
aliases packs declare for their subtypes reduce to the same id, so `appleSeeds` and
`"Apple Seeds"` both give `apple_seeds` and a reference written either way lands on
the same item. Where a subtype has no usable display name the id falls back to
`<definition>_<meta>`. Nothing is renamed to match an alias that disagrees —
`fruitLeaves` is aliased as `appleTree` but its subtypes are named "Apple Leaves",
so the block is `apple_leaves` and `appleTree` stays an item tag.

`cull` is the one property that removes rather than adds, and the one that leaves
nothing in the generated pack. It names the output of a recipe and deletes every
recipe producing it, which is a question no datapack can ask — a pack replaces a
recipe by id, and a cull knows no ids. So it stays what it always was, a pass over
the recipes: NeoForge hands the raw recipe JSON to every mod once it has been read
off every pack and before any of it is deserialised, and anything producing a culled
item is dropped there. Recipes this mod generated are spared, because the content
that culls a thing is the content that replaces it — the flower bushes cull red dye
so that red dye has to come from rose seeds, and a poppy in a crafting grid now
yields the seeds instead.

`override` is the other property that touches vanilla, and it is the one whose 2013
meaning did not survive at all. It was an id-collision escape hatch: a definition
whose numeric id was already taken could claim it anyway and evict whatever was
sitting there. Numeric ids are gone, but the name the property carries was never the
accidental part — a definition that overrides `crops` goes on to describe a wheat crop,
and one that overrides `pickaxeWood` goes on to describe a deliberately worse wooden
pickaxe. So it now means that the definition speaks for that vanilla object: nothing
new is registered, and what the definition declares is applied to the object that
already exists. An item's properties are data components patched onto it; a block's
drops are a loot table written at the block's own id, which is the one kind of
override a datapack can state outright. What needs the block or item to be a
*different* object — the wheat override's collision boxes, and nothing else in the
live content — is named as a loss rather than implied away.

The `bow` is the one archetype that ends the port with more code than it started
with. Everything else described an item and modern Minecraft has words for the
description; a bow takes decisions while it is being drawn, and no component takes a
decision. Even so, two thirds of the 2013
implementation turned out to be in the game already. The custom arrow entity existed
only to remember what it was fired from and give that back when picked up, which is
a field on every vanilla arrow now — so a crossbow firing redstone drops redstone
without a line of code. And `power`, `punch` and `flame` were written in 2013 as
numbers *added to* an enchantment level, so they are applied as extra enchantment
levels — on a copy of the bow that lives inside the arrow, which is why the bow in
your hand has no glint, no tooltip line and nothing an anvil could move elsewhere.
What is genuinely new is three properties' worth of firing logic: a bow that needs
no ammunition, a bow whose ammunition is not arrows and cannot be recovered, and a
`semiautomatic` one that — despite the name, and as its own readme says — is fully
automatic, firing on every click without ever charging.

The most repeated finding of the whole port is that a 2013 class which looks like an
implementation is usually Minecraft's own class with a name on it. It has now
happened five times: the three tree generators, leaf decay, the custom arrow entity,
the log block and the stem block. The tell is always the same — a three-hundred-line
file whose class body proper is under ten lines, with the rest marked
`// copy-pasta from XieBlock`. A `log` is a rotated pillar that joins
`#minecraft:logs`, which is simultaneously what made it mineable with an axe, what
made it sustain leaves, and what keeps a fruit tree's canopy from decaying off a trunk
this mod invented. A `stem` needed no class at all: vanilla's own melon stem takes
what it fruits into as a registry key looked up at the moment it fruits, so a stem
that grows a block this mod registered is something vanilla could already say. What
the stem did cost was a second registered block for the bent half — in 1.6 that was
worked out at render time, and Minecraft has spent a blockstate on it since 1.8.

`stick` is the third piece of behaviour, after the bow and `transforms`, and adds
least because it follows from both: right-click a listed block and it plays a crunch,
drops what the definition says that block gives up — not what the block itself drops —
and two times in three clears it away. The crimson sickle reaps nether wart with it.
It also turned up the one genuine bug in the deferred content: the sickle asks for
`nethwerwart`, and in 2013 a single unresolvable name failed the whole item, so the
crimson sickle has never worked in any version. Here the bad name is dropped, counted
and named in the log, and the rest of the item works.

`transforms` is the only other thing in the port that needed a class, and for the
same reason at a smaller scale. A `tool` is otherwise five data components and no
code; this one property replaces the block you right-click — stone becomes a gold
block, dirt becomes air — and nothing describes that, so the one definition declaring
it gets an item class and the other eight go on dissolving completely. Vanilla writes
the method twice already: tilling a block into farmland with a hoe and flattening one
into a path with a shovel are the same shape, down to the guard that says a click on
the underside of a block does nothing. Everything else it does is 2013's, including
the odd part, which is that the sound you hear is the block that appeared rather than
the one that was destroyed.

Anything a recipe or drop names is checked against the registry before it is
written, so the parts of the content whose archetypes are not ported yet are left
out rather than emitted as JSON that fails to load. The counts in the log say how
much that is.

Those counts are checked every time the server starts, which left an odd gap: every
number was verified constantly and every *behaviour* exactly once, by hand, and then
never again. A datapack can be validated by loading it; a stem fruiting, a tree
dropping its fruit, bone meal being refused and a sickle spending durability cannot.
So `acceptance/run.py` starts the server, joins a real client to it, and asserts them
— sixty-nine assertions in under three minutes, pass or fail per assertion, non-zero
exit on failure. It exists because there is no other way to ask: no command right-clicks a
block, so a `useOn` needs synthetic input at a focused game window, and nothing
random-ticks or scheduled-ticks at all unless a player is standing there, forceloading
or no forceloading.

It earned itself on the first day, on a tree that was supposed to stop shedding once
eight of its apples were lying under it and turned out to shed a ninth about two times
in five. That is the kind of failure verifying a behaviour once cannot find: watching
a tree shed proves that it sheds, and watching it stop proves nothing until you have
watched it fail to stop. [TESTING.md](TESTING.md) has the twelve checks, what each one
is really measuring, and the rules they are written to.

Every archetype the content uses is now ported, which changes what is left to find:
not a missing kind of block, but a *property* the engine reads nothing from. Those had
been turning up one at a time, by reading a definition file next to the registrar and
noticing. They are counted now — the parser has always tracked which properties
anybody read, and at the end of pack emission the ones nobody did are listed with the
definitions that declared them. Running it for the first time found three consumers
that were reading a property without recording it, and then four genuine cases, of
which none is a loss this port introduced: three are things 1.6 read nothing from
either, and the fourth is a decision documented in PORTING.md. The steel pack, which
had never been run before, turned out to declare custom tool and armour materials that
the 2013 engine parsed into a method whose body is `// TODO FTFESAD` — so the numbers
were never used by anything, and there is nothing to port but the intention.

That report had a blind spot of its own, and it has been closed. A property the parser
turns into a structure of its own — what a block drops, what a tree sheds, the blocks a
sickle reaps, a collision box, a texture assignment — was marked read the moment the
parser read it, which said nothing about whether any archetype then wanted the value.
That is how a fruit tree's collision box went missing in silence for as long as it did:
it parsed perfectly and nothing asked for it. The mark moves to the point of use now, so
the question is whether a registrar or an emitter consulted the definition, and the log
distinguishes a property nothing reads from one that parses and is then consulted by
nobody — different gaps, in different files. Auditing every archetype against the 2013
source for each of those structures first, then wiring it up, found four places where
the port consults less than 1.6 did and no content in the repo that reaches any of them.
The mechanism was proved against a throwaway pack written to trip each one.

The same pass closed the one gap where content could go missing from every count at
once. A declaration the reader refuses is in none of the lists it returns, so it is in
none of the totals the registrar and the emitters report either; three of the steel
pack's six recipes misspell `content` as `contents`, which failed the job outright in
1.6 as well, and the pack reported one recipe not emitted while four were gone. The
reader counts what it rejects now and the load summary says so.

Textures need no migration: the definition files still address spritesheet indices, but
`textures/` is already one PNG per sprite named `<definition>[_<meta>].png`. A pack
that never migrated falls back to the indices, and the emitter cuts the sprite out
of the sheet — sixteen across, tile size from the width, which is what an `Icon`
index meant. Blocks fall back the same way as items, so a pack that names one sheet
and a `sides` and an `ends` per block gets its faces cut out of it. A file always
wins, so a pack shipping both is unaffected.

An index with no sheet named at all is not a broken declaration either. 1.6 answered a
definition that named no `texFile` with `/terrain.png`, so the number addresses
vanilla's own sheet: `LegacyTerrain` translates it into a modern texture id and the
model points straight at Minecraft's sprite rather than a copy of it. That is how
`blazeTree` gets leaves of fire — index 31 was `Block.fire` — and keeps them animated.
The table holds what the content addresses, and an index it has no answer for is named
in the log rather than guessed at.

A block that has an item form has no item sprite to draw, and vanilla answers that two
ways: a sapling in an inventory slot is its block texture on a flat model, and a melon
is its block model shown as a cube. The emitter picks between them by the block's shape
now. Doing it one way for both worked for a block with a single texture and named a
sprite that does not exist for one with a face per side — found by running two such
blocks side by side for the first time.
