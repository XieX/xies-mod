Testing
=======

How this mod is verified, and what it costs to verify it. Two commands cover both
halves of it: `gradlew :common:test` is the parser and the content model, in seconds,
with no Minecraft anywhere near it, and `python acceptance/run.py` is everything that
needs a running game to be true at all. This file is about the second one — what each
check measures, why it is measured that way, and the two dozen things about driving
Minecraft from a script that each cost an hour to find once.

The acceptance run
------------------

Every *count* in this mod is verified on every run. `gradlew :neoforge:runServer`
parses and validates the whole generated datapack at startup, and the log states how
many blocks, items, models, loot tables, recipes, recipe advancements, tags, worldgen
features and burn times were emitted, plus what was skipped and why. The recipe and
advancement counts are equal by construction, and a different pair means a recipe
was written without its way into the recipe book.

Every *behaviour* used to be verified exactly once, by hand. Somebody started a
client, joined the dev server, drove synthetic input at the game window, built a
situation over rcon, read a result back, and wrote the conclusion into a document.
Nothing re-checked it afterwards, so a regression in `XieTreeBlock`'s harvest,
`XieStickItem.useOn`, `XiePlantBlock`'s bone meal refusal or `XieToolItem`'s
transforms would have gone unnoticed until a human happened to try it again.

`acceptance/` is that same run, repeatably, as one command:

```bash
python acceptance/run.py
```

It starts the server, joins a client, asserts a fixed list of in-game behaviours and
prints pass or fail for each with a summary, exiting non-zero if anything failed.
A hundred and ten assertions, about three and a half minutes end to end, unattended. `--list`
names the checks, `--only <name>` runs one, `--no-client` runs the one check that
needs no player — `counts` — and a failure presses `F2` and names the screenshot.

It needs Python 3 and a Windows desktop whose foreground it can take, because a
`useOn` is a real right-click at a real window and there is no command for it.

**The sixteen checks, in descending order of what only a running game can tell you.**

| check | what it asserts |
|---|---|
| `counts` | Every number in the emission summary, against the fixed baseline in `acceptance/checks/counts.py`. The one check that needs no world at all. |
| `stem` | A ripe `zombiePigmelonStem` fruits into `zombiePigmelon`, the fruit lands on the stem's own soil, and the stem becomes an attached stem *facing it*. Plus the null: a stem whose neighbours have stone under them ripens forever. |
| `fruit` | A right-click on a ripe leaf hands over its apple and leaves the leaf standing and bare, and gives nothing on a bare one; bone meal ripens a bare leaf and is refused by a `persistent` one; a leaf open to the air ripens on its own under random ticks while, over the same window, the persistent one stays bare and so does a bare leaf with a ripe one beside it. |
| `bush` | A right-click on a ripe tomato bush and a ripe cotton plant drops each back three stages and hands over one or two of its crop — never none, which is what `min` is for — and no seed; a tomato one stage short of ripe gives nothing to the same click. Breaking, through `/loot give ... mine`, gives a ripe tomato bush's seed and not its tomato, and a ripe soy crop's seed. |
| `sapling` | Bone meal grows a sapling into its tree, and both that tree and the same tree placed as a *feature* come up with a few leaves already ripe — every tree bears, which is the 2013 keystone's asymmetry dropped on purpose and read as a count over the canopy. |
| `decay` | A canopy beside a `blazeLog` measures one block from it and is still there after four hundred ticks of ticking; the same canopy on stone measures seven and dies. `XieTreeBlock` *is* a `LeavesBlock`, so this is the one generated tag entry that every fruit tree in the game stands on. |
| `spikey` | Walking into `blazeTree` costs health, and the stop position is `wall + 0.1 - 0.3` — the inset canopy — where the same walk into leaves that are not spikey stops at `wall - 0.3` and costs nothing. |
| `fertilisable` | `blazeTreeSapling` refuses bone meal from a dispenser and keeps the stack; a Xie sapling that does not refuse, and a vanilla one, both consume it. The top of a ripe corn stalk three high, its full `growthHeight`, keeps the stack too, and the top of one two high takes it. |
| `sickle` | Sixteen right-clicks on ripe nether wart clear sixteen blocks, cost sixteen durability and hand over nether wart, in survival; a block the sickle does not list is left alone and costs nothing. |
| `fist` | Stone becomes a gold block, dirt becomes air, and the bottom face does nothing — checked against the same block clicked from above a moment later. |
| `bows` | `redstoneBow` fires with the button still down, hands back redstone, disallows pickup and burns; the same bow with arrows instead fires nothing; `boneBow` fires nothing until release and its arrow is cold; `blazeBow` fires with an empty inventory and its arrow is creative-only. |
| `crafter` | A crafter fed the dressed-garden-salad recipe ejects the salad **and** the mayonnaise's bowl, which is `craftRemainder` and which no predicate can see. |
| `cookpot` | Water starts an empty pot and is refused by one with a base; a carrot in an unfuelled pot is still raw after `cookTime`, and coal cooks it in exactly that; the pot shows the meal its next bowl will be from the moment the carrot goes in, with `cooking` saying whether it is ready, and a bowl ladles the meal the tier predicts — one food a Weak Soup, one food and salt a Soup, a food, a porkchop and salt a Stew — and on an empty or unfinished pot serves nothing and is kept; rotten flesh, a food the pot leaves out on purpose and no fuel, moves nothing. |
| `ripen` | Six fresh cheese blocks, in the dark and on stone, all ripen into cheese blocks — which a plant's light rule would stall for ever. Bone meal from a dispenser does nothing to one and is kept, beside a vanilla sapling that takes it; broken before it ripens it yields nothing, beside a cheese block that drops itself. |
| `blocks` | A shrub comes away with its soil and a salt deposit with its sand, while a salt block, which is terrain, stays where it was put. |
| `cull` | Vanilla's stone sword recipe, which the `cull_fixture` pack culls, is gone from the recipe registry, and a vanilla recipe nothing culls and the fixture's own stone sword are still in it. |

