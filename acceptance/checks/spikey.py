"""``spikey``: one point of damage to whatever is inside the block.

Two properties of ``blazeTree`` have to be right together for this to happen at
all, which is why the stop position is asserted beside the damage.
``entityInside`` needs the entity to have *moved* — a ``/tp`` onto the block
never triggers it, and neither does it for a real cactus, which is how the rig
announces itself rather than the mod — so the player walks into a wall of the
block under test, through the same synthetic-input path as a click.

And a full cube could not hurt anybody: a player stops with their bounding box
flush against the block's face and a hair outside its cell, and
``Entity.checkInsideBlocks`` deflates by 1e-5 before testing, so flush is
outside. ``blazeTree``'s ``blockBounds`` inset the canopy to 0.1–0.9, which is
the difference between a spikey canopy that can hurt you and one that cannot.
The two stop positions say it plainly: ``wall + 0.1 - 0.3`` against an inset
canopy and ``wall - 0.3`` against a full cube.

The control is a wall of Xie leaves that are not spikey, taken the same way in
the same run — a walk that hurts nobody looks identical to a walk that never
happened.
"""

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

SPIKEY = "xiesmod:blaze_tree"
BLUNT = "xiesmod:cocoa_leaves"

#: Leaves placed by ``fill`` decay: ``persistent`` defaults to false. Xie leaves
#: measure their distance from ``#minecraft:logs`` like every other leaf, so
#: they need it as much as vanilla's do.
PERSISTENT = "[persistent=true]"

RUN_UP = 12
WALK = 4.0
HALF_WIDTH = 0.3
INSET = 0.1


def run(game, expect):
    x0, z0 = lane("spikey")
    wall = x0 + RUN_UP
    game.enter(x0, z0)
    game.clear((x0 - 2, z0 - 2), (x0 + RUN_UP + 6, z0 + 2), floor="minecraft:stone")
    game.gamemode("survival")

    spikey = _walk_into(game, SPIKEY, x0, z0, wall)
    expect.that("walking into a spikey canopy hurts",
                spikey["health"] < 20.0,
                "%.1f health after %.1fs against %s" % (spikey["health"], WALK, SPIKEY))
    expect.close_to("an inset canopy stops a player inside its cell",
                    spikey["x"], wall + INSET - HALF_WIDTH, 0.05)

    blunt = _walk_into(game, BLUNT, x0, z0, wall)
    expect.that("walking into leaves that are not spikey does not hurt",
                blunt["health"] >= 20.0,
                "%.1f health, and the walk above proves the walk lands" % blunt["health"])
    expect.close_to("a full cube stops a player outside its cell",
                    blunt["x"], wall - HALF_WIDTH, 0.05)


def _walk_into(game, block, x0, z0, wall):
    game.fill((wall, Y, z0 - 2), (wall, Y + 1, z0 + 2), "minecraft:air")
    game.fill((wall, Y, z0 - 2), (wall, Y + 1, z0 + 2), block + PERSISTENT)
    game.heal()
    # yaw -90 is east, which is +x: the walk is a straight line at the wall.
    game.tp(x0 + 0.5, Y, z0 + 0.5, -90.0, 0.0)
    game.walk("w", WALK)
    return {"health": game.health(), "x": game.position()[0]}
