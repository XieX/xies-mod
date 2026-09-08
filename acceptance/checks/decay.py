"""Leaf decay measured from ``#minecraft:logs``, and the tag entry that feeds it.

``XieTreeBlock`` *is* a ``LeavesBlock``, so decay is not this mod's code at all:
a leaf keeps a ``distance`` from the nearest block in ``#minecraft:logs`` and
dies when it reaches seven. That makes one generated file load-bearing for every
fruit tree in the game — ``data/minecraft/tags/block/logs.json``, which puts
``blazeLog`` in vanilla's own tag — and it is additive rather than an override,
so being at the bottom of the pack order costs it nothing.

Nothing else in the run would notice if that entry went. The tree checks read
their canopy within a second of growing it, which is long before a leaf with
nowhere to measure from would have random-ticked; the symptom would arrive
hours later, in somebody's world, as a fruit tree that is all trunk. So this is
the check that watches a canopy for long enough to die.

Three towers, identical but for what they are built of, and the null is taken
against two positive controls by the same measurement:

* ``blazeLog``, which is in the tag because this mod's pack says so,
* a vanilla oak log, which is in it because Mojang says so — the calibration
  step, since a canopy that stood on neither would mean the measurement was
  wrong rather than the tag missing,
* and stone, which is in nothing, and whose canopy is supposed to die.

One rig fact this is built on: **a leaf placed by a command keeps the distance
it was placed with.** ``/setblock`` writes the default state, which is
``distance=7``, and ``LeavesBlock`` only recomputes on a neighbour change. So
each tower is built canopy first and trunk last — placing the log under the leaf
is that neighbour change, and it is also why a wall of leaves ``fill``ed in one
go decays whatever is standing next to it.
"""

import time

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

CANOPY = "xiesmod:blaze_tree"
XIE_LOG = "xiesmod:blaze_log"
VANILLA_LOG = "minecraft:oak_log"
UNTAGGED = "minecraft:stone"

#: Tall enough that the canopy is clear of the ground it might otherwise be
#: confused with, and short enough to build in three commands.
TRUNK = 3

#: Far enough apart that no canopy can measure from a neighbour's trunk, which
#: reaches six blocks.
APART = 8

DECAY_TIMEOUT = 60.0

#: How long the two surviving canopies go on ticking after the third has died,
#: so that a tower which is supposed to stand is given a fair chance to fall.
WINDOW = 10.0


def run(game, expect):
    x0, z0 = lane("decay")
    game.enter(x0 + APART, z0 + 5)
    game.clear((x0 - 4, z0 - 4), (x0 + 2 * APART + 4, z0 + 6), floor="minecraft:grass_block")
    game.gamemode("creative")

    towers = (
        ("a Xie log", XIE_LOG, x0),
        ("a vanilla log", VANILLA_LOG, x0 + APART),
        ("an untagged trunk", UNTAGGED, x0 + 2 * APART),
    )
    canopy = {}
    trunk = {}
    for label, block, x in towers:
        canopy[label] = (x, Y + TRUNK, z0)
        trunk[label] = (x, Y + TRUNK - 1, z0)
        game.setblock(canopy[label], CANOPY)
        for y in range(Y, Y + TRUNK):
            game.setblock((x, y, z0), block)

    # ------------------------------------------------------------- the tag
    expect.that("a Xie log is in #minecraft:logs",
                game.block_is(trunk["a Xie log"], "#minecraft:logs"),
                "the generated tag, merged into vanilla's rather than replacing it")
    expect.that("and the control tower is in nothing, which is what makes it one",
                not game.block_is(trunk["an untagged trunk"], "#minecraft:logs"))

    # -------------------------------------------------------- the distance
    standing = expect.that("a canopy beside a Xie log measures one block from it",
                           game.block_is(canopy["a Xie log"], "%s[distance=1]" % CANOPY),
                           "which is the whole of what the tag entry buys")
    expect.that("the same canopy beside an untagged trunk measures the full seven",
                game.block_is(canopy["an untagged trunk"], "%s[distance=7]" % CANOPY),
                "seven is the value that decays")
    expect.that("neither canopy is persistent, so neither survives by the flag",
                all(game.block_is(canopy[label], "%s[persistent=false]" % CANOPY)
                    for label, _, _ in towers),
                "a leaf a player places is persistent and one a command places is not")

    if not standing:
        expect.skip("what decays and what does not",
                    "the canopy never measured from the trunk, so waiting proves nothing")
        return

    # ----------------------------------------------------------- the decay
    game.random_ticks(400)
    try:
        died = game.wait_until(
            lambda: game.block_is(canopy["an untagged trunk"], "minecraft:air"),
            DECAY_TIMEOUT)
        time.sleep(WINDOW)
        held = {label: game.block_is(canopy[label], CANOPY) for label, _, _ in towers}
    finally:
        game.random_ticks(3)

    expect.that("a canopy on an untagged trunk decays", died,
                "" if died else "it is still %s after %.0fs" % (
                    _at(game, canopy["an untagged trunk"]), DECAY_TIMEOUT))
    expect.that("a canopy on a Xie log is standing after the same ticking",
                held["a Xie log"],
                "" if held["a Xie log"] else
                "it is %s, which is the flame tree losing its canopy"
                % _at(game, canopy["a Xie log"]))
    expect.that("and so is one on a vanilla log, which says the measurement is the tag",
                held["a vanilla log"],
                "if this fell too, leaf decay is what changed rather than the tag")


def _at(game, pos):
    for candidate in ("minecraft:air", CANOPY, XIE_LOG, "minecraft:stone"):
        if game.block_is(pos, candidate):
            return candidate
    return "something else"
