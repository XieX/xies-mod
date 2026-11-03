"""The emission counts, which the server states at every startup.

Every count in this port is verified on every run; this check is only the
assertion that nothing has moved. It reads the server's own summary lines
rather than the generated pack, because those lines are what a human reads
when they want to know what the build did.

A change to any of these that is not the direct result of a gap somebody
closed is a regression — and if it *is* the result of one, the number here is
what to update, deliberately and in the same commit.
"""

import re

NEEDS_CLIENT = False

#: Marker, pattern, and the expected numbers, with all four deferred packs and
#: the live content in ``xiecontent/``.
BASELINES = [
    (
        "packs",
        r"Xie content: (\d+) packs, (\d+) blocks, (\d+) items, (\d+) recipes",
        ("packs", "block definitions", "item definitions", "recipes"),
        (5, 27, 75, 118),
    ),
    (
        "declarations rejected",
        r"Xie content: (\d+) declarations rejected",
        ("rejected",),
        (3,),
    ),
    (
        "registrations",
        r"queued (\d+) blocks and (\d+) items from (\d+) block and (\d+) item definitions, "
        r"and (\d+) items overriding",
        ("blocks", "items", "block definitions", "item definitions", "overrides"),
        (36, 93, 27, 75, 0),
    ),
    (
        "emitted pack",
        r"(\d+) block models, (\d+) item models, (\d+) textures, (\d+) loot tables, "
        r"(\d+) recipes, (\d+) recipe advancements, (\d+) alias tags, (\d+) block tags, "
        r"(\d+) worldgen features, (\d+) trees, (\d+) burn times, (\d+) vanilla files overridden",
        ("block models", "item models", "textures", "loot tables", "recipes",
         "recipe advancements", "alias tags", "block tags", "worldgen features", "trees",
         "burn times", "vanilla overrides"),
        (114, 93, 183, 38, 127, 127, 15, 2, 9, 5, 7, 0),
    ),
    (
        # The flame tree, and nothing else: `blazeTree` addresses vanilla's terrain.png
        # rather than a sheet of its own, so its canopy draws minecraft:block/fire_0
        # instead of a sprite this pack wrote. A zero here is a canopy of missing
        # texture, which is what it was before LegacyTerrain existed.
        "vanilla textures borrowed",
        r"Borrowed a vanilla texture for (\d+) models",
        ("models",),
        (1,),
    ),
    (
        "not emitted",
        r"Not emitted: (\d+) drops, (\d+) recipes, (\d+) worldgen entries and (\d+) culls",
        ("drops", "recipes", "worldgen entries", "culls"),
        (0, 1, 1, 0),
    ),
    (
        "properties nobody reads",
        r"(\d+) properties across (\d+) definitions are declared by content and read by nothing",
        ("properties", "definitions"),
        (19, 14),
    ),
]


def run(game, expect):
    log = "\n".join(game.session.server_log.snapshot())

    for name, pattern, fields, expected in BASELINES:
        found = re.search(pattern, log)
        if not found:
            expect.that(name, False, "the server never said it")
            continue
        actual = tuple(int(value) for value in found.groups())
        moved = [
            "%s %d (was %d)" % (field, was, should)
            for field, was, should in zip(fields, actual, expected)
            if was != should
        ]
        expect.that(name, not moved,
                    ", ".join(moved) if moved else
                    ", ".join("%d %s" % (n, f) for n, f in zip(actual, fields)))
