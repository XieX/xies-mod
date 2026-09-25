"""Where each check builds.

The run has a flat world of its own, so an arena is a fill rather than a
landscape. Lanes are far enough apart that nothing one check places is inside
another's, and near enough that the whole arena is within simulation distance
of the player wherever they stand in it — which matters, because nothing
random-ticks or scheduled-ticks without a player and forceloading is not
enough.
"""

#: The flat world's topmost solid layer. Blocks a check places sit one above.
GROUND = -61

#: One above the ground: where a check builds.
Y = GROUND + 1

#: Where the world is measured to see that it is the flat one: near the spawn
#: and far from every lane, so that it still reads as untouched on the second
#: run against the same world.
REFERENCE = (0, 0)

#: Each lane is ``(x, z)`` of its near corner. A lane owns everything from
#: there to the next lane's corner.
LANES = {
    "stem": (100, 100),
    "sheds": (100, 120),
    "sapling": (100, 140),
    "decay": (140, 140),
    "spikey": (100, 160),
    "fertilisable": (100, 176),
    "sickle": (100, 190),
    "fist": (140, 190),
    "bows": (100, 205),
    "crafter": (100, 220),
    "blocks": (140, 220),
    "cookpot": (100, 235),
}


def lane(name):
    return LANES[name]
