package mod.xie.xm.reg;

import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.block.XieBushBlock;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ResourceNames;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 */
public final class XieRegistrar {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Block archetypes this build can register. Grows as archetypes are ported. */
    private static final Set<String> SUPPORTED_BLOCKS = Set.of("bush");

    /** Item archetypes this build can register. */
    private static final Set<String> SUPPORTED_ITEMS = Set.of("basic", "food", "seeds");

    private final DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(XiesMod.MOD_ID);
    private final DeferredRegister.Items items = DeferredRegister.createItems(XiesMod.MOD_ID);
    private final DeferredRegister<CreativeModeTab> tabs =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, XiesMod.MOD_ID);

    /** Definition name to registered block, e.g. {@code tomatoBush} to the holder. */
    private final Map<String, DeferredBlock<Block>> registeredBlocks = new LinkedHashMap<>();
    private final Map<String, DeferredItem<Item>> registeredItems = new LinkedHashMap<>();

    /** Display names to emit into the language file, keyed by translation key. */
    private final Map<String, String> translations = new TreeMap<>();

    /** Definition name to highest age, so the emitter knows how many variants to write. */
    private final Map<String, Integer> blockMaxAges = new LinkedHashMap<>();

    private final List<DeferredItem<Item>> tabContents = new ArrayList<>();

    public void register(IEventBus modEventBus, List<ContentPack> packs) {
        for (ContentPack pack : packs) {
            // Blocks first: a seed item needs the block it plants to already exist.
            pack.blocks().values().forEach(def -> registerBlock(pack, def));
        }
        for (ContentPack pack : packs) {
            pack.items().values().forEach(def -> registerItem(pack, def));
        }

        registerCreativeTab();

        blocks.register(modEventBus);
        items.register(modEventBus);
        tabs.register(modEventBus);

        LOGGER.info("Xie registrar: queued {} blocks and {} items",
            registeredBlocks.size(), registeredItems.size());
    }

    // ---------------------------------------------------------------- blocks

    private void registerBlock(ContentPack pack, BlockDef def) {
        if (def.isOverride()) {
            LOGGER.debug("Skipping {}: overrides of vanilla blocks are not ported yet",
                def.qualifiedName());
            return;
        }
        if (!SUPPORTED_BLOCKS.contains(def.archetype())) {
            LOGGER.debug("Skipping block {}: archetype \"{}\" not ported yet",
                def.qualifiedName(), def.archetype());
            return;
        }

        String path = ResourceNames.toPath(def.name());
        XieBushBlock.Config config = bushConfig(pack, def);

        DeferredBlock<Block> holder = blocks.registerBlock(
            path,
            properties -> new XieBushBlock(properties, config),
            properties -> XieBlockProperties.forPlant(def)
        );

        registeredBlocks.put(def.name(), holder);
        blockMaxAges.put(def.name(), config.maxAge());
        translations.put("block." + XiesMod.MOD_ID + "." + path,
            pack.displayNameFor(def.name()).orElse(def.displayName()));
    }

    private XieBushBlock.Config bushConfig(ContentPack pack, BlockDef def) {
        int maxAge = def.props().getInt("maxGrowth", def.impliedMaxMeta());
        int growthRate = def.props().getInt("growthRate", 100);
        Set<String> growsOn = new LinkedHashSet<>();
        for (String name : def.props().getStringList("growsOn")) {
            growsOn.add(resolveBlockId(pack, name));
        }
        return new XieBushBlock.Config(maxAge, growthRate, growsOn, def.bounds());
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
        return "minecraft:" + ResourceNames.toVanillaPath(mod.xie.xm.content.model.ItemRef.parse(name));
    }

    // ----------------------------------------------------------------- items

    private void registerItem(ContentPack pack, ItemDef def) {
        if (def.isOverride()) {
            LOGGER.debug("Skipping {}: overrides of vanilla items are not ported yet",
                def.qualifiedName());
            return;
        }
        if (!SUPPORTED_ITEMS.contains(def.archetype())) {
            LOGGER.debug("Skipping item {}: archetype \"{}\" not ported yet",
                def.qualifiedName(), def.archetype());
            return;
        }

        String path = ResourceNames.toPath(def.name());
        DeferredItem<Item> holder = switch (def.archetype()) {
            case "food" -> registerFood(def, path);
            case "seeds" -> registerSeeds(def, path);
            default -> items.registerItem(path, Item::new, properties -> stackSize(def, properties));
        };

        if (holder == null) {
            return;
        }
        registeredItems.put(def.name(), holder);
        tabContents.add(holder);
        translations.put("item." + XiesMod.MOD_ID + "." + path,
            pack.displayNameFor(def.name()).orElse(def.displayName()));
    }

    private DeferredItem<Item> registerFood(ItemDef def, String path) {
        FoodProperties food = new FoodProperties.Builder()
            .nutrition(def.hunger().orElse(1))
            .saturationModifier(def.nourishment().orElse(0.3f))
            .build();
        return items.registerItem(path, Item::new,
            properties -> stackSize(def, properties).food(food));
    }

    /**
     * A seed is a {@link BlockItem} that plants the block named by {@code growsInto}
     * but keeps its own name, exactly as vanilla wheat seeds do.
     */
    private DeferredItem<Item> registerSeeds(ItemDef def, String path) {
        Optional<String> growsInto = def.props().getString("growsInto");
        if (growsInto.isEmpty()) {
            LOGGER.warn("Item {} is type \"seeds\" but declares no growsInto", def.qualifiedName());
            return items.registerItem(path, Item::new, properties -> stackSize(def, properties));
        }

        DeferredBlock<Block> target = registeredBlocks.get(growsInto.get());
        if (target == null) {
            LOGGER.warn("Seed {} grows into \"{}\", which was not registered; "
                + "registering it as a plain item", def.qualifiedName(), growsInto.get());
            return items.registerItem(path, Item::new, properties -> stackSize(def, properties));
        }

        return items.registerItem(path,
            properties -> new BlockItem(target.get(), properties.useItemDescriptionPrefix()),
            properties -> stackSize(def, properties));
    }

    private Item.Properties stackSize(ItemDef def, Item.Properties properties) {
        int stackSize = def.props().getInt("stacksize", 0);
        return stackSize > 0 ? properties.stacksTo(Math.clamp(stackSize, 1, 99)) : properties;
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

    /** Translation key to display name, for the generated language file. */
    public Map<String, String> translations() {
        return Map.copyOf(translations);
    }

    public Map<String, DeferredBlock<Block>> registeredBlocks() {
        return Map.copyOf(registeredBlocks);
    }

    /** Definition name to highest age, for blockstate variant generation. */
    public Map<String, Integer> blockMaxAges() {
        return Map.copyOf(blockMaxAges);
    }

    public Map<String, DeferredItem<Item>> registeredItems() {
        return Map.copyOf(registeredItems);
    }
}
