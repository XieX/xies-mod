"""A craft remainder, which no predicate can see.

``container`` is two things, because ``Item.setContainerItem`` was: the bowl a
salad leaves in your hand, and ``craftRemainder`` — which is the only thing
that method actually did in 1.6. A component predicate can read the first; the
second is a field on ``Item.Properties`` and shows up nowhere at all until
something crafts with the item.

A crafter is what asks the question. Fill its slots with ``/item replace
block``, pulse it, and the remainder is left among what it ejects. Two things
to keep in mind: a crafter with no matching recipe ejects an *ingredient*, so
"the crafter emitted an item" does not mean it crafted and the item entity has
to be read rather than counted; and ``/setblock`` with the block already there
is a no-op, so a pulse clears its position first or the redstone block keeps
the ``triggered`` state it already had.

The recipe is the dressed garden salad, whose mayonnaise declares
``"container": "bowl"``. Its own bowl ingredient is consumed, so the bowl that
comes back out is the mayonnaise's and nothing else's.
"""

import time

from ..rig.arena import Y, lane

#: Not because anything is clicked, but because a crafter built where no player
#: is standing takes triggered=1 and then never fires.
NEEDS_CLIENT = True

RESULT = "xiesmod:tasty_garden_salad"
REMAINDER = "minecraft:bowl"
INGREDIENTS = [
    "minecraft:bowl",
    "xiesmod:lettuce",
    "xiesmod:tomato",
    "xiesmod:onion",
    "xiesmod:mayo",
]


def run(game, expect):
    x0, z0 = lane("crafter")
    game.enter(x0 + 4, z0, yaw=90.0)
    game.clear((x0 - 2, z0 - 2), (x0 + 6, z0 + 2), floor="minecraft:stone")
    game.kill_items()
    game.gamemode("creative")

    crafter = (x0, Y, z0)
    game.replace(crafter, "minecraft:crafter[orientation=up_north]")
    for slot, ingredient in enumerate(INGREDIENTS):
        game.cmd("item replace block %d %d %d container.%d with %s 1"
                 % (crafter[0], crafter[1], crafter[2], slot, ingredient))

    game.replace((crafter[0], crafter[1], crafter[2] + 1), "minecraft:redstone_block")
    time.sleep(1.5)

    ejected = _ejected(game, crafter)
    expect.that("a crafter fed the recipe crafts it", ejected.get(RESULT, 0) > 0,
                "it ejected %s" % (_named(ejected) or "nothing"))
    expect.that("the craft remainder comes back out with it",
                ejected.get(REMAINDER, 0) > 0,
                "%s among %s" % (REMAINDER, _named(ejected)))


def _ejected(game, crafter, radius=3):
    counts = {}
    for item in (RESULT, REMAINDER):
        counts[item] = game.count_entities(
            '@e[type=item,nbt={Item:{id:"%s"}},x=%d,y=%d,z=%d,dx=%d,dy=%d,dz=%d]'
            % (item, crafter[0] - radius, crafter[1] - 2, crafter[2] - radius,
               2 * radius, 6, 2 * radius))
    return counts


def _named(counts):
    return ", ".join("%d %s" % (count, item) for item, count in counts.items() if count)