**Three rules the checks follow**, which are the difference between a suite that
tells you something and one that agrees with you:

- **A null is taken against a positive control in the same run, by the same
  measurement.** A click that changes nothing looks exactly like a click that never
  reached the game, so the bottom-face refusal is asserted beside the top-face
  transform, the barren stem beside the fruiting one, and the refused bone meal
  beside two dispensers that fire.
- **A dropped click is retried and counted, not hidden.** `Game.click_until` clicks
  until the world says it landed and reports how many it took; one is the normal
  answer and a check that habitually needs three is a rig that wants looking at.
  Durability is asserted against the number of *uses* rather than the number of
  clicks sent, because a click the client never raycast reaches nothing.
- **What cannot be asserted is said out loud rather than dropped.** The run prints a
  `SKIP` line for the sickle's crunch, because no assertion can hear a sound. The
  2% `etherWart` is not waited for either, and is noted with its count instead.

**What it arranges, and puts back.** The run has to be able to happen on a working
tree somebody else is using, so `acceptance/rig/session.py` restores every one of
these afterwards: the four deferred packs and the fixture packs in
`acceptance/packs/` are copied into `xiecontent/` and anything else in there is moved
aside, since another pack would move the counts;
`server.properties` gets rcon and a flat world of its own called `acceptance-world`,
so an arena is a fill rather than a landscape and no manual test world is disturbed;
and `options.txt` gets `pauseOnLostFocus:false`, written while the client is not
running because a clean exit rewrites the file.

**What it found on its first day**, which is the argument for it existing at all:

- **`shedSaturation` did not hold.** A tree with nine of its fruit already lying under
  it shed a tenth in two runs out of five. The count is taken where the fruit is about
  to be put, and the keystone leaf sits inside the canopy, so the fruit was often about
  to be put on the tree's own foliage rather than on the ground — and a tree aiming at
  its own shoulder counts the fruit lying under it as lying nowhere near.
  `XieTreeBlock.groundBeside` skipped leaves and logs from then on. It is the sort of
  failure a human verifying a behaviour once cannot find: watching a tree shed proves
  that it sheds, and watching it stop proves nothing until you have watched it not
  stop. Shedding itself went in October 2026, replaced by fruit picked off the leaves
  — see `fruit` — and the check that found this went with it; the lesson did not.
