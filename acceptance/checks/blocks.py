"""``becomesWhenDestroyed``, and the support rule a walkthroughable block gained.

Both are cheap to get wrong in a way that reads as a pass.

``becomesWhenDestroyed`` hangs off ``Block#destroy``, which is the creative-safe
break hook — ``playerDestroy`` is never called in creative — and no command
reaches it: ``/setblock ... destroy`` drops the block's loot without ever asking
the block. So this needs a real break, which is a held left click through the
same synthetic-input path as everything else, and the control beside it is a
plain stone block broken the same way.

``XieBasicBlock.canSurvive`` is the other one. ``setblock`` does not ask
``canSurvive``, so placing a plant on ground it could never stand on succeeds
and it stays there until something updates the neighbourhood — "the block is
still there" proves nothing at all. Changing the block *below* is the update,
and it tests both directions in two commands: a walkthroughable ``shrub`` comes
away with its soil, and ``saltDeposit``, which is terrain rather than something
you walk through, does not.
"""

from ..rig.arena import GROUND, Y, lane

NEEDS_CLIENT = True

SALT = "xiesmod:salt_deposit"
SHRUB = "xiesmod:shrub"


def run(game, expect):
    x0, z0 = lane("blocks")
    game.enter(x0 + 4, z0)
    game.clear((x0 - 2, z0 - 2), (x0 + 10, z0 + 2), floor="minecraft:stone")
    game.gamemode("creative")
    game.clear_inventory()

    # ------------------------------------------------- becomesWhenDestroyed
    salt = (x0, Y, z0)
    game.setblock(salt, SALT)
    game.stand_on(salt)
    became = game.click_until(lambda: game.block_is(salt, "minecraft:sand"), button="left")

    control = (x0 + 3, Y, z0)
    game.setblock(control, "minecraft:stone")
    game.stand_on(control)
    broke = game.click_until(lambda: game.block_is(control, "minecraft:air"), button="left")

    expect.that("a salt deposit broken by hand becomes sand", became,
                "" if became else "it is %s" % _at(game, salt))
    expect.that("and a plain block broken the same way is gone", broke,
                "which is what says the click broke anything at all")

    # -------------------------------------------------------- canSurvive
    shrub = (x0 + 6, Y, z0)
    game.setblock((shrub[0], GROUND, shrub[2]), "minecraft:dirt")
    game.setblock(shrub, SHRUB)
    standing = game.block_is(shrub, SHRUB)
    game.setblock((shrub[0], GROUND, shrub[2]), "minecraft:air")
    expect.that("a walkthroughable block comes away with its soil",
                standing and game.block_is(shrub, "minecraft:air"),
                "" if standing else "the shrub would not stand on dirt to begin with")

    deposit = (x0 + 9, Y, z0)
    game.setblock((deposit[0], GROUND, deposit[2]), "minecraft:dirt")
    game.setblock(deposit, SALT)
    game.setblock((deposit[0], GROUND, deposit[2]), "minecraft:air")
    expect.that("terrain that is not walkthroughable stays where it was put",
                game.block_is(deposit, SALT),
                "the same two commands, and the shrub above is the control")


def _at(game, pos):
    for candidate in ("minecraft:air", "minecraft:sand", SALT):
        if game.block_is(pos, candidate):
            return candidate
    return "something else"
