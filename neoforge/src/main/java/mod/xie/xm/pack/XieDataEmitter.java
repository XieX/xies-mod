package mod.xie.xm.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.block.XieTreeBlock;
import mod.xie.xm.content.model.AliasDef;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.DropTable;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.LegacyBlockIds;
import mod.xie.xm.content.model.MetaMap;
import mod.xie.xm.content.model.PropertyMap;
import mod.xie.xm.content.model.RecipeDef;
import mod.xie.xm.content.model.ResourceNames;
import mod.xie.xm.content.model.SeedReturns;
import mod.xie.xm.content.model.Variants;
import mod.xie.xm.reg.RegisteredContent;
import mod.xie.xm.reg.XieOverrides;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Writes the generated datapack: loot tables from {@code drops}, and recipes.
 *
 * <p>This is where the port earns the most against the 2013 engine. The original
 * rolled its own drop logic ({@code Droppables}) and registered recipes directly
 * with {@code CraftingManager}; here both become JSON that vanilla's own loaders
 * consume, so fortune, explosion decay, the recipe book and datapack overrides all
 * come for free.
 *
 * <p>The single largest reduction is aliases. {@code $salady} used to be expanded
 * into the cartesian product of concrete recipes at load time — one sammich recipe
 * with three alias slots over six-item aliases became 216 registered recipes.
 * Modern Minecraft has item tags, so an alias becomes one tag and the recipe stays
 * one recipe.
 *
 * <p>References are resolved against the live registries rather than trusted
 * blindly. Most of the content is not registered yet — only a few archetypes are
 * ported — so a recipe naming an item this build cannot make would otherwise write
 * JSON that fails to load and buries the real errors. Anything unresolvable is
 * dropped here, counted, and reported.
 */
