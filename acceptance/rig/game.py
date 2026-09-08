"""The two ways a check can touch the game, in one object.

rcon builds the situation and reads the result. Everything else — a click, a
held key — is synthetic input to the focused game window, because no command
right-clicks a block and nothing tests a ``useOn`` without one.

The helpers here are the ones that cost time to get right the first time:

* ``/execute if block`` is the state probe. A ``run say`` fires into chat,
  which the rcon reply never carries, so a test written that way answers with
  an empty body whichever way it went.
* aim is a property of the player rather than of the mouse, so ``stand_on``
  and ``stand_under`` are how a click picks its block and its face.
* ``/fill`` caps at 32768 blocks and says so in a reply that scrolls past, so
  ``fill`` splits anything larger rather than trusting it.
* ``/setblock`` does not ask ``canSurvive``, and with the block already there
  it is a no-op — which is why a pulse clears its position first.
"""

import os
import re
import time

from . import winput

FILL_LIMIT = 32768

#: A dummy objective, so that entities can be counted rather than only found.
COUNTER = "xieacceptance"

#: How long to let the client catch up with a teleport before clicking. The
#: click is raycast on the client against the client's own copy of the world,
#: so a click sent before the move has landed hits whatever the player was
#: looking at a moment ago — or nothing at all, if the chunk has not arrived.
SETTLE = 0.45

#: And how long a move to a whole new lane needs on top of that, which is the
#: chunks being sent rather than the player being moved.
ARRIVAL = 2.0


