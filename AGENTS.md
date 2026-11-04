Agent notes
===========

For coding agents working in this repository. [README.md](README.md) says what the
mod is and how the content format works, [TESTING.md](TESTING.md) how it is verified,
and [TODO.md](TODO.md) what is unfinished.

How to work here
----------------

- **Fit in with vanilla.** Before writing behaviour, find the vanilla block, item or
  data format that already does it and copy its shape. Prefer emitting data (JSON in
  the generated packs, data components, tags) to adding code. Drop what vanilla now
  covers rather than shipping a worse duplicate.
- **Keep the engine content-agnostic.** Nothing in `common/` or `neoforge/` may name
  anything from `base-content/`. Other packs load from `xiecontent/` beside it,
  including 2013-era ones.
- **New behaviour is Xie's call.** Ask before inventing a mechanic, a property or a
  number that content will depend on. `mod/` is the 2013 source, kept for reference:
  read it to answer how something used to work.
- **Fix content bugs in the content**, not with workarounds in the engine.
- **Don't commit.** Xie commits their own work. Leave the tree for review and say
  what changed.
- **Minimal diffs, LF line endings.** `.gitattributes` enforces LF. Comments describe
  what the code does, not its history. Python's `open(p, "w")` on Windows writes CRLF
  and cp1252, so pass `newline=""` and `encoding="utf-8"`.
- **Docs are part of the change.** Update README.md and TESTING.md when behaviour
  they describe moves, `base-content/README.md` when the content it describes
  changes, and delete a TODO.md section when its work lands. A content
  change that moves a count updates the baselines in `acceptance/checks/counts.py` in
  the same change.

Verifying
---------

- `./gradlew :common:test` is the parser and content model, in seconds.
- `./gradlew :neoforge:runServer` parses and validates every generated loot table,
  recipe and worldgen file at startup. It covers everything a file can be wrong
  about and nothing a block can be wrong about. Grep its log for "Failed to generate
  the Xie content pack" before trusting anything measured in the world.
- `python acceptance/run.py --no-client` or `--only <check>` may be run freely. **Ask
  before a full client run**: it takes over the desktop's foreground for minutes.
- Stop any server you start. A stray dev java process holds port 25565 and the log
  file, and the next launch fails confusingly; find it with
  `Get-NetTCPConnection -LocalPort 25565`.
- When a report goes quiet, build a throwaway pack in `xiecontent/` that should trip
  it. That is the cheap way to tell "this cannot happen" from "this does not work".

The run directory
-----------------

- **`neoforge/run/xiecontent/base-content` is a copy**, extracted once and never
  overwritten. It does not follow edits to `base-content/`. Move it away and the
  next launch extracts a fresh one.
- **Other directories in `neoforge/run/xiecontent/`** load as packs of their own and
  move the counts. Deferred packs copied there by hand are the usual culprit.
- **A shell whose working directory is inside `_generated/` locks it** on Windows,
  and the next emission fails. Use absolute paths.
- **Starting the server generates almost nothing.** "Preparing spawn area: 100%"
  leaves one chunk at `full` status, and only a `full` chunk has run its features.
  To measure worldgen, enable rcon in `neoforge/run/server.properties`,
  `forceload add` a few hundred chunks, `save-all flush`, then read the block
  palettes out of the region files.
- The Grep tool skips gitignored paths, `neoforge/run/` among them. Search there
  explicitly.

Minecraft 26.1 and NeoForge
---------------------------

- **Read the decompiled sources before writing an API call.** Minecraft's are in
  `neoforge/build/moddev/artifacts/minecraft-patched-<version>-sources.jar`, and
  NeoForge's sources jar is in `~/.gradle/caches/modules-2/`. Renames that cost time:
  `ResourceLocation` is `Identifier`, `ResourceKey.location()` is `identifier()`,
  `noCollission` is `noCollision`, plants extend `VegetationBlock`, and the arrows
  live in `net.minecraft.world.entity.projectile.arrow`.
- **Copy vanilla's JSON, don't recall it.** Extract examples from the merged jar's
  `assets/minecraft/` and `data/minecraft/`. An item needs two files:
  `models/item/x.json` and `items/x.json`.
- **`pack_format` is a bare integer**, and from format 65 `min_format` and
  `max_format` are required as `[major, minor]`.
- **A block with no `age` needs the empty variant key**, `{"variants": {"": ...}}`.
  Naming a property a block doesn't have, in a blockstate or a loot condition, is a
  load error.
- **A block model needs no `render_type`.** The chunk layer comes from the sprite's
  alpha. `minecraft:block/leaves` tints every face and needs a registered block
  colour; `cube_all` does not.
- **`playerDestroy` is not called in creative.** `Block#destroy` is the
  creative-safe break hook, and the client calls it too as a prediction.
- **`InteractionResult` is a sealed interface.** `SUCCESS` swings on the client,
  `SUCCESS_SERVER` waits for the server, and `useOn` runs on the client too as a
  prediction. `HoeItem.useOn` is the shape to copy.
- **The generated packs sit at the bottom of the pack order on purpose**, so a
  user's pack can override them. A file written there at a vanilla id is read and
  then discarded, with nothing in the log, which is why vanilla overrides go to
  `_generated/_overrides/`, a separate pack at the top. Tags merge rather than
  override, so a `#minecraft:` tag needs no such move. Test an override where it
  differs most from vanilla, so a lucky roll can't pass for it working.
- **`ModifyDefaultComponentsEvent` fires at datapack load**, not registration. Tags
  resolve there, and the bootstrap registration lookup throws. `DataComponentMap.Builder.set`
  replaces a component; it does not append to it.
- **Registries are unfrozen while items register**, so an item can name a block tag
  nothing has defined yet, as vanilla's `ToolMaterial` does.
- **Burn time is a data map**, `data/neoforge/data_maps/item/furnace_fuels.json`. The
  directory names the map, so the file cannot go under this mod's namespace. A
  smoker shows half the burn time a furnace does.
- **A file is disabled with `"neoforge:conditions": [{"type": "neoforge:never"}]`.**
- **A `HolderSet` is one tag or a list of ids, never a list of tags.** A list
  containing a `#tag` fails with "Not a JSON object", so several tags need a tag file
  of their own.
- **Write a patch the way `patch_pumpkin` does.** Vanilla no longer uses
  `random_patch`: a `simple_block` feature, placed with `count`, `random_offset` and
  `block_predicate_filter`.
- **Prefer `c:` biome tags.** Vanilla has no `is_plains`, `is_swamp` or `is_desert`,
  and other mods tag their biomes into `c:`.
- **There is no "within n blocks" block predicate.** It is one offset test per
  position, `(2n+1)³` of them, so the emitter caps the radius.
- **Biome modifiers are read when a world opens.** `/reload` does not apply them.
- **Channel versions are negotiated before registries sync**, and a
  `RegisterConfigurationTasksEvent` task runs after both. A check that must beat the
  registry error goes in a channel version, which is how `XieContentCheck` works.
- **A skeleton does not fire through the bow item**, so summoning one holding a Xie
  bow tests almost nothing. Only a player fires a bow.
