Porting status
==============

Tracks what is left to move from the 1.6.2 engine (`mod/`, kept for reference) onto
NeoForge. The 2013 source is the specification: when behaviour is unclear, read it
rather than inventing something.

Working today
-------------

The whole pipeline runs end to end. Content parses during mod construction, blocks
and items register before the registries freeze, and a resource pack and datapack
are generated to `<gamedir>/xiecontent/_generated/` on every launch.

Seventeen distinct `type` values appear across every definition file in the content repo,
deferred packs included. **Sixteen of them register.** The one that does not is
`furnace`, in `SurvivalTweaks/_campfire.json`, and it is content this port dropped
rather than an archetype it cannot build — vanilla gained a campfire in 1.14. See
the table at the end of the README. The seventeenth is `stove`, the one archetype
with no 2013 file behind it: the cookpot in `cooking/cookpot.json` was rebuilt from
the mod posting, and is described under [The cookpot](#the-cookpot).

That is worth stating that way round, because "every archetype the content uses" was
true of the *live* content long before it was true of all of it, and the two are easy
to confuse. Two more distinctions are worth keeping straight:

- **A supported archetype is not the same as wanted content.** `foragingStick` is a
  `stick`, which now registers, but it lives in `farming/misc/_foragingrod.json` and
  the leading underscore means it is never read at all. It stays dropped.
- **Half the 2013 archetypes are not in this repo to port.** `chest`, `grass`,
  `mounted`, `facing`, `pane`, `fence` and `glass` are archetypes `JobBlock`
  dispatched on that nothing here declares. The Cooking Mod content that would need
  most of them did not survive; its stove is the one piece rebuilt from the mod
  posting rather than ported from a file, and the rest stays dropped — see the last
  section of this file.
- **Every archetype ported is not every property carried.** With all sixteen accounted
  for, what is left to find in unfamiliar content is per-*property* rather than
  per-archetype: a declaration the port reads nothing from, or reads and then never
  uses. Those had been found by hand until now, one definition file at a time; both kinds
  are counted in the log as of this session, and the log says which kind each one is.
  See [Properties nobody reads](#properties-nobody-reads) and [Structures that parse and
  go nowhere](#structures-that-parse-and-go-nowhere).

From the 17 blocks and 53 items in the live content repo:

| | registered | notes |
|---|---|---|
| blocks | 17 of 17 definitions, 25 objects | `fruitLeaves` alone splits into four |
| items | 68 of 53 definitions | multi-variant `names` split into one item each, plus five sapling block items and the cookpot's |
| recipes | 111 | 99 of the 101 in `recipes` sections, plus 12 from `cooked`; the other 2 are `cull` and remove rather than add |
| loot tables | 25 | per-age drop chances as `block_state_property` pools |
| alias tags | 15 | `$protein` became `#xiesmod:protein`, so one recipe replaces hundreds |
| worldgen | 9 of 10 `spawns` | the 1 left is the dropped melon |
| tree features | 5 | a `minecraft:tree` configured feature per `tree` block |
| burn times | 8 | `fuel`, as one entry each in NeoForge's furnace-fuel data map |

The growing archetypes share one class, as they did in 2013. `XiePlantBlock` is both
the concrete `plant` archetype and the base the other two extend, mirroring
`XieBlockPlant`; it owns the age property, the per-age boxes, `growsOn`, the growth
roll, `growthHeight` and bone meal. The subclasses differ in exactly one method each:

- **`bush`** is perennial. Right-click a ripe bush and it drops its fruit and falls
  back three growth stages. This follows `SweetBerryBushBlock`, which arrived in 1.14
  with the same mechanic — matching Mojang's idiom is what makes it feel native, and
  it works identically in creative, which a break-based implementation does not.
- **`crop`** is annual — break it and replant from seed — and divides its growth roll
  by vanilla's farmland-adjacency bonus, so a crop in a tended field grows several
  times faster than the same crop alone on dry soil.
- **`basic`** grows at all, so it is not in that hierarchy: it is a plain `Block` with
  `becomesWhenDestroyed`, `walkthroughable` and `textureBase`, and no `age` property.

One thing a `basic` block does that the original's did not: if it is
`walkthroughable` it breaks, and drops, when what it stands on is taken away. That
matters because `shrub` declares no `type` and so is `basic` rather than `plant` —
1.6 only ever checked support for `BlockBush` and its descendants, so a shrub whose
dirt you mined stayed floating in 2013 too. Faithful, and it reads as a bug: every
vanilla plant since Alpha comes away with its soil. `saltDeposit` is untouched by
this, being terrain rather than something you walk through. See
`XieBasicBlock.canSurvive`.

Five classes that were already in the game
------------------------------------------

The single most repeated finding of this port, collected in one place because it has
now happened five times and the next archetype should be read with it in mind. **A
2013 class that looks like an implementation is usually 1.6's own vanilla class with
a name on it.** In order of discovery:

1. **`TreeGenerator`'s three generators** are `WorldGenTrees`, `WorldGenTaiga2` and
   `WorldGenTaiga1`. All three survive as configurations of `minecraft:tree`, so the
   file became JSON — see [Fruit trees](#fruit-trees).
2. **`XieBlockLeaves.updateTick`** is `BlockLeaves`' flood fill, verbatim. `LeavesBlock`
   keeps a `distance` property and reaches the same answer without the array.
3. **`XieEntityArrow`** is `AbstractArrow`. Its whole reason for existing — remember
   the item you were fired from, hand it back on pickup — is `pickupItemStack`.
4. **`XieBlockLog`** is `BlockLog`, which is `RotatedPillarBlock`.
5. **`XieBlockStem`** is `BlockStem`, which is `StemBlock` and `AttachedStemBlock`.

The tell is the same every time: a three-hundred-line file whose class body proper is
under ten lines, with the rest marked `// copy-pasta from XieBlock` or silently
duplicated from it. **Read the class body before the file length.** Both of the
archetypes below were found this way, and between them they cost one small block class
and no new behaviour at all.

Logs and stems
--------------

The last two block archetypes, and the two that needed the least.

**`log` is a `RotatedPillarBlock`.** `JobBlock`'s branch for it is four lines —
construct, set a wood step sound, expand the common parameters — and `XieBlockLog`'s
constructor is four calls: hardness 2.0, flammability 5/5, effective against every
axe, and `canSustainLeaves`/`isWood` both true. Every one of those is what a modern
log already is, and three of the four arrive together:

- **`#minecraft:logs` is the whole of `canSustainLeaves`, `isWood` and the axes.**
  A generated tag entry puts the block in it, `#minecraft:mineable/axe` contains that
  tag, and `LeavesBlock` measures its `distance` from it — which is not a nicety but
  the thing that keeps a flame tree standing, because `XieTreeBlock` *is* a
  `LeavesBlock` and a canopy on an untagged trunk decays the moment it is placed. The
  tag is additive rather than an override, so it goes in the content pack and being
  below vanilla costs it nothing. That is asserted rather than reasoned now: the
  `decay` check in [the acceptance run](TESTING.md#the-acceptance-run) stands the same
  canopy on a `blazeLog`, on a vanilla log and on a block in no tag at all, and watches
  which of the three loses it.
- **Flammability is the one that does not.** `FireBlock.bootStrap` names each log
  block outright instead of reading the tag, so 5/5 goes on the block through
  NeoForge, exactly as `XieTreeBlock` puts its leaves' 30/60.

What is left is one small class, for two properties of the generic boilerplate a log
can still be handed. `becomesWhenDestroyed`, which no log declares and which costs one
field to keep rather than lose in silence — shared with `XieBasicBlock` now, since
both were copying the same 1.6 method. And `blockBounds`, which `blazeLog` uses to be
a four-pixel trunk rather than a full one, and which brought two things with it:

- **The box rotates with the axis**, which the original's fixed `AxisAlignedBB` did
  not. 1.6 kept the axis in the same metadata it read nothing else from, and a tree
  only ever places an upright trunk, so no log in the content was ever drawn sideways
  for the difference to show.
- **A cube block that does not fill its cube is now drawn that way.** 1.6's standard
  block renderer drew the block's own AABB, so `blockBounds` was its shape on screen
  as much as its collision box. `minecraft:block/cube` cannot say that, so such a block
  gets a model with an element of its own and `noOcclusion` — which is
  `XieBlock.setBlockBounds` calling `setIsOpaque(false)` for exactly that case. No live
  content reaches it: every `blockBounds` in the live content is on a cross or a crop,
  where the box is the selection outline and the model is flat quads, which is what
  vanilla's own wheat does too.

**`stem` needs no class at all**, which no other archetype in the port has managed.
`XieBlockStem`'s class body is three methods — `fertilizeStem` calls `super` and asks
for a render update, `getPlantType` returns `plantType`, `canThisPlantGrowOnThisBlockID`
answers `growsOn` — and `StemBlock`'s constructor takes exactly the four things a
definition can vary. Four decisions the translation needed:

- **`grows` is the property no datapack can express, and vanilla expresses it.** A
  stem fruiting into a block of this mod's own is the whole point of the archetype,
  and `StemBlock` holds its fruit as a `ResourceKey` looked up from the registry at
  the moment it fruits — so it may name a block that does not exist yet. That is the
  same lazy resolution `XieTreeBlock` and `XieToolItem` each had to write by hand,
  already written.
- **The attached stem is a second registered block**, because in 1.6 it was not a
  block at all: `BlockStem` looked for its fruit among the four neighbours at render
  time and drew itself leaning towards it. Vanilla has spent a blockstate on that
  since 1.8. So one definition becomes two registrations — the same split `names`
  already makes for a different reason — and the second takes the first's name with
  "Attached" in front of it, which is vanilla's own wording.
- **`growsOn` becomes a generated block tag, and `plantType` is the same thing said
  in one word.** `Xie.parsePlantType` matched Forge's `EnumPlantType`, whose whole
  effect was to pick a soil; `PlantTypes` is that table. `zombiePigmelonStem` declares
  only `plantType: nether`, which is what makes it a *nether* crop, and taking
  `growsOn` first when both are given is not a guess about which one 1.6 consulted —
  `canThisPlantGrowOnThisBlockID` answered `false` for an empty list, so a definition
  declaring neither would be unplantable everywhere, and `plantType` is plainly what
  it uses to say where it grows. Two of the seven constants are not carried and cannot
  be: `Cave` asked whether the block below had a solid top face and `Beach` asked for
  water within one block, and a block tag cannot ask a question. Nothing declares
  either.
- **The fruit stands on the same ground the stem does**, and this is the one decision
  in the archetype that nothing but a running game could confirm — it was reasoned out
  of `StemBlock` and verified only by the datapack loading. It is asserted now, both
  ways round: [the acceptance run](TESTING.md#the-acceptance-run) watches a ripe stem
  fruit onto soul sand and bend towards it, and watches an otherwise identical stem
  whose neighbours have stone under them ripen forever. Vanilla keeps two tags here —
  a melon stem grows on farmland and its melon lands on dirt — but 1.6 hardcoded the
  fruit's soil to tilled earth, dirt and grass, which is the untilled form of the very
  ground the stem was standing on. They were one list written twice. A definition
  saying its stem grows on soul sand is therefore saying where its fruit sits, and
  with anything else the zombie pigmelon ripens forever and never fruits at all.

**A stem is drawn with vanilla's melon stem, tint included.** `zombiePigmelonStem`
writes `"tex": "#melonstem"` and ships no sprite, and a stem is not a shape a texture
can be pasted onto — it is eight hand-built models of crossed quads that lengthen as
the plant grows, plus a ninth that bends towards the fruit. So the blockstate points
straight at vanilla's nine. Their green is a tint rather than a sprite, and that is
the one thing in the port decided on the client: `XieBlockColors` registers vanilla's
own `BlockTintSources.stem()` for every registered stem, without which they draw grey.
It does not contradict leaves being emitted untinted — `enableColorisation` defaulted
off in 2013 and no definition turns it on, so a leaf has no tint to reproduce, whereas
a borrowed model brings its tint with it.

**A block that drops itself now gets an item form**, which until now only a sapling
had. It is the narrowest rule that works: a definition saying it drops itself has
asked for something that cannot exist without an item, so leaving the item out turns
the declaration into a drop nobody could ever be handed. `zombiePigmelon` is the
melon's case — a block a stem fruits and a player breaks, which is neither of the two
ways every other Xie block is obtained — and it is also what makes
`"cookedPorkchop:4": "zombiePigmelon"` a recipe rather than a skip. In 2013 the
question never arose, because `ModLoader.registerBlock` gave every block an
`ItemBlock` whether anything needed one or not. Only `cheeseBlock` and
`zombiePigmelon` declare it — the first live since cheese was ported, the second
still in a deferred pack.

Fruit trees
-----------

The last two archetypes, and the mod's most distinctive content: vanilla still has
no fruit trees thirteen years on. `sapling` and `tree` are a pair — a sapling grows
into a tree — and between them they cover `fruitSapling` and `fruitLeaves`, which is
eight registered blocks once `names` splits them four ways. The 2013 pack had a
cocoa tree as well, grown from a sapling crafted with cocoa beans and shedding more
of them, and it was dropped in October 2026: cocoa beans were already renewable in
1.6, from pods planted on any jungle log, so the tree was only a second way to farm
them.

**`sapling` was a vanilla sapling with the serial numbers filed off.** It is a
`plant` whose `growsInto` replaces it with something else, so it inherits everything
from `XiePlantBlock` and overrides one thing: what a ripe one does with a growth
roll it has no stages left to spend. Two details give the game away — the original
set `growthRate` to 14, which its own formula turned into the 1-in-7 roll
`SaplingBlock.randomTick` still uses, and it spent a single metadata bit on one
intermediate stage, which is exactly vanilla's `stage`.

**`tree` did not need `TreeGenerator` at all.** That file held three generators, and
every one of them was 1.6's own tree generator copied out of vanilla:
`generateOak` is `WorldGenTrees`, `generateConifer` is `WorldGenTaiga2` and
`generateTallConifer` is `WorldGenTaiga1`. All three are still in the game as
configurations of the `minecraft:tree` feature, so the shape is emitted as JSON —
one configured feature per tree block — and vanilla places it. The numbers match
because they are the same numbers: `generateOak` rolls `nextInt(3) + 4` for its
trunk and `straight_trunk_placer` with a base of 4 and a random of 2 rolls the same
4 to 6. `TreeShape` picks between the three; nothing in the content declares one, so
every fruit tree is an oak.

That leaves `XieTreeBlock` holding only what vanilla has no equivalent for:

- **`sheds`** — a tree drops its fruit on the ground of its own accord, which is the
  whole point of a fruit tree and the one thing a datapack cannot express. One leaf
  per tree does the shedding, the one directly above the topmost log; 1.6 called it
  the keystone and packed it into a metadata bit, and it is a blockstate property
  now. Having exactly one is not an accident of the old format — a canopy is fifty
  blocks, and fifty leaves each rolling for fruit is fifty times the fruit.
- **`shedSaturation`** — stop once eight of the fruit are already lying nearby. It is
  what keeps an orchard from burying itself, and it is why shedding does not need
  rate-limiting on top. **The count is taken where the fruit is about to be put**, as
  1.6 took it, which makes it worth exactly as much as that position is right — see
  below.
- **Where the fruit lands had to be read rather than copied, twice.** The original
  walks the neighbouring column down while it is *solid* and stops at the first air,
  which is the opposite of finding the ground; that much was corrected when the
  archetype was written. What was left is subtler and only a running game could show
  it. The keystone sits inside the canopy, so a neighbouring column is air above
  *leaves* about as often as it is air above the ground, and a descent that stops at
  the first surface leaves the fruit on the canopy's own shoulder. As a landing spot
  that is harmless, since the item falls out of the leaves anyway — but the
  saturation count is taken there, so a tree aiming at its own foliage counts the
  fruit lying under it as lying nowhere near, and sheds past the eight that were
  meant to stop it. `groundBeside` skips leaves and logs now. It was found by
  [the acceptance run](TESTING.md#the-acceptance-run) on its first day: the tree with
  nine cocoa beans under it shed a tenth in two runs out of five, depending on the
  trunk's height and which of the eight directions came up — which is exactly the kind of thing a
  human verifying a behaviour once would have watched work and never seen fail.
- Leaf decay is `LeavesBlock`'s. The forty lines of flood fill in
  `XieBlockLeaves.updateTick` were 1.6's `BlockLeaves` verbatim; the modern one keeps
  a `distance` property instead and reaches the same answer without the array.

**Only a planted tree sheds.** `TreeGenerator` set the keystone when a sapling grew
and skipped it when `Spawnable` generated the tree, and that asymmetry is kept: a
wild fruit tree still yields fruit when you break its leaves, but an orchard you
planted is what fruits on its own. It is also the difference between a handful of
item entities and one per wild tree in every forest.

**Saplings are the first blocks to register a `BlockItem`**, and the only ones. Every
other Xie block is planted from a seed or placed by worldgen, so an item form would
be one nobody could obtain. A sapling is the opposite case on both counts: a fruit
sapling grown from its seed is dug up by breaking it and replanted somewhere else,
and `blazeTreeSapling` is crafted from a sapling and crafted into blaze rods in
turn, and none of that is expressible without an item — which is exactly why
vanilla saplings are block items too. A block with an item form and no declared
`drops` drops itself, which is the one branch `XieBlockSapling` added to the drop
code.

Worldgen
--------

`spawns` entries become datapack worldgen rather than a runtime generator: a
configured feature for what, a placed feature for where, and a NeoForge biome
modifier for which biomes. `XieWorldgenEmitter` writes all three.

Most of the mapping is direct, because vanilla's placement modifiers ask the same
questions `Spawnable` did — `rarity` is `rarity_filter`, picking a column and taking
the surface height is `in_square` then `heightmap`, `clusterSize` scattered around
that point is `count` then `random_offset`, and `spawnsOn`/`spawnsIn`/`spawnsUnder`/
`spawnsNear` are one `block_predicate_filter`. That is the shape vanilla's own
pumpkin patch has.

Three things needed a decision rather than a translation:

- **`clump`** was a contiguous blob grown in square rings from a centre, which no
  amount of scattering reproduces, so a clumped entry becomes a `disk` feature. A
  disk's `target` is itself a block predicate, so the placement rules move there
  unchanged. Its radius uses the original's own `ceil((sqrt(n) - 1) / 2)`.
- **Where the block goes.** A heightmap reports the first free position *above* the
  ground. A plant wants exactly that; a block that replaces terrain wants one lower.
  Whether `spawnsIn` admits air decides the heightmap, the y offset, and the
  decoration step — `vegetal_decoration` with the flowers, or `underground_ores`
  with the disks, which is where vanilla puts `disk_sand`.
- **A tree is already a feature.** An entry spawning a `tree` block writes only a
  placed feature and a biome modifier, pointing at the configured feature that block
  already has — the same one a sapling grows. The placement itself is unchanged,
  because that is how vanilla scatters its own trees, and the feature refuses a
  position it does not fit.

The target has to be a block this mod registered. An entry naming a vanilla block is
either unported or, for `melon`, content dropped because vanilla supersedes it, and
adding vanilla features back into biomes under this mod's name is not the same thing
the content asked for. Those are counted and named, like recipes.

An entry keyed by an *alias* resolves through it, which is what `Xie.parseBlock` did
before anything else. `fruit.json` declares no spawn entry named `fruitLeaves`, only
four named for the trees, and the alias is the whole of the link between them.

Eating, cooking and burning
---------------------------

What `XieItemFood` did by hand, and the one place the 2013 engine kept behaviour on
an item rather than in a recipe. Nearly all of it is a data component now, which is
the same trade the rest of the port has made — describe it to vanilla rather than
implement it — and the exception is `cooked`, which was a recipe all along.

- **`container`** is two things, because `Item.setContainerItem` was. It is
  `minecraft:use_remainder`, the bowl a salad leaves in your hand, and it is
  `craftRemainder`, which is the only thing that method actually did in 1.6 — craft a
  dressed salad with mayonnaise and the mayonnaise's bowl comes back. Honey bottle
  sets both, for the same reason.
- **A food's `drops`** means the same as `container` one step along, and always did:
  `JobItem` filled the drops in *from* the container when a food declared none, and
  `XieItemFood.onFoodEaten` handed whichever it ended up with to the player. So a
  single unconditional drop is a use remainder too, which is how the juices and the
  tea give their glass bottle back. Several drops, or one with a chance, are left
  alone — one item is all the component can hold.
- **`action`** picks the animation and the sound: `Consumables.defaultDrink()` rather
  than `defaultFood()`, on the `minecraft:consumable` component. `XieItemFood`
  understood exactly `drink` and treated everything else, including nothing, as
  eating.
- **`potionEffect`** is an `apply_effects` entry on that same component, one per
  declared effect, since the chance is per effect in the legacy format.
  `PotionEffectDef` does the reading and `PotionNames` the naming.
- **`cooked`** — and `smelted`, its other spelling — is a recipe written on the
  ingredient rather than in a `recipes` section, so it is emitted alongside them.
  1.6 had only the furnace; a result that is food also gets the smoker and campfire
  recipes vanilla gives every one of its own, which is all four of them: cooking corn
  and frying a tortilla is the whole of what the property was used for.
- **`fuel`** is a burn time, and there is no component for it — vanilla builds its
  table from item tags in `FuelValues.vanillaBurnTimes`. NeoForge's
  `neoforge:furnace_fuels` data map is the datapack-shaped answer, so that is what is
  emitted.

`shape` is read by `TreeShape` but nothing in the live content declares one, so every
fruit tree is an oak. `colorize` is read by nothing and nothing declares it either;
the original defaulted leaf colourisation off, which is what an untinted model does.

The cookpot
-----------

The one archetype in the port with no 2013 file behind it. The Cooking Mod pack that
held the original stove did not survive, so `cooking/cookpot.json` is rebuilt from
the mod posting rather than ported: a pot you feed by hand, which the posting
describes as "wait until the pot changes colour". `StovePot` in `common/` is the
rules and the reasoning, testable without Minecraft; `XieStoveBlock` and its block
entity are the interaction. The archetype keeps the 2013 name, `stove`, because that
is what the posting called it; the block is the cookpot because that is what it is.

- **It is a cauldron with a fire under it, and the recipe says so.** Campfire plus
  cauldron, shapeless. The 2013 stove was a furnace with an iron top because the
  cauldron did not exist yet, and the old iron-and-furnace recipe went with it.
  Every model is vanilla's own cauldron with the `content` sprite swapped — water,
  then mud for stew — so the block needs no art at all, and the three meals are the
  only sprites the pack ships for it.
- **Everything goes in by hand, with no menu.** Water on an empty pot starts it;
  anything in the `ingredients` alias is a food and anything in `flavourings` is a
  seasoning; an empty bowl ladles out the best meal the pot can afford once it has
  cooked; any furnace fuel stokes it. The 2013 stove had a menu, and dropping it for
  right-click costs nothing that existed, since the pot was always the whole of it.
- **Water, food, bowl is a Weak Soup; a second food or a flavouring makes it Soup;
  both make Stew.** The tier is food plus flavouring, capped at two and one per meal,
  and the pot always ladles the highest tier it can afford. Hunger 4, 6 and 8, all
  on a bowl, so the cookpot is a way of turning any of a dozen cheap ingredients
  into a few medium-value meals. Where it departs from the posting is capacity: the
  original cooked one meal at a time, and this one accumulates and serves repeatedly.
- **The fried half is deliberately not ported.** The posting listed the same meals
  again with oil in place of water — Fried Mess, Fried Meal, Stirfry — sharing the
  ingredient list, the flavouring list and the food values, so it was six items doing
  the work of three. `StovePot.Base` keeps room for a second base; it should only
  come back if it comes back different.
- **How it is verified.** `StoveContentTest` holds the content file and the state
  machine to the same meal names and the agreed food values, and `StovePotTest`
  covers the rules without a world. The interaction is the `cookpot` acceptance
  check (`acceptance/checks/cookpot.py`, see TESTING.md): real right-clicks in
  survival put water, coal, foods, salt and a bowl into three pots and read the
  meal back out of the player's inventory, with a null beside each positive — a
  second bucket refused, an unfuelled pot still raw after `cookTime`, a bowl kept
  on an empty and on an unfinished pot, and an apple moving nothing. Its first run
  found that a bowl on a pot with nothing to serve was being burnt as furnace
  fuel, which vanilla lists a bowl as; a bowl is now never fuel.

Tools and swords
----------------

The largest missing piece of the item side, and the same trade the food work made.
Between them `XieItemSword` and `XieItemTool` were two hundred lines of overrides —
mining speed, harvestability, attack damage, durability, enchantability, blocking —
and vanilla has a word for nearly every one, so both classes go and nothing takes
their place.

- **`uses` is `minecraft:max_damage` and `enchantability` is
  `minecraft:enchantable`**, on a sword. `JobItem.doTool` read neither: a tool's
  durability and enchantability were its tier's and nothing else's, which is why
  `ultimateFist` declares no `uses` and never needed to.
- **`damage` is an attack damage modifier**, and needs one conversion. 1.6's
  `getDamageVsEntity` was the *total* damage dealt, and the modern component holds a
  modifier over the player's base attack damage of 1, so every number moves down by
  one. That the arithmetic then lands exactly on vanilla's own values is the check
  that the reading is right: `boneSword` asks for 6, which is `ToolMaterial.IRON`'s
  bonus of 2 on top of vanilla's sword baseline of 3, and `weapons/readme.txt` says
  in so many words that the bone sword "does as much damage as iron". A sword's
  `damage` is the whole of it; a tool's is a bonus over its tier's, because
  `ItemTool` added the two together — which is the same shape as vanilla's own
  baselines, where a pickaxe passes 1 and a sword 3.
- **`blocksEffective` and `efficiency` are rules on `minecraft:tool`**, with
  `canHarvest` and `cannotHarvest` as two more, in the order `XieItemTool` consulted
  them: refuse, then allow, then decide the speed. First match wins per field in
  `Tool`, which is the same precedence. The tier contributes no rule of its own,
  because `canHarvestBlock` ignored the material entirely and asked only the declared
  lists — so no `incorrect_for_*_tool` rule is added and a wooden-tier Xie tool mines
  exactly what its content says it does.
- **`canBlock` is `minecraft:blocks_attacks`**, the component a shield carries and
  the nearest thing left to a sword you could raise. `XieItemSword.onItemRightClick`
  only called `setItemInUse` and left the rest to vanilla, and vanilla's sword
  blocking halved the incoming damage, cost the sword no durability and had no
  wind-up — so the component says exactly that, and takes the shield's
  `#minecraft:bypasses_shield` for what gets through. `glassSword` is the one that
  declares `false`, and simply has no component. `Item` does the rest generically:
  a stack with `blocks_attacks` gets the block animation and a 72000-tick use
  duration whatever class it is.
- **Attack speed had to be chosen** rather than translated, because 1.6 had no attack
  cooldown at all. Each archetype takes the speed of the vanilla item it is: a sword
  swings at -2.4 and a tool at a pickaxe's -2.8.

**`"material": "diamond"` meant wood in 2013.** `XieMaterialManager.getToolMaterial`
upper-cased the word and looked for a matching `EnumToolMaterial` constant, and 1.6
called the diamond tier `EMERALD` — so the Ultimate Fist matched nothing, logged a
line nobody read, and came out a wooden tool with 59 uses on an item costing seven
diamonds. Both spellings resolve to the diamond tier now, on the same grounds
`PotionNames` maps both spellings of an effect: the modern name is the word the
content already wrote. `ToolTier` is that table, and an unrecognised word is still
wood, as it always was. `"material": "stone"` on the survival pickaxes really was
stone and needs no such rescue.

**A block list becomes a generated block tag.** `minecraft:tool` names block tags or
block sets and never a list of ids, so each `blocksEffective`, `canHarvest`,
`cannotHarvest` and `canCut` is written into a tag of its own under
`data/xiesmod/tags/block/tool/<item>/`, and the rule points at it exactly as
vanilla's pickaxe points at `#minecraft:mineable/pickaxe`. Mapping onto those vanilla
tags instead was the alternative, and it destroys the only thing these lists are for:
`survivalpickaxe.json` exists to make a wooden pickaxe mine *less* than vanilla's, and
its eleven named blocks are a hand-picked subset of `mineable/pickaxe` rather than
the whole of it. A name nothing answers to is left out and said so in the log, because
a tag entry naming a missing block is a load error and an optional one is
indistinguishable from a typo.

`blocksEffective: "all"` is the exception, because it is not a list. It was
`makeUltimate`, a flag, and the rule it becomes names every block in the registry
directly. Enumerating a thousand ids into the pack would say the same thing at
greater length and would stop being true the moment somebody turned the generated
pack off.

Every one of the nine declarations takes effect: three swords, three bows and the
Ultimate Fist register, and the two survival pickaxes are patched onto the vanilla
pickaxes they `overide` (sic) rather than registered — see the overrides section,
where the same components are written onto an item that already exists. One
*property* does not survive, and why:

- **`flame` on `blazeSword`**, where `hitEntity` set the target alight for a hundred
  seconds. Vanilla's only word for it is an enchantment — the readme even describes
  the sword as working "as if it had the Fire Aspect enchantment" — but an
  enchantment puts a glint and a tooltip line on the item and lets an anvil move it
  to another one, none of which the property did. The bow section takes the
  enchantment route for the same property and is not contradicting this: the
  enchanted stack there is a copy that lives inside the arrow and is never held,
  drawn or repaired, so none of the three objections reaches it.

**`transforms` is the one property here that describes nothing**, and so the only one
that needed a class. Right-click stone with the Ultimate Fist and it becomes a gold
block; right-click dirt and it becomes air. It is a map from one block to another,
tried in the order it was written with the first match winning, and no component
replaces the block you clicked — which is the same test the bow section below applies,
resolved the same way. So `XieToolItem` sits beside `XieBowItem` in
`mod.xie.xm.item`, and this is the second archetype in the port to end with more code
than it started with rather than less.

**Only a definition that declares it gets the class.** The other eight `tool` and
`sword` declarations dissolve into components entirely, which is the fork in
`XieRegistrar.registerToolLike` and is the shape `boneBow` already has, taking `super`
on every branch of `XieBowItem`. It registers no extra object and emits no extra file:
every count in the log is identical with the property implemented and without it.

**Vanilla writes this method twice already.** `HoeItem.useOn` tills a block into
farmland and `ShovelItem.useOn` flattens one into a path, and both are
`XieItemTool.onItemUse` line for line — play a sound on both sides, change the block
and spend one durability on the server alone, answer `SUCCESS`. That settles three of
the four decisions the 2013 method took:

- **The bottom face does nothing**, and that is deliberate rather than an artefact of
  how 1.6 numbered its faces. The guard is `l != 0`, where `l` is the clicked side;
  `ShovelItem` opens with `if (context.getClickedFace() == Direction.DOWN) return
  PASS`, which is the same face and, as it happens, still the same ordinal. Reaching
  under a block to reshape it is the one angle from which you cannot see what you did,
  and Mojang was still of that opinion thirteen years later.
- **The sound plays on both sides and the block changes on one.** `if (world.isRemote)
  return true;` sits between the two, so the client that predicted the click hears it
  at once and the server tells everybody else — which is exactly what
  `Level.playSound` means by taking the acting player as the listener to *exclude*.
  The guard is load-bearing rather than defensive:
  `MultiPlayerGameMode.performUseItemOn` really does call `useOn` on the client.
- **`canPlayerEdit` is checked before anything happens**, which is
  `Player.mayUseItemAt`, asked about the clicked position and the clicked face. It
  only ever refuses in adventure mode.

The fourth is the odd one, and it is reproduced as written. **The sound is the sound of
what appears, not of what was destroyed** — turning stone into gold plays a gold block
breaking. The branch is `if (blockID.blockID == 0) block = Block.blocksList[world
.getBlockId(i, j, k)]`: air has no sound of its own, so a transform *to* air falls back
to the block that was there. That half reads as intended and the general case reads as
odd, and neither is broken — a transform is not a mining action and has no more claim
on one sound than the other — so both survive. What does not survive is the sound's
*position*: the call site added half a block to each coordinate and
`Xie.playBreakSoundEffect` added another half on top, putting it a block away
diagonally, and that is a slip rather than a decision.

**Two shapes of the property were dead on arrival**, which is why neither is carried.
`JobItem.expandTransformsNode` accepted a bare string — `"transforms": "goldBlock"`,
meaning anything at all into a gold block — and the key `"all"`, and both built the
wildcard `new BlockID()`, whose block id is `-1`. `BlockID.matches` compares that
against the id actually in the world, which is never negative, so a wildcard transform
could not fire. Nothing declares either, and reviving one would be inventing content
rather than porting it, so both are reported and left out. A `:meta` suffix goes the
same way for a different reason: metadata is a blockstate now, so `stone:1` would
widen to every state of stone rather than narrow to one, which is worth saying in the
log. Nothing declares one of those either. What the content does declare resolves
without help — `"air"` is a name `ResourceNames` already answers for, and needs
nothing from the numeric table that maps the bare id `0` to it.

**An override cannot carry it**, and now says so. `XieOverrides` patches data
components onto a vanilla item and does nothing else, so a `tool` override declaring
`transforms` would lose it in silence. Neither survival pickaxe declares one, but the
case is reported the same way an unsupported override archetype already is.

`magicWand` is the other property of `XieItemTool` that was behaviour: it cycled the
clicked block's metadata by one per click. Nothing in the content declares it, and
metadata cycling has no modern meaning at all — a block's states are named and typed
now, and "the next one" is not a question a blockstate answers. It is left out on those
grounds rather than for want of somewhere to put it.

**The deferred packs never migrated their textures.** `weapons` was written for
Minecraft 1.2.5 and ships `img/xieweapons.png` with an `icon` index per definition
rather than one PNG per sprite — which is the case `TexDef` keeps the indices around
for. The emitter cuts the sprite out of the sheet when it finds no migrated file:
sixteen sprites across, tile size from the width, which is how an `Icon` index
worked. Live content is untouched by this, because it ships both and the file wins.

That is also what `_plox_update/` means. The 2013 "Big ole shuffle" was the 1.5
texture migration, and every pack still addressing a sheet was parked there to be
updated rather than left broken — which is why cheese, the one pack in it with
nothing else wrong, sat there for thirteen years. Porting one out of it is cutting
its sheet into the per-name files the live content ships, dropping the legacy `id`s
and moving the definition in; the indices stay, as they do on every live
definition.

Bows
----

The last archetype, and the first thing in the port to end with more code than it
started with. Everything before it went the other way — the three tree generators
were vanilla's own, `XieItemFood` became four components, `XieItemSword` and
`XieItemTool` became five and left one property behind — because those classes were
describing an item and modern Minecraft has words for the description. A bow is not
a description. `ammo`, `ammoless`, `ammoConsumed` and `semiautomatic` are decisions
taken at the moment the bow is drawn and fired, and there is no component that takes
a decision. So `XieBowItem` exists, and it opened `mod.xie.xm.item`; the one property
of `XieItemTool` that survives, `transforms` above, is the other thing in it.

It is one class rather than the three the properties suggest, because two thirds of
what `XieItemBow` needed is already in the game.

**`XieEntityArrow` is `AbstractArrow`.** Sixty-nine lines, and its entire reason for
existing was to remember the `Item` it was fired from and hand *that* back on pickup
— which is how the redstone a crossbow spends could be recovered from where it
lands. `AbstractArrow.pickupItemStack` is that field: set from the drawn stack,
serialised under `item`, returned by `getPickupItem`. And
`ProjectileWeaponItem.createProjectile` fills it in from whatever was actually drawn,
including ammunition that is not an arrow at all — it falls back to `Items.ARROW` for
the arrow's *shape* and keeps the drawn stack for its *identity*. So a redstone
crossbow fires an arrow that gives back redstone, with no code, the same way
`TreeGenerator`'s three generators turned out to be vanilla's three.

**`power`, `punch` and `flame` are extra enchantment levels, and 2013 said so.**
Every one of the three lines in `fire` reads `base + EnchantmentHelper
.getEnchantmentLevel(...)` — the declared number is *added to* the enchantment level
rather than substituted for it, so a bow declaring `power: 1` and carrying Power II
fires as Power III. The translation is therefore to hand the arrow a bow that
already has those levels, and vanilla computes the rest:

- Power adds `1.0`, and `0.5` per level above the first. 1.6 added `power * 0.5 +
  0.5`. Those are the same expression. Both land on the arrow's base damage before
  it is multiplied by velocity, so nothing else shifts either.
- Punch is the only route there is. `AbstractArrow` has `setBaseDamage` and no
  knockback setter at all — `doKnockback` asks `EnchantmentHelper.modifyKnockback`
  about the weapon and nothing else. Anything short of a projectile class of its own
  has to go through the enchantment.
- Flame ignites for `duration: 100.0` seconds, which is the number the next
  paragraph is about.

The enchanted stack is a **copy**, made in `createProjectile` and handed to the
arrow's constructor. It becomes `AbstractArrow.firedFromWeapon`, which is what all
three effects are read from — `modifyDamage` at the hit, `modifyKnockback` in
`doKnockback`, `applyOnProjectileSpawned` the moment the arrow exists — and the
player's own bow is never touched. That is why this does not contradict the decision
two sections up to keep `flame` off `blazeSword`: the objection there is that an
enchantment adds a glint, a tooltip line and anvil transferability, and none of the
three can reach a stack nobody ever holds.

**`flame` was always a hundred seconds or nothing, whatever the number says.** The
line is

```java
int flame = baseFlame + EnchantmentHelper.getEnchantmentLevel(...) > 0 ? 100 : 0;
```

and both `+` and `>` bind tighter than `?:`, so the declared value only ever decided
*whether* the arrow burned. `redstoneBow`'s `"flame": 1` is not one second and never
was. Unlike the `growthRate` inversion, which was reproduced as the correction
because the original's arithmetic made a crop advance on every tick, this one is
reproduced **as written** — because the truncation lands on the right answer. An
arrow burning for a hundred seconds outlives any flight, so what the property means
either way is "this arrow is on fire", and vanilla's own Flame enchantment says the
same thing with the same hundred. Reading `1` as a duration would be the change of
behaviour: the redstone crossbow's arrows would go out in the air and stop setting
anything alight, which is neither what the code did nor what `readme.txt` promises.
`blazeSword.flame` is the same hundred for the same reason.

Four properties that are not what their names suggest:

- **`semiautomatic` is fully automatic**, and `readme.txt` calls it that. A normal
  bow charges: `onItemRightClick` calls `setItemInUse` and `onPlayerStoppedUsing`
  fires with a force taken from how long it was held. A `semiautomatic` one fires
  *in* `onItemRightClick`, at once, with a random force clamped up to at least 0.9,
  and `onPlayerStoppedUsing` returns without firing. It is a crossbow that never
  needs winding. Holding the button repeats it every few ticks, which is the
  automatic fire the readme describes, and the 0.9 carries over untouched because
  the velocity scale did not move: a 1.6 arrow flew at `force * 2.0F * 1.5F` and a
  modern one at `pow * 3.0F`.
- **`ammoConsumed` does not mean the ammunition is consumed.** It is consumed
  whenever the shot was not free, with or without the property. What `ammoConsumed`
  added was `canBePickedUp = 0` on the arrow, so the shot cannot be picked up again
  — the ammunition is gone for good, which is what makes a crossbow that eats
  redstone worth building. It is `Pickup.DISALLOWED`, set after construction on
  purpose: `AbstractArrow.setOwner` promotes `DISALLOWED` to `ALLOWED` for a player
  owner, and the constructor calls it.
- **`ammoless` was one branch with creative mode and Infinity** — `freeFire` — and a
  free shot always produced an unrecoverable arrow. Modern vanilla splits it: creative
  and Infinity are both handled inside `ProjectileWeaponItem.useAmmo`, which returns
  an unconsumed copy carrying `INTANGIBLE_PROJECTILE`, and the arrow's constructor reads
  that and takes `Pickup.CREATIVE_ONLY`. So `ammoless` is the only part left to write,
  and it is written the same way: bypass `draw`, mark the stack intangible. The one
  difference from 2013 is that a creative player may pick the arrow back up, and a
  creative player was never going to run out of them.
- **`ammo` is a list, and the list is a predicate now.**
  `ProjectileWeaponItem.getAllSupportedProjectiles` is a `Predicate<ItemStack>` that
  vanilla asks for both the held-projectile check and the inventory sweep, which is
  `XieItemBow.hasAmmo` and `getAmmo` in one. A bow declaring no `ammo` keeps
  `#minecraft:arrows` rather than the literal arrow item the constructor listed,
  because that tag is what "an arrow" means now — it covers the tipped and spectral
  arrows that did not exist to be listed in 2013. NeoForge's `getDefaultCreativeAmmo`
  is what stops a creative player being handed an arrow their redstone crossbow
  rejects.

**A bow reads `uses` and `enchantability`**, unlike a tool, which read neither.
Both are the same two components a sword takes and are written by the same file, so
`XieToolProperties.forBow` is three lines. `redstoneBow` declares no `uses` and
inherits vanilla's own 384, which `readme.txt` states outright — "as durable as a
normal bow".

**The pulling animation is not reproduced.** A vanilla bow has four models: the base
one, and three drawn frames selected by a `minecraft:condition` on `using_item`
wrapped around a `range_dispatch` on `use_duration`. A Xie bow has exactly one
sprite, cut out of a 2012 spritesheet by index, and there are no drawn frames to
show — inventing three would be inventing content rather than porting it. So a bow
gets the flat generated model every other Xie item gets. `boneBow` and `blazeBow`
still animate the *player*, because `ItemUseAnimation.BOW` is on the item and not in
the model; `redstoneBow` never enters a use at all, which is the point of it.

Sticks
------

The third piece of ported behaviour, after `transforms` and the bow, and the one that
adds least because it follows from both. Like `transforms` it is a `useOn` that
changes the block you clicked; like a bow it is a decision rather than a description —
what a stick yields is not what the block it hits drops, so no loot table can carry
it. `XieItemStick` is seventy-nine lines holding two lists walked in step, a
`BlockID` and the `Droppables` that block gives up, consulted in declaration order
with the first match winning. Right-click a listed block and it plays a crunch, spawns
that block's drops, and on a `destroyChance` roll clears the block to air, for one
durability out of sixteen from a stack of one.

`crimsonSickle` is what makes the archetype worth porting: a blaze rod and nether
brick sickle that reaps nether wart. `foragingStick` is the other declaration and is
dropped content in a file the parser never reads.

The method is `XieToolItem.useOn`'s shape for the reason given there — `HoeItem` and
`ShovelItem` both write it, so the sound plays on both sides, the world changes and
the durability is spent on the server alone, and the answer is an `InteractionResult`.
Four things differ, and three of them are the 2013 method's own:

- **Every face works.** `XieItemTool` guarded on `l != 0` and this did not. That reads
  as deliberate rather than forgotten: a tool reshapes the surface of a block and wants
  you to see the face you changed, and a sickle takes the whole block, which looks the
  same from underneath.
- **The sound is the clicked block's *step* sound**, not its break sound —
  `Xie.playBlockCrunch` reads `stepSound.getStepSound()` where
  `Xie.playBreakSoundEffect` reads the break one. That is the whole of what makes it a
  crunch and not a mining noise. It plays whether or not the roll takes the block,
  because in the original it played before the roll.
- **The block's own drops are not run.** The line that would have, `harvestBlock`, is
  commented out in place, so the block is cleared with `setBlock` rather than
  destroyed. What the definition lists is the whole of what you get.
- **`canPlayerEdit` is added**, which `XieItemTool.onItemUse` checked and this did not.
  That omission is not a design decision expressed in code — it lets an adventure-mode
  player clear blocks the gamemode exists to protect, and every vanilla item that
  changes a block asks first. Same trade as `XieBasicBlock.canSurvive`: faithful and
  reads as a bug.

The destroy particles moved sides, and gained something. `Xie.addBlockDestroyEffects`
went through the proxy, whose dedicated-server implementation is an empty method, so
the burst was drawn only on the client that swung and nobody else saw it. It is a
`levelEvent` now, sent by the server, so everybody does.

**A `blocks` key may carry metadata, and `crimsonSickle` is the first content in the
port that actually does.** It names `netherStalk:3` — ripe nether wart. The suffix
goes the way the same suffix goes on a `transforms` entry, which is out with a word
about it in the log: metadata is a blockstate now, and dropping the suffix widens the
entry to every state of the block rather than narrowing it to one, so the sickle reaps
nether wart at any age. That is a real widening rather than the theoretical one the
transforms section could only describe, and it is carried anyway for the same reason:
honouring it would mean inventing a per-block notion of which state a 2013 metadata
value picked, which is exactly what that decision declined to do.

**One drop in the content does not resolve, and it is a typo.** `crimsonSickle` asks
for `nethwerwart`, and nether wart is spelt with the letters the other way round. In
2013 that cost the whole item: `JobItem.doStick` set `problemFlag`, the job never
completed, and the sickle registered as nothing at all — so the crimson sickle has
never worked, in any version. Here the rule is the one every other drop gets: the bad
name is left out, counted and reported, and the rest of the item works. Which leaves
the sickle dropping only `etherWart`, at the 2% its own table asks for.

That skip needed the emitter to grow a check that writes no file. A stick's drops are
not a loot table — they are handed to `XieStickItem`, which resolves them the first
time somebody swings it — so without it they would be the only drops in the content
that could go missing without being counted, and they would go missing hours into a
game rather than at load.

The recipe book
---------------

1.6 had no recipe book, so nothing in the 2013 engine or the content says how a
recipe gets into one, and for the whole port none did. Vanilla's book lists only what
a player has unlocked, and every vanilla recipe is unlocked by an advancement under
`recipes/` — no display, parented to `minecraft:recipes/root`, satisfied by either a
`recipe_unlocked` criterion or an `inventory_changed` on an ingredient, rewarding the
recipe. Nothing gave ours one, so the book never showed a single `xiesmod` recipe and
a player without JEI or EMI had no way to learn that tortillas exist. Since
2026-11-02 `XieDataEmitter.writeRecipeFile` writes that advancement beside every
recipe at the same id: crafting, the content's `smelt`, and all three cookers a
`cooked` becomes. A recipe skipped for an unresolvable name never reaches it, so it
gets no advancement either, and culling never touches our own namespace. The vanilla
advancements for the recipes a cull removes stay where they are and grant nothing,
because `ServerPlayer.awardRecipesByKey` drops a key with no recipe behind it.

Two decisions, both derived from what the content already says rather than from a
new property:

- **Every distinct ingredient unlocks it.** Vanilla hand-picks one key ingredient per
  recipe — the cake unlocks on an egg — and the content names no key, so the
  advancement carries one `inventory_changed` criterion per distinct ingredient and
  any of them is enough. An alias is tested by its tag, which is a shape vanilla uses
  itself (`#minecraft:acacia_logs` for acacia planks), so a taco unlocks on a taco
  shell or on anything in `#xiesmod:protein`, `#xiesmod:salady` or `#xiesmod:saucy`.
  The cost is that one common ingredient can unlock a lot at once: any meat brings in
  every taco, burrito and sammich. For a mod whose only discovery is this book that
  is the right way to err; narrowing it would mean the content declaring a key.
- **Recipes are grouped by product.** Each recipe's `group` is its result's id path,
  which is vanilla's own rule for its several red dye recipes (`"group": "red_dye"`).
  The book then shows snack tacos as one entry with nine alias combinations behind it
  and full tacos as another, which is the split the content already makes by naming
  them differently; corn chips' eight stack sizes are one entry. A product that is a
  vanilla item joins vanilla's group of the same name, which is the point of it.

Vanilla's datagen files advancements under a tab directory, `recipes/food/` and so on,
taken from the recipe's book category. Ours carry no `category` — a crafting recipe
defaults to `misc` and a furnace recipe to the furnace's misc tab — so they sit flat in
`recipes/`; the directory decides nothing. Giving `cooked` food recipes
`"category": "food"`, so popcorn sits in the furnace's food tab, is the obvious next
step and was left out as a change to the recipes themselves.

Culling vanilla recipes
-----------------------

`cull` is the only thing the format asks for that removes rather than adds, and the
only one that leaves no trace in the generated pack. It names the **output** of a
recipe and deletes every recipe producing it: `JobRecipe.doCulling` walked
`CraftingManager.getRecipeList()` and dropped each entry whose `getRecipeOutput()`
matched. There is no such list to mutate now.

**A datapack cannot ask the question.** A pack overrides a recipe by *id*, and a
file carrying `"neoforge:conditions": [{"type": "neoforge:never"}]` loads as
nothing — so writing `data/minecraft/recipe/red_dye_from_poppy.json` into the
generated pack really would delete it. But a cull knows no ids. Finding them at
emit time means reading the game jar's own `data/minecraft/recipe/*.json` and
matching on `result.id`, which is a table of what vanilla shipped and blind to
every other mod, datapack and future version.

**`ModifyRecipeJsonsEvent` asks it of the recipes that actually loaded.** It fires
on the server once every pack's recipe JSON has been read and before any of it is
deserialised, and hands over the whole map to modify in place — the same walk, over
everything, on the first load and on every `/reload`. That is `XieRecipeCuller`.
What it costs is that a definition file's effect can no longer be read off disk in full:
this one property is a runtime hook, and the generated pack does not show it. It
also reads `result.id` out of raw JSON rather than from a deserialised recipe, which
is safe because every recipe type writes its result as an `ItemStack` and that codec
has one shape. The nineteen vanilla recipes with no result at all — the smithing
trims and repairing two items together — produce nothing that could be culled.

Two decisions the translation needed:

- **The stack count is ignored.** `Xie.itemstacksEqual` compared item, count *and*
  damage, so `"cull": "redDye:2"` meant 1.6's poppy-to-two-red-dye recipe — then the
  only one there was. Reproducing that check today does something worse than
  nothing: vanilla gives one dye per flower now and has grown three more red dye
  recipes, one of which — the rose bush, a two-block flower — yields exactly two. A
  faithful count match culls the rose bush and leaves the poppy recipe the content
  was written to remove. The count described the recipe of the day rather than
  selecting among several, which is plain in `cullStoneTools.json` where all five
  culls give no count at all. So what carries over is the rule without it: every
  recipe producing this item. Red dye from beetroot goes with the flowers, which is
  the rule applied to content 1.6 did not have rather than a special case.
- **This mod's own recipes are spared.** The content that culls a thing is
  invariably the content that replaces it — `flowerbushes.json` culls red dye and
  then declares red dye from rose seeds, and `cullStoneTools.json` culls every stone
  tool so `flintTools.json` can craft them from flint. In 2013 load order did this: a
  cull job ran against the list as it stood and the pack's own recipes were added by
  later jobs. Sparing the `xiesmod` namespace preserves it without depending on
  which file was read first.

The result in the live content is that a poppy in a crafting grid now yields rose
seeds rather than red dye, and red dye comes from the seeds — which is exactly the
chain `flowerbushes.json` describes, and was not reachable before because vanilla
supplied the shortcut.

Vanilla overrides
-----------------

`override` — and `overide`, the typo the parser has always accepted — names a vanilla
object that a definition speaks for. Three definitions declare it, all in deferred
packs: `wheatOverride`, and the two survival pickaxes, which is why those were the
two `tool` definitions the tools work could not register.

**It did not mean "replace vanilla's behaviour" in 2013.** `JobBlock.doWork` and
`JobItem.doWork` consult it in exactly one place each, around line 85 of both: when
the definition's declared numeric id is *already occupied*, `override` permits nulling
out `Block.blocksList[id]` or `Item.itemsList[id]` and registering this definition in
its place. Without it the job failed with "ID is in use". It was an id-collision
escape hatch, and the vanilla object it displaced was whichever one happened to hold
the id the pack picked.

Numeric ids are gone, so there is no collision to escape and no faithful translation.
What carries over instead is the *name* the property holds, because that was never the
accident: it says `crops` and the definition beside it describes a wheat crop; it says
`pickaxeWood` and the definition beside it describes a nerfed wooden pickaxe. The
author chose those ids deliberately and wrote the content to match. So an override is
read here as **this definition speaks for that vanilla object**, and what it declares
is applied to that object.

**Applied, not replaced.** Nothing is registered for an overriding definition. The
vanilla block or item stays the object it is — it cannot be unregistered, and
registering a second one beside it would only add a `wheat_override` nobody could
obtain next to the wheat everybody already has. That covers less than a replacement
would, and both live uses need less than one:

- **The pickaxes are data components and nothing else.** They declare `material`,
  `blocksEffective` and `canHarvest`, which is exactly the surface `XieToolProperties`
  already writes, and NeoForge's `ModifyDefaultComponentsEvent` patches a registered
  item's defaults in place. Each component is *set* rather than added to, and that is
  the point: a vanilla pickaxe already carries a `minecraft:tool` built from its
  `ToolMaterial`, and `survivalpickaxe.json` exists to make a wooden pickaxe mine
  *less* than vanilla's, so its eleven-block list has to displace
  `#minecraft:mineable/pickaxe` rather than join it. The generated tags are named
  after the item being overridden — `tags/block/tool/wooden_pickaxe/effective` — so
  the pack reads as what it is. Everything else about the two is untouched, because
  the definitions say nothing about it and their `material` is the tier vanilla had
  already given them: 59 uses and 15 enchantability on the wooden one, unchanged.
- **The wheat override is a loot table.** A datapack overrides a loot table by *id*,
  so writing `data/minecraft/loot_table/blocks/wheat.json` really does replace
  vanilla's — the same mechanism the cull section describes and rejects only because a
  cull knows no ids. An override knows exactly one. Ages come from the vanilla block
  rather than from the definition, because a `block_state_property` naming a value the
  property cannot hold is a load error; `wheatOverride` asks for 0-7 and vanilla wheat
  has 0-7, so nothing is clamped.

Those two routes are the whole of it: an item override carries components and a block
override carries `drops`. On the item side only `tool` and `sword` are expressible,
which covers every override the content declares — an archetype that is more than
components cannot be patched onto an item that already exists, and is reported rather
than half-applied. `container` is the one property of a supported archetype that does
not survive the crossing, because a craft remainder is a field on `Item.Properties`
rather than a component and there is nothing to patch; no override declares one.

**The generated pack had to grow a second half to carry that file.** The content pack
is registered at the *bottom* on purpose, so that a user pack can override it — and a
pack below vanilla loses every argument with vanilla. That is not a failure that
announces itself: the table is generated, written, read, and then discarded, and wheat
goes on dropping exactly what it always did. So the files that replace a vanilla id go
to `_generated/_overrides/` and are registered as a datapack of their own at the
*top*, while everything additive stays at the bottom where it cannot trample somebody
else's pack. It also means one directory listing answers "what has this content taken
away from vanilla".

**What `wheatOverride` declares that this does not carry**, precisely, because most of
it turns out not to be a loss at all:

- `type: crop` and `maxGrowth: 7` — vanilla wheat is a crop with ages 0 to 7. Already
  agreed.
- `growthRate: 100` is vanilla's own speed. `XiePlantBlock` calibrates 100 to a
  denominator of 25 exactly because that is `CropBlock.randomTick`'s `25 / speed`, and
  `crop` divides by the same farmland bonus vanilla wheat does. The number is vanilla's
  written out. It was *not* in 2013, where `(int)(100F / growthRate)` made 100 mean
  "advance on every random tick" — see the growth-rate note below.
- `textureBase: "crops"` — `crops` was 1.6's name for the wheat sprites, so this asks
  for the drawing vanilla wheat already has.
- `name: "Wheat Crop"` — every definition carries a `name` and `JobBlock` failed
  without one, so it is not evidence of a rename request. Vanilla wheat keeps its own
  name.
- `blockBounds` is the one genuine loss, and it is cosmetic. It is the same 16-stage
  ladder `corn.json`, `soy.json` and `hybridwheat.json` carry, truncated at seven: ages 0
  to 7 come out 3, 6, 6, 8, 8, 11, 11 and 13 sixteenths tall where vanilla's are 2, 4,
  6, 8, 10, 12, 14 and 16, so a ripe plant would stop short of filling its block. A
  crop has no collision, so this is the selection outline and nothing else — and
  changing it needs the block to be a different object, which is the thing an override
  does not do.

Its `recipes` section is emitted like any other pack's: three seeds from wheat and
wheat from three seeds, both naming vanilla items and neither one a recipe vanilla has.

Properties nobody reads
-----------------------

The report that should have existed from the start, and the one thing in the port that
needs to know nothing about any archetype. `PropertyMap` has tracked reads since it was
written and its own javadoc says `untouchedKeys()` "is how typos surface without the
parser needing an allowlist" — but **nothing outside the tests ever called it**, so
nothing surfaced. `spikey` and `fertilisable` were both found by a human reading a
definition file beside the registrar, which does not scale and did not have to happen.
`XieUnreadProperties` runs it at the end of pack emission, by which point every
consumer has had its turn: the registrar reads a definition to build a block or an
item, and the emitters read it again to draw and describe it.

A key nobody read is one of three things, and all three earn a line:

- **A property the port does not carry.** What `spikey` was until this session.
- **A spelling the engine never knew.** `smeltsInto` below, and `contents` for
  `content`.
- **A property of a definition nothing registers**, which says how much of it is going
  unused rather than only that it was skipped. `wildshrubs@melon` reports its
  `clusterSize` because the melon is dropped content whose spawn entry is never
  emitted.

Two kinds of key are exempt, because reporting them would bury the rest: `id`, whose
absence from the port is the whole design rather than a gap in it, and anything
beginning with an underscore, which is the format's comment convention — `steel.json`
annotates each ingot with `"_comment": "10% improvement over iron"`. An overriding
definition is skipped whole, because `XieOverrides` applies its properties from
`ModifyDefaultComponentsEvent`, which waits for the datapack and so runs later than
this report; what an override genuinely cannot carry is reported by that path already.

**Wiring it up found three false positives first, and they are the interesting part** —
each was a consumer reading a property without saying so, so the mechanism was
accusing the port of dropping things it does not drop:

- **`hunger` and `nourishment` on all thirty-three foods.** `ItemDef` read them through
  `peek`, which deliberately does not mark a key. They are consuming reads now; `peek`
  is for asking a question about a declaration, not for using it.
- **`rarity`, `clusterSize`, `clump` and `nearDistance` on every spawn entry**, for the
  same reason on `SpawnDef`.
- **`textureBase` on `saltDeposit`.** `readTex` builds a `PropertyMap` of its own over
  the same JSON object when a definition declares its texture flat rather than nested
  in `tex`, so the definition's own map never sees the read. It is in the structural key
  list now, beside `texFile` and `icon`.

The four that remain are all real, and none of them is a loss this port introduced:

- **`growsOn` on a `tree`** — `fruitLeaves` and `blazeTree` both declare it, and **1.6 read it on no tree either**. `JobBlock` consults `growsOn` in exactly
  two places, `doStem` and `setPlantProps`; `doTree` reads `sheds`, `colorize`, `shape`,
  `shedSaturation`, `trunkMeta` and `trunk`, and never asks. Leaves are not planted, so
  there was nothing for it to mean — where a fruit tree may stand is the sapling's
  `growsOn` and the spawn entry's `spawnsOn`, and both of those are carried.
- **`smeltsInto` on the five steel ores** — nothing reads it and **nothing ever did**.
  The engine knew `cooked` and `smelted`, both handled by `parseSmeltingRecipe`, and
  `smeltsInto` appears nowhere in the 2013 source. It goes the way `nethwerwart` goes
  and for the same reason: accepting it as a third spelling would make a recipe work
  that has never worked, which is inventing content rather than porting it. Every
  spelling the port does accept — `override`/`overide`, `seed`/`seeds`,
  `cooked`/`smelted`, `fertiliser`/`fertilizer`, `stepSound`/`stepsound` — is one the
  original itself accepted.
- **`toolMaterial` and `armorMaterial` on the five steel ingots** — and this is the find
  of the pack. `JobItem` has a branch for each, and both are stubs:

  ```java
  protected void doToolMaterial() {
      JsonNode node = getNode("toolMaterial");
      ...
      for (JsonStringNode key : map.keySet()) {
          if (key.getText().equals("harvestLevel")) {
              // TODO FTFESAD
          }
      }
  }

  protected void doArmorMaterial() {
      // TODO FTFESAD
  }
  ```

  So the ingots' declared durabilities, efficiencies, damage bonuses and armour
  reduction values were read into nothing, and no tool or armour piece was ever built
  from any of them. `steel.xid` is the clearest evidence of the intent — "need to leave
  9 blank ids after each ingot for armour and tools", with the ingot ids spaced ten
  apart to match — and the intent is all there is. Carrying it over would mean
  designing five tool sets and five armour sets from a table of numbers, which is
  writing the content the pack never got to. It is why the pack is in `_wip/`.
- **`flame` on `blazeSword`** — already decided, in the tools section: vanilla's only
  word for it is an enchantment, and an enchantment brings a glint, a tooltip line and
  anvil transferability that the property did not.

Structures that parse and go nowhere
------------------------------------

The blind spot the report above had, and the harder half of the same question.
`untouchedKeys()` works for a scalar because the only thing that ever reads one is a
consumer. It said nothing about `drops`, `sheds`, `blocks`, `blockBounds` and the
texture keys, because `XieFileReader` reads *those* for every definition it
understands — into `BlockDef.bounds()`, `.drops()`, `.sheds()`, `.tex()` and
`ItemDef.blockDrops()` — and marked them consumed on the spot. Which was true, and
proved only that the parser understood them.

**`blazeTree`'s `blockBounds` is what that costs.** It parsed perfectly, and
`XieBlockProperties.forTree` never called `fillsItsCube`, `XieRegistrar.treeConfig`
never called `boundsFor`, and `XiePackEmitter.partialCube` is only asked about
cube-styled blocks — so a cross-rendered `tree` dropped its box entirely and the report
had nothing to say. It was found by a human reading the registrar, which is exactly
what this report exists to stop being necessary.

**The marking moves to the accessor.** `PropertyMap.markStructural` hands a key back to
the report after the reader has parsed it, and the definition's own accessor is what
marks it consumed — so the question the report asks is no longer "did anything read this
key" but "did any archetype ask the definition for the value". Three things fall out of
it:

- **A key that parsed into nothing is settled by the reader**, which has already
  reported whatever was wrong with it. `zombiePigmelonStem` writes
  `"tex": "#melonstem"`, a string where the format wants an object, and that is one
  diagnostic rather than a diagnostic and a report line as well.
- **The report says which of the two kinds a key is**, because they want looking at in
  different places: "which nothing reads" is a property the port does not carry or a
  spelling it never knew, and the parser is where to look; "which the parser reads and
  no archetype then consults" is a hole in a registrar or an emitter path.
- **Reading a component field inside the record does not count.**
  `BlockDef.impliedMaxMeta` sizes a blockstate from the bounds and the texture faces,
  and that is not anybody's *use* of either. Nor is `ContentPack.referencedSheets`,
  which answers a question about the content rather than using it — `peekTex` is there
  for the same reason `PropertyMap.peek` is.

**Two consumers were reading further than anything used, and both had to be narrowed
before the mechanism told the truth:**

- **The texture copy asked `def.tex()` only in the spritesheet fallback.** Live content
  ships one PNG per sprite and a file always wins, so the declaration was never
  consulted for any of it — and every migrated definition in the content would have been
  reported. It asks once at the top of `copyBlockTextures` and `copyItemTexture` now,
  which is the honest reading: the texture path consulted the assignment, and whether it
  needed the indices depends on which files the pack shipped.
- **`XieDataEmitter.checkStickDrops` validated the `blocks` map of every item**, not
  just a `stick`'s. That both counted skipped drops nothing consumes and marked the key
  read on a definition quietly losing it. It asks a `stick`, which is where
  `JobItem.doStick` read the key and the only place anything reads it here.

**The audit, which is the point of the exercise** — knowing what the answer should be
before the code says it. Left column is where `JobBlock` and `JobItem` read the key in
2013; right is what consults the parsed value here:

| structure | 1.6 read it for | the port consumes it for |
|---|---|---|
| block `drops` | every archetype, in `expandCommonBlockParameters` | every registered block: the loot tables, and the item-form test |
| `sheds` | `tree` alone, in `doTree` | `tree` alone, in `treeConfig` |
| `blockBounds` | every archetype | `basic`, `log`, `tree` and the four growing ones — **not `stem`** |
| block `tex`, `texFile`, `icon` | every archetype | every registered block **but `stem`** |
| `textureBase` | every archetype, in `doTextureBase` | **cube-styled blocks only**, in a model's face entries |
| item `drops` | set on every archetype; read by `food`, `basic` and `seeds` | **`food` alone**, as the use remainder |
| `blocks` | `stick` alone, in `doStick` | `stick` alone |
| item `texFile`, `icon` | every archetype | every registered item |
| item `textureBase` | nothing: `setTextureBase` is on `IXieBlock` | nothing |

Four rows disagree, and each is a real gap that no content in this repo reaches:

- **`blockBounds` on a `stem`** — there is no class to hand a shape to, `StemBlock`
  being vanilla's, which is the same limit that stops a stem being `spikey`. Reported
  rather than half-applied, like `transforms` on an override.
- **`tex` on a `stem`** — it draws with vanilla's nine melon-stem models and never
  reaches the texture copy. `zombiePigmelonStem`'s is the refused declaration above, so
  it costs nothing.
- **`textureBase` on a cross or a crop** — 1.6's was a fallback for any sprite lookup
  that came up empty; here it reaches the face entries of a cube model and nothing else.
  `saltDeposit` is the only content that declares one and it is a cube.
- **An item's `drops` on `basic` or `seeds`** — `XieItem.onItemRightClick` turned a
  plain item into its drops and `XieItemSeeds.onItemUse` handed them over for planting,
  and only `food` carries them here, as the use remainder. Every `drops` on an item in
  every pack in this repo is on a food.

**Nothing in the content trips any of it**, which is the answer the audit predicted and
the run confirmed: the five packs report the same 20 properties across the same 15
definitions as before. The mechanism was therefore verified against a throwaway `probe`
pack in `xiecontent/` declaring one of each — a `sheds` on a `basic`, a `blockBounds` on
a `stem`, a `blocks` on a `food`, and a `textureBase` and a `drops` on a plain item —
which produced four lines and not one false positive from the real packs. Building a
pack to fire a report is the cheapest way to tell "this cannot happen" from "this does
not work", and it is worth doing again the next time a report goes quiet.

One hole of the same family is left, and named here so it is not rediscovered: **the
`PropertyMap` inside a drop entry is reported by nothing.** `"drops": {"tomato":
{"chance": 20, "amuont": 2}}` loses the amount in silence, because the report walks a
definition's own properties and not those of the structures hanging off it. Reaching
them means a non-consuming route to a `DropTable` from outside the model package, which
is a wider change than this one; nothing in the content declares anything but `chance`
and `amount` there today.

`spikey` and `fertilisable`
---------------------------

The two properties that report was going to catch anyway, both declared by
`_plox_update/nether_crops/flame_tree.json` and both now carried. Neither is a class
that turned out to be vanilla's — they are the opposite case, two of the smallest real
implementations in the 2013 tree.

**`spikey` is one point of generic damage to anything inside the block**, from
`XieBlock.onEntityCollidedWithBlock`:

```java
if (isSpikey && !world.isRemote) entity.attackEntityFrom(DamageSource.generic, 1);
```

Those two lines are copied into every one of the original's block classes —
`XieBlockLeaves`, `XieBlockLog`, `XieBlockPlant`, `XieBlockPane`, `XieBlockGrass`,
`XieBlockStem`, `XieBlockChest` and `XieBlockFurnace` each carry their own `isSpikey`
field and their own identical override — and `setIsSpikey` is on `IXieBlock`, so it
really was every archetype. That is `becomesWhenDestroyed`'s shape exactly, so it takes
`becomesWhenDestroyed`'s answer: one implementation in `XieSpikes`, and a one-line
override in each of the port's four block classes. An archetype with no class of its
own cannot be handed it — `stem` is vanilla's `StemBlock` — and so never reads the key,
which is what makes a definition declaring it there a reported line rather than a
silent drop.

`CactusBlock` is the vanilla block that does exactly this and is the shape copied: no
rate limiting of its own, because a damaged entity is invulnerable for the next twenty
ticks anyway, and no attention paid to `entityInside`'s `isPrecise` flag, which is a
hint about how deeply the entity overlaps that cactus also ignores. 1.6's method fired
on every tick of contact too. `hurtServer` rather than `hurt` is the `!world.isRemote`
guard said the way `SweetBerryBushBlock` says it.

**`fertilisable: false` refuses bone meal**, and is one line. `XiePlantBlock`
implemented `BonemealableBlock` unconditionally, so bone meal worked on a sapling that
had asked it not to. `XieBlockPlant.fertilisable` initialised to `true` and both
`XieItemFertiliser.onItemUse` and `BonemealHandler.attemptBonemealing` consulted it
before calling `fertilize`, so refusing in `isValidBonemealTarget` is where it belongs.
`JobBlock` read both spellings and so does this.

**`blazeTree`'s `blockBounds` was going missing as well**, and it is what makes `spikey`
reachable at all. `expandCommonBlockParameters` read `blockBounds` for every archetype,
leaves included; the port read it for `basic`, `log` and the growing blocks and not for
`tree`. It was the one property the unread report could not see, being structural, and
it is what the section above was built out of. The
canopy is a cross (`renderType: "flower"`) and a cross's model carries no box, so this
is purely the collision shape and the selection outline — the same split vanilla wheat
has. Inset to 0.1–0.9 it is also the difference between a spikey canopy that can hurt
you and one that cannot: a full cube stops a player with their bounding box flush
against the block's edge and a hair outside its cell, and `Entity.checkInsideBlocks`
deflates by 1e-5 before testing, so flush is outside. The measured stop positions say
it plainly — 109.8 against the inset canopy, and 117.7 and 125.7 against full-cube
leaves eight blocks further along, which is `wall + 0.1 - 0.3` against `wall - 0.3`.

A sprite index with no sheet is vanilla's own
--------------------------------------------

`blazeTree` declares `"tex": {"all": {"0": 31}}` and no `texFile`, and its canopy
rendered untextured because the slicer had no sheet to cut sprite 31 out of. Read as a
gap in the *content* — a `texFile` somebody forgot in 2013 — that is unfixable from
here, since the content repo would have to be edited to close it. It is not one.
`SpritesheetManager.getTextureFilepath` answered a definition that named no sheet with
`getDefaultTextureFile()`, which returns `/terrain.png`, so a bare index addresses
**vanilla's** sheet and always did.

Index 31 is where `Block.fire` drew from — `new BlockFire(51, 31)` — which is exactly
what the block is for. Three things say so at once, which is what makes this a reading
rather than a guess: the [2013 announcement][nethercrops] says a blaze tree's trunks
"are made from blaze rods, and their leaves from flame", the definition's
`"lightValue": 1.0` is fire's own light value, and cell 31 of the pack's own
`nether_crops.png` is empty — the index was never for that sheet.

`LegacyTerrain` is the translation from a sprite index to a modern texture id and
`XiePackEmitter.borrowedSprite` is where a face consults it. Nothing is copied into the
pack for one of these: the model points straight at `minecraft:block/fire_0`, which
keeps the flames animated and lets a resource pack redraw the canopy by redrawing
vanilla's fire — both of which a copy taken at emit time would lose. The pack summary
counts the models that borrow one, because a block drawn with somebody else's sprite is
worth saying out loud.

The table holds the one index the content addresses. `terrain.png` was retired in 1.5
and its layout is not derivable from anything modern, so an entry nothing addresses is
a claim about a picture no test can check; an index the table cannot answer for is
named in the log, with the definition that wants it, rather than guessed at. Filling it
out is the work of whoever brings in the pack that needs the next one.

[nethercrops]: https://github.com/XieX/xiecraft/blob/master/_posts/2013-02-25-nether-crops.md

A block item is drawn the way vanilla draws one
-----------------------------------------------

Found by putting `cheeseBlock` beside `zombiePigmelon`, which is the whole value of
running a pack the port has never seen: both are blocks that drop themselves, so both
get an item form, and only one of them had a drawing.

The item form of a block has no item sprite to work from, and vanilla answers that two
different ways depending on the block's shape. A sapling's item model is
`item/generated` over the block sprite, because a cross has no thickness to show in an
inventory slot. A melon's is the **block** model as its parent, so it appears in the
hand and the slot as the cube it is. The emitter took the flat route for both, which
worked for a sapling and for `cheeseBlock` — one `all` face, so one sprite named
`cheese_block.png` for a flat model to draw — and did not work for `zombiePigmelon`,
whose `tex` gives a `sides` and an `ends`. Its sprites are `zombie_pigmelon_sides.png`
and `zombie_pigmelon_ends.png`, there is no bare `zombie_pigmelon.png`, and the client
said so: `Missing textures in model xiesmod:item/zombie_pigmelon`. So the split is
`RenderStyle.isCube()` now, and a cube block's item form parents its block model.

Recipes that never reach the emitter
------------------------------------

`steel.json` writes `"contents"` where the format wants `"content"` in three of its six
recipes, and **1.6 lost the same three**: `JobRecipe.doWork` looks for `content`,
nothing else, and answers a missing one with `logFailure("Failed to find ingredients
for this recipe. Job undoable.")` and no recipe. So the three are reported and dropped,
which is the `nethwerwart` rule again.

What that exposed is an accounting hole rather than a behaviour one. **A declaration
the reader refuses is in none of the lists it returns**, so it is in no count the
registrar or the emitters keep either — the pack reported "129 recipes" and "Not
emitted: 1 recipes" while four of the six steel recipes were missing. The one skip it
did count is `greySteelBlock`, whose `greySteel` names nothing, because an
*unresolvable reference* is found at emit time where a *malformed declaration* is found
at parse time. Every `readX` in `XieFileReader` answers with an empty `Optional` for a
declaration it has refused, which is one place to count them, and the total is now said
outright at load: "3 declarations rejected and not counted above".

The schema follows the archetype
--------------------------------

`xie-content.schema.json` listed every block property on one `block` and every item
property on one `item`, with the archetype a property belonged to written as a prefix in
its description, so an editor offered `cookTime` on a bush. It is an `allOf` of
`if`/`then` branches keyed on `type` now, and **the branches come from the consumers,
not the prefixes**, because the prefixes were wrong often enough to matter. `spikey` said `tree:` and
is read by seven archetypes; `growthHeight` said `crop:` and is read by all four growing
ones; `container` and `cooked` said `food:` and are read for every item, `cooked` for a
block with an item form too; `flame` said `sword, bow` and a sword never reads it, while a
bow reads it as a flag rather than seconds; `power` said "speed multiplier" and is levels
of Power; `names` said "Ignored" and is what splits a definition into subtypes; and
`uses` and `enchantability` carried no prefix and a `tool` reads neither, its tier
deciding both. Eight properties the engine reads were missing outright — `shape`,
`punch`, `cannotHarvest`, `canCut`, `potionEffects`, and `cooked` and `smelted` on a
block — and are in their branches now.

Each property sits in the one branch whose `if` lists exactly the archetypes that read
it, so the value schema is written once; a branch whose list includes `basic` leaves out
`required: ["type"]`, which is what makes it apply to a definition with no type at all.
Three calls in it are judgement rather than transcription. **`stacksize` is offered only
where it can do something**: every archetype reads it, but a sword, bow, stick or tool
then sets a durability, which forces a stack of one over whatever was asked. **A stem is
not offered `tex`**, because it draws vanilla's melon stem whatever it declares — which
means `zombiePigmelonStem`'s `"tex": "#melonstem"`, the one thing in the content the old
schema rejected, now passes and is left to the reader's own diagnostic. **Keys that
nothing reads stay listed where porting a 2013 pack will meet them** — `smeltsInto`,
`toolMaterial`, `armorMaterial` and an item's `textureBase` — described as never read, so
the editor says what the unread-property report would. The value forms are untouched:
`damage` and `power` are read as whole numbers but stay `number`, because narrowing
them could only turn a pack that loads into one that fails validation. Nothing in a
branch is `required` except a stem's `grows`, the one property whose absence stops
registration; a sapling, seed or ripening block without `growsInto` registers and says
so.

Other systems
-------------

- **Biome words with no modern counterpart** — `marsh` and `shrubland` came from the
  biome mods of 2013 and nothing tags anything as either. Each takes its nearest
  neighbour in `BiomeNames`: a marsh is a wetland and joins `#c:is_swamp`, and a
  shrubland is dry open scrub and joins `#c:is_savanna`. The wild shrubs, which
  declared both, now reach every biome they asked for rather than two of four.
- **`seed` is `seeds`, and always was.** `JobItem.doWork` dispatched
  `subtype.equals("seeds") || subtype.equals("seed")` to one `doSeeds`. Only
  `cheese.json` and `zombie_pigmelon.json` wrote the singular, both in deferred packs,
  which is why a fully ported archetype was skipping definitions with "archetype not
  ported yet" over a spelling. Nothing else in the parser or the emitters keys off the
  literal string — `ItemDef.KNOWN_ARCHETYPES` and `SUPPORTED_ITEMS` were the two
  places, and the switch that dispatches them.
- **Soy's drop chances were sprite indices, and are its 2013 numbers again.** A drop's
  `amount` is independent rolls at `chance` percent each (`Droppables`, and
  `XieDataEmitter.addDropPools`), and `soyCrop` asked for three at 64, 65, 66, 67 and
  68 — about two beans from breaking a bush planted a moment before, out of a bean that
  is also the seed, so planting and breaking duplicated beans with no growing. 1.6 did
  exactly the same, but the numbers were never meant: `default/soy.xie` had 25, 50 and
  90, and on 2013-02-02 `753d12c` replaced the chance map with one shaped like a
  `xie_crops.png` tex map, which "Soy fix" then wrote into the real `tex` as 64–68. The
  June shuffle to five stages collapsed both maps through the same ranges, and those
  ranges carry the original table across as `0-2: 25, 3: 50, 4+: 90` — 0.75 beans from
  an unripe break, 1.5 at stage 3, 2.7 from a ripe one or a ripe pick. Xie chose that
  over vanilla's shapes: cocoa and nether wart give back exactly one, which `drops`
  cannot say while `amount` is one number per item, and a sweet berry bush gives back
  nothing young. No vanilla plant pays out for zero growth, and now neither does soy.
- **A crop that pays for its own seed unripe is warned about at emission.** Nothing
  failed on soy's numbers, so nothing said anything. `SeedReturns` asks every `plant`,
  `bush`, `crop` and `stem` whether it drops the `seeds` item whose `growsInto` names it
  at an age-0 chance times `amount` of one or more, within its own pack as `seedFor`
  does, and `XieDataEmitter` logs a warning per block with the average yield and writes
  the table regardless — it is not counted under "Not emitted", because it was. It
  reads past the consuming accessors, so it hides nothing from the unread-property
  report. Live content gives no warning, and `RealContentTest` holds it there.
- **Deferred content** — `_wip/` and `_plox_update/` are skipped by the `_` prefix
  convention. They contain a steel ore set, nether crops with a flame tree,
  a zombie pigmelon with a `stem` block, survival tool tweaks, and a `weapons` pack
  whose three swords and three bows all register. Copying a deferred directory into
  `<gamedir>/xiecontent/` as a pack of its own is how it gets exercised, because the
  `_` prefix is what skips it and the content repo should not be edited to run a
  test. Note that `_wip/steel/` sits one level deeper than the rest. Against a baseline
  of `weapons/` and `ultimatefist/` alone — 24 blocks from 16 definitions, 71 items from
  57, 89 block models, 71 item models, 155 textures, 24 loot tables, 117 recipes, 13
  alias tags, 0 block tags, 9 worldgen features, 5 trees and 8 burn times, with 0
  skipped drops, 0 recipes not emitted, 1 worldgen entry not emitted and 0 skipped
  culls:
  - `weapons/` and `ultimatefist/` add 7 items and 7 recipes and skip nothing.
  - `SurvivalTweaks/` and `_wheatOverride.json` add the two pickaxe overrides, the
    wheat loot table, 11 recipes and the five stone-tool culls, and skip nothing —
    `tweaks@furnace` names `clayBlock`, which was the one 1.6 name the rename
    table did not answer for, and is `minecraft:clay` in it now.
  - `nether_crops/` adds 6 blocks from 5 definitions, 5 items from 3, 3 loot tables,
    5 recipes, 2 block tags and a tree, and skips exactly one thing: the `nethwerwart`
    typo in the sticks section above. Its `spikey` and its `fertilisable: false` were
    the two properties nothing read, and `blazeTree`'s texture indices with no
    `texFile` looked like a third gap in the content until they turned out to name
    vanilla's own sheet. All three are carried now — see the three sections on them
    above.
  - `cheese/` added 1 block from 1 definition, 3 items from 2 — the third is
    `cheeseBlock`'s item form, since it drops itself — 1 block model, 3 item models,
    3 textures, 1 loot table and 5 recipes, and **skipped nothing at all**. It was the
    only deferred pack that arrived complete, and it is live content now, as
    `farming/foods/cheese.json`: the cookpot's `soupable` had been naming `cheeseSlice`
    all along. It contributes `cheeseSlice` to the
    existing `#xiesmod:protein` tag rather than making a fourteenth, and it is the
    pack that carries the first `smelt` written in a `recipes` section rather than as
    a `cooked` on the ingredient — `"bucketOfCheese": {"smelt": "milkBucket"}`, which
    `JobRecipe.doWork` dispatched to `doSmelting` and the reader dispatches the same
    way. Its two junk recipes, `"furnace": "dirt"` and `"milkBucket": "dirt,dirt"`,
    were emitted as written while it was deferred, because they were what the file
    said; they were test scaffolding, and were dropped when it went live. So was
    `"cheeseBlock": "bucketOfCheese"`, deliberately: the block is got by pouring the
    bucket out, which is also what gives the bucket back (see `XieSeedItem`), so it
    adds 2 recipes now rather than 5.
  - `_wip/steel/` adds 4 blocks from 4 definitions, 10 items from 10, 4 block models,
    10 item models and 2 recipes, and is the pack that earned this session's whole
    reporting pass. Nothing in it is an archetype problem — four `basic` blocks and
    ten items with no `type` at all, which is `doBasic` — and everything in it is a
    declaration going nowhere: **three of six recipes** to `contents` for `content`,
    **a fourth** to `greySteel` naming nothing, **five `smeltsInto`**, **ten
    `toolMaterial`/`armorMaterial`** stubbed out in 2013 as well, and **fourteen
    sprites** cut from an `xie_steel.png` the pack never shipped. Steel is therefore
    unobtainable and drops nothing when mined, and both are faithful: an empty
    `Droppables` dropped nothing in 1.6 too, `XieBlock.drops` being initialised to one
    rather than left null. It is a work in progress in the most literal sense, and the
    port's job with it was to say so in the log rather than to finish it.
- **Cooking Mod content** — the pack that held the 2013 stove and its stews is not
  in this repo and did not survive. The stove is back as the cookpot, rebuilt from
  the mod posting with a block entity and no menu — see [The cookpot](#the-cookpot)
  — and it is still the one thing here a datapack could not have supplied, which is
  the strongest remaining argument for the engine existing at all. Whatever else the
  Cooking Mod declared (the `chest`, `mounted`, `facing`, `pane`, `fence` and
  `glass` archetypes above are the likely candidates) stays dropped, because there
  is no file to port it from.

The acceptance run
------------------

Moved to [TESTING.md](TESTING.md), which is where it belongs: this file is temporary
and the run is not. What each of the fourteen checks measures, the three rules they
follow, what the run arranges and puts back, what it found on its first day and where
it does not reach are all there, along with the working notes below that were about
driving the game rather than about the port.

Working notes
-------------

Things that cost time once and should not cost it twice. What used to be in this
section was largely how to drive the game by hand; the acceptance run does that now,
and the notes that belong to the rig went to [TESTING.md](TESTING.md) with it. What is
left here is what the port itself cost time on, with the file that encodes each one.

- **Read the decompiled sources before writing API calls.** Extract from
  `neoforge/build/moddev/artifacts/minecraft-patched-26.1.2.94-sources.jar` and the
  NeoForge sources jar in `~/.gradle/caches/modules-2/`. Renames already found:
  `ResourceLocation` is `Identifier`, `noCollission` is `noCollision`,
  `ResourceKey.location()` is `identifier()`, and `BushBlock` is concrete so custom
  plants extend `VegetationBlock`. `AbstractArrow` and `Arrow` moved down into
  `net.minecraft.world.entity.projectile.arrow`, which is why grepping the sources
  jar for `projectile/AbstractArrow.java` finds nothing.
- **`Item.useOn` is `onItemUse`, and `HoeItem` is the shape to copy.** `UseOnContext`
  carries the level, the clicked position, the clicked face, the hand, the player and
  the stack, so nothing has to be threaded through. What has moved is how success is
  reported: `InteractionResult` is a sealed interface rather than a boolean or an
  enum, and `SUCCESS` now carries a `SwingSource` — `SUCCESS` swings the arm on the
  client and `SUCCESS_SERVER` waits for the server to say so, which matters because
  the client runs `useOn` too as a prediction. `PASS` is the old `return false`.
- **A skeleton does not fire through the bow item.**
  `AbstractSkeleton.performRangedAttack` builds its arrow with
  `ProjectileUtil.getMobArrow` and spawns it itself, so it reaches neither
  `use`/`releaseUsing` nor `createProjectile` — only `getSupportedHeldProjectiles`,
  through `getProjectile`. Summoning one holding a Xie bow therefore tests almost
  nothing, and looks like a test rig rather than the dead end it is. A bow is fired
  by a player right-clicking or not at all.
- **Copy vanilla's JSON schema, don't recall it.** Extract examples from
  `...-merged.jar` under `assets/minecraft/` and `data/minecraft/`. Items need
  *two* files since 1.21.4 — `models/item/x.json` and `items/x.json`.
- **`pack_format` is `Codec.INT`**, a bare major version. From format 65 on,
  `min_format` and `max_format` are also mandatory; those take `[major, minor]`.
- **`playerDestroy` is never called in creative** — see
  `ServerPlayerGameMode.destroyBlock`. Prefer `useWithoutItem` for harvest.
- **`Block#destroy` is the creative-safe break hook.** It is the modern
  `onBlockDestroyedByPlayer`: `ServerPlayerGameMode.removeBlock` calls it after the
  block has been cleared, on both the survival and the creative branch, which is what
  `becomesWhenDestroyed` needs. `MultiPlayerGameMode` calls it client-side too as a
  prediction, so guard on `isClientSide`.
- **A block with no `age` property needs the empty variant key** — `{"variants":
  {"": {...}}}`. Emitting `age=0` for a block that has no such property is a
  blockstate load error, not a no-op, and the same goes for naming `age` in a loot
  table's `block_state_property` condition.
- **`CropBlock` does not fit as a base.** Its age is fixed at `AGE_7` and its shape
  array is sized to match, but `hybridWheat` has nine stages; the property, state
  definition, shapes, growth roll and placement rule would all be overridden. It also
  cannot be the base for `bush` and `plant`, so using it would split one archetype
  family across two hierarchies. `CropBlock.getGrowthSpeed` is `protected static` and
  therefore only reachable by extending it — copy it, as the original did.
- **`XieBlockCrop.getCropGrowthRate` was used inverted.** The speed it returns is
  larger-is-better but feeds `nextInt(n) == 0`, where larger is slower, so good soil
  made a crop grow more slowly — and with the content's `growthRate` of 20 the
  product truncated to zero and the crop advanced on every random tick. `XieCropBlock`
  divides instead, which is what vanilla and `XieBlockPlant.updateTick` both meant.
  When a 2013 formula looks wrong, check it against `mod/` twice before reproducing it.
- **Numeric block ids still appear in content.** `Xie.parseBlock` accepted a bare
  number and indexed `Block.blocksList` with it, so `salt.json` says
  `"spawnsNear": "8,9"` — flowing and still water — and `"spawnsUnder": 0` for air.
  `LegacyBlockIds` is that table, kept apart from the name table in `ResourceNames`
  because an id is unambiguously a block where a name is not, and because 1.6 spent
  two ids on what is now one block with a blockstate.
- **`material` names in the content are not the ones the engine parsed.**
  `wildshrubs.json` says `"material": "plant"`, but `Xie.parseMaterial` only knew
  `plants`, and its fallback for an unknown name was reflection on `Material` and
  then `Material.ground`. So the shrubs were ground-material blocks in 2013, by
  accident. Infer plant-ness from `walkthroughable`, which the original really did
  read, rather than from the material string.
- **Texture indices are not always bare.** `hybridWheat_00.png` through `_08.png` are
  zero-padded to two digits; everything else in the content is not. The 2013 loader
  matched a filename regex rather than composing a name, so both were equally valid.
- **Open-ended metadata ranges.** `"8+"` means 8 is the final stage, not that
  sixteen exist. `MetaRange.openEnded` records this because `"14-15"` is a real
  closed range in the content and is otherwise indistinguishable.
- **Verify on the dedicated server.** `gradlew :neoforge:runServer` parses and
  validates every loot table, recipe and worldgen file at startup with no GUI. The
  client only registers the datapack when a world loads. That covers everything a
  file can be wrong about and nothing a block can be wrong about, which is what
  [the acceptance run](TESTING.md#the-acceptance-run) is for.
- **Half the effect names in the content never worked.** `Xie.parsePotionID` built
  `"potion." + word` and compared it against every `Potion.getName()`, so the word had
  to be Mojang's own field name — `moveSpeed`, `digSpeed`, `confusion` — and
  `potionNameTranslator` knew exactly one synonym, `speed`. `haste` was not one of
  them, so `chocolateMilk` asked for an effect that resolved to nothing and silently
  got none in 2013. The modern ids *are* those friendly words, so `PotionNames` maps
  both spellings and the content now means what it says. Nine names genuinely moved;
  the rest are the modern id in camelCase, which `ResourceNames.toPath` already
  splits.
- **There is no burn-time component.** `minecraft:fuel` does not exist: vanilla builds
  a `FuelValues` table from item tags at load. NeoForge's `neoforge:furnace_fuels`
  data map is how a pack adds to it, and its file has to live under
  `data/neoforge/data_maps/item/` — `DataMapLoader` keys the map by the *directory* it
  finds the file in, so writing it under this mod's namespace produces a data map
  named `xiesmod:furnace_fuels` that nothing reads. It is the only file the emitter
  writes outside its own namespace.
- **A smoker halves the burn time it is given**, so a fuel declared as 100 shows
  `lit_total_time: 50` there and 100 in a furnace. That is
  `AbstractFurnaceBlockEntity`, not a lost value.
- **A generated pack below vanilla cannot override vanilla.** `/datapack list enabled`
  reports load order and the *last* pack wins, and a `Pack.Position.BOTTOM` pack sits
  under `[vanilla (built-in)]`. A file written at a vanilla id from down there is read
  and then thrown away, with nothing in the log to say so — the only symptom is that
  the block goes on behaving exactly as it did. The overriding half of the output is a
  separate pack at `TOP` for this reason. Test an override at the state where it
  differs *most* from vanilla, not where it differs subtly: vanilla wheat at age 0
  drops one seed and `wheatOverride` drops nothing, which cannot be mistaken for a
  lucky roll, whereas a ripe plant drops much the same thing either way.
- **`ModifyDefaultComponentsEvent` fires at datapack load, not at registration.** It
  is a mod-bus event, but `DataComponentModifiers.apply` runs from
  `DataComponentInitializers.build`, which `ReloadableServerResources` calls with
  `lookupWithUpdatedTags`. Two things follow. The `HolderLookup.Provider` handed to a
  patch resolves generated tags for real rather than creating them unbound, which is
  what a tool's mining rules need; and
  `BuiltInRegistries.acquireBootstrapRegistrationLookup` is not available there —
  `MappedRegistry.createRegistrationLookup` calls `validateWrite` and throws once the
  registry is frozen.
- **`DataComponentMap.Builder.set` replaces**, which is what makes an item override
  work at all. A vanilla pickaxe already has a `minecraft:tool` from its
  `ToolMaterial`; setting the component again drops those rules rather than appending
  to them. `Item.Properties.durability` is the only method with no one-to-one
  component — it sets `max_damage`, `max_stack_size` and `damage` between them.
- **Registries are unfrozen while items register**, so an item's properties can name
  a block tag that nothing has defined yet.
  `BuiltInRegistries.acquireBootstrapRegistrationLookup` creates it unbound and the
  datapack fills it in later, which is exactly what vanilla's `ToolMaterial` does
  with `#minecraft:mineable/pickaxe`. `GameData` unfreezes everything, fires every
  registration event, and freezes again, so this holds for any registry earlier in
  the order than the one being filled.
- **The disabling condition is `neoforge:never`,** not `false`, under the key
  `neoforge:conditions` — `ConditionalOps.DEFAULT_CONDITIONS_KEY` and the id
  `NeoForgeMod.NEVER_CONDITION` registers. `SimpleJsonResourceReloadListener.scanDirectory`
  wraps every codec in `createConditionalCodec`, so a file whose conditions fail is
  skipped with a debug line rather than erroring, for recipes and everything else
  loaded that way.
- **A block model needs no `render_type`.** As of 26.1 the chunk layer comes from the
  *sprite*: `BakedQuad.MaterialInfo.of` asks `ChunkSectionLayer.byTransparency` about
  the stitched texture's alpha. Nothing in the NeoForge sources reads a `render_type`
  field any more.
- **`minecraft:block/leaves` tints, `block/cube_all` does not.** The vanilla leaves
  parent puts `tintindex: 0` on every face, which needs a registered block colour to
  mean anything. The original defaulted `enableColorisation` to off, so leaves are
  emitted as a plain cube and there is nothing to register.
- **References are `name:count:meta`, except where they are not.** `Xie.parseItemStack`
  reads a two-part reference as name *and count*, but `JobSpawn.initSpawn` splits its
  own key as name and *meta*. That inconsistency is why `fruit.json` had
  `"appleTree": "fruitLeaves:0"` through `"fruitLeaves:3"` — four aliases that all
  resolved to subtype 0, so in 2013 every one of the four fruit-tree spawn entries
  generated an apple tree. They are `fruitLeaves:1:0` through `1:3` in the content
  now; the four values enumerating the four `names` in order leaves no doubt what was
  meant, and it is the only content edit this port has needed.
- **Starting the server generates almost nothing.** "Preparing spawn area: 100%" is
  not 441 finished chunks: exactly *one* reaches `full` status, and only a chunk at
  `full` has had its features run. Scanning the region files after a plain start
  therefore finds no worldgen at all, whatever you emitted, and looks exactly like a
  bug. Force a real sample first — enable `rcon` in `neoforge/run/server.properties`,
  `forceload add` a few hundred chunks, `save-all flush`, then read the block
  palettes out of `world/dimensions/minecraft/overworld/region/*.mca`. 1024 forced
  chunks gave 1296 at `full` and a clear count of every generated block.
- **A `HolderSet` is one tag or a list of ids, never a list of tags.** A biome
  modifier's `"biomes"` field takes `"#c:is_plains"` or `["minecraft:plains", ...]`,
  and a list containing a `#tag` fails to parse with the unhelpful "Not a JSON
  object". A tag *file* has no such restriction, so an entry allowing several tags
  gets a biome tag of its own listing them.
- **`random_patch` is no longer how vanilla writes a patch.** As of 26.1 nothing in
  vanilla data uses it: `patch_pumpkin` is a `simple_block` feature whose placed
  feature does `count` then `random_offset` then `block_predicate_filter` itself.
  Copy that shape — it also maps onto the 2013 generator more directly.
- **Prefer `c:` biome tags to `minecraft:` ones.** `Spawnable.biomeCheck` matched a
  substring of the biome's display name, so `"biomes": "swamp"` caught other mods'
  swamps for free. NeoForge's `c:is_swamp` is what preserves that; vanilla has no
  `is_plains`, `is_swamp` or `is_desert` tag at all.
- **There is no "within n blocks" block predicate.** `spawnsNear` has to be written
  out as one offset test per position, which is `(2n+1)³` of them — 125 at the
  distance `salt.json` asks for and 2197 at `Spawnable`'s default of 6. The emitter
  caps the radius, which only ever makes the test stricter.
- **The run directory holds a copy of the content**, at
  `neoforge/run/xiecontent/mod-content`. It does not track edits to `../mod-content`,
  and the bundled copy is never extracted over it — delete the directory and the next
  launch installs the bundled content fresh from the build's own copy, which is the
  re-sync.
- **A block spritesheet is cut the same way an item one is.** The slicer used to be
  item-only, because the pack that needed it (`weapons`) has only items; `nether_crops`
  ships one sheet with a `sides` and an `ends` per block and no per-sprite PNGs, so
  every block in it rendered untextured. A block face now falls back to the sheet
  exactly as an item icon does, with `all` written under the bare sprite name because
  that is what a bare file means and what a cross or crop model asks for. A file
  always wins, so live content is untouched.
- **A `#minecraft:` tag written by this mod is additive, not an override.** Tags from
  every pack are merged unless one says `replace`, so `data/minecraft/tags/block/logs.json`
  in the content pack joins vanilla's rather than replacing it, and being at the bottom
  of the pack order costs it nothing. That is the opposite of a loot table at a vanilla
  id, which is why only those go to `_overrides/`.
