package mod.xie.xm.reg;

import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.block.XieBasicBlock;
import mod.xie.xm.block.XieBushBlock;
import mod.xie.xm.block.XieCropBlock;
import mod.xie.xm.block.XieLogBlock;
import mod.xie.xm.block.XiePlantBlock;
import mod.xie.xm.block.XieSaplingBlock;
import mod.xie.xm.block.XieTreeBlock;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.Bounds;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.DropTable;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.LegacyLogs;
import mod.xie.xm.content.model.MetaMap;
import mod.xie.xm.content.model.PlantTypes;
import mod.xie.xm.content.model.PotionEffectDef;
import mod.xie.xm.content.model.PropertyMap;
import mod.xie.xm.content.model.ResourceNames;
import mod.xie.xm.content.model.TreeShape;
import mod.xie.xm.content.model.Variants;
import mod.xie.xm.item.XieBowItem;
import mod.xie.xm.item.XieStickItem;
import mod.xie.xm.item.XieToolItem;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.grower.TreeGrower;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns parsed content into registry entries.
 *
 * <p>Everything happens during mod construction. Block and item registries are
 * frozen once loading completes, so a definition not queued here is one that can
 * never exist — which is why the parser has to run this early too.
 *
 * <p>Archetypes are dispatched by name and unknown ones are skipped with a warning
 * rather than failing the load, so a pack written against a future version of the
 * engine degrades instead of crashing the game.
 *
 * <p>One definition does not always mean one registered object. A definition that
 * declares {@code names} described a single 1.6 id with a subtype per damage value;
 * damage-value subtypes are gone, so it expands into one registration per subtype
 * here. See {@link Variants}.
 */
public final class XieRegistrar {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Block archetypes this build can register. Grows as archetypes are ported. */
    private static final Set<String> SUPPORTED_BLOCKS =
        Set.of("bush", "plant", "crop", "basic", "sapling", "tree", "log", "stem");

    /** Of those, the ones that grow and so carry an {@code age} blockstate property. */
    private static final Set<String> GROWING_BLOCKS = Set.of("bush", "plant", "crop", "sapling");

    /**
     * The four that need naming rather than just listing: a sapling has to be
     * registered after the tree it grows into, a tree is the one archetype that is
     * neither a plant nor a plain block, and a log and a stem are each vanilla's own
     * block with a definition's properties on it.
     */
    private static final String SAPLING = "sapling";
    private static final String TREE = "tree";
    private static final String LOG = "log";
    private static final String STEM = "stem";

    /**
     * Item archetypes this build can register.
     *
     * <p>{@code seed} and {@code seeds} are the same archetype and always were:
     * {@code JobItem.doWork} dispatched {@code subtype.equals("seeds") ||
     * subtype.equals("seed")} to one {@code doSeeds}. Only {@code cheese.xie} and
     * {@code zombie_pigmelon.xie} write the singular, which is why it went unnoticed.
     */
    private static final Set<String> SUPPORTED_ITEMS =
        Set.of("basic", "food", "seeds", "seed", "tool", "sword", "bow", "stick");

    /** The two that are almost entirely data components. See {@link XieToolProperties}. */
    private static final String TOOL = "tool";
    private static final String SWORD = "sword";

    /** The two item archetypes that are behaviour. See {@link XieBowItem}. */
    private static final String BOW = "bow";
    private static final String STICK = "stick";

    /**
     * The one property of a {@code tool} that is not a component either, and the
     * only thing that decides whether one gets {@link XieToolItem}.
     */
    private static final String TRANSFORMS = "transforms";

    /** {@code transforms}' wildcard key, which matched nothing. See {@link #toolConfig}. */
    private static final String EVERY_BLOCK = "all";

