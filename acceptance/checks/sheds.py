"""A tree shedding, and ``shedSaturation`` stopping it.

``sheds`` is the whole point of a fruit tree and the one thing a datapack
cannot express: a tree that drops its fruit on the ground of its own accord.
One leaf per tree does it — the keystone, directly above the topmost log — and
it stops once eight of the fruit are already lying nearby, which is what keeps
an orchard from burying itself.

Saturation is a null case, so it is taken against a positive control in the
same run and by the same measurement: two identical planted trees, nine apples
scattered around one of them, and the count read back at both.

The markers take three precautions, and all three are the same fact — the tree
counts item *entities*, and a merged stack of nine is one entity and therefore
no saturation at all. They are scattered rather than stacked; they are summoned
with no motion, because ``/summon`` otherwise gives an item a random shove and
two two blocks apart can drift within merging distance; and each carries a
distinct ``custom_name``, which makes them unmergeable outright without changing
what item they are.
"""

import time

from . import _orchard as orchard
from ..rig.arena import GROUND, Y, lane

NEEDS_CLIENT = True

FRUIT = "minecraft:apple"
SATURATION = 8
MARKERS = SATURATION + 1
APART = 16
TIMEOUT = 90.0

#: How long both trees go on ticking after the first fruit falls, so that the
#: tree which is supposed to stop is given a fair chance to fail.
WINDOW = 15.0


def run(game, expect):
    x0, z0 = lane("sheds")
    game.enter(x0 + APART / 2.0, z0 + 5)
    game.clear((x0 - 4, z0 - 4), (x0 + APART + 4, z0 + 6), floor="minecraft:grass_block")
    game.kill_items()
    game.gamemode("creative")

    free = (x0, Y, z0)
    saturated = (x0 + APART, Y, z0)

    tops = {}
    for name, base in (("free", free), ("saturated", saturated)):
        tops[name] = (base, orchard.grow(game, base))

    missing = [name for name, (_, top) in tops.items() if not orchard.is_keystone(game, top)]
    if not expect.that("both planted trees have a keystone to shed from", not missing,
                       "the %s tree has none: %s at %s, %d of trunk"
                       % (missing[0], orchard.what(game, tops[missing[0]][1]),
                          tops[missing[0]][1], tops[missing[0]][1][1] - tops[missing[0]][0][1])
                       if missing else "nothing below can mean anything without it"):
        return

    for index, spot in enumerate(_ring(saturated)):
        game.cmd(
            'summon minecraft:item %d %.1f %d '
            '{Item:{id:"%s",count:1,components:{"minecraft:custom_name":\'"marker %d"\'}},'
            'PickupDelay:32767s,Motion:[0.0d,0.0d,0.0d]}'
            % (spot[0], Y + 0.2, spot[1], FRUIT, index))
    lying = game.count_entities(_near(saturated))
    if not expect.that("nine fruit are lying under the saturated tree",
                       lying == MARKERS, "counted %d, and merged items would say fewer" % lying):
        return

    game.tp((free[0] + saturated[0]) / 2.0, Y, z0 + 5, 0.0, 0.0)
    game.random_ticks(400)
    try:
        started = time.monotonic()
        shed = game.wait_until(lambda: game.count_entities(_near(free)), TIMEOUT)
        # The free tree can shed in a second, which would leave the saturated one
        # barely tested. Both keep ticking for a fixed window instead, long enough
        # that a saturation check doing nothing would show several times over.
        time.sleep(WINDOW)
        still = game.count_entities(_near(saturated))
        shed = game.count_entities(_near(free))
    finally:
        game.random_ticks(3)

    expect.that("a keystone tree sheds its fruit", shed > 0,
                "%d apples under it, the first after %.0fs"
                % (shed, time.monotonic() - started - WINDOW))
    expect.that("shedSaturation stops a tree with eight fruit already lying nearby",
                still == MARKERS,
                "%d under the saturated tree and %d under the free one, over the same %.0fs"
                % (still, shed, WINDOW))


def _ring(base):
    """Nine positions around the trunk, none of them close enough to merge."""
    spots = []
    for dx in (-2, 0, 2):
        for dz in (-2, 0, 2):
            if dx == 0 and dz == 0:
                continue
            spots.append((base[0] + dx, base[2] + dz))
    spots.append((base[0] + 3, base[2] + 3))
    return spots


def _near(base, radius=4):
    return ('@e[type=item,nbt={Item:{id:"%s"}},x=%d,y=%d,z=%d,dx=%d,dy=%d,dz=%d]'
            % (FRUIT, base[0] - radius, GROUND, base[2] - radius,
               2 * radius, 6, 2 * radius))
