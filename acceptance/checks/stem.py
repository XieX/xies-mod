"""A stem fruiting, which is the archetype nothing has ever watched work.

``stem`` needs no class at all — it is vanilla's ``StemBlock`` with four things
filled in from the definition — so it was reasoned entirely out of vanilla and
verified only by the datapack loading. What that leaves unchecked is the whole
point of the archetype: a stem fruiting into a block of this mod's own, and
the attached stem taking the right facing.

Two things about ``StemBlock.randomTick`` shape the rig here. It only fruits at
light 9 or better, which the arena's open sky gives it; and the fruit needs a
*neighbouring* position that is air with the stem's own soil under it — which
is the decision recorded in ``XieRegistrar``, that 1.6 hardcoded the fruit's ground
to the untilled form of the very ground the stem stands on, so
``plantType: nether`` puts both on soul sand.

The null case is the second half of that decision, taken against the positive
control in the same run: a stem whose neighbours have *stone* under them ripens
forever and never fruits at all.
"""

import time

from ..rig.arena import GROUND, Y, lane

#: Not because anything is clicked, but because nothing random-ticks without a
#: player and forceloading is not enough.
NEEDS_CLIENT = True

STEM = "xiesmod:zombie_pigmelon_stem"
ATTACHED = "xiesmod:attached_zombie_pigmelon_stem"
FRUIT = "xiesmod:zombie_pigmelon"
SOIL = "minecraft:soul_sand"

#: Six stems that can fruit and one that cannot. Six because a ripe stem fruits
#: on a growth roll of 1 in 26 per random tick, and six of them is the
#: difference between waiting a minute and waiting ten seconds.
FRUITFUL = 6
SPACING = 5

NEIGHBOURS = {
    "east": (1, 0),
    "west": (-1, 0),
    "south": (0, 1),
    "north": (0, -1),
}

TIMEOUT = 120.0


def run(game, expect):
    x0, z0 = lane("stem")
    game.enter(x0 + SPACING * FRUITFUL / 2.0, z0 + 6, pitch=30.0)
    game.clear((x0 - 2, z0 - 2), (x0 + SPACING * (FRUITFUL + 2), z0 + 2), floor="minecraft:stone")

    fruitful = [(x0 + SPACING * index, Y, z0) for index in range(FRUITFUL)]
    barren = (x0 + SPACING * (FRUITFUL + 1), Y, z0)

    for stem in fruitful:
        _cell(game, stem, SOIL)
    # The one that cannot fruit: its own soil below it, stone under everything a
    # fruit could land on.
    _cell(game, barren, "minecraft:stone")

    game.random_ticks(400)
    try:
        started = time.monotonic()
        attached = game.wait_until(lambda: _attached(game, fruitful), TIMEOUT)
        fruited = attached or _fruit_beside(game, fruitful)
    finally:
        game.random_ticks(3)

    if not expect.that("a ripe stem fruits", fruited,
                       "" if fruited else "nothing beside any of %d stems in %.0fs"
                       % (FRUITFUL, TIMEOUT)):
        return

    stem = fruited
    took = time.monotonic() - started
    facing = _facing(game, stem)
    fruit = _fruit_beside(game, [stem], want_position=True)
    expect.note("%s at %s after %.0fs" % (FRUIT.split(":")[1], fruit, took))

    expect.that("the fruit is this mod's own block", bool(fruit),
                "" if fruit else "the stem attached itself to nothing")
    if fruit:
        expect.that("the fruit stands on the stem's own soil",
                    game.block_is((fruit[0], GROUND, fruit[2]), SOIL))
    expect.that("the stem it grew from becomes an attached stem",
                game.block_is(stem, ATTACHED))
    expect.that("the attached stem faces its fruit", bool(facing) and bool(fruit)
                and _offset(stem, facing) == fruit,
                "facing=%s, fruit at %s" % (facing, fruit))

    # The null, taken the same way and in the same run as the control above.
    anyway = _fruit_beside(game, [barren], want_position=True)
    expect.that("a stem whose soil does not reach the neighbours never fruits",
                not anyway, "fruit at %s" % (anyway,) if anyway else "")
    expect.that("that stem is ripe rather than stalled",
                game.block_is(barren, "%s[age=7]" % STEM))


def _cell(game, stem, under_neighbours):
    """One stem on its soil, with what a fruit would land on around it."""
    x, y, z = stem
    game.fill((x - 1, GROUND, z - 1), (x + 1, GROUND, z + 1), under_neighbours)
    game.setblock((x, GROUND, z), SOIL)
    game.fill((x - 1, y, z - 1), (x + 1, y, z + 1), "minecraft:air")
    game.setblock(stem, "%s[age=7]" % STEM)


def _attached(game, stems):
    """The cheap poll: a stem and its fruit are set in the same tick."""
    for stem in stems:
        if game.block_is(stem, ATTACHED):
            return stem
    return None


def _fruit_beside(game, stems, want_position=False):
    for stem in stems:
        for offset in NEIGHBOURS.values():
            beside = (stem[0] + offset[0], stem[1], stem[2] + offset[1])
            if game.block_is(beside, FRUIT):
                return beside if want_position else stem
    return None


def _facing(game, stem):
    for direction in NEIGHBOURS:
        if game.block_is(stem, "%s[facing=%s]" % (ATTACHED, direction)):
            return direction
    return None


def _offset(stem, direction):
    dx, dz = NEIGHBOURS[direction]
    return (stem[0] + dx, stem[1], stem[2] + dz)