class Game:
    def __init__(self, session):
        self.session = session
        self.rcon = session.rcon
        self.player = session.player
        self.window = session.window
        self.run_dir = session.run_dir
        self._counter = False

    # ------------------------------------------------------------------ rcon

    #: Replies that are never a result, only a rig mistake. A command the
    #: parser refuses changes nothing and says so in a reply nobody reads —
    #: which is how a whole run of fills went missing once, with every check
    #: after it measuring a lane that had never been built.
    MISTAKES = ("Unknown or incomplete command", "Incorrect argument for command",
                "Expected whitespace", "Unknown block type", "Unknown item",
                "Can't find element", "That position is not loaded")

    def cmd(self, line):
        reply = self.rcon.command(line.strip())
        for mistake in self.MISTAKES:
            if mistake in reply:
                print("  !!! %s -> %s" % (line.strip(), reply.splitlines()[0]), flush=True)
                break
        return reply

    def probe(self, line):
        """A bare ``execute if ...``, which answers "Test passed"/"Test failed"."""
        return self.cmd(line).startswith("Test passed")

    def block_is(self, pos, block):
        return self.probe("execute if block %d %d %d %s" % (pos[0], pos[1], pos[2], block))

    def entity_exists(self, selector):
        return self.probe("execute if entity %s" % selector)

    def count_entities(self, selector):
        """How many entities a selector matches.

        ``execute store result ... if entity`` stores the match *count* rather
        than a boolean, and stores 0 when nothing matches, which is the only
        way to count without reading them one at a time.
        """
        if not self._counter:
            self.cmd("scoreboard objectives add %s dummy" % COUNTER)
            self._counter = True
        self.cmd("execute store result score #count %s if entity %s" % (COUNTER, selector))
        return int(_number(self.cmd("scoreboard players get #count %s" % COUNTER).split("has", 1)[-1]))

    def recipe_exists(self, recipe):
        """``/recipe give`` is a recipe-registry probe: the argument resolves
        against the recipes that actually loaded, so a culled or absent one
        answers "Unknown recipe" where a live one unlocks. It needs a real
        player — with nobody online the target check fails first and both cases
        answer the same thing, which reads like a result and is not one.

        Answers ``(exists, reply)``. The take first is what keeps a second run
        against the same world from answering "no recipes were unlocked" for a
        recipe that is perfectly present."""
        self.cmd("recipe take %s %s" % (self.player, recipe))
        reply = self.cmd("recipe give %s %s" % (self.player, recipe))
        return reply.startswith("Unlocked"), reply

    def data(self, target, path=""):
        reply = self.cmd(("data get entity %s %s" % (target, path)).strip())
        marker = "following entity data: "
        return reply.split(marker, 1)[1] if marker in reply else ""

    def block_data(self, pos, path=""):
        reply = self.cmd(("data get block %d %d %d %s" % (pos[0], pos[1], pos[2], path)).strip())
        marker = "following block data: "
        return reply.split(marker, 1)[1] if marker in reply else ""

    # ----------------------------------------------------------------- world

    def setblock(self, pos, block):
        return self.cmd("setblock %d %d %d %s" % (pos[0], pos[1], pos[2], block))

    def replace(self, pos, block):
        """Clears first, because setblock with the block already there is a no-op."""
        self.setblock(pos, "minecraft:air")
        return self.setblock(pos, block)

    def fill(self, corner, opposite, block, mode=""):
        x1, y1, z1 = corner
        x2, y2, z2 = opposite
        low = (min(x1, x2), min(y1, y2), min(z1, z2))
        high = (max(x1, x2), max(y1, y2), max(z1, z2))
        span_x = high[0] - low[0] + 1
        span_z = high[2] - low[2] + 1
        per_layer = span_x * span_z
        if per_layer > FILL_LIMIT:
            raise ValueError("a single layer of that fill is over the 32768 cap")
        layers = max(1, FILL_LIMIT // per_layer)
        y = low[1]
        while y <= high[1]:
            top = min(y + layers - 1, high[1])
            self.cmd("fill %d %d %d %d %d %d %s %s"
                     % (low[0], y, low[2], high[0], top, high[2], block, mode))
            y = top + 1

    def clear(self, corner, opposite, floor=None, height=20):
        """Empties a lane and, when asked, relays its floor."""
        x1, z1 = corner
        x2, z2 = opposite
        ground = self.ground
        self.fill((x1, ground + 1, z1), (x2, ground + height, z2), "minecraft:air")
        if floor:
            self.fill((x1, ground, z1), (x2, ground, z2), floor)

    def kill_items(self):
        self.cmd("kill @e[type=item]")
        self.cmd("kill @e[type=arrow]")

    def place_feature(self, feature, pos):
        return self.cmd("place feature %s %d %d %d" % (feature, pos[0], pos[1], pos[2]))

    # ---------------------------------------------------------------- player

    def gamemode(self, mode):
        self.cmd("gamemode %s %s" % (mode, self.player))

    def tp(self, x, y, z, yaw=0.0, pitch=0.0):
        self.cmd("tp %s %.3f %.3f %.3f %.1f %.1f" % (self.player, x, y, z, yaw, pitch))
        time.sleep(SETTLE)

    def stand_on(self, pos, yaw=0.0):
        """Aims at the block underfoot: ``pitch 90`` is the block you stand on."""
        self.tp(pos[0] + 0.5, pos[1] + 1, pos[2] + 0.5, yaw, 90.0)

    def stand_under(self, pos, yaw=0.0):
        """Aims at the block overhead, which is the one face a transform refuses."""
        self.tp(pos[0] + 0.5, pos[1] - 2, pos[2] + 0.5, yaw, -90.0)

    def heal(self):
        self.cmd("effect clear %s" % self.player)
        self.cmd("effect give %s minecraft:instant_health 1 10 true" % self.player)
        self.cmd("effect give %s minecraft:saturation 1 5 true" % self.player)
        time.sleep(0.2)
        self.cmd("effect clear %s" % self.player)

    def clear_inventory(self):
        self.cmd("clear %s" % self.player)

    def hold(self, item, count=1):
        self.cmd("item replace entity %s weapon.mainhand with %s %d" % (self.player, item, count))

    def put(self, slot, item, count=1):
        self.cmd("item replace entity %s %s with %s %d" % (self.player, slot, item, count))

    def count_items(self, item):
        """How many of an item the player is carrying.

        ``/clear <player> <item> 0`` counts without taking anything, which is
        the only way to read a number rather than a yes or no — and a dropped
        item is in the player's hands within a second of landing at their feet,
        so counting on the ground is counting a race.
        """
        reply = self.cmd("clear %s %s 0" % (self.player, item))
        found = re.search(r"Found (\d+)", reply)
        return int(found.group(1)) if found else 0

    def holding(self, predicate):
        return self.probe("execute if items entity %s weapon.mainhand %s"
                          % (self.player, predicate))

    def health(self):
        return _number(self.data(self.player, "Health"))

    def position(self):
        raw = self.data(self.player, "Pos")
        return tuple(_number(part) for part in raw.strip("[]").split(","))

    # ----------------------------------------------------------------- input

    def focus(self):
        if self.window is None:
            raise RuntimeError("no client window: this check needs a client")
        winput.focus(self.window)

    def right_click(self, hold=0.06):
        self.focus()
        winput.click("right", hold)

    def left_click(self, hold=0.12):
        self.focus()
        winput.click("left", hold)

    def click_until(self, predicate, attempts=3, button="right", hold=0.06, timeout=1.5):
        """Clicks until the world says the click landed, and says how many it took.

        A click can be lost for reasons that have nothing to do with the mod —
        the client is still being sent the chunk it is about to raycast against,
        and it cannot pick a block it does not have yet. Reporting the number of
        attempts keeps that visible instead of hiding it: one is the normal
        answer, and a check that habitually needs three is a rig that wants
        looking at.
        """
        for attempt in range(1, attempts + 1):
            if button == "right":
                self.right_click(hold)
            else:
                self.left_click(hold)
            if self.wait_until(predicate, timeout=timeout, interval=0.25):
                return attempt
        return 0

    def press(self, button="right"):
        self.focus()
        winput.mouse_down(button)

    def release(self, button="right"):
        winput.mouse_up(button)

    def walk(self, key="w", seconds=2.0):
        self.focus()
        winput.key_down(key)
        try:
            time.sleep(seconds)
        finally:
            winput.key_up(key)
        time.sleep(0.3)

    def screenshot(self):
        """``F2`` writes a real PNG; ``PrintWindow`` on an OpenGL window does not."""
        folder = os.path.join(self.run_dir, "screenshots")
        before = set(os.listdir(folder)) if os.path.isdir(folder) else set()
        try:
            self.focus()
            winput.tap("f2")
        except Exception:
            return None
        for _ in range(20):
            time.sleep(0.25)
            if os.path.isdir(folder):
                fresh = set(os.listdir(folder)) - before
                if fresh:
                    return sorted(fresh)[-1]
        return None

    # --------------------------------------------------------------- waiting

    def wait_until(self, predicate, timeout=30.0, interval=0.5):
        """Answers what the predicate answered, once it is true or time is up."""
        deadline = time.monotonic() + timeout
        while True:
            answer = predicate()
            if answer:
                return answer
            if time.monotonic() >= deadline:
                return answer
            time.sleep(interval)

    # ----------------------------------------------------------------- arena

    #: The flat world's top solid layer. Blocks a check places sit at
    #: ``ground + 1``; a check that needs soil replaces the ground itself.
    ground = -61

    #: Gamerules did not merely become snake_case in 26.1 — several were
    #: renamed outright, and the old name fails with a bare "Incorrect argument
    #: for command" that scrolls past. ``doDaylightCycle`` is ``advance_time``,
    #: ``doMobSpawning`` is ``spawn_mobs``, ``naturalRegeneration`` is
    #: ``natural_health_regeneration``, and ``doFireTick`` is gone altogether in
    #: favour of a radius.
    GAMERULES = (
        ("advance_time", "false"),
        ("advance_weather", "false"),
        ("spawn_mobs", "false"),
        ("fire_spread_radius_around_player", "0"),
        ("natural_health_regeneration", "false"),
        ("immediate_respawn", "true"),
        ("keep_inventory", "true"),
        ("random_tick_speed", "3"),
        ("mob_griefing", "false"),
        ("send_command_feedback", "true"),
    )

    def prepare(self):
        """The gamerules every check wants, and a fixed noon."""
        for rule, value in self.GAMERULES:
            self.cmd("gamerule %s %s" % (rule, value))
        self.cmd("time set noon")
        self.cmd("weather clear")
        self.cmd("difficulty easy")
        self.kill_items()

    def enter(self, x, z, yaw=0.0, pitch=0.0, timeout=30.0):
        """Moves the player into a lane and waits for its chunks.

        Every check does this before it builds anything. A command against an
        unloaded position answers "That position is not loaded" and changes
        nothing — and with no player there is nothing loaded anywhere, which is
        the same reason a check cannot be run from rcon alone.
        """
        self.tp(x + 0.5, self.ground + 1, z + 0.5, yaw, pitch)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if "not loaded" not in self.cmd(
                    "execute if block %d %d %d minecraft:air" % (int(x), self.ground + 8, int(z))):
                time.sleep(ARRIVAL)
                return True
            time.sleep(0.5)
        raise RuntimeError("the chunks at %d, %d never loaded" % (x, z))

    def preflight(self, x, z):
        """Whether the world is the flat one the arena is measured against.

        Against a normal world every lane would be built inside a hillside and
        every check would fail for the same uninteresting reason.
        """
        self.enter(x, z)
        if not self.block_is((int(x), self.ground, int(z)), "minecraft:grass_block"):
            return "the world is not flat: nothing at y=%d to build on" % self.ground
        if not self.block_is((int(x), self.ground + 10, int(z)), "minecraft:air"):
            return "the world is not flat: there is terrain over the arena"
        return None

    def random_ticks(self, speed):
        """Nothing random-ticks without a player, and not much does with one at 3."""
        self.cmd("gamerule random_tick_speed %d" % speed)


def _number(text):
    match = re.search(r"-?\d+(?:\.\d+)?", text or "")
    return float(match.group(0)) if match else float("nan")
