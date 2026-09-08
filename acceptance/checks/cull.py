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

Three probes rather than one, because "the recipe is missing" and "the probe is
broken" look the same: the poppy recipe the live content culls, a vanilla recipe
nothing culls, and this mod's own red dye, which is what the cull was written to
make reachable and which the culler spares by namespace.
"""

NEEDS_CLIENT = False

CULLED = [
    ("minecraft:red_dye_from_poppy", "the poppy recipe flowerbushes.json culls"),
    ("minecraft:yellow_dye_from_dandelion", "and the dandelion one beside it"),
]

LIVE = [
    ("minecraft:stick", "a vanilla recipe nothing culls"),
    ("xiesmod:red_dye", "this mod's own red dye, which the culler spares"),
]


def run(game, expect):
    for recipe, what in CULLED:
        exists, reply = game.recipe_exists(recipe)
        expect.that("%s is gone" % what, not exists, reply if exists else "")

    for recipe, what in LIVE:
        exists, reply = game.recipe_exists(recipe)
        expect.that("%s is still there" % what, exists, "" if exists else reply)
