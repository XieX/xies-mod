"""``ripening``: fresh cheese aging where it stands and becoming a cheese block.

Three things make it a ripening block rather than a plant, and each is taken
against a control in the same run:

* **It ripens with no light.** ``XiePlantBlock`` will not grow below light 9,
  and a solid, opaque block reads light 0 at its own position — so every cell
  here is in the dark by construction, and a ripening block that had inherited
  the plant's rule would sit at age 0 for ever. The cells stand on stone, which
  no plant here would take either.
* **Bone meal does nothing.** The dispenser rig from ``fertilisable``: the stack
  is only consumed when the bone meal took, and a vanilla sapling beside it is
  the proof the rig fires at all.
* **Breaking it early yields nothing.** ``/setblock ... destroy`` runs the loot
  table, and fresh cheese declares no drops; a cheese block broken the same way
  drops itself, which is what says the command dropped anything.

Bone meal goes first, at the arena's usual random-tick speed, so that a cell
ripening under the dispenser cannot be mistaken for bone meal having worked.
"""

import time

from ..rig.arena import GROUND, Y, lane

#: Not because anything is clicked, but because nothing random-ticks without a
#: player, and a dispenser built where no player is standing never fires.
NEEDS_CLIENT = True

FRESH = "xiesmod:fresh_cheese"
RIPE = "xiesmod:cheese_block"
BONE_MEAL = "minecraft:bone_meal"

#: The content's maxGrowth. A cell at this age has one roll left to become RIPE.
LAST_STAGE = 3

#: Six because at a random-tick speed of 400 one cell takes a few seconds on a
#: good run and several times that on a bad one, and six of them waiting for the
#: slowest still finishes well inside the timeout.
CELLS = 6
SPACING = 2
TIMEOUT = 90.0


def run(game, expect):
    x0, z0 = lane("ripen")
    game.enter(x0 + CELLS, z0 + 6, pitch=30.0)
    game.clear((x0 - 2, z0 - 2), (x0 + SPACING * CELLS + 8, z0 + 8), floor="minecraft:stone")
    game.gamemode("creative")
    game.kill_items()

    _bone_meal(game, expect, (x0, z0 + 6))
    _breaking(game, expect, (x0 + 8, z0 + 6))

    cells = [(x0 + SPACING * index, Y, z0) for index in range(CELLS)]
    for cell in cells:
        game.replace(cell, "%s[age=0]" % FRESH)

    game.random_ticks(400)
    try:
        started = time.monotonic()
        ripened = game.wait_until(lambda: all(game.block_is(cell, RIPE) for cell in cells),
                                  TIMEOUT)
        took = time.monotonic() - started
    finally:
        game.random_ticks(3)

    done = [cell for cell in cells if game.block_is(cell, RIPE)]
    expect.that("fresh cheese ripens into a cheese block, in the dark and on stone",
                bool(done), "" if done else "none of %d ripened in %.0fs" % (CELLS, TIMEOUT))
    expect.that("every cell ripens, not just a lucky one", ripened,
                "%d of %d after %.0fs" % (len(done), CELLS, took))
    if ripened:
        expect.note("all %d ripe after %.0fs at random-tick speed 400" % (CELLS, took))


def _bone_meal(game, expect, origin):
    """The refusal and its control, read together after both dispensers fire."""
    x, z = origin
    cheese = (x, Y, z)
    sapling = (x + 4, Y, z)

    game.replace(cheese, "%s[age=0]" % FRESH)
    game.setblock((sapling[0], GROUND, sapling[2]), "minecraft:dirt")
    game.replace(sapling, "minecraft:oak_sapling")

    dispensers = []
    for target in (cheese, sapling):
        dispenser = (target[0], Y + 1, target[2])
        game.replace(dispenser, "minecraft:dispenser[facing=down]")
        game.cmd("item replace block %d %d %d container.0 with %s 1"
                 % (dispenser[0], dispenser[1], dispenser[2], BONE_MEAL))
        game.replace((target[0] + 1, Y + 1, target[2]), "minecraft:redstone_block")
        dispensers.append(dispenser)

    # A dispenser fires four ticks after the pulse; a refusal never arrives, so
    # there is nothing to wait on but the clock.
    time.sleep(2.0)
    cheese_kept, sapling_kept = (_still_holds(game, dispenser) for dispenser in dispensers)

    expect.that("bone meal does nothing to fresh cheese", cheese_kept,
                "" if cheese_kept else "the dispenser spent its bone meal on it")
    expect.that("and the fresh cheese is still at its first stage",
                game.block_is(cheese, "%s[age=0]" % FRESH))
    expect.that("a vanilla sapling takes bone meal, which is the rig working", not sapling_kept,
                "" if not sapling_kept else "the dispenser never fired")


def _breaking(game, expect, origin):
    """Fresh cheese broken early, against a cheese block broken the same way."""
    x, z = origin
    fresh = (x, Y, z)
    ripe = (x + 3, Y, z)

    game.kill_items()
    game.replace(fresh, "%s[age=%d]" % (FRESH, LAST_STAGE))
    game.setblock(fresh, "minecraft:air destroy")
    time.sleep(0.5)
    fresh_drops = game.count_entities("@e[type=item,x=%d,y=%d,z=%d,distance=..2]" % fresh)

    game.replace(ripe, RIPE)
    game.setblock(ripe, "minecraft:air destroy")
    time.sleep(0.5)
    ripe_drops = game.count_entities("@e[type=item,x=%d,y=%d,z=%d,distance=..2]" % ripe)
    game.kill_items()

    expect.that("fresh cheese broken before it ripens yields nothing", fresh_drops == 0,
                "%d item(s) dropped" % fresh_drops if fresh_drops else "")
    expect.that("a cheese block broken the same way drops itself, which is the rig working",
                ripe_drops > 0, "" if ripe_drops else "nothing dropped")


def _still_holds(game, dispenser):
    return game.probe("execute if items block %d %d %d container.* %s"
                      % (dispenser[0], dispenser[1], dispenser[2], BONE_MEAL))
