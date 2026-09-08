"""``transforms``, which is the one property in the tools work that describes
nothing and so needed a class of its own.

Right-click stone with the Ultimate Fist and it becomes a gold block;
right-click dirt and it becomes air. Both are deterministic, and the third
assertion is the guard the 2013 method wrote deliberately: **the bottom face
does nothing.** ``XieItemTool`` refused ``l != 0`` and ``ShovelItem`` still
opens with ``if (getClickedFace() == DOWN) return PASS`` — reaching under a
block to reshape it is the one angle from which you cannot see what you did.

That last one is a null, so it is taken against the positive control directly
above it: the same block, the same item, the same synthetic click, from the top
face instead. A click on the underside that changes nothing looks identical to a
click that never reached the game.
"""

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

FIST = "xiesmod:ultimate_fist"


def run(game, expect):
    x0, z0 = lane("fist")
    game.enter(x0 + 4, z0)
    game.clear((x0 - 2, z0 - 2), (x0 + 10, z0 + 2), floor="minecraft:stone")
    game.gamemode("creative")
    game.hold(FIST)

    stone = (x0, Y, z0)
    dirt = (x0 + 3, Y, z0)
    overhead = (x0 + 6, Y + 3, z0)

    game.setblock(stone, "minecraft:stone")
    game.stand_on(stone)
    clicks = game.click_until(lambda: game.block_is(stone, "minecraft:gold_block"))
    expect.that("right-clicking stone with the Ultimate Fist gives a gold block",
                clicks, _took(clicks))

    game.setblock(dirt, "minecraft:dirt")
    game.stand_on(dirt)
    clicks = game.click_until(lambda: game.block_is(dirt, "minecraft:air"))
    expect.that("right-clicking dirt with it gives air", clicks,
                "a transform to air is the case that falls back to the old block's "
                "sound; " + _took(clicks))

    # The underside, against the same block clicked from above a moment later.
    game.setblock((overhead[0], Y, overhead[2]), "minecraft:stone")
    game.setblock(overhead, "minecraft:stone")
    game.stand_under(overhead)
    game.right_click()
    refused = game.block_is(overhead, "minecraft:stone")

    game.stand_on(overhead)
    clicks = game.click_until(lambda: game.block_is(overhead, "minecraft:gold_block"))

    expect.that("a click on the bottom face does nothing", refused,
                "" if refused else "the block changed from underneath")
    expect.that("and the same click on the top face does", clicks,
                "which is what tells a refusal from a click that never landed; "
                + _took(clicks))


def _took(clicks):
    return "one click" if clicks == 1 else "%d clicks" % clicks
