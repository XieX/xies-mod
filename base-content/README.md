Xie's Mod: base content
=======================

The crops, fruit trees, foods and cookpot that come with Xie's Mod, and how to play
with them. Everything here is plain JSON: the mod unpacks this folder to
`.minecraft/xiecontent/base-content/` on first launch, and what you change there is
what the game loads. See the [main README](../README.md) for installing and for
writing content of your own.

Your recipe book unlocks each recipe the first time you pick up one of its
ingredients, and a recipe viewer such as JEI or EMI shows every combination. This
page explains the patterns behind them, and the things a recipe viewer can't show.

Getting started
---------------

The quickest way in is **seed splicing**. Craft wheat seeds with redstone to make
**Hybrid Seeds**, then craft a hybrid seed with another seed to make a new one:

| hybrid seeds + | makes | | corn kernels + | makes |
|---|---|---|---|---|
| rose seeds | apple seeds | | dandelion seeds | onion seeds |
| dandelion seeds | lemon seeds | | melon seeds | tea leaves |
| pumpkin seeds | orange seeds | | pumpkin seeds | soy beans |
| melon seeds | avocado seed | | | |
| apple seeds | tomato seeds | | | |
| lemon seeds | corn kernels | | | |
| orange seeds | cotton seeds | | | |
| avocado seed | lettuce seeds | | | |

Rose and dandelion seeds come from crafting a poppy or a dandelion, so between
them and the vanilla seeds every crop and tree in the mod can be started from
scratch. Hybrid seeds also grow into **Hybrid Wheat**, which drops wheat and
occasionally more hybrid seeds.

You can also find most crops growing wild:

- **Shrubs** grow in plains, hills, swamps and savannas. Breaking one sometimes
  gives lettuce or onion seeds.
- **Wild corn** grows in plains, meadows and savannas, and sometimes drops a corn
  cob.
