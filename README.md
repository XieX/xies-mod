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
- [ ] Emitter: generated resource pack and datapack (models, lang, loot, recipes)
- [ ] Remaining block and item archetypes
- [ ] Vanilla overrides

Textures need no migration: the `.xie` files still address spritesheet indices, but
`textures/` is already one PNG per sprite named `<definition>[_<meta>].png`. The
sheet indices are only needed as a fallback for packs that never migrated.
