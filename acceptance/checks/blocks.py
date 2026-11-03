"""The support rule a walkthroughable block gained, and the terrain it leaves alone.

``XieBasicBlock.canSurvive`` is cheap to get wrong in a way that reads as a
pass. ``setblock`` does not ask ``canSurvive``, so placing a plant on ground it
could never stand on succeeds and it stays there until something updates the
neighbourhood — "the block is still there" proves nothing at all. Changing the
block *below* is the update, and it tests both directions in two commands: a
walkthroughable ``shrub`` comes away with its soil and a ``saltDeposit``, a
layer on sand, with its sand, while a ``saltBlock``, which is terrain rather
than something you walk through, does not.

``becomesWhenDestroyed`` has no live content any more and is not covered here.
"""

from ..rig.arena import GROUND, Y, lane

NEEDS_CLIENT = True

SALT = "xiesmod:salt_deposit"
SALT_BLOCK = "xiesmod:salt_block"
SHRUB = "xiesmod:shrub"


def run(game, expect):
    x0, z0 = lane("blocks")
    game.enter(x0 + 4, z0)
    game.clear((x0 - 2, z0 - 2), (x0 + 10, z0 + 2), floor="minecraft:stone")
    game.gamemode("creative")
    game.clear_inventory()

    shrub = (x0, Y, z0)
    game.setblock((shrub[0], GROUND, shrub[2]), "minecraft:dirt")
    game.setblock(shrub, SHRUB)
    standing = game.block_is(shrub, SHRUB)
    game.setblock((shrub[0], GROUND, shrub[2]), "minecraft:air")
    expect.that("a walkthroughable block comes away with its soil",
                standing and game.block_is(shrub, "minecraft:air"),
                "" if standing else "the shrub would not stand on dirt to begin with")

    layer = (x0 + 3, Y, z0)
    game.setblock((layer[0], GROUND, layer[2]), "minecraft:sand")
    game.setblock(layer, SALT)
    laid = game.block_is(layer, SALT)
    game.setblock((layer[0], GROUND, layer[2]), "minecraft:air")
    expect.that("a salt deposit comes away with its sand",
                laid and game.block_is(layer, "minecraft:air"),
                "" if laid else "the deposit would not lie on sand to begin with")

    block = (x0 + 6, Y, z0)
    game.setblock((block[0], GROUND, block[2]), "minecraft:dirt")
    game.setblock(block, SALT_BLOCK)
    game.setblock((block[0], GROUND, block[2]), "minecraft:air")
    expect.that("terrain that is not walkthroughable stays where it was put",
                game.block_is(block, SALT_BLOCK),
                "the same two commands, and the shrub above is the control")