- **Wild tomatoes** grow in forests, swamps and hills, and drop a tomato.
- **Fruit trees** grow in the biomes listed under [Fruit trees](#fruit-trees).

Wild plants, fruit trees and salt deposits all come from the "Xie's Mod worldgen"
data pack. It is on by default; turn it off when creating a world if you would
rather craft everything.

Crops
-----

Crops come in three kinds, and the kind decides how you harvest them.

- **Bushes are picked.** Tomato, cotton, soy, tea, rose and dandelion bushes ripen,
  and a right-click on a ripe bush picks its crop and sets it back a few stages to
  grow again. Breaking a bush gives you its seeds instead, more the riper it was.
- **Crops are reaped.** Corn and hybrid wheat are broken when ripe and replanted.
  They grow several times faster in a tended field, beside other crops on
  farmland, than alone. Corn is three blocks tall when fully grown.
- **Plants are pulled.** Lettuce and onion are broken when grown. Lettuce gives a
  head and a chance of seeds. Onion seeds grow your first onion, and from then on
  you replant the onions themselves, which come up one to four at a time.

Everything plants on farmland. Tomatoes, roses and dandelions also grow on dirt
and grass. Bone meal works on all of them.

Fruit trees
-----------

Apple, orange, lemon and avocado trees. Plant a fruit seed and it grows into a
sapling, then a tree. Fruit seeds are crafted from the fruit itself, or spliced.

Fruit ripens on the leaves, on the outside of the canopy, and shows when it is
ready. Right-click a ripe leaf to pick it. A tree spaces its fruit out, so one that
nobody picks fills up and then stops. Bone meal ripens a leaf, and breaking a leaf has a
small chance of dropping its fruit. Leaves you placed yourself never
bear fruit.

In the wild each fruit has its own biomes: **apples** in forests other than birch,
**lemons** in birch forests, **oranges** in savannas and **avocados** in jungles
and swamps.

Salt
----

Salt deposits are a thin crust on sand, near water anywhere and in larger patches
in deserts. Break one for salt. Four salt craft into a block of salt and back.

If you play without the worldgen pack, or your beaches were generated before you
installed the mod, smelt a dried kelp block into a block of salt.

Salt goes into corn chips, jerky, ketchup and tomato juice, and seasons the cookpot.

Food
----

### Fillings

Most meals are built from three groups of fillings, and any member of a group
works:

- **Protein:** cooked meat or fish, tofu (raw or cooked) and cheese slices.
- **Salad:** lettuce, tomato, onion, avocado, soy beans, carrot and beetroot.
- **Sauce:** ketchup, mayonnaise and guacamole.

### Sammiches, tacos and burritos

All three are shapeless: bread, taco shells or tortillas, plus fillings.

- **Sammiches** take two slices of bread per filling, up to six slices and three
  fillings for a batch of six.
- **Tacos** take two taco shells per filling, the same way.
- **Burritos** take one tortilla per filling, up to three.

What you get depends on the fillings:

- **Sammiches:** a protein and a sauce make Tasty Sammiches. Otherwise, at least as
  much protein as salad makes Sammiches, and anything else Snack Sammiches.
- **Tacos and burritos:** a protein and a sauce make Tacos or Burritos, and
  anything else makes Snack Tacos or Snack Burritos.

### Salads

A bowl and three fillings, shapeless. Three salad ingredients make a **Garden
Salad**, and two salad ingredients and a protein make a **Salad**. Add a sauce as a
fourth for the dressed version of either. Three fruits (apple, melon slice, orange,
avocado, sweet berries, glow berries) make a **Fruit Salad**. You get the bowl back
when you eat it.

### Cooking

Anything here can be cooked in a furnace, smoker or on a campfire.

- A **tortilla** (three corn kernels) cooks into a **taco shell**.
- **Corn kernels** cook into **popcorn**, and a **corn cob** into **corn on the cob**.
- **Tofu** (nine soy beans) cooks into **cooked tofu**.

### Corn

A corn cob crafts into two corn kernels, which are also its seeds. A taco shell
crafts into four tortilla chips, and salt with up to eight tortilla chips makes
the same number of corn chips.

### Drinks and sauces

- **Juice** is fruit over a glass bottle: two apples for cider, an orange, two
  melon slices, sugar and a lemon for lemonade, or salt and a tomato for tomato
  juice.
- **Bottled milk** is a milk bucket and three glass bottles, which makes three and
  gives you the bucket back. Sugar and an avocado over bottled milk make avocado
  juice, and sugar and cocoa beans over it make chocolate milk.
- **Sweet tea** is a water bottle, sugar and tea leaves, and gives a short burst
  of speed. **Chocolate milk** gives a short burst of haste.
- **Ketchup** is three tomatoes, an onion, salt, sugar and a glass bottle.
- **Mayonnaise** is a lemon over an egg over oil, in a column. **Oil** is six seeds,
  or three corn kernels, with a bowl, and also burns as fuel.
- **Guacamole** is a bowl, an avocado, a tomato, a lemon and an onion.

You get the bottle or bowl back when you finish a drink or a sauce, or when one
goes into a recipe.

### Everything else

- **Beef jerky:** salt and raw beef makes four.
- **Chocolate bars:** a row of sugar over a row of cocoa beans over a milk bucket
  makes six.
- **Milk:** six soy beans and a water bucket make a milk bucket.
- **String:** two cotton, one above the other, make two string.
- **Dyes:** a poppy crafts into rose seeds rather than red dye, and red dye now
  comes only from rose seeds. Dandelions and yellow dye work the same way.

The cookpot
-----------

Craft a campfire and a cauldron together. Everything goes in by right-click:

1. **Fill it** with a water bucket.
2. **Light it** with any furnace fuel. It cooks only while it is burning.
3. **Add ingredients.** Almost any plain food works. Proteins are raw or cooked
   meat and fish, tofu and cheese, and salt or beef jerky season it.
4. **Ladle it out** with an empty bowl once it has stopped steaming.

The pot keeps cooking as long as it has fuel. It changes colour to show the
meal the next bowl will get, and serves the best meal its contents allow:

| in the pot | meal | hunger |
|---|---|---|
| food | Weak Soup | 4 |
| food and seasoning, or protein alone | Soup | 6 |
| food, protein and seasoning | Stew | 10 |

It won't take harmful foods, magical ones such as golden apples, or anything that comes in a
bowl or bottle.

Cheese
------

Smelt a milk bucket into a **bucket of cheese**, then place it like a block to
pour out **fresh cheese** and get your bucket back. Fresh cheese ripens where it
stands into a **cheese block** in about a day. It needs no light or soil, so a
cellar is fine, and bone meal doesn't hurry it. Breaking fresh cheese before it ripens wastes
it.

What happened to…
-----------------

Minecraft has caught up with some of what this mod added in 2013, so those have
been dropped rather than kept as worse duplicates:

| removed | use instead |
|---|---|
| campfire and ash pile | the vanilla campfire (1.14) |
| fertiliser and potash | the composter and bone meal (1.14) |
| pumpkin pieces | pumpkin pie |
| glowberry bush | glow berries on cave vines (1.17) |
| melon changes | vanilla melons |
| foraging stick | seed splicing, and wild plants |
| cocoa tree | cocoa pods on jungle logs |

The Food Stacking and Hunger mods are gone for the same reason: food stacks to 64
and hunger has been part of the game since Beta 1.8. The Cooking Mod's stove
lives on as the cookpot. Vanilla still has no fruit trees, salt, tofu or
sandwiches.

Credits
-------

Better textures contributed by Thistle, the less good textures by Xie. This content and its
textures are licensed [CC BY 4.0](LICENSE).
