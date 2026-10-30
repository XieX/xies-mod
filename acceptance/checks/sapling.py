"""A sapling growing into its tree, and the keystone that only a planted one gets.

``sapling`` is a ``plant`` whose ripe growth roll replaces it with something
else, and the something else is a datapack ``minecraft:tree`` feature — so what
this checks is the join between the two, plus the asymmetry ``TreeGenerator``
had and this port keeps: **only a planted tree sheds.** The keystone is a
blockstate rather than an item on the ground, so the same measurement — the
first block above the trunk — reads the planted tree's keystone and the
generated tree's lack of one.

Eight blocks of spacing is not decoration. ``TreeFeature`` checks a column one
wider than the trunk for the whole height and leaves are not
``REPLACEABLE_BY_TREES``, so a test orchard planted three blocks apart looks
exactly like a bug in the growth code. That is asserted here rather than
assumed, because it is the rig fact most likely to be forgotten.
"""

from . import _orchard as orchard
from ..rig.arena import Y, lane

NEEDS_CLIENT = True

SPACING = orchard.SPACING


def run(game, expect):
    x0, z0 = lane("sapling")
    game.enter(x0 + SPACING, z0 + 6)
    game.clear((x0 - 4, z0 - 4), (x0 + 3 * SPACING + 4, z0 + 8), floor="minecraft:grass_block")
    game.gamemode("creative")

    planted = (x0, Y, z0)
    crowded = (x0 + 3, Y, z0)
    generated = (x0 + 2 * SPACING, Y, z0)

    # --------------------------------------------------------- a planted tree
    orchard.plant(game, planted)
    expect.that("a sapling stands on its soil", game.block_is(planted, orchard.SAPLING))

    clicks = orchard.bonemeal_by_hand(game, planted, orchard.grew(game, planted))
    if not clicks and game.block_is(planted, "minecraft:air"):
        # Neither a sapling nor a tree is the situation coming apart rather than
        # the behaviour failing, so it is set up again. A sapling that is still a
        # sapling is a failure and is not retried. What it left behind is read
        # first, because that is the only evidence there will ever be of why —
        # see orchard.vanished.
        expect.note(orchard.vanished(game, planted))
        expect.note("planting it again")
        orchard.plant(game, planted)
        clicks = orchard.bonemeal_by_hand(game, planted, orchard.grew(game, planted))
    game.tp(planted[0] + 0.5, Y, planted[2] + 6, 0.0, 0.0)

    grew = expect.that("bone meal grows a sapling into its tree", clicks,
                       "the sapling position is still %s" % _at(game, planted)
                       if not clicks else "")
    top = orchard.trunk_top(game, planted)
    expect.that("the tree is leaves of this mod's own",
                game.block_is(top, orchard.LEAVES),
                "%s above a trunk of %d" % (_at(game, top), top[1] - planted[1]))
    expect.that("the leaf above the trunk of a planted tree is the keystone",
                orchard.is_keystone(game, top))

    # ------------------------------------------------- a tree the world places
    game.place_feature("xiesmod:apple_leaves_tree", generated)
    wild_top = orchard.trunk_top(game, generated)
    canopy = game.block_is(wild_top, orchard.LEAVES)
    placed = expect.that("the world can place the same tree as a feature", canopy,
                         "" if canopy else "no canopy above %s" % (generated,))
    if placed:
        expect.that("a generated tree has no keystone, so it never sheds",
                    not orchard.is_keystone(game, wild_top))

    # ------------------------------------------------------------- the spacing
    if grew:
        orchard.plant(game, crowded)
        # The bone meal ripens it whether or not a tree can fit, so age=1 is
        # what says the click landed and the null below means something.
        ripened = orchard.bonemeal_by_hand(
            game, crowded, lambda: game.block_is(crowded, "%s[age=1]" % orchard.SAPLING))
        game.tp(crowded[0] + 0.5, Y, crowded[2] + 6, 0.0, 0.0)
        expect.that("a sapling three blocks from a canopy refuses to grow",
                    ripened and game.block_is(crowded, orchard.SAPLING),
                    "which is why the orchard is planted %d apart" % SPACING
                    if ripened else "the bone meal never reached it")


def _at(game, pos):
    """What is actually there, for a failure that would otherwise say nothing."""
    for candidate in ("minecraft:air", orchard.SAPLING, orchard.LEAVES,
                      "#minecraft:logs", "minecraft:dirt", "minecraft:grass_block"):
        if game.block_is(pos, candidate):
            return candidate
    return "something else"
