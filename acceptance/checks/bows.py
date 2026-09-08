"""Bows: the four properties that are decisions rather than descriptions.

Each one is read by holding the button rather than by timing a click, which is
what makes the assertions robust. A ``semiautomatic`` bow fires *in* ``use``,
so an arrow exists while the button is still down; a charging bow fires in
``releaseUsing``, so it does not. That is the difference the archetype is
about, and it needs no guess about how many ticks a short click lasts.

* **``semiautomatic`` is fully automatic.** ``redstoneBow`` fires at once with
  a force clamped up to at least 0.9, and needs no winding.
* **``ammo`` is a list, and the list is a predicate now.** The same bow with
  arrows in the inventory and no redstone fires nothing at all — a null, taken
  against the shot above it.
* **The arrow hands back what it was fired from.** ``AbstractArrow`` remembers
  the drawn stack in ``pickupItemStack``, which is the whole of what
  ``XieEntityArrow`` existed for, and ``ammoConsumed`` then sets
  ``Pickup.DISALLOWED`` so the redstone is gone for good.
* **``ammoless``** shoots with an empty inventory, and the arrow carries
  ``INTANGIBLE_PROJECTILE``, which the constructor reads as ``CREATIVE_ONLY``.

``flame`` rides along: it is asserted as a pair, since ``boneBow`` declares
none and fires the same shape of arrow. Survival throughout, because a creative
player is handed ammunition they do not have.
"""

import time

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

#: Long enough that a charging bow has been drawn for a while, short enough
#: that an automatic one has not filled the sky.
HELD = 0.5

PICKUP_DISALLOWED = 0
PICKUP_CREATIVE_ONLY = 2


def run(game, expect):
    x0, z0 = lane("bows")
    game.enter(x0, z0)
    game.clear((x0 - 2, z0 - 2), (x0 + 40, z0 + 2), floor="minecraft:stone")
    game.gamemode("survival")
    game.heal()
    # Aimed along the lane and a little up, so an arrow is in the air rather
    # than in the block it was fired at.
    game.tp(x0 + 0.5, Y, z0 + 0.5, -90.0, -20.0)
    game.kill_items()

    # --------------------------------------------- the crossbow that snap-fires
    _arm(game, "xiesmod:redstone_bow", "minecraft:redstone", 16)
    while_held = _shoot(game, hold=HELD, read_before_release=True)
    expect.that("a semiautomatic bow fires without being charged",
                while_held["during"] > 0,
                "%d arrows in the air while the button was still down" % while_held["during"])
    expect.that("the arrow hands back what it was fired from",
                game.entity_exists('@e[type=arrow,nbt={item:{id:"minecraft:redstone"}}]'),
                "which is the whole of what XieEntityArrow existed for")
    expect.that("ammoConsumed makes the shot unrecoverable",
                game.entity_exists("@e[type=arrow,nbt={pickup:%db}]" % PICKUP_DISALLOWED))
    alight = _burning(game)
    expect.that("flame lights the arrow", alight > 0, "remaining fire ticks: %d" % alight)

    # ---------------------------------------------------- and refuses an arrow
    _arm(game, "xiesmod:redstone_bow", "minecraft:arrow", 16)
    rejected = _shoot(game, hold=HELD)
    expect.equal("a bow declaring its ammo refuses everything else",
                 rejected["after"], 0,
                 "and the shot above is what says the click reaches the game")

    # ------------------------------------------------------ the bow that draws
    _arm(game, "xiesmod:bone_bow", "minecraft:arrow", 16)
    drawn = _shoot(game, hold=HELD, read_before_release=True)
    expect.equal("a bow with no semiautomatic charges rather than firing on the press",
                 drawn["during"], 0)
    expect.that("and fires when it is released", drawn["after"] > 0,
                "%d arrows after release" % drawn["after"])
    expect.that("a bow declaring no flame fires a cold arrow", _burning(game) <= 0)

    # ------------------------------------------------- and the one that is free
    _arm(game, "xiesmod:blaze_bow", None, 0)
    free = _shoot(game, hold=1.2)
    expect.that("ammoless fires with an empty inventory", free["after"] > 0)
    expect.that("and its arrow is intangible rather than pickable",
                game.entity_exists("@e[type=arrow,nbt={pickup:%db}]" % PICKUP_CREATIVE_ONLY))


def _arm(game, bow, ammo, count):
    """Holds the bow and stocks the ammunition, and says so if it did not.

    The ammunition goes in the *inventory* rather than the hotbar because
    ``weapon.mainhand`` is whichever hotbar slot happens to be selected, so
    stocking a hotbar slot can quietly replace the bow that was just put in
    hand — which reads as a bow that will not fire, and is a rig fault rather
    than a result. Checked rather than assumed, for the same reason.
    """
    game.clear_inventory()
    game.hold(bow)
    if ammo:
        game.put("inventory.0", ammo, count)
    game.cmd("kill @e[type=arrow]")
    time.sleep(0.4)
    if not game.holding(bow):
        raise RuntimeError("armed with %s but holding something else (selected slot %s)"
                           % (bow, game.data(game.player, "SelectedItemSlot")))
    if ammo and not game.probe("execute if items entity %s container.* %s"
                               % (game.player, ammo)):
        raise RuntimeError("the %s never reached the inventory" % ammo)


def _shoot(game, hold, read_before_release=False):
    counts = {"during": 0, "after": 0}
    game.press("right")
    try:
        time.sleep(hold)
        if read_before_release:
            counts["during"] = game.count_entities("@e[type=arrow]")
    finally:
        game.release("right")
    time.sleep(0.6)
    counts["after"] = game.count_entities("@e[type=arrow]")
    return counts


def _burning(game):
    fire = game.data("@e[type=arrow,limit=1]", "Fire")
    try:
        return int(float(fire.rstrip("sbfd") or 0))
    except ValueError:
        return 0