- **The zombie pigmelon fruits**, which nothing had ever confirmed. It was the first
  thing the run was pointed at, and the archetype it was reasoned out of — vanilla's
  own `StemBlock`, with no class of this mod's own anywhere in it — turned out to be
  right in every particular, down to the attached stem's facing.
- **A bowl was fuel.** Vanilla's burn table lists a wooden bowl at a hundred
  ticks, and `XieStoveBlock.useItemOn` resolved a bowl the pot could not fill by
  falling through to the fuel branch — so a player who tried a pot too early lost
  the bowl to the fire, and the pot lit for five seconds. The `cookpot` check's
  first run found it in its first assertion, and the null it was written for
  (a bowl on an empty pot is kept) is now the guard: a bowl is never fuel. The
  same table is why the check's refused item is an apple rather than a stick.

**Where it does not reach.** Nothing here asserts a sound, a texture or anything else
on the client's screen — the client is present because the server needs a player, not
because the run looks at it. The screenshot on failure is evidence for a human, not an
assertion. `SurvivalTweaks/` is not among the four packs, so the two pickaxe overrides
and the wheat loot table are still verified by hand; the `cull` check uses a fixture
in its shape rather than its culls. The client and server
share `neoforge/run/xiecontent/`, so the run cannot show `XieContentCheck` refusing a
client whose content differs. That is checked by hand: start the server, add a
throwaway pack, start the client, and the join fails on the mod mismatch screen
with the extra pack in the client's version and not in the server's. Confirmed
that way in November 2026, against a matching client joining the same server
first.

Driving the game
----------------

Things that cost an hour once and should not cost it twice. Every one of them is
encoded in a file under `acceptance/`, and the encoding is the record rather than this
list: a fact that only exists in prose gets rediscovered, and one that a check depends
on announces itself the moment it stops being true.

- **Nothing random-ticks without a player**, and nothing scheduled-ticks either.
  Forceloading a few hundred chunks brings them to `full` and runs their features,
  but no crop, sapling or leaf block ever advances in them, whatever
  `random_tick_speed` says — and a dispenser or a crafter sitting on a redstone block
  takes `triggered=1` and then never fires, which looks like the thing under test
  being broken rather than the rig. Vanilla wheat, a vanilla oak sapling and a
  vanilla dispenser all behave identically, so this is the environment and not the
  mod. It is why the acceptance run joins a client at all, why every check moves the
  player into its own lane before building anything, and why `NEEDS_CLIENT` is true
  on checks that never click a thing.
- **No command right-clicks a block**, so a `useOn` cannot be driven from rcon at all
  — rcon can only build the situation and read the result. What closes the gap is
  that aim is a property of the player and not of the mouse: `/tp <player> <x> <y>
  <z> <yaw> <pitch>` fixes exactly which block and which face the crosshair is on
  (`pitch 90` is the block you stand on, `-90` the one overhead, `pitch 0` a side
  face), and the click itself is synthetic input to the focused game window. That is
  `Game.stand_on`, `Game.stand_under` and `acceptance/rig/winput.py`, where three
  things have to be right or the click vanishes without a word — each one an hour to
  find, and all three now asserted in code rather than remembered:
  - **`PostMessage` is not enough.** The game reads the mouse through SDL, which
    takes raw input on Windows, so it has to be `SendInput` — and the window has to
    genuinely hold focus, which a background process only gets by attaching its input
    queue to the foreground thread first (`AttachThreadInput`). `SetForegroundWindow`
    alone is refused and returns as though it worked, so the foreground is read back
    rather than assumed.
  - **`SendInput` checks `cbSize` against the real `sizeof(INPUT)`,** which is the
    size of its *largest* union member. A struct declared with only `KEYBDINPUT` in
    the union is 32 bytes where 40 is required: `SendInput` returns 0 and sets
    `ERROR_INVALID_PARAMETER`, and the symptom is indistinguishable from the game
    ignoring you. Both the struct size and the return value are checked.
  - **`pauseOnLostFocus` is on by default,** so the moment the driving process hands
    the window back, the game menu opens — and a click sent while it is closing is
    swallowed, which made the whole thing about one run in three. Turning it off in
    `neoforge/run/options.txt` takes it to four in four. The client rewrites
    `options.txt` on a clean exit, so the run writes it while the client is *not*
    running and restores it after killing rather than closing the client.
