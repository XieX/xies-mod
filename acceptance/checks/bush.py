"""A ripe bush picked for its fruit, and broken for its seed.

A bush is the perennial: a right-click on a ripe one pops its ``fruit`` out
of ``harvest/<path>`` and drops it back three stages, and breaking it removes
it and leaves its ``drops``, which for a bush is what to replant from. The two
were one table until November 2026, which made breaking a ripe cotton plant
lose it with no seed back, and a pick that came up empty read as a bug however
rare it was — so the fruit now promises one with ``min`` and the pick is
asserted every time, not on average.

Three bushes, on grass: a ripe tomato and a ripe cotton plant picked, and a
tomato one stage short of ripe clicked for the null. The break is
``/loot give ... mine``, which runs the block's own table at its current state
and hands the result over, so it is counted in the player's hands as the pick
is. Its seed is asserted on a soy crop, whose three rolls at 90 come up empty
one time in a thousand, because the tomato's three rolls at 60 come up empty
one time in sixteen; the tomato is asserted to give no tomato, which is certain.
"""

import time

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

TOMATO = "xiesmod:tomato_bush"
COTTON = "xiesmod:cotton_plant"
SOY = "xiesmod:soy_crop"
APART = 3


def run(game, expect):
    x0, z0 = lane("bush")
    game.enter(x0 + APART, z0 + 6)
    game.clear((x0 - 2, z0 - 2), (x0 + 3 * APART + 2, z0 + 6), floor="minecraft:grass_block")
    game.kill_items()
    game.gamemode("creative")
    game.clear_inventory()

    tomato = (x0, Y, z0)
    cotton = (x0 + APART, Y, z0)
    green = (x0 + 2 * APART, Y, z0)
    soy = (x0 + 3 * APART, Y, z0)
    for pos, state in ((tomato, TOMATO + "[age=4]"), (cotton, COTTON + "[age=5]"),
                       (green, TOMATO + "[age=3]"), (soy, SOY + "[age=4]")):
        game.setblock(pos, state)

    standing = all(game.block_is(pos, block) for pos, block in
                   ((tomato, TOMATO), (cotton, COTTON), (green, TOMATO), (soy, SOY)))
    if not expect.that("four bushes stand on grass", standing,
                       "nothing below can mean anything without them"):
        return

    # -------------------------------------------------------------- the pick
    game.stand_on(tomato)
    picked = game.click_until(lambda: game.block_is(tomato, "%s[age=1]" % TOMATO))
    expect.that("a right-click on a ripe tomato bush drops it back three stages", picked,
                "it is still %s" % _age(game, tomato, TOMATO) if not picked else "")
    got = game.wait_until(lambda: game.count_items("xiesmod:tomato"), 5.0)
    expect.that("and hands over one or two tomatoes, never none", 1 <= got <= 2,
                "%d tomatoes in hand" % got)

    game.stand_on(cotton)
    picked = game.click_until(lambda: game.block_is(cotton, "%s[age=2]" % COTTON))
    expect.that("a ripe cotton plant does the same", picked,
                "it is still %s" % _age(game, cotton, COTTON) if not picked else "")
    got = game.wait_until(lambda: game.count_items("xiesmod:cotton"), 5.0)
    expect.that("and hands over one or two cotton", 1 <= got <= 2, "%d cotton in hand" % got)
    seeds = game.count_items("xiesmod:cotton_seeds")
    expect.that("but no seed, which is what breaking it is for", seeds == 0,
                "%d cotton seeds in hand" % seeds)

    tomatoes = game.count_items("xiesmod:tomato")
    game.stand_on(green)
    game.right_click()
    time.sleep(1.0)
    expect.that("a bush one stage short of ripe gives nothing to the same click",
                game.block_is(green, "%s[age=3]" % TOMATO)
                and game.count_items("xiesmod:tomato") == tomatoes,
                "it is %s and there are %d tomatoes in hand"
                % (_age(game, green, TOMATO), game.count_items("xiesmod:tomato")))

    # ------------------------------------------------------------- the break
    game.setblock(tomato, TOMATO + "[age=4]")
    game.cmd("loot give %s mine %d %d %d" % ((game.player,) + tomato))
    expect.that("breaking a ripe tomato bush leaves no tomato",
                game.count_items("xiesmod:tomato") == tomatoes,
                "%d tomatoes in hand, %d before" % (game.count_items("xiesmod:tomato"), tomatoes))

    game.cmd("loot give %s mine %d %d %d" % ((game.player,) + soy))
    got = game.count_items("xiesmod:soy_beans")
    expect.that("and a ripe soy crop leaves its seed to replant", got >= 1,
                "%d soy beans in hand" % got)


def _age(game, pos, block):
    """Which stage is there, for a failure that would otherwise say nothing."""
    for age in range(16):
        if game.block_is(pos, "%s[age=%d]" % (block, age)):
            return "age=%d" % age
    return "not %s" % block
