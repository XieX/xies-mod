"""``crimsonSickle``, the ``stick`` archetype: right-click a block, get its
declared drops rather than the block's own.

Three of the four things the archetype does are deterministic and are asserted
outright: ``destroyChance: 100`` clears the block every time, every face works
(unlike a ``tool``, which refuses the bottom one), and one durability is spent
per use — which doubles as proof that every click landed, since the damage on
the sickle is the number of times it worked.

Two are not. The 2% ``etherWart`` is not something a run can wait for without
being flaky, so it is only noted. The nether wart is asserted, because six rolls
at 20% over sixteen swings all failing is a chance below one in a billion. And
the crunch is the clicked block's *step* sound, which no assertion can hear.

Creative mode does not wear tools — ``ItemStack.hurtAndBreak`` returns
immediately for a player with infinite materials — so this one runs in
survival.
"""

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

SICKLE = "xiesmod:crimson_sickle"
WART = "minecraft:nether_wart"
DROP = "xiesmod:ether_wart"

#: Enough clicks that the durability is worth reading, and few enough that the
#: check stays under a quarter of a minute.
CLICKS = 16


def run(game, expect):
    x0, z0 = lane("sickle")
    game.enter(x0 + CLICKS / 2.0, z0)
    game.clear((x0 - 2, z0 - 2), (x0 + CLICKS + 2, z0 + 2), floor="minecraft:soul_sand")
    game.kill_items()
    game.gamemode("survival")
    game.heal()
    game.clear_inventory()
    game.hold(SICKLE)

    warts = [(x0 + index, Y, z0) for index in range(CLICKS)]
    for wart in warts:
        game.setblock(wart, "%s[age=3]" % WART)

    attempts = []
    for wart in warts:
        game.stand_on(wart)
        # A click the client had not yet raycast against a loaded chunk reaches
        # nothing at all, which is not the sickle refusing: retry until the
        # world says otherwise, and count what it took.
        attempts.append(game.click_until(lambda at=wart: game.block_is(at, "minecraft:air")))

    cleared = sum(1 for wart in warts if game.block_is(wart, "minecraft:air"))
    expect.equal("every right-clicked nether wart is cleared", cleared, CLICKS,
                 "%d of the %d landed first time"
                 % (sum(1 for taken in attempts if taken == 1), CLICKS))
    # Not the number of clicks sent: a click the client never raycast against a
    # loaded chunk reaches nothing, and one durability per *use* is the claim.
    expect.that("one durability is spent per use",
                game.holding("%s[minecraft:damage=%d]" % (SICKLE, cleared)),
                "%d cleared, so %d damage on the sickle" % (cleared, cleared))

    # Counted in the player's hands rather than on the ground: a drop lands at
    # their feet and is picked up within the second.
    dropped = game.count_items(DROP)
    reaped = game.count_items(WART)
    expect.that("the sickle's nether wart drop reaches the player", reaped > 0,
                "%d nether wart from %d swings, at six rolls of 20%%" % (reaped, CLICKS))
    expect.note("%d ether wart from %d swings, at the 2%% the table asks for"
                % (dropped, CLICKS))

    # The null: a block the sickle does not list is left alone, and the swings
    # above are the control that says the click reaches the game at all.
    bare = (x0 + CLICKS + 1, Y, z0)
    game.setblock(bare, "minecraft:stone")
    game.stand_on(bare)
    game.right_click()
    expect.that("a block the sickle does not list is left alone",
                game.block_is(bare, "minecraft:stone"))
    expect.that("and costs it no durability",
                game.holding("%s[minecraft:damage=%d]" % (SICKLE, cleared)))

    expect.skip("the crunch is the clicked block's step sound",
                "no assertion can hear it; XieStickItem records the reading")