- **A click can be lost for reasons that are nobody's fault.** The client raycasts
  against its own copy of the world, so a click sent before the chunk has arrived
  picks nothing at all — which is why every teleport is followed by a settle, a move
  to a new lane by longer, and every state-changing click goes through
  `Game.click_until`, which retries and reports how many attempts it took. A lost
  click leaves the world exactly as it was, which is what makes the next note a
  different thing entirely.
- **The vanishing sapling, and the three things it is not.** Roughly once in ten runs
  the first sapling planted in a freshly cleared lane is air afterwards — not grown,
  not still a sapling — and only ever the first action in a lane. Three explanations
  are out, so that nobody spends the hour on them again:
  - **`TreeGrower.growTree` cannot leave air.** It clears the sapling, places the
    feature and puts the sapling back when the feature refuses. The emitted
    `two_layers_feature_size` carries no `min_clipped_height`, so `TreeFeature.doPlace`
    refuses anything short of the full height rather than placing a clipped tree, and
    `StraightTrunkPlacer` always writes a log at the origin. A `true` return means a
    log where the sapling was and a `false` one means the sapling: neither is air.
  - **The support rule cannot drop it.** `fruitSapling` declares no `growsOn`, so its
    ground is `VegetationBlock`'s `#minecraft:dirt` or farmland. The lane floor is
    grass, the soil it is planted on is dirt, and the grass that spreads back onto that
    dirt while it stands there is in the same tag as both.
  - **It is not the plant-and-bone-meal sequence itself.** Sixty plants in a tight
    loop — thirty of them the first in a freshly cleared lane, each with a real arrival
    and a real fill in front of it, with the position polled every tenth of a second
    from the moment it was planted — grew on the first click, every time. Whatever it
    is needs something a whole run has that a loop does not, and the likeliest
    candidate is the two minutes of no input at all that `stem` spends before the first
    click of the run.

  What is left is three mechanisms that air alone cannot tell apart, so the next
  occurrence is made to say which one it was rather than noted and forgotten.
  `_orchard.vanished` reads whether a sapling item is lying nearby — that is the block
  having popped, and the only one of the three that drops anything — whether there is
  anything in the column, which would be a tree that grew and a measurement taken in
  the wrong place, or neither, which is a break with no drops and on a driven client
  means a click that arrived as the wrong button. Both checks that plant a sapling
  print it and then set the situation up again, since air is neither the situation nor
  the result.
- **A leaf placed by a command keeps the distance it was placed with.** `/setblock`
  writes the block's default state, which for leaves is `distance=7`, and
  `LeavesBlock` recomputes only when a neighbour changes — so a canopy built on top of
  a trunk that is already there measures seven from a log directly beneath it and
  decays anyway. Build the canopy first and the trunk last: the log going in under the
  leaf is the neighbour change. `acceptance/checks/decay.py`.
- **`entityInside` needs the entity to have moved.** `Entity.move` records a
  `Movement` for the tick only inside `if (movementLength > 1.0E-7 || ...)`, and
  `checkInsideBlocks` walks that list — so a player teleported into a block and left
  standing there never triggers it, whatever the block is. A `/tp` onto a spikey block
  gives a null result, and so does the same test against a **cactus**, which is how
  the rig announced itself rather than the mod. `acceptance/checks/spikey.py` holds
  `W` into a two-block wall for it, and two traps in choosing a control are why the
  control is the mod's own non-spikey leaves:
  - **A cactus cannot be a wall.** `CactusBlock.canSurvive` refuses any solid
    horizontal neighbour, so a `fill`ed wall of it breaks on placement and the player
    strolls through the gap — which reads exactly like a block that does not hurt.
    `minecraft:sweet_berry_bush` is no better on stone: it needs `#minecraft:dirt`
    below.
  - **Leaves placed by `fill` decay.** `persistent` defaults to false, so a wall of
    them is gone by the time the player reaches it, Xie leaves included —
    `XieTreeBlock`'s decay is `LeavesBlock`'s, measured from `#minecraft:logs`.
    `[persistent=true]` is the fix.
