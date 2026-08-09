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

Format
------

A content pack is a directory of `.xie` files (JSON), plus optional `.loc` display
names and a `textures/` tree. Files and directories starting with `_` are skipped.

```json
{
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
- [x] Registrar: definitions to `DeferredRegister` (`bush` blocks, `food`/`seeds` items)
- [x] Emitter: generated resource pack (blockstates, models, textures, lang)
- [x] Emitter: generated datapack (loot tables from `drops`, recipes, alias tags)
- [ ] `cull` recipes, which remove vanilla recipes
- [ ] Multi-variant items (`names` + `inheritMeta`) as separate items
- [ ] Remaining block and item archetypes
- [ ] Vanilla overrides

The generated pack is written to `<gamedir>/xiecontent/_generated/` on every launch
rather than served from memory, so you can open it and see exactly what a `.xie`
file produced. One directory is registered twice, as a resource pack and as a
datapack, so `assets/` and `data/` sit side by side in it.

Aliases are the clearest win from the port. `$salady` used to be expanded into the
cartesian product of concrete recipes at load time, turning one sammich recipe with
three alias slots into hundreds of registered ones. It is now a single item tag and
the recipe stays a single recipe.

`cull` has no equivalent. It removed every vanilla recipe producing a given item,
and neither vanilla nor NeoForge offers recipe removal by output — only replacement
of a recipe by id, which is not the same question. Cull entries are skipped and
counted in the emitter's summary line.

Anything a recipe or drop names is checked against the registry before it is
written, so the parts of the content whose archetypes are not ported yet are left
out rather than emitted as JSON that fails to load. The counts in the log say how
much that is.

Textures need no migration: the `.xie` files still address spritesheet indices, but
`textures/` is already one PNG per sprite named `<definition>[_<meta>].png`. The
sheet indices are only needed as a fallback for packs that never migrated.
