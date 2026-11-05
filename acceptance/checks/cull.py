"""``cull``, the only thing the format asks for that removes rather than adds.

It is also the only one that leaves no trace in the generated pack: there is no
recipe list to mutate any more, so ``XieRecipeCuller`` runs off
``ModifyRecipeJsonsEvent`` and drops entries from the map of everything that
loaded. A datapack cannot say it and a file on disk cannot show it, so the only
place the answer exists is the running server's recipe registry.

``/recipe give <player> <id>`` is that probe: the argument resolves against the
recipes that actually loaded, so a culled one answers "Unknown recipe" while a
live one unlocks. It needs a real player — with nobody online the target check
fails first and both cases answer "No player was found", which reads like a
result and is not one.

The live content culls nothing, so the cull comes from
``acceptance/packs/cull_fixture/``, which the run copies in: the shape of the
2013 SurvivalTweaks pack, culling vanilla's stone sword and adding one of its
own made of flint.

Three probes rather than one, because "the recipe is missing" and "the probe is
broken" look the same: the stone sword recipe the fixture culls, a vanilla
recipe nothing culls, and the fixture's own stone sword, which is what the cull
was written to make room for and which, not being vanilla's, no cull can reach.
"""

#: Not for a click: ``/recipe give`` needs a player to give to, and with nobody
#: online every probe answers "No player was found" — which the culled half reads
#: as "gone" and the live half as "missing", so the check would half-pass for the
#: wrong reason rather than fail.
NEEDS_CLIENT = True

CULLED = [
    ("minecraft:stone_sword", "the stone sword recipe the fixture culls"),
]

LIVE = [
    ("minecraft:stick", "a vanilla recipe nothing culls"),
    ("xiesmod:stone_sword", "the fixture's own stone sword, which no cull reaches"),
]


def run(game, expect):
    for recipe, what in CULLED:
        exists, reply = game.recipe_exists(recipe)
        expect.that("%s is gone" % what, not exists, reply if exists else "")

    for recipe, what in LIVE:
        exists, reply = game.recipe_exists(recipe)
        expect.that("%s is still there" % what, exists, "" if exists else reply)