- **A screenshot of the game is the game's own.** `PrintWindow` on an OpenGL window
  returns a stale frame, and the client rect is not the render size. `F2` through the
  same synthetic-input path writes a real PNG to `neoforge/run/screenshots/` and logs
  the filename, which doubles as the cheapest proof that input is landing at all. The
  run presses it on the first failure of a check and names the file in the report.
- **`--quickPlayMultiplayer` cannot be passed with `--args`**, which replaces the dev
  launcher's whole argument list, main class included. `runClient` takes
  `-Pxie.join=<host>` instead; see `neoforge/build.gradle.kts`.
- **Creative mode does not wear tools.** `ItemStack.hurtAndBreak` returns immediately
  for a player with infinite materials, so a durability assertion in creative fails
  against an item that is working perfectly. The sickle check runs in survival for
  exactly this, and every other click check runs in creative.
- **`weapon.mainhand` is whichever hotbar slot happens to be selected**, and the
  selection is saved in the world rather than reset on join. So `/item replace entity
  <player> hotbar.1` can quietly replace the thing you just put in the player's hand,
  and it will do it on every run against that world and none against a fresh one. The
  bow check stocks ammunition in `inventory.0` for that reason and then checks that
  the bow is still held, because "the bow fired nothing" and "the bow was not in the
  player's hand" are the same line in a report otherwise.
- **A dispenser applies bone meal, and needs no aim at all.** The behaviour registered
  for `Items.BONE_MEAL` calls `BoneMealItem.growCrop` on the block in front, which goes
  straight to `applyBonemeal` and the same `isValidBonemealTarget` the hand path asks.
  It also self-reports, which is what makes it the right rig for a refusal: the stack
  is only consumed when the bone meal took, and `growCrop` shrinks it whether or not
  `isBonemealSuccess` rolled true, so a control never fails by luck. Three ways to
  get nothing: a dispenser one block off fires at the air between; one above a ripe
  stalk fills the space its next segment needs, so it fires from the side; and
  building it where no player is standing, since nothing ticks there.
  `acceptance/checks/fertilisable.py`.
- **A crafter is what asks whether a craft remainder works**, since no predicate can
  see one: fill its slots with `/item replace block`, pulse it, and the remainder is
  left among what it ejects. `acceptance/checks/crafter.py`.
- **A crafter with no matching recipe ejects an ingredient**, so "the crafter emitted
  an item" does not mean it crafted. Read the item entity, don't count it.
- **A sapling will not grow into a neighbour's canopy**, so a test orchard planted
  three blocks apart looks like a bug in the growth code. `TreeFeature` checks a
  column one wider than the trunk for the whole height, and leaves are not
  `REPLACEABLE_BY_TREES`. Eight blocks apart is clear, and the acceptance run asserts
  the three-block case refuses rather than trusting the number.

Asking the game what happened
-----------------------------

The other half of the rig. Everything above puts the world into a state; these are
what read it back, and most of them are ways a command answers something that is not
what it looks like.

- **The block id is the definition key, not the display name.** `blazeTree` is
  `xiesmod:blaze_tree` even though its `name` is "Blaze Leaves" — the display name only
  reaches the lang file, and `names` subtypes are the one exception. A wrong guess is
  worse than a typo in a command: `fill ... xiesmod:blaze_leaves` answers "Unknown
  block type" once, in a reply that scrolls past, and then every measurement afterwards
  is taken against a wall that was never built. `ls _generated/assets/xiesmod/blockstates/`
  is the list, and `Game.cmd` shouts about that reply rather than passing it on — see
  the note on refused commands below.
- **A refused command changes nothing and says so where nobody is looking.** That is
  the shape of the worst hour of building the acceptance run: one trailing space made
  every `/fill` an "Incorrect argument for command", so no lane was ever cleared, and
  the checks that noticed were the ones whose blocks happened to need clearing. Every
  reply is scanned for the handful of things the parser says when it has refused —
  `Game.MISTAKES` — and any of them is shouted about immediately.