    private final DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(XiesMod.MOD_ID);
    private final DeferredRegister.Items items = DeferredRegister.createItems(XiesMod.MOD_ID);
    private final DeferredRegister<CreativeModeTab> tabs =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, XiesMod.MOD_ID);

    /** Id path to registered block, e.g. {@code tomato_bush} to the holder. */
    private final Map<String, DeferredBlock<Block>> registeredBlocks = new LinkedHashMap<>();
    private final Map<String, DeferredItem<Item>> registeredItems = new LinkedHashMap<>();

    /** Definition name to the id paths it registered, indexed by metadata value. */
    private final Map<String, List<String>> blockPaths = new LinkedHashMap<>();
    private final Map<String, List<String>> itemPaths = new LinkedHashMap<>();

    /** Display names to emit into the language file, keyed by translation key. */
    private final Map<String, String> translations = new TreeMap<>();

    /** Block id path to highest age, so the emitter knows how many variants to write. */
    private final Map<String, Integer> maxAges = new LinkedHashMap<>();

    /** Block id paths that also registered a {@link BlockItem}. */
    private final Set<String> blockItems = new LinkedHashSet<>();

    /** Leaves block id path to the tree it grows, for the worldgen emitter. */
    private final Map<String, RegisteredContent.Tree> trees = new LinkedHashMap<>();

    /**
     * Generated block tag id to the blocks it holds.
     *
     * <p>Three things need one. A tool's {@code blocksEffective}, {@code canHarvest},
     * {@code cannotHarvest} and {@code canCut} lists, because {@code minecraft:tool}
     * takes tags rather than lists of ids; a stem's {@code growsOn}, for the same
     * reason on {@code StemBlock}; and {@code #minecraft:logs}, which a {@code log}
     * joins rather than defines. Keyed by full id because of that last one — it is
     * the only generated tag that is not in this mod's namespace, and it is additive
     * rather than a replacement, so it belongs in the content pack and not the
     * override pack.
     */
    private final Map<String, List<String>> blockTags = new LinkedHashMap<>();

    /** The tag a {@code log} joins, which is the whole of what made it a log. */
    private static final String LOGS_TAG = "minecraft:logs";

    private final List<DeferredItem<Item>> tabContents = new ArrayList<>();

    /** Definitions that speak for a vanilla object instead of registering one. */
    private final XieOverrides overrides = new XieOverrides();

    public void register(IEventBus modEventBus, List<ContentPack> packs) {
        // Blocks first: a seed item needs the block it plants to already exist.
        // Within them, trees before saplings, for exactly the same reason — a
        // sapling needs the id of the leaves it grows into.
        for (ContentPack pack : packs) {
            pack.blocks().values().stream()
                .filter(def -> !def.archetype().equals(SAPLING))
                .forEach(def -> registerBlock(pack, def));
        }
        for (ContentPack pack : packs) {
            pack.blocks().values().stream()
                .filter(def -> def.archetype().equals(SAPLING))
                .forEach(def -> registerBlock(pack, def));
        }
        for (ContentPack pack : packs) {
            pack.items().values().forEach(def -> registerItem(pack, def));
        }

        registerCreativeTab();

        blocks.register(modEventBus);
        items.register(modEventBus);
        tabs.register(modEventBus);
        overrides.install(modEventBus);

        LOGGER.info("Xie registrar: queued {} blocks and {} items from {} block and {} item "
                + "definitions, and {} items overriding a vanilla one",
            registeredBlocks.size(), registeredItems.size(), blockPaths.size(), itemPaths.size(),
            overrides.itemOverrides().size());
    }

    // ---------------------------------------------------------------- blocks

    private void registerBlock(ContentPack pack, BlockDef def) {
        if (def.isOverride()) {
            // An override registers nothing of its own — it speaks for a vanilla
            // block that already exists. Everything expressible about one is a
            // datapack file, so the emitter has the whole of it. See XieOverrides.
            LOGGER.debug("Block {} overrides {}, so it registers nothing here",
                def.qualifiedName(), XieOverrides.targetId(def.override().orElseThrow()));
            return;
        }
        if (!SUPPORTED_BLOCKS.contains(def.archetype())) {
            LOGGER.debug("Skipping block {}: archetype \"{}\" not ported yet",
                def.qualifiedName(), def.archetype());
            return;
        }
        if (def.archetype().equals(STEM) && def.props().getString("grows").isEmpty()) {
            // JobBlock.doStem failed outright without it, and so does this: a stem is
            // defined by what it fruits into, and StemBlock takes that in its
            // constructor rather than looking it up later.
            LOGGER.warn("Block {} is type \"stem\" but declares no grows; it cannot be registered",
                def.qualifiedName());
            return;
        }

        List<String> names = Variants.namesIn(def.props());
        List<String> paths = claim(def.qualifiedName(), def.name(),
            Variants.pathsFor(def.name(), names), registeredBlocks.keySet());

        for (int meta = 0; meta < paths.size(); meta++) {
            registerBlockSubtype(pack, def, names, meta, paths.get(meta));
        }
        blockPaths.put(def.name(), paths);
    }

    private void registerBlockSubtype(ContentPack pack, BlockDef def,
                                      List<String> names, int meta, String path) {
        DeferredBlock<Block> holder;

        if (def.archetype().equals(TREE)) {
            XieTreeBlock.Config config = treeConfig(pack, def, meta);
            holder = blocks.registerBlock(
                path,
                properties -> new XieTreeBlock(properties, config),
                properties -> XieBlockProperties.forTree(def)
            );
            trees.put(path, new RegisteredContent.Tree(trunkFor(pack, def, meta), TreeShape.forBlock(def)));
        } else if (def.archetype().equals(LOG)) {
            XieLogBlock.Config config = new XieLogBlock.Config(
                def.props().getString("becomesWhenDestroyed").map(name -> resolveBlockId(pack, name)),
                boundsFor(def, !names.isEmpty(), meta).flatMap(MetaMap::first)
                    .filter(box -> !box.isFullBlock()),
                spikey(def));
            holder = blocks.registerBlock(
                path,
                properties -> new XieLogBlock(properties, config),
                properties -> XieBlockProperties.forLog(def)
            );
            // What "canSustainLeaves" and "isWood" both said, and what made every axe
            // effective against it — one tag rather than three overrides. It also
            // keeps a fruit tree standing: XieTreeBlock is a LeavesBlock, and leaves
            // measure their distance from #minecraft:logs.
            blockTags.computeIfAbsent(LOGS_TAG, tag -> new ArrayList<>())
                .add(XiesMod.MOD_ID + ":" + path);
        } else if (def.archetype().equals(STEM)) {
            holder = registerStem(pack, def, path);
            // A stem's ages are vanilla's 0-7, which is what 1.6's BlockStem had too.
            // Recorded so that a stem declaring drops gets its loot conditions right.
            maxAges.put(path, StemBlock.MAX_AGE);
        } else if (GROWING_BLOCKS.contains(def.archetype())) {
            XiePlantBlock.Config config = plantConfig(pack, def, names, meta);
            Optional<TreeGrower> grower = def.archetype().equals(SAPLING)
                ? treeGrower(def, names, meta, path)
                : Optional.empty();
            holder = blocks.registerBlock(
                path,
                properties -> switch (def.archetype()) {
                    case "bush" -> new XieBushBlock(properties, config);
                    case "crop" -> new XieCropBlock(properties, config);
                    case SAPLING -> new XieSaplingBlock(properties, config, grower);
                    default -> new XiePlantBlock(properties, config);
                },
                properties -> XieBlockProperties.forPlant(def)
            );
            // Only a growing block has an age property, and the emitters key their
            // blockstate variants and loot conditions off exactly that.
            maxAges.put(path, config.maxAge());
        } else {
            XieBasicBlock.Config config = basicConfig(pack, def, names, meta);
            holder = blocks.registerBlock(
                path,
                properties -> new XieBasicBlock(properties, config),
                properties -> XieBlockProperties.forBasic(def)
            );
        }

        registeredBlocks.put(path, holder);
        translations.put("block." + XiesMod.MOD_ID + "." + path,
            displayName(pack, def.name(), def.displayName(), names, meta));

        if (def.archetype().equals(SAPLING) || dropsItself(def)) {
            registerBlockItem(path, holder);
        }
    }

    /**
     * Whether a block's {@code drops} names the block itself.
     *
     * <p>The second reason a block gets an item form, and the narrowest one there is:
     * a definition saying it drops itself has asked for something that cannot exist
     * without an item, so leaving the item out turns the declaration into a drop
     * nobody could ever be handed. It is the melon's case — {@code zombiePigmelon} is
     * a block a stem fruits and a player breaks, which is neither of the two ways
     * every other Xie block is obtained.
     *
     * <p>In 2013 the question never arose: {@code ModLoader.registerBlock} gave every
     * block an {@code ItemBlock} whether anything needed one or not.
     */
    private static boolean dropsItself(BlockDef def) {
        return def.drops().drops().stream().anyMatch(drop -> drop.item().name().equals(def.name()));
    }

    /**
     * Gives a block an item form.
     *
     * <p>Two kinds of block get one, and no others. Every other Xie block is either
     * planted from a seed or placed by worldgen, so an item form would be one nobody
     * could obtain; a sapling is the opposite case on both counts, and a block that
     * {@linkplain #dropsItself drops itself} is the other. {@code cocoaLeaves} drops
     * {@code cocoaSapling} and a recipe crafts one out of an oak sapling and cocoa
     * beans, and neither is expressible without an item — which is exactly why
     * vanilla saplings are block items too.
     */
    private void registerBlockItem(String path, DeferredBlock<Block> block) {
        DeferredItem<Item> item = items.registerItem(path,
            properties -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
        registeredItems.put(path, item);
        blockItems.add(path);
        tabContents.add(item);
    }

    /**
     * A split block has spent its metadata on picking a subtype, so it has no growth
     * stages left and takes only the bounds declared for its own subtype. An unsplit
     * one keeps the whole per-age map.
     */
    private XiePlantBlock.Config plantConfig(ContentPack pack, BlockDef def, List<String> names, int meta) {
        boolean split = !names.isEmpty();
        int maxAge = def.props().getInt("maxGrowth", split ? 0 : def.impliedMaxMeta());
        int growthRate = def.props().getInt("growthRate", 100);
        int growthHeight = def.props().getInt("growthHeight", 1);

        // Both spellings, because JobBlock read both.
        boolean fertilisable = def.props().getBoolean("fertilisable",
            def.props().getBoolean("fertilizable", true));

        return new XiePlantBlock.Config(maxAge, growthRate, growthHeight, growsOn(pack, def),
            boundsFor(def, split, meta), fertilisable, spikey(def));
    }

    /**
     * A basic block has one state, so a per-metadata {@code blockBounds} map can only
     * have been describing subtypes — which are separate blocks now, each taking the
     * one box declared for it.
     */
    private XieBasicBlock.Config basicConfig(ContentPack pack, BlockDef def,
                                             List<String> names, int meta) {
        boolean split = !names.isEmpty();
        Optional<Bounds> shape = boundsFor(def, split, meta)
            .flatMap(MetaMap::first)
            .filter(box -> !box.isFullBlock());

        Optional<String> becomes = def.props().getString("becomesWhenDestroyed")
            .map(name -> resolveBlockId(pack, name));

        // A basic block you can walk through is vegetation rather than terrain, and
        // is the one that should come away with its soil. See XieBasicBlock.canSurvive.
        boolean needsSupport = def.props().getBoolean("walkthroughable", false);

        return new XieBasicBlock.Config(becomes, shape, needsSupport,
            needsSupport ? growsOn(pack, def) : Set.of(), spikey(def));
    }

    /**
     * {@code spikey}: one point of generic damage to anything standing in the block.
     *
     * <p>Asked of every archetype the port has a class for, because {@code setIsSpikey}
     * was on {@code IXieBlock} and every one of the original's block classes carried
     * its own copy of the same two-line override. An archetype with no class of its own
     * — {@code stem}, which is vanilla's {@code StemBlock} — cannot be handed it, and
     * so never reads the key: a definition declaring it there is reported by
     * {@link XieUnreadProperties}. See {@link mod.xie.xm.block.XieSpikes}.
     */
    private static boolean spikey(BlockDef def) {
        return def.props().getBoolean("spikey", false);
    }

    // ------------------------------------------------------------------ stems

    /**
     * A {@code stem} — and the attached stem that goes with it, which is the only
     * archetype that registers a block the content never named.
     *
     * <p><strong>{@code XieBlockStem} was 1.6's own {@code BlockStem}.</strong> Three
     * hundred lines, of which the class body proper is three methods:
     * {@code fertilizeStem} calls {@code super} and asks for a render update,
     * {@code getPlantType} returns {@code plantType}, and
     * {@code canThisPlantGrowOnThisBlockID} answers {@code growsOn}. Everything else
     * is the {@code // copy-pasta from XieBlock} boilerplate. That makes it the fifth
     * 2013 class this port has found to be vanilla's, and — unlike the other four —
     * it needs no subclass at all: {@link StemBlock} takes exactly the four things a
     * definition can vary, as constructor arguments.
     *
     * <p>The one that matters is <strong>{@code grows}</strong>. A stem fruiting into
     * a block of this mod's own is the thing no datapack can say, and it is also the
     * thing vanilla turns out to have solved already: the fruit is a
     * {@code ResourceKey} looked up from the registry at the moment the stem fruits,
     * so it may name a block that does not exist yet — the same lazy resolution
     * {@link XieTreeBlock} and {@link XieToolItem} had to write for themselves.
     *
     * <p><strong>The attached stem is the bend, and in 1.6 it was not a block.</strong>
     * {@code BlockStem} looked for its fruit among the four neighbours at render time
     * and drew itself leaning towards it; vanilla has spent a blockstate on that since
     * 1.8. So one definition becomes two registrations, which is the same split
     * {@code names} already makes for a different reason, and the second one takes the
     * first one's name with "Attached" in front of it — vanilla's own wording.
     */
    private DeferredBlock<Block> registerStem(ContentPack pack, BlockDef def, String path) {
        String attachedPath = RegisteredContent.attachedStemPath(path);
        ResourceKey<Block> stemKey = blockKey(path);
        ResourceKey<Block> attachedKey = blockKey(attachedPath);
        ResourceKey<Block> fruitKey = ResourceKey.create(Registries.BLOCK,
            Identifier.parse(resolveBlockId(pack, def.props().getString("grows").orElseThrow())));
        ResourceKey<Item> seedKey = seedFor(pack, def);
        TagKey<Block> soil = stemSupportTag(pack, def, path);

        DeferredBlock<Block> attached = blocks.registerBlock(
            attachedPath,
            properties -> new AttachedStemBlock(stemKey, fruitKey, seedKey, soil, properties),
            properties -> XieBlockProperties.forAttachedStem()
        );
        registeredBlocks.put(attachedPath, attached);
        translations.put("block." + XiesMod.MOD_ID + "." + attachedPath,
            "Attached " + pack.displayNameFor(def.name()).orElse(def.displayName()));

        // The same tag twice over. Vanilla keeps two — the stem stands on farmland and
        // the fruit lands on dirt — but 1.6 hardcoded the fruit's soil to tilled
        // earth, dirt and grass, which is the untilled form of the very ground the
        // stem was standing on. A definition that says its stem grows on soul sand is
        // therefore saying where its fruit sits too, and with anything else the
        // zombie pigmelon would ripen forever and never fruit at all.
        return blocks.registerBlock(
            path,
            properties -> new StemBlock(fruitKey, attachedKey, seedKey, soil, soil, properties),
            properties -> XieBlockProperties.forStem(def)
        );
    }

    /**
     * The blocks a stem may stand on, as a generated tag.
     *
     * <p>{@code growsOn} if the definition gives one; otherwise {@code plantType},
     * which is the terse way of writing the same thing and the only thing
     * {@code zombiePigmelonStem} says. Taking {@code growsOn} first is not a guess
     * about which of the two 1.6 consulted: a stem declaring neither would be
     * unplantable everywhere under {@code canThisPlantGrowOnThisBlockID}, which
     * answered {@code false} for an empty list, so {@code plantType} is plainly what
     * a definition uses to say where it grows. With neither, {@code
     * XieBlockStem.getPlantType} filled in {@code Crop} — vanilla's melon stem.
     */
    private TagKey<Block> stemSupportTag(ContentPack pack, BlockDef def, String path) {
        List<String> soils = new ArrayList<>(growsOn(pack, def));

        if (soils.isEmpty()) {
            String plantType = def.props().getString("plantType").orElse(PlantTypes.DEFAULT);
            Optional<List<String>> named = PlantTypes.soilsFor(plantType);
            if (named.isEmpty()) {
                LOGGER.warn("Block {} is a stem with plantType \"{}\", which names {}; "
                        + "it will grow on farmland, as a stem with neither property did",
                    def.qualifiedName(), plantType,
                    PlantTypes.isCondition(plantType)
                        ? "a test on the block below rather than a set of blocks"
                        : "nothing this build knows");
            }
            soils.addAll(named.orElseGet(() -> PlantTypes.soilsFor(PlantTypes.DEFAULT).orElseThrow()));
        }

        String tag = XiesMod.MOD_ID + ":" + STEM_SUPPORT_TAG_PREFIX + path;
        blockTags.put(tag, List.copyOf(soils));
        return TagKey.create(Registries.BLOCK, Identifier.parse(tag));
    }

    /** Grouped so a generated pack lists a stem's soil beside the stem it belongs to. */
    private static final String STEM_SUPPORT_TAG_PREFIX = "supports/";

    /**
     * The seed a stem is planted from, which vanilla asks for so that picking the
     * block in creative gives you something you can plant.
     *
     * <p>Nothing on the block says it — the link runs the other way, from a
     * {@code seeds} item's {@code growsInto} — so this is that search run backwards.
     * It is the only use {@code StemBlock} puts the key to, and an absent one falls
     * back to the block itself, so a stem nothing plants is not an error.
     */
    private ResourceKey<Item> seedFor(ContentPack pack, BlockDef def) {
        for (ItemDef item : pack.items().values()) {
            if (!SEED_ARCHETYPES.contains(item.archetype())) {
                continue;
            }
            if (item.props().getString("growsInto").map(String::trim)
                    .filter(def.name()::equals).isEmpty()) {
                continue;
            }
            List<String> paths = Variants.pathsFor(item.name(), Variants.namesIn(item.props()));
            return ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(XiesMod.MOD_ID, paths.get(0)));
        }
        LOGGER.debug("Block {} is a stem that nothing in its pack plants; picking it in creative "
            + "will give the block", def.qualifiedName());
        return ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(XiesMod.MOD_ID, ResourceNames.toPath(def.name())));
    }

    private static final Set<String> SEED_ARCHETYPES = Set.of("seeds", "seed");

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(XiesMod.MOD_ID, path));
    }

    // ------------------------------------------------------------------ trees

    /**
     * What a tree lets fall of its own accord, narrowed to one subtype.
     *
     * <p>{@code sheds} is keyed by metadata exactly as {@code drops} is, and for a
     * split definition metadata chose the subtype rather than a growth stage — so
     * the apple tree takes the 20% written against 0 and the orange tree the one
     * written against 1, and each is blind to the other's.
     */
    private XieTreeBlock.Config treeConfig(ContentPack pack, BlockDef def, int meta) {
        List<XieTreeBlock.Shed> sheds = new ArrayList<>();
        for (DropTable.Drop shed : def.sheds().drops()) {
            int chance = Math.clamp(shed.chanceFor(meta), 0, 100);
            if (chance > 0) {
                sheds.add(new XieTreeBlock.Shed(
                    resolveItemId(pack, shed.item()), chance, shed.amount()));
            }
        }
        return new XieTreeBlock.Config(sheds, def.props().getInt("shedSaturation", 0),
            spikey(def),
            boundsFor(def, false, meta).flatMap(MetaMap::first)
                .filter(box -> !box.isFullBlock()));
    }

    /**
     * The log a tree's trunk is built from, resolving {@code trunk} and
     * {@code trunkMeta} together.
     *
     * <p>They have to be read as a pair because in 1.6 they were one block: the log
     * id with a metadata value picking the wood. {@code fruitLeaves} names no trunk
     * at all and relies on {@code XieBlockLeaves} having defaulted to that block, so
     * an absent {@code trunk} means the log too. See {@link LegacyLogs}.
     */
    private String trunkFor(ContentPack pack, BlockDef def, int meta) {
        Optional<String> trunk = def.props().getString("trunk");
        if (trunk.isEmpty() || LegacyLogs.isLegacyLog(trunk.get())) {
            return "minecraft:" + LegacyLogs.pathOrOak(trunkMeta(def, meta));
        }
        return resolveBlockId(pack, trunk.get());
    }

    /**
     * The wood this subtype's trunk is made of, from {@code trunkMeta}.
     *
     * <p>One entry per subtype, and {@code XieBlockLeaves.getTrunkMeta} answered 0 —
     * oak — for a subtype past the end of the list rather than reusing the last, so
     * that is what a short list means here too.
     */
    private static int trunkMeta(BlockDef def, int meta) {
        List<String> declared = def.props().getStringList("trunkMeta");
        if (meta < 0 || meta >= declared.size()) {
            return 0;
        }
        try {
            return Integer.parseInt(declared.get(meta).trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("{} has an unparseable trunkMeta entry \"{}\"; using oak",
                def.qualifiedName(), declared.get(meta));
            return 0;
        }
    }

    /**
     * The tree a sapling grows, as vanilla's own saplings hold it: a {@link
     * TreeGrower} pointing at a configured feature by id.
     *
     * <p>{@code growsInto} names the leaves and {@code inheritMeta} says the apple
     * sapling grows the apple tree rather than whichever subtype came first, which
     * is the same pairing {@link #registerSeeds} resolves for a seed and the block
     * it plants.
     *
     * @return empty when the leaves are not a tree this build registered, which
     *         leaves a sapling that grows through its stages and then stops
     */
    private Optional<TreeGrower> treeGrower(BlockDef def, List<String> names, int meta, String path) {
        Optional<String> growsInto = def.props().getString("growsInto");
        if (growsInto.isEmpty()) {
            LOGGER.warn("Block {} is type \"sapling\" but declares no growsInto", def.qualifiedName());
            return Optional.empty();
        }

        boolean inheritMeta = def.props().getBoolean("inheritMeta", false);
        int targetMeta = inheritMeta && !names.isEmpty() ? meta : 0;

        List<String> targetPaths = blockPaths.get(growsInto.get());
        String leaves = targetPaths != null && targetMeta < targetPaths.size()
            ? targetPaths.get(targetMeta)
            : null;

        if (leaves == null || !trees.containsKey(leaves)) {
            LOGGER.warn("Sapling {} grows into \"{}\", which is not a registered tree; "
                + "it will grow through its stages and stop", def.qualifiedName(), growsInto.get());
            return Optional.empty();
        }

        // The name is the grower's identity in TreeGrower's own registry, so it has
        // to be unique across every sapling in every pack. The block's id is.
        return Optional.of(new TreeGrower(
            XiesMod.MOD_ID + ":" + path,
            Optional.empty(),
            Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE,
                Identifier.fromNamespaceAndPath(XiesMod.MOD_ID,
                    RegisteredContent.treeFeaturePath(leaves)))),
            Optional.empty()));
    }

    /** The blocks a definition's {@code growsOn} names, as namespaced ids. */
    private Set<String> growsOn(ContentPack pack, BlockDef def) {
        Set<String> growsOn = new LinkedHashSet<>();
        for (String name : def.props().getStringList("growsOn")) {
            growsOn.add(resolveBlockId(pack, name));
        }
        return growsOn;
    }

    /** The bounds a subtype takes: its own entry when split, the whole map otherwise. */
    private Optional<MetaMap<Bounds>> boundsFor(BlockDef def, boolean split, int meta) {
        return split
            ? def.bounds().flatMap(map -> map.get(meta)).map(MetaMap::uniform)
            : def.bounds();
    }

    /**
     * Resolves a {@code growsOn} entry to a namespaced id. Names declared by the
     * pack resolve into this mod; everything else is assumed vanilla and run
     * through the legacy rename table.
     */
    private String resolveBlockId(ContentPack pack, String name) {
        String bare = name.contains("@") ? name.substring(name.indexOf('@') + 1) : name;
        if (pack.blocks().containsKey(bare)) {
            return XiesMod.MOD_ID + ":" + ResourceNames.toPath(bare);
        }
        return "minecraft:" + ResourceNames.toVanillaPath(ItemRef.parse(name));
    }

    /**
     * Resolves a reference to the id of the item it names.
     *
     * <p>The narrow counterpart of the data emitter's resolution, and narrow on
     * purpose: this runs while the registries are still filling, so it can only
     * derive the id a name <em>would</em> have and not check that anything answers to
     * it. The block that holds the answer looks it up when it first needs it — see
     * {@link XieTreeBlock} — by which time the registries are frozen.
     *
     * <p>A name a pack declares reduces to an id in this mod, picking the subtype the
     * reference's metadata names; anything else is assumed vanilla. A block declared
     * by the pack answers too, because a tree may shed a sapling.
     */
    private String resolveItemId(ContentPack pack, ItemRef ref) {
        ItemDef item = pack.items().get(ref.name());
        if (item != null) {
            return XiesMod.MOD_ID + ":" + subtypePath(ref.name(), item.props(), ref);
        }
        BlockDef block = pack.blocks().get(ref.name());
        if (block != null) {
            return XiesMod.MOD_ID + ":" + subtypePath(ref.name(), block.props(), ref);
        }
        return "minecraft:" + ResourceNames.toVanillaPath(ref);
    }

    /** The id path of the subtype a reference names, or the definition's own. */
    private static String subtypePath(String name, PropertyMap props, ItemRef ref) {
        List<String> paths = Variants.pathsFor(name, Variants.namesIn(props));
        int meta = ref.hasMeta() ? ref.meta() : 0;
        return paths.get(meta >= 0 && meta < paths.size() ? meta : 0);
    }

    // ----------------------------------------------------------------- items

    private void registerItem(ContentPack pack, ItemDef def) {
        if (def.isOverride()) {
            overrideItem(pack, def);
            return;
        }
        if (!SUPPORTED_ITEMS.contains(def.archetype())) {
            LOGGER.debug("Skipping item {}: archetype \"{}\" not ported yet",
                def.qualifiedName(), def.archetype());
            return;
        }

        List<String> names = Variants.namesIn(def.props());
        List<String> paths = claim(def.qualifiedName(), def.name(),
            Variants.pathsFor(def.name(), names), registeredItems.keySet());

        List<String> registered = new ArrayList<>(paths.size());
        for (int meta = 0; meta < paths.size(); meta++) {
            String path = paths.get(meta);
            DeferredItem<Item> holder = switch (def.archetype()) {
                case "food" -> registerFood(pack, def, meta, path);
                case "seeds", "seed" -> registerSeeds(pack, def, names, meta, path);
                case TOOL, SWORD -> registerToolLike(pack, def, path);
                case BOW -> registerBow(pack, def, path);
                case STICK -> registerStick(pack, def, path);
                default -> items.registerItem(path, Item::new,
                    properties -> common(pack, def, properties));
            };
            if (holder == null) {
                continue;
            }
            registeredItems.put(path, holder);
            tabContents.add(holder);
            translations.put("item." + XiesMod.MOD_ID + "." + path,
                displayName(pack, def.name(), def.displayName(), names, meta));
            registered.add(path);
        }

        if (!registered.isEmpty()) {
            itemPaths.put(def.name(), List.copyOf(registered));
        }
    }

    /**
     * An item that speaks for a vanilla one rather than being registered.
     *
     * <p>Nothing is queued: the vanilla item already exists and stays the object it
     * is, and what the definition declares is patched onto its default components
     * later. The tags its mining rules point at are named here for the same reason
     * they are for a registered tool — the emitter has to be told what to write.
     *
     * <p>Only {@code tool} and {@code sword} are expressible this way, which is
     * every override the content declares. An archetype that is more than components
     * — a {@code bow}, or anything wanting a class of its own — cannot be patched
     * onto an item that already exists, so it is reported and left alone.
     *
     * <p>The same limit reaches inside a supported archetype exactly once.
     * {@code transforms} is the one {@code tool} property that is behaviour rather
     * than a component, and behaviour lives on {@link XieToolItem} — which an
     * override never registers. Neither survival pickaxe declares one, but a
     * third-party pack could, and it would otherwise go missing without a word.
     */
    private void overrideItem(ContentPack pack, ItemDef def) {
        ItemRef ref = def.override().orElseThrow();
        String targetId = XieOverrides.targetId(ref);

        Optional<Item> target = BuiltInRegistries.ITEM.getOptional(Identifier.parse(targetId));
        if (target.isEmpty()) {
            LOGGER.warn("Item {} overrides \"{}\", which resolves to {} and is not a registered item",
                def.qualifiedName(), ref, targetId);
            return;
        }
        if (!def.archetype().equals(TOOL) && !def.archetype().equals(SWORD)) {
            LOGGER.warn("Item {} overrides {} but is type \"{}\", which is more than data "
                    + "components can describe; the vanilla item is left as it is",
                def.qualifiedName(), targetId, def.archetype());
            return;
        }
        if (def.props().markUsed(TRANSFORMS).has(TRANSFORMS)) {
            LOGGER.warn("Item {} overrides {} and declares transforms, which needs an item class "
                    + "and so cannot be patched onto an item that already exists; everything "
                    + "else it declares is applied", def.qualifiedName(), targetId);
        }

        String targetPath = ResourceNames.toVanillaPath(ref);
        collectToolTags(pack, def, targetPath);
        overrides.addItem(def, target.get(), targetPath);
    }

    /**
     * Food, and the three components that describe how it is eaten.
     *
     * <p>{@code hunger} and {@code nourishment} are the food itself. The rest were
     * behaviour {@code XieItemFood} implemented by hand and vanilla now has words
     * for: {@code action} chooses the animation and sound, {@code potionEffect}
     * becomes an effect applied on consuming, and {@code container} — or a
     * {@code drops} naming one thing, which {@code JobItem} treated as the same
     * declaration — is the bowl or bottle left in your hand afterwards.
     */
    private DeferredItem<Item> registerFood(ContentPack pack, ItemDef def, int meta, String path) {
        FoodProperties food = new FoodProperties.Builder()
            .nutrition(def.hunger().orElse(1))
            .saturationModifier(def.nourishment().orElse(0.3f))
            .build();
        Consumable consumable = consumable(def, meta);

        return items.registerItem(path, Item::new, properties -> {
            Item.Properties built = common(pack, def, properties).food(food, consumable);
            useRemainder(pack, def).ifPresent(built::usingConvertsTo);
            return built;
        });
    }

    /**
     * How this food is consumed: the {@code drink} animation when {@code action} asks
     * for one, and any {@code potionEffect} it applies.
     *
     * <p>{@code XieItemFood.setAction} understood {@code drink} and treated everything
     * else, including the absent case, as eating.
     */
    private Consumable consumable(ItemDef def, int meta) {
        String action = def.props().getString("action", "eat").trim().toLowerCase(Locale.ROOT);
        Consumable.Builder builder = action.equals("drink")
            ? Consumables.defaultDrink()
            : Consumables.defaultFood();

        for (PotionEffectDef effect : effects(def, meta)) {
            Optional<Holder.Reference<MobEffect>> holder =
                BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect.effect()));
            if (holder.isEmpty()) {
                LOGGER.warn("Item {} asks for the effect \"{}\", which no mob effect answers to",
                    def.qualifiedName(), effect.effect());
                continue;
            }
            // One ApplyStatusEffectsConsumeEffect per declared effect rather than one
            // holding all of them: the chance is per effect in the legacy format, and
            // PotionEffectWrapper.testChance rolled for each separately.
            builder.onConsume(new ApplyStatusEffectsConsumeEffect(
                new MobEffectInstance(holder.get(), effect.durationTicks(), effect.amplifier()),
                effect.chance()));
        }
        return builder.build();
    }

    /**
     * The effects declared for this subtype, from either spelling of the property.
     *
     * <p>Both shapes the property takes are read as a {@link MetaMap}: a string
     * applies to the whole item, and {@code JobItem}'s object form gave one set of
     * effects per damage value — {@code "all"} for every one, which is
     * {@link mod.xie.xm.content.model.MetaRange#ALL}. A damage value on a food was
     * its subtype, and subtypes are separate items now, so each takes what was
     * written against its own.
     */
    private List<PotionEffectDef> effects(ItemDef def, int meta) {
        Optional<String> declared = declaredEffects(def, "potionEffect", meta)
            .or(() -> declaredEffects(def, "potionEffects", meta));
        if (declared.isEmpty()) {
            return List.of();
        }
        for (String bad : PotionEffectDef.unresolved(declared.get())) {
            LOGGER.warn("Item {} has an unreadable potionEffect entry \"{}\"",
                def.qualifiedName(), bad);
        }
        return PotionEffectDef.parseAll(declared.get());
    }

    private Optional<String> declaredEffects(ItemDef def, String key, int meta) {
        return def.props()
            .getMetaMap(key, value -> value.isJsonPrimitive() ? value.getAsString() : null)
            .flatMap(map -> map.get(meta));
    }

    /**
     * A tool or a sword, which are the same registration with different components.
     *
     * <p>Almost none of either needs a class: {@code XieItemTool} and
     * {@code XieItemSword} overrode mining speed, harvestability, attack damage,
     * durability, enchantability and blocking, and every one of those is a data
     * component now. See {@link XieToolProperties}.
     *
     * <p>{@code transforms} is the exception, and the fork below is the whole of it.
     * It replaces the block you right-click, which is something the tool does rather
     * than something it is, so a definition declaring it gets {@link XieToolItem}
     * and the other eight declarations go on dissolving completely — the same split
     * {@link #registerBow} makes for the property that needs a decision taken at the
     * moment of the click.
     *
     * <p>The block tags its rules point at are named here rather than inside the
     * property supplier, because the supplier does not run until the item registry
     * fills and the emitter has to be told what to write before that.
     */
    private DeferredItem<Item> registerToolLike(ContentPack pack, ItemDef def, String path) {
        collectToolTags(pack, def, path);
        boolean sword = def.archetype().equals(SWORD);

        XieToolItem.Config transforms = sword ? XieToolItem.Config.NONE : toolConfig(pack, def);
        if (transforms.transformsAnything()) {
            return items.registerItem(path,
                properties -> new XieToolItem(properties, transforms),
                properties -> XieToolProperties.forTool(def, path, common(pack, def, properties)));
        }

        return items.registerItem(path, Item::new, properties -> {
            Item.Properties built = common(pack, def, properties);
            return sword
                ? XieToolProperties.forSword(def, path, built)
                : XieToolProperties.forTool(def, path, built);
        });
    }

    /**
     * What a tool turns the block you click into.
     *
     * <p>Resolved to ids here and to blocks later, for the reason
     * {@link #resolveBlockId} gives: the registries are still filling, so all this
     * can do is derive the id a name <em>would</em> have. {@link XieToolItem} looks
     * the blocks up when it first needs them, as {@link XieTreeBlock} does with what
     * it sheds.
     *
     * <p>Two shapes of the property from 2013 are not carried, and neither loses
     * anything, because both were dead on arrival. A bare string — {@code
     * "transforms": "goldBlock"} — and the key {@code "all"} both built the
     * wildcard {@code new BlockID()}, whose block id is {@code -1}; {@code
     * BlockID.matches} compares that against the id actually in the world, which is
     * never negative, so a wildcard transform could not fire. Nothing declares
     * either, and reviving one would be inventing content rather than porting it.
     */
    private XieToolItem.Config toolConfig(ContentPack pack, ItemDef def) {
        Map<String, String> declared = def.props().getStringMap(TRANSFORMS);
        if (declared.isEmpty()) {
            if (def.props().has(TRANSFORMS)) {
                LOGGER.warn("Item {} declares transforms that name no block to block pair; "
                    + "it is registered as a plain tool", def.qualifiedName());
            }
            return XieToolItem.Config.NONE;
        }

        List<XieToolItem.Transform> transforms = new ArrayList<>(declared.size());
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(EVERY_BLOCK)) {
                LOGGER.warn("Item {} transforms \"{}\", which never matched a block in 2013 "
                    + "either; that entry is left out", def.qualifiedName(), entry.getKey());
                continue;
            }
            warnAboutMeta(def, entry.getKey());
            warnAboutMeta(def, entry.getValue());
            transforms.add(new XieToolItem.Transform(
                resolveBlockId(pack, entry.getKey()), resolveBlockId(pack, entry.getValue())));
        }
        return new XieToolItem.Config(transforms);
    }

    /**
     * {@code Xie.parseBlockAndMetadata} read a {@code stone:1} in a transforms entry
     * as a block and a subtype. Subtypes are blockstates now and a state is not
     * something a block id can carry, so the suffix is dropped — which widens the
     * entry to every state of that block rather than narrowing it, and is worth
     * saying out loud. Nothing in the content writes one.
     */
    private static void warnAboutMeta(ItemDef def, String reference) {
        if (reference.indexOf(':') >= 0) {
            LOGGER.warn("Item {} transforms \"{}\", whose metadata has no modern equivalent; "
                + "the whole block is taken", def.qualifiedName(), reference);
        }
    }

    /**
     * A bow, and the one item archetype in the port that gets a class.
     *
     * <p>The two properties a component can hold go the way a sword's do; everything
     * else is a decision taken while the bow is drawn and fired, and so belongs to
     * {@link XieBowItem}. That is the opposite direction from every other archetype
     * here, all of which lost code — and it is a class rather than a class and a
     * projectile, because {@code AbstractArrow} already carries the item it was fired
     * from and gives it back on pickup.
     */
    private DeferredItem<Item> registerBow(ContentPack pack, ItemDef def, String path) {
        XieBowItem.Config config = bowConfig(pack, def);
        return items.registerItem(path,
            properties -> new XieBowItem(properties, config),
            properties -> XieToolProperties.forBow(def, common(pack, def, properties)));
    }

    /**
     * What a bow declares beyond its durability.
     *
     * <p>{@code ammo} is resolved to ids here and to items later, for the reason
     * {@link #resolveItemId} gives: the item registry is still filling, so a bow
     * whose ammunition is another pack's item cannot look it up yet.
     * {@code XieItemBow} had the same problem and solved it the other way, by making
     * {@code JobItem} run late enough to hold real {@code Item} references.
     */
    private XieBowItem.Config bowConfig(ContentPack pack, ItemDef def) {
        List<String> ammo = def.props().getRefList("ammo").stream()
            .map(ref -> resolveItemId(pack, ref))
            .distinct()
            .toList();

        return new XieBowItem.Config(ammo,
            def.props().getBoolean("ammoless", false),
            def.props().getBoolean("ammoConsumed", false),
            def.props().getBoolean("semiautomatic", false),
            def.props().getInt("power", 0),
            def.props().getInt("punch", 0),
            // Not a duration. See XieBowItem.empowered — the 2013 expression only
            // ever asked whether this was greater than zero.
            def.props().getInt("flame", 0) > 0);
    }

    /**
     * A stick, and the third archetype in the port that needed a class.
     *
     * <p>It is the same shape as {@link #registerBow}'s and for the same reason: what
     * a stick yields is decided at the click, and no component decides anything. The
     * two properties that <em>are</em> descriptions — a stack of one and sixteen uses
     * — go on the properties here, and the rest is {@link XieStickItem}.
     *
     * <p>{@code JobItem.doStick} read {@code uses} through {@code setMaxDamage},
     * which is {@code minecraft:max_damage}; {@code Item.Properties.durability} sets
     * it along with the stack size of one that {@code XieItemStick}'s constructor
     * already asked for.
     */
    private DeferredItem<Item> registerStick(ContentPack pack, ItemDef def, String path) {
        XieStickItem.Config config = stickConfig(pack, def);
        if (!config.harvestsAnything()) {
            LOGGER.warn("Item {} is type \"stick\" but names no blocks to work on; it is "
                + "registered as a plain item", def.qualifiedName());
        }
        return items.registerItem(path,
            properties -> new XieStickItem(properties, config),
            properties -> common(pack, def, properties)
                .durability(def.props().getInt("uses", XieStickItem.DEFAULT_USES)));
    }

    /**
     * What a stick works on and what each block gives up.
     *
     * <p>Resolved to ids here and to blocks and items later, for the reason
     * {@link #resolveBlockId} gives: the registries are still filling, so all this can
     * do is derive the id a name <em>would</em> have.
     *
     * <p>A block reference may carry a metadata suffix — {@code crimsonSickle} names
     * {@code netherStalk:3}, ripe nether wart — and it goes the way the same suffix
     * goes on a {@code transforms} entry, which is out with a word about it. Metadata
     * is a blockstate now, and dropping the suffix widens the entry to every state of
     * the block rather than narrowing it to one, so a sickle reaps nether wart at any
     * age. That is a real widening rather than a theoretical one, and it is the first
     * content in the port to declare the case at all; honouring it would mean
     * inventing a per-block notion of which state a 2013 metadata value picked, which
     * is precisely what the transforms decision declined to do.
     */
    private XieStickItem.Config stickConfig(ContentPack pack, ItemDef def) {
        List<XieStickItem.Harvest> harvests = new ArrayList<>(def.blockDrops().size());

        for (ItemDef.BlockDrops entry : def.blockDrops()) {
            warnAboutBlockMeta(def, entry.block());
            List<XieStickItem.Drop> drops = new ArrayList<>(entry.drops().drops().size());
            for (DropTable.Drop drop : entry.drops().drops()) {
                // Chance at metadata zero: Droppables.getDrops passed -1, and
                // Drop.getDropForMeta fell straight through to metaChance[0] for it.
                drops.add(new XieStickItem.Drop(resolveItemId(pack, drop.item()),
                    Math.clamp(drop.chanceFor(0), 0, 100), drop.amount()));
            }
            harvests.add(new XieStickItem.Harvest(
                resolveBlockId(pack, entry.block().qualifiedName()), drops));
        }

        return new XieStickItem.Config(harvests,
            def.props().getInt("destroyChance", XieStickItem.DEFAULT_DESTROY_CHANCE));
    }

    /** {@link #warnAboutMeta}, asked about a block reference rather than a string. */
    private static void warnAboutBlockMeta(ItemDef def, ItemRef ref) {
        if (ref.count() != ItemRef.DEFAULT_COUNT || ref.hasMeta()) {
            LOGGER.warn("Item {} works on \"{}\", whose metadata has no modern equivalent; "
                + "the whole block is taken", def.qualifiedName(), ref.name());
        }
    }

    /**
     * Records the generated block tag for each list a tool declares.
     *
     * <p>{@code blocksEffective: "all"} is not one of them — it was a flag rather
     * than a list, and the rule it becomes names every block directly rather than
     * enumerating the registry into a file.
     */
    private void collectToolTags(ContentPack pack, ItemDef def, String path) {
        for (XieToolProperties.BlockList list : XieToolProperties.BlockList.forArchetype(def.archetype())) {
            if (list == XieToolProperties.BlockList.EFFECTIVE && XieToolProperties.isEverything(def)) {
                continue;
            }
            if (!XieToolProperties.declares(def, list)) {
                continue;
            }
            List<String> declared = def.props().getStringList(list.property());
            blockTags.put(XiesMod.MOD_ID + ":" + XieToolProperties.tagPath(path, list),
                declared.stream().map(name -> resolveBlockId(pack, name)).distinct().toList());
        }
    }

    /**
     * A seed is a {@link BlockItem} that plants the block named by {@code growsInto}
     * but keeps its own name, exactly as vanilla wheat seeds do.
     *
     * <p>{@code inheritMeta} is how the legacy format said "subtype 2 of this seed
     * plants subtype 2 of that block" — the apple seed grows an apple sapling, not
     * whichever one happened to be first.
     */
    private DeferredItem<Item> registerSeeds(ContentPack pack, ItemDef def,
                                             List<String> names, int meta, String path) {
        Optional<String> growsInto = def.props().getString("growsInto");
        if (growsInto.isEmpty()) {
            LOGGER.warn("Item {} is type \"seeds\" but declares no growsInto", def.qualifiedName());
            return items.registerItem(path, Item::new, properties -> common(pack, def, properties));
        }

        boolean inheritMeta = def.props().getBoolean("inheritMeta", false);
        int targetMeta = inheritMeta && !names.isEmpty() ? meta : 0;

        List<String> targetPaths = blockPaths.get(growsInto.get());
        DeferredBlock<Block> target = targetPaths != null && targetMeta < targetPaths.size()
            ? registeredBlocks.get(targetPaths.get(targetMeta))
            : null;

        if (target == null) {
            LOGGER.warn("Seed {} grows into \"{}\", which was not registered; "
                + "registering it as a plain item", def.qualifiedName(), growsInto.get());
            return items.registerItem(path, Item::new, properties -> common(pack, def, properties));
        }

        return items.registerItem(path,
            properties -> new BlockItem(target.get(), properties.useItemDescriptionPrefix()),
            properties -> common(pack, def, properties));
    }

    /** The properties every archetype takes, whatever else it adds. */
    private Item.Properties common(ContentPack pack, ItemDef def, Item.Properties properties) {
        Item.Properties built = stackSize(def, properties);
        container(pack, def).ifPresent(built::craftRemainder);
        return built;
    }

    private Item.Properties stackSize(ItemDef def, Item.Properties properties) {
        int stackSize = def.props().getInt("stacksize", 0);
        return stackSize > 0 ? properties.stacksTo(Math.clamp(stackSize, 1, 99)) : properties;
    }

    /**
     * The item {@code container} names, which crafting with this one leaves behind.
     *
     * <p>{@code JobItem} passed it to {@code Item.setContainerItem}, whose whole
     * effect was on the crafting grid — the modern {@code craftRemainder}. That it is
     * also what eating leaves behind is a second thing the same declaration meant, and
     * is {@link #useRemainder}.
     */
    private Optional<Item> container(ContentPack pack, ItemDef def) {
        return def.props().getString("container")
            .flatMap(name -> lookupItem(pack, def, "container", ItemRef.parse(name)));
    }

    /**
     * What eating this leaves in your hand.
     *
     * <p>Two declarations mean it. {@code container} is one, and a {@code drops}
     * naming a single thing unconditionally is the other — {@code JobItem} filled the
     * drops in from the container when there were none, and {@code
     * XieItemFood.onFoodEaten} handed whichever it ended up with to the player. Only
     * one item can be a use remainder, so a {@code drops} listing several or rolling a
     * chance is left alone rather than half-honoured.
     */
    private Optional<Item> useRemainder(ContentPack pack, ItemDef def) {
        Optional<Item> container = container(pack, def);
        if (container.isPresent()) {
            return container;
        }
        List<DropTable.Drop> drops = def.drops().drops();
        if (drops.size() != 1 || !drops.get(0).isUnconditional() || drops.get(0).amount() != 1) {
            if (!drops.isEmpty()) {
                LOGGER.debug("Item {} drops {} when used, which is more than a use remainder "
                    + "can express; leaving it out", def.qualifiedName(), drops);
            }
            return Optional.empty();
        }
        return lookupItem(pack, def, "drops", drops.get(0).item());
    }

    /**
     * A reference resolved to a live item.
     *
     * <p>Unlike {@link #resolveItemId} this needs the object rather than the id, so it
     * can only see what is already in the registry — every vanilla item, and any of
     * this mod's registered before this one. Live content only ever names a bowl or a
     * glass bottle here, and a name nothing answers to is reported rather than
     * dropped silently.
     */
    private Optional<Item> lookupItem(ContentPack pack, ItemDef def, String property, ItemRef ref) {
        String id = resolveItemId(pack, ref);
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
        if (item.isEmpty()) {
            LOGGER.warn("Item {} names \"{}\" as its {}, which resolves to {} and is not registered",
                def.qualifiedName(), ref, property, id);
        }
        return item;
    }

    // ------------------------------------------------------------- subtypes

    /**
     * The display name for one subtype: its own entry in {@code names}. A
     * {@code .loc} file keys on the definition, which predates subtypes having
     * names of their own, so it only speaks for an unsplit definition.
     */
    private String displayName(ContentPack pack, String definitionName,
                               String fallback, List<String> names, int meta) {
        if (!names.isEmpty()) {
            return names.get(meta);
        }
        return pack.displayNameFor(definitionName).orElse(fallback);
    }

    /**
     * Reserves each derived path, renaming any that is already taken.
     *
     * <p>Subtype ids come from display names, which nothing guarantees to be unique
     * or distinct from another definition's. A collision would otherwise be a
     * duplicate-registration crash at a point far from its cause, so the loser falls
     * back to {@code <definition>_<meta>} and says so.
     */
    private List<String> claim(String qualifiedName, String definitionName,
                               List<String> paths, Set<String> taken) {
        List<String> claimed = new ArrayList<>(paths.size());
        Set<String> used = new HashSet<>(taken);

        for (int meta = 0; meta < paths.size(); meta++) {
            String path = paths.get(meta);
            if (!used.add(path)) {
                String fallback = ResourceNames.toPath(definitionName) + "_" + meta;
                for (int n = 2; !used.add(fallback); n++) {
                    fallback = ResourceNames.toPath(definitionName) + "_" + meta + "_" + n;
                }
                LOGGER.warn("{} subtype {} wants the id \"{}\", which is already taken; "
                    + "registering it as \"{}\" instead", qualifiedName, meta, path, fallback);
                path = fallback;
            }
            claimed.add(path);
        }
        return List.copyOf(claimed);
    }

    // --------------------------------------------------------------- the tab

    private void registerCreativeTab() {
        tabs.register("content", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + XiesMod.MOD_ID + ".content"))
            .icon(() -> tabContents.isEmpty()
                ? new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS)
                : new ItemStack(tabContents.get(0).get()))
            .displayItems((parameters, output) -> tabContents.forEach(item -> output.accept(item.get())))
            .build());

        translations.put("itemGroup." + XiesMod.MOD_ID + ".content", "Xie's Mod");
    }

    // -------------------------------------------------------------- exposure

    /** Everything that made it into a registry, for the pack emitters. */
    public RegisteredContent registered() {
        return new RegisteredContent(blockPaths, itemPaths, maxAges, blockItems, trees,
            blockTags, translations);
    }

    /** Id path to registered block. */
    public Map<String, DeferredBlock<Block>> registeredBlocks() {
        return Map.copyOf(registeredBlocks);
    }

    /** Id path to registered item. */
    public Map<String, DeferredItem<Item>> registeredItems() {
        return Map.copyOf(registeredItems);
    }
}
