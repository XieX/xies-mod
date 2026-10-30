"""What the two tree checks both need: a planted tree, and its keystone leaf.

A sapling is grown by hand rather than by waiting, because bone meal on a ripe
Xie sapling is ``growWhenRipe`` outright — no roll — and because the hand path
is the one a player takes. That needs a real right-click: no command applies
bone meal, and a dispenser cannot do it here because a dispenser above the
sapling is in the way of the trunk the feature is about to place.
"""

from ..rig.arena import GROUND, Y
from ..rig.session import say

SAPLING = "xiesmod:apple_sapling"
LEAVES = "xiesmod:apple_leaves"
SOIL = "minecraft:dirt"

#: A sapling will not grow into a neighbour's canopy: ``TreeFeature`` checks a
#: column one wider than the trunk for the whole height, and leaves are not
#: ``REPLACEABLE_BY_TREES``. Eight blocks apart is clear; three looks like a bug
#: in the growth code.
SPACING = 8


def plant(game, pos, sapling=SAPLING, soil=SOIL):
    """Puts a sapling on soil of its own, and answers whether it is there.

    ``setblock`` says "Could not set the block" in a reply that scrolls past, so
    a plant that never happened and a sapling that vanished afterwards are the
    same air at the same position. They are not the same fault, and telling them
    apart costs one probe — see :func:`vanished`.
    """
    game.setblock((pos[0], GROUND, pos[2]), soil)
    game.setblock(pos, sapling)
    return game.block_is(pos, sapling)


def bonemeal_by_hand(game, pos, until, yaw=0.0):
    """Stands on the block and right-clicks it with bone meal.

    ``until`` is what the click is supposed to do, so that a click the client
    never got to raycast is retried rather than read as a result. Even the case
    where nothing is supposed to grow has one: bone meal on a sapling that
    cannot become a tree still ripens it, so ``age=1`` is what says the click
    landed.
    """
    game.hold("minecraft:bone_meal", 16)
    game.stand_on(pos, yaw)
    return game.click_until(until)


def grew(game, pos):
    return lambda: game.block_is(pos, "#minecraft:logs")


def grow(game, pos, attempts=2):
    """Plants a sapling and bone-meals it into its tree. Answers the trunk top.

    A tree that did not grow is a rig failure rather than a result — every check
    that calls this is about what the tree does *afterwards* — so it plants
    again rather than handing back a position with nothing at it, and says what
    it saw when it gives up.

    Growing is the only thing in the run that is not idempotent under a retry:
    the whole plant-and-bone-meal cycle is repeated rather than the click, since
    a first attempt that half-happened leaves neither a sapling nor a tree.
    """
    game.gamemode("creative")
    for attempt in range(1, attempts + 1):
        if not plant(game, pos):
            say("the sapling would not go in at %s: it is %s, on %s"
                % (pos, what(game, pos), what(game, (pos[0], GROUND, pos[2]))))
            continue
        if bonemeal_by_hand(game, pos, grew(game, pos)):
            if attempt > 1:
                say("the tree at %s needed planting %d times" % (pos, attempt))
            game.tp(pos[0] + 0.5, Y, pos[2] + SPACING / 2.0, 0.0, 0.0)
            return trunk_top(game, pos)
        say(vanished(game, pos))
    raise RuntimeError("planted a sapling at %s %d times; it is %s now, on %s"
                       % (pos, attempts, what(game, pos),
                          what(game, (pos[0], GROUND, pos[2]))))


def vanished(game, pos):
    """What became of a sapling that did not turn into a tree.

    Roughly one run in ten, the first sapling planted in a freshly cleared lane
    is air afterwards. Two explanations are out by reading, and a third by
    measurement:

    * ``TreeGrower.growTree`` cannot leave air. The emitted
      ``two_layers_feature_size`` carries no ``min_clipped_height``, so
      ``TreeFeature.doPlace`` refuses anything short of the full height, and
      ``StraightTrunkPlacer`` always writes a log at the origin. True means a
      log where the sapling was; false puts the sapling back.
    * the support rule cannot drop it. ``fruitSapling`` declares no ``growsOn``,
      so its ground is ``VegetationBlock``'s ``#minecraft:dirt`` or farmland, and
      both the lane floor and the soil this plants on are in that tag.
    * it is not the plant-and-bone-meal sequence as such: sixty plants in a
      tight loop, thirty of them the first in a freshly cleared lane, grew on
      the first click every time.

    So the next occurrence is asked which of the three remaining mechanisms it
    was, because air on its own names none of them:

    * **a sapling item lying nearby** is the block having popped, which is a
      support rule failing through some neighbour none of the above covers.
      That is the only one of the three that leaves drops.
    * **no item, and nothing in the column** is a break with no drops, which on
      a driven client means a click that arrived as the wrong button.
    * **a log or a leaf in the column** is a tree that grew and a measurement
      taken in the wrong place.
    """
    box = ("x=%d,y=%d,z=%d,dx=8,dy=6,dz=8"
           % (pos[0] - 4, GROUND, pos[2] - 4))
    try:
        dropped = game.count_entities('@e[type=item,nbt={Item:{id:"%s"}},%s]' % (SAPLING, box))
    except Exception:
        # This only ever runs when something has already gone wrong, and a
        # diagnostic that throws replaces the evidence with a stack trace.
        dropped = -1
    column = [y for y in range(pos[1], pos[1] + 12)
              if game.block_is((pos[0], y, pos[2]), "#minecraft:logs")
              or game.block_is((pos[0], y, pos[2]), LEAVES)]
    return ("the sapling at %s is %s: %d of it lying nearby, %d tree blocks in the "
            "column above, %s below — see _orchard.vanished"
            % (pos, what(game, pos), dropped, len(column),
               what(game, (pos[0], GROUND, pos[2]))))


def trunk_top(game, base, limit=32):
    """The first position above the trunk that is not a log.

    That is exactly where ``XieTreeBlock.markKeystone`` puts the keystone, and
    therefore the one position where a generated tree's *absence* of one can be
    read with the same measurement as a planted tree's presence.
    """
    y = base[1]
    while y < base[1] + limit and game.block_is((base[0], y, base[2]), "#minecraft:logs"):
        y += 1
    return (base[0], y, base[2])


def is_keystone(game, pos):
    return game.block_is(pos, "%s[keystone=true]" % LEAVES)


def what(game, pos):
    """Roughly what is there, for a failure that would otherwise say nothing."""
    for candidate in ("minecraft:air", SAPLING, LEAVES, "#minecraft:logs", SOIL,
                      "minecraft:grass_block"):
        if game.block_is(pos, candidate):
            return candidate
    return "something else"
