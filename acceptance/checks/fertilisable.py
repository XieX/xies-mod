"""``fertilisable: false`` refusing bone meal, through the dispenser rig.

A dispenser applies bone meal and needs no aim at all: the behaviour registered
for ``Items.BONE_MEAL`` calls ``BoneMealItem.growCrop`` on the block in front,
which goes straight to the same ``isValidBonemealTarget`` the hand path asks.
It also self-reports, which is what makes it the right rig for a refusal — the
stack is only consumed when the bone meal took, so "still there" and "gone"
tell a refusal from a hit without looking at the plant at all. That is
deliberate on vanilla's part: ``growCrop`` shrinks the stack whether or not
``isBonemealSuccess`` rolled true, so a control never fails by luck.

Two ways to get nothing out of this, both of them the rig rather than the mod:
an off-by-one on ``facing=down`` (a dispenser two above fires at the air
between), and building it where no player is standing, since nothing
scheduled-ticks there and the dispenser simply takes ``triggered=1`` and waits.
"""

import time

from ..rig.arena import GROUND, Y, lane

#: Not because anything is clicked, but because a dispenser built where no
#: player is standing takes triggered=1 and then never fires.
NEEDS_CLIENT = True

SPACING = 4
BONE_MEAL = "minecraft:bone_meal"

#: The refusal, and two controls: this mod's own plant that says nothing about
#: fertiliser, and a vanilla sapling that proves the rig fires at all. Then the
#: top of a ripe corn stalk, which refuses at its full three and takes it at two,
#: where there is still a segment to grow. The last field is how many are stacked.
CELLS = [
    ("blazeTreeSapling refuses bone meal", "xiesmod:blaze_tree_sapling",
     "minecraft:soul_sand", False, 1),
    ("a Xie sapling that does not refuse takes it", "xiesmod:apple_sapling",
     "minecraft:dirt", True, 1),
    ("a vanilla sapling takes it, which is the rig working", "minecraft:oak_sapling",
     "minecraft:dirt", True, 1),
    ("the top of a full-height ripe corn stalk refuses it", "xiesmod:corn_crop[age=6]",
     "minecraft:farmland", False, 3),
    ("the top of a ripe corn stalk with room to grow takes it", "xiesmod:corn_crop[age=6]",
     "minecraft:farmland", True, 2),
]


def run(game, expect):
    x0, z0 = lane("fertilisable")
    game.enter(x0 + SPACING * len(CELLS) / 2.0, z0 + 4, pitch=20.0)
    game.clear((x0 - 2, z0 - 2), (x0 + SPACING * len(CELLS) + 2, z0 + 2),
               floor="minecraft:stone")
    game.gamemode("creative")

    fired = []
    for index, (label, plant, soil, expected, height) in enumerate(CELLS):
        x = x0 + SPACING * index
        dispenser = (x, Y + height, z0)
        game.setblock((x, GROUND, z0), soil)
        for level in range(height):
            game.setblock((x, Y + level, z0), plant)
        game.replace(dispenser, "minecraft:dispenser[facing=down]")
        game.cmd("item replace block %d %d %d container.0 with %s 1"
                 % (dispenser[0], dispenser[1], dispenser[2], BONE_MEAL))
        # Clear first: setblock with the block already there is a no-op, so a
        # pulse that re-places the redstone leaves triggered=1 and does nothing.
        game.replace((x + 1, Y + height, z0), "minecraft:redstone_block")
        fired.append((label, dispenser, expected))

    # A dispenser fires four ticks after the pulse. Read them all afterwards
    # rather than waiting on each: a refusal never arrives, and waiting for it
    # is waiting for nothing.
    time.sleep(2.0)
    for label, dispenser, expected in fired:
        consumed = not _still_holds(game, dispenser)
        expect.that(label, consumed == expected,
                    "bone meal was %s" % ("consumed" if consumed else "left in the dispenser"))


def _still_holds(game, dispenser):
    return game.probe("execute if items block %d %d %d container.* %s"
                      % (dispenser[0], dispenser[1], dispenser[2], BONE_MEAL))