public final class XieDataEmitter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Whether a namespaced id names an item that actually exists in the registry. */
    @FunctionalInterface
    public interface ItemLookup {
        boolean exists(String id);
    }

    /**
     * The same question for blocks.
     *
     * <p>Kept separate because the two registries genuinely disagree here: no Xie
     * block registers a {@link net.minecraft.world.item.BlockItem}, so the four
     * blocks worldgen exists to place — {@code shrub}, {@code wild_corn},
     * {@code wild_tomato} and {@code salt_deposit} — are in one registry and not the
     * other. Asking the item registry about them would answer no.
     */
    @FunctionalInterface
    public interface BlockLookup {
        boolean exists(String id);
    }

    /** Vanilla's crafting grid; anything larger was rejected in 2013 too. */
    private static final int MAX_GRID = RecipeDef.MAX_GRID;

    /** Furnace defaults, since a {@code cooked} declaration names neither. */
    private static final int SMELT_TIME = 200;
    private static final float SMELT_EXPERIENCE = 0.1f;

    /**
     * The three ways food is cooked, and how long each takes.
     *
     * <p>Vanilla's own numbers: a smoker is twice as fast as a furnace and a campfire
     * three times as slow, and every vanilla foodstuff that smelts has all three
     * recipes. 1.6 had only the furnace, so the other two are what "cook this" means
     * now rather than something the content asked for.
     */
    private record Cooker(String recipeType, int cookingTime, boolean foodOnly) { }

    private static final List<Cooker> COOKERS = List.of(
        new Cooker("minecraft:smelting", SMELT_TIME, false),
        new Cooker("minecraft:smoking", SMELT_TIME / 2, true),
        new Cooker("minecraft:campfire_cooking", SMELT_TIME * 3, true));

    /** Where NeoForge reads burn times from; the namespace is the data map's, not ours. */
    private static final String FUEL_DATA_MAP = "data/neoforge/data_maps/item/furnace_fuels.json";

    /** The one archetype whose {@code blocks} map anything reads. See {@link #checkStickDrops}. */
    private static final String STICK = "stick";

    /**
     * What one call to {@link #emit} produced.
     *
     * @param lootTables    block loot tables written
     * @param recipes       recipes written
     * @param advancements  recipe-book unlock advancements written, one per recipe
     * @param tags          alias item tags written
     * @param blockTags     block tags written: a tool's mining rules, a stem's soil,
     *                      and the log tag a {@code log} joins
     * @param features      worldgen entries written, as feature and biome modifier
     * @param trees         tree features written, one per registered {@code tree}
     * @param fuels         burn times written into the furnace fuel data map
     * @param skippedDrops  drops whose item could not be resolved
     * @param skippedRecipes recipes dropped because something in them is not ported
     * @param skippedSpawns worldgen entries dropped for the same reason
     * @param skippedCulls  {@code cull} recipes naming nothing that exists to remove
     * @param overrides     files written into the override pack, replacing a vanilla
     *                      id rather than adding one of ours
     */
    public record Counts(int lootTables, int recipes, int advancements, int tags, int blockTags,
                         int features, int trees, int fuels, int skippedDrops, int skippedRecipes,
                         int skippedSpawns, int skippedCulls, int overrides) { }

    private final Path outputRoot;
    private final Path overrideRoot;
    private final Path dataRoot;
    private final ItemLookup lookup;
    private final BlockLookup blockLookup;
    private final XieWorldgenEmitter worldgen;

    /** Every name any pack declares, so a reference can be told from a vanilla one. */
    private final Set<String> declaredNames = new HashSet<>();

    /**
     * Declared names that speak for a vanilla object rather than registering one, and
     * the object each names.
     *
     * <p>Both halves are needed. A definition declaring {@code override} registers
     * nothing, so a reference to it has to fall through — and it has to fall through
     * to the object it overrides rather than to its own name, which is generally not
     * a vanilla name at all: {@code wheatOverride} means {@code minecraft:wheat}, not
     * {@code minecraft:wheat_override}.
     */
    private final Map<String, ItemRef> overrideTargets = new LinkedHashMap<>();

    /** Alias name to the tag reference it became, e.g. {@code salady} to {@code #xiesmod:salady}. */
    private final Map<String, String> aliasTags = new LinkedHashMap<>();

    /**
     * Alias name to the first thing it stands for.
     *
     * <p>An alias is a set, and as a recipe ingredient it becomes a tag — but a
     * <em>block</em> reference cannot be a set, so {@code Xie.parseBlock} took the
     * first member and so does this. It is how a {@code spawns} entry keyed by an
     * alias finds its block at all: {@code fruit.json} declares no spawn entry named
     * {@code fruitLeaves}, only four named for the trees, and the alias is the whole
     * of the link between them.
     */
    private final Map<String, ItemRef> aliasTargets = new LinkedHashMap<>();

    /** Recipe ids already written; names repeat across files and must not collide. */
    private final Set<String> usedRecipeIds = new HashSet<>();

    /**
     * The items every {@code cull} entry named, resolved.
     *
     * <p>A cull writes no file — it removes recipes rather than adding any — so this
     * is the emitter's whole output for it, handed to {@link mod.xie.xm.recipe.XieRecipeCuller}
     * to apply when the recipes are actually loaded. Resolving it here is what keeps
     * {@code redDye} and {@code stoneSword} meaning the same thing they mean anywhere
     * else in a definition file.
     */
    private final Set<String> culledItems = new LinkedHashSet<>();

    /**
     * The id path of every subtype of a split definition, mapped to itself.
     *
     * <p>Content refers to a subtype two ways: as {@code fruitSeed:1:0}, which the
     * definition-name lookup handles, and by an alias declared for it. The alias
     * reduces to the same path the subtype's own display name does — {@code
     * appleSeeds} and {@code "Apple Seeds"} both give {@code apple_seeds} — so a
     * reference by alias name resolves against this set without a lookup table.
     * Only subtype paths are in it, so an unregistered definition still fails to
     * resolve instead of being quietly rewritten.
     */
    private final Set<String> subtypePaths = new HashSet<>();

    /** What the registrar registered; empty until {@link #emit} has been called. */
    private RegisteredContent registered = RegisteredContent.empty();

    /** The registered ids of everything declared {@code type: food}; see {@link #foodIds}. */
    private Set<String> foods = Set.of();

    /** Files written into the override pack, which is registered only if there are any. */
    private int overrides;

    /** Unlock advancements written; see {@link #writeRecipeFile}. */
    private int advancements;

    public XieDataEmitter(Path outputRoot, Path overrideRoot, Path worldgenRoot,
                          ItemLookup lookup, BlockLookup blockLookup) {
        this.outputRoot = outputRoot;
        this.overrideRoot = overrideRoot;
        this.dataRoot = outputRoot.resolve("data").resolve(XiesMod.MOD_ID);
        this.lookup = lookup;
        this.blockLookup = blockLookup;
        Path spawnRoot = worldgenRoot.resolve("data").resolve(XiesMod.MOD_ID);
        this.worldgen = new XieWorldgenEmitter(dataRoot, spawnRoot, new XieWorldgenEmitter.BlockRefs() {
            @Override
            public Optional<String> modBlock(ItemRef ref) {
                return XieDataEmitter.this.modBlock(ref);
            }

            @Override
            public Optional<String> anyBlock(ItemRef ref) {
                return XieDataEmitter.this.anyBlock(ref);
            }
        });
    }

    /**
     * @param packs      the loaded content
     * @param registered what the registrar registered; only those ids can be named
     */
    public Counts emit(List<ContentPack> packs, RegisteredContent registered) throws IOException {
        declaredNames.clear();
        overrideTargets.clear();
        aliasTags.clear();
        aliasTargets.clear();
        usedRecipeIds.clear();
        subtypePaths.clear();
        culledItems.clear();
        overrides = 0;
        advancements = 0;
        this.registered = registered;

        for (ContentPack pack : packs) {
            pack.aliases().values().stream().filter(alias -> !alias.members().isEmpty())
                .forEach(alias -> aliasTargets.putIfAbsent(alias.name(), alias.members().get(0)));

            declaredNames.addAll(pack.blocks().keySet());
            declaredNames.addAll(pack.items().keySet());
            pack.blocks().values().stream().filter(BlockDef::isOverride)
                .forEach(def -> overrideTargets.put(def.name(), def.override().orElseThrow()));
            pack.items().values().stream().filter(ItemDef::isOverride)
                .forEach(def -> overrideTargets.put(def.name(), def.override().orElseThrow()));

            collectSubtypePaths(pack.blocks().values().stream()
                .filter(def -> Variants.isSplit(def.props())).map(BlockDef::name).toList(),
                registered.blockPaths());
            collectSubtypePaths(pack.items().values().stream()
                .filter(def -> Variants.isSplit(def.props())).map(ItemDef::name).toList(),
                registered.itemPaths());
        }

        int tags = writeAliasTags(packs);
        int blockTags = writeBlockTags(registered);
        foods = foodIds(packs, registered);

        int lootTables = 0;
        int skippedDrops = 0;
        int recipes = 0;
        int skippedCulls = 0;
        List<String> skippedRecipes = new ArrayList<>();

        for (ContentPack pack : packs) {
            for (BlockDef def : pack.blocks().values()) {
                if (def.isOverride()) {
                    LootResult result = writeOverrideLootTable(def);
                    lootTables += result.written() ? 1 : 0;
                    skippedDrops += result.skippedDrops();
                    continue;
                }
                List<String> paths = registered.blockPaths().get(def.name());
                if (paths == null) {
                    continue;
                }
                List<String> names = Variants.namesIn(def.props());
                for (int meta = 0; meta < paths.size(); meta++) {
                    String path = paths.get(meta);
                    if (def.archetype().equals("tree") || def.archetype().equals("bush")) {
                        // A tree reads its fruit per subtype; a bush is picked only
                        // ripe, so reads it at its last stage.
                        int chanceAt = def.archetype().equals("bush")
                            ? registered.maxAges().getOrDefault(path, 0)
                            : names.isEmpty() ? 0 : meta;
                        LootResult harvest = writeHarvestTable(def, chanceAt, path);
                        lootTables += harvest.written() ? 1 : 0;
                        skippedDrops += harvest.skippedDrops();
                    }
                    if (def.drops().isEmpty()) {
                        // A block that declares no drops yields nothing — unless it
                        // has an item form, in which case it yields itself. That is
                        // the one branch XieBlockSapling added to the drop code, and
                        // the only archetype with an item form is the sapling.
                        if (registered.blockItems().contains(path)) {
                            writeSelfDropTable(path);
                            lootTables++;
                        }
                        continue;
                    }
                    LootResult result = writeLootTable(def, names, meta, path,
                        registered.maxAges().getOrDefault(path, 0));
                    lootTables += result.written() ? 1 : 0;
                    skippedDrops += result.skippedDrops();
                }
            }

            for (ItemDef def : pack.items().values()) {
                skippedDrops += checkStickDrops(def);
            }

            // Written all the same: the content may mean it, and only the content can
            // say what it should be instead.
            for (SeedReturns.Finding found : SeedReturns.in(pack.blocks(), pack.items())) {
                LOGGER.warn("Block {} drops its own seed \"{}\" at {}% x {} when just planted, "
                        + "{} per break on average: planting and breaking it costs no seed",
                    found.block().qualifiedName(), found.seed().name(), found.chance(),
                    found.amount(), found.perBreak());
            }

            for (RecipeDef recipe : pack.recipes()) {
                if (recipe.kind() == RecipeDef.Kind.CULL) {
                    if (!collectCulls(recipe)) {
                        skippedCulls++;
                    }
                    continue;
                }
                int written = writeRecipe(recipe);
                if (written > 0) {
                    recipes += written;
                } else {
                    skippedRecipes.add(recipe.origin().qualifiedName());
                }
            }

            recipes += writeCookingRecipes(pack);
        }

        int fuels = writeFuelValues(packs);

        // Named, not just counted. A recipe that quietly fails to appear is the
        // hardest thing to diagnose while archetypes are still being ported, and
        // the reason for each one is a debug line away once you know which to look
        // for.
        if (!skippedRecipes.isEmpty()) {
            LOGGER.info("Recipes not emitted, because something they name is not registered yet: {}",
                String.join(", ", skippedRecipes));
        }

        if (!culledItems.isEmpty()) {
            LOGGER.info("Culling every recipe but this mod's own for: {}",
                String.join(", ", culledItems));
        }

        XieWorldgenEmitter.Counts spawns = worldgen.emit(packs, registered.trees());

        return new Counts(lootTables, recipes, advancements, tags, blockTags, spawns.features(),
            spawns.trees(), fuels, skippedDrops, skippedRecipes.size(), spawns.skipped(),
            skippedCulls, overrides);
    }

    /** The items every {@code cull} entry named; empty until {@link #emit} has run. */
    public Set<String> culledItems() {
        return Set.copyOf(culledItems);
    }

    // ----------------------------------------------------------- loot tables

    private record LootResult(boolean written, int skippedDrops) { }

    /**
     * One loot table per block, at the id vanilla derives from the block's own:
     * {@code xiesmod:blocks/<path>}, so nothing has to be wired up on the block.
     *
     * <p>A split block gets one table per subtype. Its drop chances are keyed by the
     * metadata value that chose the subtype, not by an age it no longer has, so the
     * chance for its own subtype applies flat and the others belong to its siblings.
     */
    private LootResult writeLootTable(BlockDef def, List<String> names,
                                      int meta, String path, int maxAge) throws IOException {
        String blockId = XiesMod.MOD_ID + ":" + path;
        boolean split = !names.isEmpty();

        JsonArray pools = new JsonArray();
        int skipped = 0;

        for (DropTable.Drop drop : def.drops().drops()) {
            Optional<String> item = resolve(drop.item());
            if (item.isEmpty()) {
                LOGGER.debug("Block {} drops \"{}\", which is not registered; skipping that drop",
                    def.qualifiedName(), drop.item());
                skipped++;
                continue;
            }
            if (split) {
                addPools(pools, drop, null, item.get(), clampPercent(drop.chanceFor(meta)));
            } else {
                addDropPools(pools, blockId, item.get(), drop, maxAge, maxAge);
            }
        }

        if (pools.isEmpty()) {
            return new LootResult(false, skipped);
        }

        PackJson.write(dataRoot.resolve("loot_table").resolve("blocks").resolve(path + ".json"),
            blockLootRoot(XiesMod.MOD_ID + ":blocks/" + path, pools));
        return new LootResult(true, skipped);
    }

    /**
     * What picking a ripe leaf or bush yields, at {@code xiesmod:harvest/<path>}: the
     * id {@code XieTreeBlock} and {@code XieBushBlock} ask for, and the same kind of
     * address vanilla keeps its own picked things at,
     * {@code minecraft:harvest/sweet_berry_bush} and {@code harvest/cave_vine}.
     *
     * <p>{@code fruit} is read as a split block's {@code drops} are: the chance written
     * against {@code chanceAt} applies flat, and no age condition is needed because
     * the block consults the table only when it is ripe. A block the table gives
     * nothing gets no file, and one that asks for it anyway finds an empty table,
     * which is what a missing loot table resolves to.
     *
     * @param chanceAt the metadata the fruit's chance is read at: a tree's subtype, or
     *                 a bush's last growth stage
     */
    private LootResult writeHarvestTable(BlockDef def, int chanceAt, String path) throws IOException {
        JsonArray pools = new JsonArray();
        int skipped = 0;

        for (DropTable.Drop drop : def.fruit().drops()) {
            Optional<String> item = resolve(drop.item());
            if (item.isEmpty()) {
                LOGGER.debug("Tree {} bears \"{}\", which is not registered; skipping that fruit",
                    def.qualifiedName(), drop.item());
                skipped++;
                continue;
            }
            addPools(pools, drop, null, item.get(), clampPercent(drop.chanceFor(chanceAt)));
        }

        if (pools.isEmpty()) {
            return new LootResult(false, skipped);
        }
        String id = XieTreeBlock.HARVEST_PREFIX + path;
        PackJson.write(dataRoot.resolve("loot_table").resolve(id + ".json"),
            blockLootRoot(XiesMod.MOD_ID + ":" + id, pools));
        return new LootResult(true, skipped);
    }

    /**
     * The loot table for a definition that {@code override}s a vanilla block, written
     * at that block's own id so it replaces the table vanilla ships.
     *
     * <p>This is the one place a datapack can express an override outright, and it is
     * the mechanism {@link mod.xie.xm.recipe.XieRecipeCuller} exists because
     * <em>cannot</em> be used for {@code cull}: a pack overrides a loot table by id,
     * and a cull knows no ids where an override knows exactly one. Writing
     * {@code data/minecraft/loot_table/blocks/wheat.json} into the generated pack
     * really does replace vanilla's, wholesale — which is what an override asks for,
     * since in 2013 the vanilla block was gone and the Xie block's drops were all
     * there were.
     *
     * <p>The ages come from the vanilla block rather than from the definition. A
     * {@code block_state_property} condition naming a value the property cannot hold
     * is a load error, so a definition asking for more growth stages than the block
     * has is clamped and said so. {@code wheatOverride} asks for exactly vanilla
     * wheat's 0-7 and nothing is clamped.
     */
    private LootResult writeOverrideLootTable(BlockDef def) throws IOException {
        String targetId = XieOverrides.targetId(def.override().orElseThrow());
        Optional<Block> target = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(targetId));
        if (target.isEmpty()) {
            LOGGER.warn("Block {} overrides \"{}\", which resolves to {} and is not a registered "
                + "block", def.qualifiedName(), def.override().orElseThrow(), targetId);
            return new LootResult(false, 0);
        }
        if (def.drops().isEmpty()) {
            // Nothing to say. The definition may still have declared things that need
            // the block itself to be different, which registerBlock has already noted.
            return new LootResult(false, 0);
        }

        OptionalInt blockMaxAge = maxAgeOf(target.get());
        int declaredMaxAge = def.props().getInt("maxGrowth", def.impliedMaxMeta());
        if (blockMaxAge.isPresent() && declaredMaxAge > blockMaxAge.getAsInt()) {
            LOGGER.warn("Block {} declares {} growth stages but {} has {}; the drops written "
                    + "for the stages past the end are left out",
                def.qualifiedName(), declaredMaxAge + 1, targetId, blockMaxAge.getAsInt() + 1);
        }

        JsonArray pools = new JsonArray();
        int skipped = 0;

        for (DropTable.Drop drop : def.drops().drops()) {
            Optional<String> item = resolve(drop.item());
            if (item.isEmpty()) {
                LOGGER.debug("Block {} drops \"{}\", which is not registered; skipping that drop",
                    def.qualifiedName(), drop.item());
                skipped++;
                continue;
            }
            if (blockMaxAge.isEmpty() && !drop.isUnconditional()) {
                LOGGER.warn("Block {} drops \"{}\" per growth stage, but {} has no age property; "
                    + "skipping that drop", def.qualifiedName(), drop.item(), targetId);
                skipped++;
                continue;
            }
            int blockMax = blockMaxAge.orElse(0);
            addDropPools(pools, targetId, item.get(), drop, blockMax,
                Math.min(declaredMaxAge, blockMax));
        }

        if (pools.isEmpty()) {
            return new LootResult(false, skipped);
        }

        Identifier id = Identifier.parse(targetId);
        // Into the override pack rather than the content pack, because this file has
        // to beat the one it is replacing and the content pack sits deliberately
        // below vanilla. See XiePackProvider.
        //
        // The vanilla table's own random sequence, so the rolls are seeded exactly as
        // the table being replaced would have seeded them.
        PackJson.write(overrideRoot.resolve("data").resolve(id.getNamespace())
                .resolve("loot_table").resolve("blocks").resolve(id.getPath() + ".json"),
            blockLootRoot(id.getNamespace() + ":blocks/" + id.getPath(), pools));
        overrides++;

        LOGGER.info("Replacing the loot table of {} with the drops {} declares",
            targetId, def.qualifiedName());
        return new LootResult(true, skipped);
    }

    /**
     * The highest value a block's {@code age} property can hold, or empty when it has
     * no such property.
     */
    private static OptionalInt maxAgeOf(Block block) {
        Property<?> property = block.getStateDefinition().getProperty("age");
        if (!(property instanceof IntegerProperty age)) {
            return OptionalInt.empty();
        }
        return age.getPossibleValues().stream().mapToInt(Integer::intValue).max();
    }

    /**
     * Counts the drops in a {@code stick}'s {@code blocks} map that name nothing.
     *
     * <p>The one place this emitter writes no file and checks anyway. A stick's drops
     * are not a loot table — they are handed to {@link mod.xie.xm.item.XieStickItem},
     * which resolves them the first time somebody swings it — so without this they are
     * the only drops in the content that could go missing without being counted, and
     * they would go missing hours into a game rather than at load.
     *
     * <p>Asked of a {@code stick} and nothing else, which is where {@code JobItem} read
     * the {@code blocks} key — {@code doStick} and nowhere else. Checking every item
     * would have counted the drops of a {@code blocks} map no archetype consumes, and
     * would have marked the key read on a definition that is quietly losing it; the
     * unread-property report says so instead.
     *
     * @return how many drops named something unresolvable
     */
    private int checkStickDrops(ItemDef def) {
        if (!def.archetype().equals(STICK)) {
            return 0;
        }
        int skipped = 0;
        for (ItemDef.BlockDrops harvest : def.blockDrops()) {
            if (anyBlock(harvest.block()).isEmpty()) {
                LOGGER.debug("Item {} works on \"{}\", which is not a registered block; "
                    + "that entry does nothing", def.qualifiedName(), harvest.block());
                skipped++;
            }
            for (DropTable.Drop drop : harvest.drops().drops()) {
                if (resolve(drop.item()).isEmpty()) {
                    LOGGER.debug("Item {} drops \"{}\" from {}, and nothing answers to that name; "
                        + "skipping that drop",
                        def.qualifiedName(), drop.item(), harvest.block());
                    skipped++;
                }
            }
        }
        return skipped;
    }

    /** The wrapper every block loot table shares. */
    private static JsonObject blockLootRoot(String randomSequence, JsonArray pools) {
        JsonArray functions = new JsonArray();
        functions.add(namedFunction("minecraft:explosion_decay"));

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:block");
        root.addProperty("random_sequence", randomSequence);
        root.add("functions", functions);
        root.add("pools", pools);
        return root;
    }

    /**
     * The loot table for a block that drops itself: one item, always, its own.
     *
     * <p>{@code XieBlockSapling.dropBlockAsItemWithChance} is where this comes from —
     * "if no drops are declared, drop self" — and it is a sapling's rule rather than
     * a plant's, because a plant with nothing declared really did drop nothing.
     */
    private void writeSelfDropTable(String path) throws IOException {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", XiesMod.MOD_ID + ":" + path);

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", single(entry));

        PackJson.write(dataRoot.resolve("loot_table").resolve("blocks").resolve(path + ".json"),
            blockLootRoot(XiesMod.MOD_ID + ":blocks/" + path, single(pool)));
    }

    /**
     * Turns one {@code drops} entry into pools.
     *
     * <p>Legacy semantics, from {@code Droppables.getDropsForMetaAndMultiplier}:
     * {@code amount} is a number of <em>independent</em> rolls, each yielding a
     * single item at {@code chance} percent — not a stack size. That maps exactly
     * onto a pool with {@code rolls} set to the amount and the chance as a
     * per-entry condition, because pool conditions are tested once but entry
     * conditions are tested on every roll.
     *
     * <p>The chance map is per age, so contiguous ages sharing a chance are
     * collapsed into one pool with a ranged {@code age} predicate. Ages that can
     * never drop the item produce no pool at all.
     *
     * <p>{@code lastDeclared} is the last stage the definition knows of, which is
     * short of {@code maxAge} only for an override of a block with more stages than
     * it declared. Every stage past it drops as that last one does, so the ripe
     * vanilla crop still pays out.
     */
    private void addDropPools(JsonArray pools, String blockId, String itemId,
                              DropTable.Drop drop, int maxAge, int lastDeclared) {
        if (drop.isUnconditional()) {
            addPools(pools, drop, null, itemId, 100);
            return;
        }

        int age = 0;
        while (age <= maxAge) {
            int chance = clampPercent(drop.chanceFor(Math.min(age, lastDeclared)));
            int last = age;
            while (last < maxAge
                    && clampPercent(drop.chanceFor(Math.min(last + 1, lastDeclared))) == chance) {
                last++;
            }
            addPools(pools, drop, ageCondition(blockId, age, last, maxAge), itemId, chance);
            age = last + 1;
        }
    }

    /**
     * One drop where its chance is {@code chance}: its {@code min} in a pool that always
     * pays, and the rest of its {@code amount} rolled at the chance. Nothing at all
     * where the chance is zero, {@code min} included, which is how a drop keyed by
     * growth stage stays out of the stages it was not written for.
     */
    private void addPools(JsonArray pools, DropTable.Drop drop, JsonObject ageCondition,
                          String itemId, int chance) {
        if (chance <= 0) {
            return;
        }
        int rolled = drop.amount() - drop.min();
        if (drop.min() > 0) {
            pools.add(dropPool(drop.min(), ageCondition, itemId, 100));
        }
        if (rolled > 0) {
            pools.add(dropPool(rolled, ageCondition, itemId, chance));
        }
    }

    private JsonObject dropPool(int rolls, JsonObject ageCondition, String itemId, int chance) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", itemId);
        if (chance < 100) {
            JsonObject randomChance = new JsonObject();
            randomChance.addProperty("condition", "minecraft:random_chance");
            randomChance.addProperty("chance", chance / 100.0f);
            entry.add("conditions", single(randomChance));
        }

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", rolls);
        if (ageCondition != null) {
            pool.add("conditions", single(ageCondition));
        }
        pool.add("entries", single(entry));
        return pool;
    }

    /**
     * A {@code block_state_property} predicate over an age range, or null when the
     * range covers every age and the condition would be dead weight.
     */
    private JsonObject ageCondition(String blockId, int from, int to, int maxAge) {
        if (from <= 0 && to >= maxAge) {
            return null;
        }
        JsonObject properties = new JsonObject();
        if (from == to) {
            properties.addProperty("age", String.valueOf(from));
        } else {
            // The ranged form takes strings and parses them through the property,
            // so an IntegerProperty compares numerically rather than lexically.
            JsonObject range = new JsonObject();
            range.addProperty("min", String.valueOf(from));
            range.addProperty("max", String.valueOf(to));
            properties.add("age", range);
        }

        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:block_state_property");
        condition.addProperty("block", blockId);
        condition.add("properties", properties);
        return condition;
    }

    private static int clampPercent(int percent) {
        return Math.clamp(percent, 0, 100);
    }

    // ------------------------------------------------------------ alias tags

    /**
     * Writes one item tag per alias, merged across packs so two packs contributing
     * to {@code $protein} share a tag rather than overwriting each other.
     *
     * <p>Members that are not registered are left out rather than emitted as
     * optional entries: an alias over six items where four are unported should
     * still craft with the two that exist.
     */
    private int writeAliasTags(List<ContentPack> packs) throws IOException {
        Map<String, Set<String>> members = new TreeMap<>();

        for (ContentPack pack : packs) {
            for (AliasDef alias : pack.aliases().values()) {
                Set<String> ids = members.computeIfAbsent(alias.name(), name -> new LinkedHashSet<>());
                for (ItemRef member : alias.members()) {
                    resolve(member).ifPresentOrElse(ids::add, () ->
                        LOGGER.debug("Alias ${} names \"{}\", which is not registered; leaving it out",
                            alias.name(), member));
                }
            }
        }

        int written = 0;
        for (Map.Entry<String, Set<String>> entry : members.entrySet()) {
            String path = ResourceNames.toPath(entry.getKey());
            if (entry.getValue().isEmpty()) {
                LOGGER.debug("Alias ${} has no registered members; recipes using it will be skipped",
                    entry.getKey());
                continue;
            }
            JsonArray values = new JsonArray();
            entry.getValue().forEach(values::add);

            JsonObject root = new JsonObject();
            root.add("values", values);
            PackJson.write(dataRoot.resolve("tags").resolve("item").resolve(path + ".json"), root);

            aliasTags.put(entry.getKey(), "#" + XiesMod.MOD_ID + ":" + path);
            written++;
        }
        return written;
    }

    // ------------------------------------------------------------- tool tags

    /**
     * Writes the generated block tags.
     *
     * <p>Four things need one, and all four for the same reason — the thing that
     * reads them takes a tag rather than a list of ids. A tool's
     * {@code blocksEffective}, {@code canHarvest}, {@code cannotHarvest} and
     * {@code canCut} lists become the rules of a {@code minecraft:tool} component; a
     * stem's soil becomes {@code StemBlock}'s support tag; a {@code log} joins
     * {@code #minecraft:logs}, which is what earns it an axe and keeps a fruit tree's
     * leaves from decaying off it; and a {@code stove} joins
     * {@code #minecraft:mineable/pickaxe}, without which it requires a tool nothing
     * counts as. Vanilla's own tools point at those tags the same way; the
     * difference is only that these are generated rather than shipped.
     *
     * <p>The vanilla tags are the ones not in this mod's namespace, and they are
     * <em>additive</em> — tags from every pack are merged unless one says
     * {@code replace} — so it belongs in the content pack rather than the override
     * pack, and being below vanilla costs it nothing.
     *
     * <p>Names nothing answers to are left out rather than written as optional
     * entries, because a tag entry naming a missing block is a load error and a
     * silently optional one is indistinguishable from a typo. The registrar has
     * already resolved each name to the id it would have; this is where it is
     * checked against what exists.
     */
    private int writeBlockTags(RegisteredContent registered) throws IOException {
        int written = 0;
        for (Map.Entry<String, List<String>> entry : registered.blockTags().entrySet()) {
            Identifier tag = Identifier.parse(entry.getKey());
            JsonArray values = new JsonArray();
            for (String id : entry.getValue()) {
                if (BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).isPresent()) {
                    values.add(id);
                } else {
                    LOGGER.debug("Block tag {} names \"{}\", which is not a registered block; "
                        + "leaving it out", tag, id);
                }
            }
            if (values.isEmpty()) {
                LOGGER.info("Block tag {} has no registered blocks; whatever reads it matches "
                    + "nothing", tag);
            }
            JsonObject root = new JsonObject();
            root.add("values", values);
            PackJson.write(outputRoot.resolve("data").resolve(tag.getNamespace())
                .resolve("tags").resolve("block").resolve(tag.getPath() + ".json"), root);
            written++;
        }
        return written;
    }

    // --------------------------------------------------------------- recipes

    /** @return how many recipe files this definition produced; 0 means it was skipped */
    private int writeRecipe(RecipeDef recipe) throws IOException {
        return switch (recipe.kind()) {
            case SHAPED -> writeShaped(recipe) ? 1 : 0;
            case SHAPELESS -> writeShapeless(recipe) ? 1 : 0;
            case SMELTING -> writeSmelting(recipe);
            // A cull writes no file: see collectCulls.
            case CULL -> 0;
        };
    }

    /**
     * Resolves what a {@code cull} names, for {@link mod.xie.xm.recipe.XieRecipeCuller}
     * to remove once the recipes are loaded.
     *
     * <p><strong>The stack count is deliberately ignored.</strong> A cull names an
     * output <em>stack</em>, and {@code Xie.itemstacksEqual} compared the count along
     * with the item — so {@code "cull": "redDye:2"} meant 1.6's poppy-to-two-red-dye
     * recipe, which was then the only one there was. Vanilla gives one dye per flower
     * now and has grown three more red dye recipes since, one of which — the rose
     * bush, a two-block flower — yields exactly two. Reproducing the count check
     * therefore does something worse than nothing: it culls the rose bush and leaves
     * the poppy recipe the content was written to remove. The count described the
     * recipe of the day rather than selecting among several, which is plain in
     * {@code cullStoneTools.json} where all five culls give no count at all, so what
     * carries over is the rule without it — every recipe producing this item.
     *
     * @return whether anything resolved; false means the cull has nothing to act on
     */
    private boolean collectCulls(RecipeDef recipe) {
        boolean resolved = false;
        for (ItemRef target : recipe.culls()) {
            Optional<String> id = resolve(target);
            if (id.isEmpty()) {
                LOGGER.debug("Cull {} names \"{}\", which is not a registered item; "
                    + "there is nothing to remove", recipe.origin(), target);
                continue;
            }
            culledItems.add(id.get());
            resolved = true;
        }
        return resolved;
    }

    private boolean writeShaped(RecipeDef recipe) throws IOException {
        Optional<JsonObject> result = result(recipe);
        if (result.isEmpty()) {
            return false;
        }

        List<String> rows = recipe.pattern();
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        if (rows.isEmpty() || rows.size() > MAX_GRID || width > MAX_GRID) {
            LOGGER.debug("Skipping recipe {}: pattern is {}x{}, larger than the {}x{} grid",
                recipe.origin(), width, rows.size(), MAX_GRID, MAX_GRID);
            return false;
        }

        Map<Character, ItemRef> symbols = recipe.symbolMap();
        JsonObject key = new JsonObject();
        List<String> ingredients = new ArrayList<>();
        for (Map.Entry<Character, ItemRef> bound : symbols.entrySet()) {
            Optional<String> ingredient = ingredient(bound.getValue());
            if (ingredient.isEmpty()) {
                LOGGER.debug("Skipping recipe {}: ingredient \"{}\" is not available",
                    recipe.origin(), bound.getValue());
                return false;
            }
            key.add(String.valueOf(bound.getKey()), ingredientJson(bound.getValue(), ingredient.get()));
            ingredients.add(ingredient.get());
        }

        JsonArray pattern = new JsonArray();
        for (String row : rows) {
            String padded = row + " ".repeat(width - row.length());
            for (int i = 0; i < padded.length(); i++) {
                char symbol = padded.charAt(i);
                if (symbol != ' ' && !symbols.containsKey(symbol)) {
                    // symbolMap binds positionally and gives up when it runs out of
                    // ingredients; the parser already warned, so just drop it.
                    LOGGER.debug("Skipping recipe {}: pattern symbol '{}' has no ingredient",
                        recipe.origin(), symbol);
                    return false;
                }
            }
            pattern.add(padded);
        }

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shaped");
        root.add("key", key);
        root.add("pattern", pattern);
        root.add("result", result.get());
        return write(recipe, "", root, ingredients);
    }

    private boolean writeShapeless(RecipeDef recipe) throws IOException {
        Optional<JsonObject> result = result(recipe);
        if (result.isEmpty()) {
            return false;
        }
        if (recipe.ingredients().isEmpty() || recipe.ingredients().size() > MAX_GRID * MAX_GRID) {
            LOGGER.debug("Skipping recipe {}: {} ingredients does not fit the grid",
                recipe.origin(), recipe.ingredients().size());
            return false;
        }

        // Ingredient stack counts are ignored: one reference is one grid slot, which
        // is what the 1.6 shapeless matcher did too. Content that wants two of
        // something writes it twice.
        List<String> ingredients = new ArrayList<>();
        JsonArray grid = new JsonArray();
        for (ItemRef ref : recipe.ingredients()) {
            Optional<String> ingredient = ingredient(ref);
            if (ingredient.isEmpty()) {
                LOGGER.debug("Skipping recipe {}: ingredient \"{}\" is not available",
                    recipe.origin(), ref);
                return false;
            }
            ingredients.add(ingredient.get());
            grid.add(ingredientJson(ref, ingredient.get()));
        }

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shapeless");
        root.add("ingredients", grid);
        root.add("result", result.get());
        return write(recipe, "", root, ingredients);
    }

    /**
     * {@code "smelt"} may name several inputs, which the original turned into one
     * furnace recipe each. There is no "any of these smelts to that" recipe type,
     * so that is still one file per input.
     */
    private int writeSmelting(RecipeDef recipe) throws IOException {
        Optional<JsonObject> result = result(recipe);
        if (result.isEmpty()) {
            return 0;
        }

        int written = 0;
        List<ItemRef> inputs = recipe.ingredients();
        for (int i = 0; i < inputs.size(); i++) {
            Optional<String> ingredient = ingredient(inputs.get(i));
            if (ingredient.isEmpty()) {
                LOGGER.debug("Skipping smelting of \"{}\" in {}: it is not available",
                    inputs.get(i), recipe.origin());
                continue;
            }
            JsonObject root = new JsonObject();
            root.addProperty("type", "minecraft:smelting");
            root.addProperty("category", cookingCategory(result.get().get("id").getAsString()));
            root.add("ingredient", ingredientJson(inputs.get(i), ingredient.get()));
            root.addProperty("experience", SMELT_EXPERIENCE);
            root.addProperty("cookingtime", SMELT_TIME);
            // Deep-copy the result: several files may be written from one definition.
            root.add("result", result.get().deepCopy());

            String suffix = inputs.size() > 1 ? "_from_" + i : "";
            if (write(recipe, suffix, root, List.of(ingredient.get()))) {
                written++;
            }
        }
        return written;
    }

    // --------------------------------------------------------------- cooking

    /**
     * Recipes from {@code cooked}, which is a recipe written on the ingredient.
     *
     * <p>{@code JobItem.parseSmeltingRecipe} read it — and {@code smelted}, the other
     * spelling — and called {@code addSmeltingRecipe} with the item's own id, so a
     * definition saying {@code "cooked": "popcorn"} is a furnace recipe from itself to
     * that. It applies to blocks as well as items, which is why this walks both, and a
     * block only has a recipe input if it has an item form.
     *
     * @return how many recipe files this pack's {@code cooked} properties produced
     */
    private int writeCookingRecipes(ContentPack pack) throws IOException {
        int written = 0;
        for (ItemDef def : pack.items().values()) {
            for (String path : registered.itemPaths().getOrDefault(def.name(), List.of())) {
                written += writeCooking(def.props(), def.qualifiedName(), path);
            }
        }
        for (BlockDef def : pack.blocks().values()) {
            for (String path : registered.blockPaths().getOrDefault(def.name(), List.of())) {
                if (registered.blockItems().contains(path)) {
                    written += writeCooking(def.props(), def.qualifiedName(), path);
                }
            }
        }
        return written;
    }

    /**
     * The recipes one {@code cooked} declaration becomes.
     *
     * <p>A furnace recipe always, and a smoker and campfire one when the result is
     * food — which every {@code cooked} in the content is, since cooking corn and
     * frying a tortilla are the only things it was ever used for. The test is on the
     * result rather than the ingredient because that is where the food is: corn
     * kernels are seeds, and popping them still belongs in a smoker.
     */
    private int writeCooking(PropertyMap props, String origin, String inputPath)
            throws IOException {
        Optional<String> cooked = props.getString("cooked").or(() -> props.getString("smelted"));
        if (cooked.isEmpty()) {
            return 0;
        }

        ItemRef ref = ItemRef.parse(cooked.get());
        Optional<String> resultId = resolve(ref);
        if (resultId.isEmpty()) {
            LOGGER.debug("Skipping the cooked recipe for {}: \"{}\" is not registered", origin, ref);
            return 0;
        }

        JsonObject result = new JsonObject();
        result.addProperty("id", resultId.get());
        if (ref.count() > 1) {
            result.addProperty("count", ref.count());
        }

        int written = 0;
        for (Cooker cooker : COOKERS) {
            if (cooker.foodOnly() && !foods.contains(resultId.get())) {
                continue;
            }
            JsonObject root = new JsonObject();
            root.addProperty("type", cooker.recipeType());
            root.addProperty("category", cookingCategory(resultId.get()));
            String ingredient = XiesMod.MOD_ID + ":" + inputPath;
            root.addProperty("ingredient", ingredient);
            root.addProperty("experience", SMELT_EXPERIENCE);
            root.addProperty("cookingtime", cooker.cookingTime());
            root.add("result", result.deepCopy());

            String name = cooker.recipeType().substring(cooker.recipeType().indexOf(':') + 1);
            writeRecipeFile(uniqueRecipeId(inputPath + "_" + name), root, List.of(ingredient));
            written++;
        }
        return written;
    }

    /**
     * The recipe-book tab a cooking recipe files under.
     *
     * <p>Vanilla's recipe builder takes this as an argument, and its data generator
     * gives food to a smoker, a campfire and a furnace recipe that cooks food, blocks
     * to a furnace recipe that makes a block (smooth stone, glass, terracotta) and misc
     * to the rest (ingots, charcoal, dye). This reads the same three cases off the
     * result. Food is the content's own, as for the smoker: item components are not
     * bound yet when this runs, so a vanilla food cannot be recognised. A block is a
     * definition that registered an item form, not any item that places a block, or
     * a {@code seeds} item would count as a building block.
     */
    private String cookingCategory(String resultId) {
        if (foods.contains(resultId)) {
            return "food";
        }
        Identifier id = Identifier.parse(resultId);
        boolean block = id.getNamespace().equals(XiesMod.MOD_ID)
            ? registered.blockItems().contains(id.getPath())
            : BuiltInRegistries.ITEM.getValue(id) instanceof BlockItem;
        return block ? "blocks" : "misc";
    }

    /**
     * Every registered id belonging to a definition declared {@code type: food}.
     *
     * <p>Only this mod's, because that is all a content definition can say it
     * about. A {@code cooked} naming a vanilla item gets the furnace recipe and
     * nothing else, which is the conservative half of the guess.
     */
    private Set<String> foodIds(List<ContentPack> packs, RegisteredContent registered) {
        Set<String> foods = new HashSet<>();
        for (ContentPack pack : packs) {
            for (ItemDef def : pack.items().values()) {
                if (!def.archetype().equals("food")) {
                    continue;
                }
                for (String path : registered.itemPaths().getOrDefault(def.name(), List.of())) {
                    foods.add(XiesMod.MOD_ID + ":" + path);
                }
            }
        }
        return foods;
    }

    // ------------------------------------------------------------------ fuel

    /**
     * {@code fuel} as NeoForge's furnace-fuel data map: one file, one entry per item.
     *
     * <p>Burn time has no data component — vanilla builds its own table from item tags
     * at {@code FuelValues.vanillaBurnTimes} — so this is the datapack-shaped answer,
     * and the only file this emitter writes outside its own namespace: the loader
     * keys the data map by the <em>directory</em> it is found in, so it has to be
     * {@code neoforge}'s.
     *
     * <p>The property comes two ways. {@code cornCob} and {@code oil} write a bare
     * number, and the saplings write a metadata table — {@code "fuel": {"0-4": 100}} —
     * which was a burn time per subtype of one 1.6 id. Those subtypes are separate
     * blocks now, so each takes the entry written against its own metadata value,
     * exactly as {@code XieRegistrar.treeConfig} narrows {@code fruit}.
     *
     * @return how many items were given a burn time
     */
    private int writeFuelValues(List<ContentPack> packs) throws IOException {
        JsonObject values = new JsonObject();
        int written = 0;

        for (ContentPack pack : packs) {
            for (ItemDef def : pack.items().values()) {
                written += addFuelValues(values, def.props(), def.qualifiedName(),
                    registered.itemPaths().getOrDefault(def.name(), List.of()), path -> true);
            }
            for (BlockDef def : pack.blocks().values()) {
                // A block burns as its item form, and a block without one — every Xie
                // block but the saplings — has nothing that could go in a furnace.
                written += addFuelValues(values, def.props(), def.qualifiedName(),
                    registered.blockPaths().getOrDefault(def.name(), List.of()),
                    registered.blockItems()::contains);
            }
        }

        if (written == 0) {
            return 0;
        }
        JsonObject root = new JsonObject();
        root.add("values", values);
        PackJson.write(outputRoot.resolve(FUEL_DATA_MAP), root);
        return written;
    }

    private int addFuelValues(JsonObject values, PropertyMap props, String origin,
                              List<String> paths, Predicate<String> burns) {
        if (!props.has("fuel")) {
            return 0;
        }
        for (String bad : props.badMetaKeys("fuel")) {
            LOGGER.warn("{} has an unparseable fuel range \"{}\"", origin, bad);
        }
        Optional<MetaMap<Integer>> fuel = props.getMetaMap("fuel",
            value -> PropertyMap.asInt(value).orElse(null));
        if (fuel.isEmpty()) {
            LOGGER.warn("{} declares a fuel value that is not a number", origin);
            return 0;
        }

        int written = 0;
        for (int meta = 0; meta < paths.size(); meta++) {
            String path = paths.get(meta);
            if (!burns.test(path)) {
                LOGGER.debug("{} declares a burn time but {} has no item form to burn", origin, path);
                continue;
            }
            Optional<Integer> burnTime = fuel.get().get(meta).filter(time -> time > 0);
            if (burnTime.isEmpty()) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("burn_time", burnTime.get());
            values.add(XiesMod.MOD_ID + ":" + path, entry);
            written++;
        }
        return written;
    }

    /** The {@code result} object, or empty when the product is not registered. */
    private Optional<JsonObject> result(RecipeDef recipe) {
        Optional<ItemRef> product = recipe.product();
        if (product.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> id = resolve(product.get());
        if (id.isEmpty()) {
            LOGGER.debug("Skipping recipe {}: product \"{}\" is not registered",
                recipe.origin(), product.get());
            return Optional.empty();
        }
        JsonObject result = new JsonObject();
        result.addProperty("id", id.get());
        if (product.get().count() > 1) {
            result.addProperty("count", product.get().count());
        }
        return Optional.of(result);
    }

    private boolean write(RecipeDef recipe, String suffix, JsonObject root,
                          List<String> ingredients) throws IOException {
        writeRecipeFile(uniqueRecipeId(ResourceNames.toPath(recipe.name()) + suffix), root,
            ingredients);
        return true;
    }

    /**
     * Writes one recipe and the advancement that puts it in the recipe book.
     *
     * <p>Vanilla's recipe book shows only the recipes a player has unlocked, and
     * every vanilla recipe is unlocked by an advancement under {@code recipes/} that
     * fires when the player first holds an ingredient. This writes the same file,
     * with a criterion for every distinct ingredient rather than one hand-picked key
     * ingredient, since the content names no key: any of them unlocks it, and an
     * alias is tested by its tag exactly as vanilla tests {@code #minecraft:eggs} for
     * a cake.
     *
     * <p>It also sets the recipe's {@code group} to the product's id path, which is
     * how vanilla folds its several red dye recipes into one book entry. Without it
     * the alias variants of one taco would be a dozen entries, and a product shared
     * with vanilla joins vanilla's own group.
     */
    private void writeRecipeFile(String id, JsonObject root, List<String> ingredients)
            throws IOException {
        String product = root.getAsJsonObject("result").get("id").getAsString();
        root.addProperty("group", product.substring(product.indexOf(':') + 1));
        PackJson.write(dataRoot.resolve("recipe").resolve(id + ".json"), root);

        String recipeId = XiesMod.MOD_ID + ":" + id;
        JsonObject criteria = new JsonObject();
        JsonArray anyOf = new JsonArray();

        JsonObject unlocked = new JsonObject();
        unlocked.addProperty("recipe", recipeId);
        criteria.add("has_the_recipe", criterion("minecraft:recipe_unlocked", unlocked));
        anyOf.add("has_the_recipe");

        for (String ingredient : new LinkedHashSet<>(ingredients)) {
            String name = "has_" + ingredient.substring(ingredient.indexOf(':') + 1);
            if (criteria.has(name)) {
                // The same path in two namespaces: keep both, told apart in full.
                name = "has_" + ingredient.replace("#", "").replace(':', '_');
            }
            JsonObject item = new JsonObject();
            item.addProperty("items", ingredient);
            JsonArray items = new JsonArray();
            items.add(item);
            JsonObject held = new JsonObject();
            held.add("items", items);
            criteria.add(name, criterion("minecraft:inventory_changed", held));
            anyOf.add(name);
        }

        JsonArray requirements = new JsonArray();
        requirements.add(anyOf);
        JsonArray recipes = new JsonArray();
        recipes.add(recipeId);
        JsonObject rewards = new JsonObject();
        rewards.add("recipes", recipes);

        JsonObject advancement = new JsonObject();
        advancement.addProperty("parent", "minecraft:recipes/root");
        advancement.add("criteria", criteria);
        advancement.add("requirements", requirements);
        advancement.add("rewards", rewards);
        PackJson.write(dataRoot.resolve("advancement").resolve("recipes").resolve(id + ".json"),
            advancement);
        advancements++;
    }

    private static JsonObject criterion(String trigger, JsonObject conditions) {
        JsonObject criterion = new JsonObject();
        criterion.add("conditions", conditions);
        criterion.addProperty("trigger", trigger);
        return criterion;
    }

    /**
     * Recipe names are unique within a file, not within a pack — two files may both
     * declare {@code tomatoSeeds}. The first one keeps the plain id.
     */
    private String uniqueRecipeId(String base) {
        if (usedRecipeIds.add(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String candidate = base + "_" + n;
            if (usedRecipeIds.add(candidate)) {
                return candidate;
            }
        }
    }

    // ------------------------------------------------------------ resolution

    /** An ingredient reference: a concrete item, or an alias standing for a tag. */
    private Optional<String> ingredient(ItemRef ref) {
        return switch (ref.kind()) {
            case ITEM -> resolve(ref);
            case ALIAS -> Optional.ofNullable(aliasTags.get(ref.name()));
            // "#food" and friends were supplied by the engine itself; there is
            // nothing to map them onto until those groups are ported.
            case BUILTIN_GROUP -> Optional.empty();
        };
    }

    /**
     * An ingredient as recipe JSON: its id, or a NeoForge components ingredient when
     * the id alone matches more than the reference means.
     *
     * <p>A legacy water bottle resolves to {@code minecraft:potion}, which as a bare id
     * accepts every potion, so it is written with water in its {@code potion_contents}.
     * The unlock advancement still tests the bare id: holding any potion shows the
     * recipe in the book, and only a water bottle crafts it.
     */
    private static JsonElement ingredientJson(ItemRef ref, String id) {
        Optional<String> potion = id.equals("minecraft:potion")
            ? ResourceNames.legacyPotion(ref)
            : Optional.empty();
        if (potion.isEmpty()) {
            return new JsonPrimitive(id);
        }
        JsonObject contents = new JsonObject();
        contents.addProperty("potion", potion.get());
        JsonObject components = new JsonObject();
        components.add("minecraft:potion_contents", contents);
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("neoforge:ingredient_type", "neoforge:components");
        ingredient.addProperty("items", id);
        ingredient.add("components", components);
        return ingredient;
    }

    /**
     * Resolves one reference to a namespaced item id, or empty when nothing by that
     * name exists.
     *
     * <p>A name a pack declares resolves into this mod. When that name is not in
     * the registry it must <em>not</em> quietly become the vanilla object of the
     * same name: a pack declaring its own {@code campfire} that this build cannot
     * register yet would otherwise have its recipe silently rewritten into one that
     * crafts Mojang's campfire out of four sticks. An unported definition is
     * unresolvable, and the recipe waits for the archetype.
     *
     * <p>A definition that declares {@code override} is the exception, and it
     * resolves to the object it overrides rather than to itself. It registers nothing
     * — speaking for a vanilla object is precisely what it asked for — so the vanilla
     * object is what its name means. {@code wheatOverride} is {@code minecraft:wheat},
     * which is not the id its own name would derive.
     */
    private Optional<String> resolve(ItemRef ref) {
        if (ref.isGroup()) {
            return Optional.empty();
        }
        ItemRef target = ref;

        if (declaredNames.contains(ref.name())) {
            Optional<String> path = subtypePath(ref);
            if (path.isEmpty()) {
                return Optional.empty();
            }
            String id = XiesMod.MOD_ID + ":" + path.get();
            if (lookup.exists(id)) {
                return Optional.of(id);
            }
            ItemRef overridden = overrideTargets.get(ref.name());
            if (overridden == null) {
                return Optional.empty();
            }
            target = overridden;
        }

        // Not a declared name, but possibly the name of a subtype: see subtypePaths.
        String derived = ResourceNames.toPath(target.name());
        if (subtypePaths.contains(derived)) {
            String id = XiesMod.MOD_ID + ":" + derived;
            if (lookup.exists(id)) {
                return Optional.of(id);
            }
        }

        String vanilla = "minecraft:" + ResourceNames.toVanillaPath(target);
        return lookup.exists(vanilla) ? Optional.of(vanilla) : Optional.empty();
    }

    /**
     * Resolves a reference to a block <em>this mod</em> registered, or empty.
     *
     * <p>This is deliberately narrower than {@link #resolve}: it never falls through
     * to the vanilla object of the same name. Worldgen uses it for the block an entry
     * spawns, where falling through would mean adding vanilla features back into
     * biomes under this mod's name — which is not the same thing the content asked
     * for, and in the one case where live content names a vanilla block
     * ({@code melon}) is content that was dropped precisely because vanilla now
     * supplies it.
     */
    private Optional<String> modBlock(ItemRef ref) {
        if (ref.isGroup()) {
            return Optional.empty();
        }
        // A name that is not a definition's may still be an alias for one, which
        // Xie.parseBlock checked before anything else. Only one level: an alias of
        // an alias is not something the format ever did, and resolving it blindly
        // would loop on a self-reference.
        ItemRef target = declaredNames.contains(ref.name())
            ? ref
            : aliasTargets.getOrDefault(ref.name(), ref);

        Optional<String> path = Optional.empty();
        if (declaredNames.contains(target.name())) {
            path = subtypePath(target);
        } else {
            // Possibly the name of a subtype rather than of a definition: an alias a
            // pack declares for one reduces to the same path its display name does.
            String derived = ResourceNames.toPath(target.name());
            if (subtypePaths.contains(derived)) {
                path = Optional.of(derived);
            }
        }
        return path.map(id -> XiesMod.MOD_ID + ":" + id).filter(blockLookup::exists);
    }

    /**
     * Resolves a reference to any registered block, ours or vanilla.
     *
     * <p>Worldgen uses this for the blocks a placement rule names, which are almost
     * always vanilla terrain — and, uniquely in the content, are sometimes written as
     * bare 1.6 block ids, which is what {@link LegacyBlockIds} is for.
     */
    private Optional<String> anyBlock(ItemRef ref) {
        if (ref.isGroup()) {
            return Optional.empty();
        }
        Optional<String> mine = modBlock(ref);
        if (mine.isPresent()) {
            return mine;
        }
        String path = LegacyBlockIds.path(ref.name()).orElseGet(() -> ResourceNames.toVanillaPath(ref));
        String id = "minecraft:" + path;
        return blockLookup.exists(id) ? Optional.of(id) : Optional.empty();
    }

    /**
     * The id path a declared name resolves to, picking the subtype the reference's
     * metadata value names.
     *
     * <p>{@code fruitSeed:1:2} is the lemon one. A definition with no subtypes
     * ignores metadata, as it did in 1.6, where a damage value on a single-subtype
     * item was just a scratched item.
     */
    private Optional<String> subtypePath(ItemRef ref) {
        int subtypes = registered.subtypeCount(ref.name());
        if (subtypes == 0) {
            // Not registered; the caller reports it as unresolvable.
            return Optional.of(ResourceNames.toPath(ref.name()));
        }
        if (subtypes == 1) {
            return registered.pathFor(ref.name(), 0);
        }
        if (!ref.hasMeta()) {
            // Legacy parsing defaulted a missing damage value to zero, and content
            // that meant a later subtype but wrote "shrub:1" got subtype zero in
            // 2013 too. Faithful, but worth being able to find.
            LOGGER.debug("Reference \"{}\" names one of {} subtypes but gives no metadata value; "
                + "taking the first, as the original did", ref, subtypes);
            return registered.pathFor(ref.name(), 0);
        }
        Optional<String> path = registered.pathFor(ref.name(), ref.meta());
        if (path.isEmpty()) {
            LOGGER.debug("Reference \"{}\" names subtype {}, but only {} were declared",
                ref, ref.meta(), subtypes);
        }
        return path;
    }

    /** Records the subtype id paths of the named split definitions. */
    private void collectSubtypePaths(List<String> splitNames, Map<String, List<String>> paths) {
        for (String name : splitNames) {
            List<String> registeredPaths = paths.get(name);
            if (registeredPaths != null) {
                subtypePaths.addAll(registeredPaths);
            }
        }
    }

    // ---------------------------------------------------------------- plumbing

    private static JsonObject namedFunction(String function) {
        JsonObject json = new JsonObject();
        json.addProperty("function", function);
        return json;
    }

    private static JsonArray single(JsonObject element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }
}