- **`/fill` caps at 32768 blocks** and says so, which is easy to miss when the reply
  scrolls past — a test area that was never actually cleared then blocks everything
  planted in it. `Game.fill` splits anything larger into layers.
- **`/setblock` with the block already there is a no-op**, and says "Could not set
  the block" in a reply that scrolls past. Pulsing a crafter or a dispenser by
  re-placing the redstone therefore leaves the old `triggered` state and does
  nothing; clear the position to air first, which is `Game.replace`.
- **`setblock` does not ask `canSurvive`.** Placing a plant on ground it could never
  stand on succeeds and stays there until something updates the neighbourhood, so
  "the block is still there" proves nothing about its support rule. Change the block
  *below* it and read the result — that is the update, and it tests both directions in
  two commands. `acceptance/checks/blocks.py`.
- **`/execute if block` is the state probe; `/say` is not.** A `run say` fires into
  chat, which the rcon reply never carries, so a test written that way answers with an
  empty body whichever way it went. Bare `execute if block <pos> <block>` answers
  "Test passed" or "Test failed" in the reply itself, and takes a `#tag` and a
  blockstate as happily as a block id — which is how the run reads a ripe leaf
  and an attached stem's facing without touching an item.
- **Entities can be counted, not just found.** `execute store result score <holder>
  <objective> if entity <selector>` stores the number matched rather than a boolean,
  and stores 0 rather than failing, which is the only way to ask "how many" in one
  command. `Game.count_entities`. Items in a player's hands are counted with
  `/clear <player> <item> 0`, which counts without taking anything — and counting
  drops on the ground instead is counting a race, because a drop at a player's feet
  is in their inventory within the second.
- **Data components can be asserted from rcon** without anyone eating anything:
  `/execute if items entity <player> container.* <item>[<component>=<value>]` compares
  against the stack's *effective* components, defaults included, where `/data get`
  only ever shows the patch. `[!<component>]` tests absence, which is the presence
  test spelt backwards. Calibrate the literal against a plain vanilla-shaped item
  first — matching one is proof the syntax is right, and a failed match otherwise
  looks the same as a missing component.
  - **`[!minecraft:enchantments]` is never true**, because every item carries an empty
    `minecraft:enchantments` by default — a plain vanilla bow fails the test exactly as
    a Xie one does, which is the calibration step above earning its keep again. The
    test for "carries no enchantment" is `[minecraft:enchantments={}]`, the empty
    value rather than the absent component.
  - **A float component value cannot be matched with the obvious literal.** An item
    predicate compares the decoded component for equality, and an attack speed built
    from a `float` widens to a `double` that is not the one an SNBT `-2.4d` parses to:
    vanilla's own iron sword only matches `-2.4000000953674316d`. It fails silently
    and looks exactly like a missing modifier, which is why the calibration step
    matters — the same literal against a vanilla item fails first and says so.
- **`/recipe give <player> <id>` is a recipe-registry probe.** The argument resolves
  against the recipes that actually loaded, so a culled or absent one answers
  "Unknown recipe" while a live one answers "Unlocked 1 recipe(s)" — no crafting, no
  inventory, no ambiguity. It needs a real player: with `@a` and nobody online the
  target check fails first and both cases answer "No player was found", which reads
  like a result and is not one. Take the recipe before giving it, or a second run
  against the same world answers "no recipes were unlocked" for a recipe that is
  perfectly present.
- **Gamerules were renamed in 26.1, not merely made snake_case.** `randomTickSpeed`
  is `random_tick_speed`, but `doDaylightCycle` is `advance_time`, `doMobSpawning` is
  `spawn_mobs`, `naturalRegeneration` is `natural_health_regeneration`,
  `doImmediateRespawn` is `immediate_respawn`, and `doFireTick` is gone altogether in
  favour of `fire_spread_radius_around_player`. Every old name fails with a bare
  "Incorrect argument for command", so a rule you thought you set is simply not set.
  `Game.GAMERULES` is the list the run uses; `net.minecraft.world.level.gamerules`
  is the authority.
