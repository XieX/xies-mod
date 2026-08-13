package mod.xie.xm.reg;

import com.mojang.logging.LogUtils;

import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.ResourceNames;

import net.minecraft.world.item.Item;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code override}, which names a vanilla object this definition speaks for.
 *
 * <h2>What the property meant, and what it means here</h2>
 *
 * <p><strong>In 2013 it did not mean "replace vanilla's behaviour".</strong>
 * {@code JobBlock.doWork} and {@code JobItem.doWork} consult it in exactly one place
 * each: when the definition's declared numeric id is already occupied, {@code override}
 * permits nulling out {@code Block.blocksList[id]} or {@code Item.itemsList[id]} and
 * registering this definition in its place. Without it the job failed with "ID is in
 * use". It was an id-collision escape hatch, and the vanilla object it displaced was
 * whichever one happened to hold the id the pack picked.
 *
 * <p>Numeric ids are gone, so there is no collision to escape and no faithful
 * translation. What carries over instead is the <em>name</em> the property holds,
 * because that was never the accident: {@code _wheatOverride.xie} declares
 * {@code "override": "crops"} and goes on to describe a wheat crop, and
 * {@code survivalpickaxe.xie} declares {@code "overide": "pickaxeWood"} and goes on
 * to describe a nerfed wooden pickaxe. The author picked those ids deliberately and
 * the content beside the property says what it is for. So an override is read here
 * as <em>this definition speaks for that vanilla object</em>, and its properties are
 * applied to that object rather than to one of our own.
 *
 * <h2>Applied, not replaced</h2>
 *
 * <p>Nothing is registered for an overriding definition. The vanilla block or item
 * stays exactly the object it was — it cannot be unregistered, and registering a
 * second one beside it would only add a duplicate nobody could obtain — and what the
 * definition declares is written onto it in place. That covers less than a
 * replacement would, and both live uses turn out to need less:
 *
 * <ul>
 *   <li>An <strong>item</strong> override is data components, and this class applies
 *       them through NeoForge's {@link ModifyDefaultComponentsEvent}. The two survival
 *       pickaxes declare {@code material}, {@code blocksEffective} and
 *       {@code canHarvest} and nothing else, which is exactly the surface
 *       {@link XieToolProperties} already writes.</li>
 *   <li>A <strong>block</strong> override is a loot table, and
 *       {@link mod.xie.xm.pack.XieDataEmitter} writes it at the vanilla block's own
 *       id. {@code wheatOverride} changes only {@code drops}.</li>
 * </ul>
 *
 * <p>Whatever a definition declares beyond that needs the block or item to be a
 * different object, and is counted and named rather than quietly dropped.
 */
public final class XieOverrides {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * A vanilla item and the definition that speaks for it.
     *
     * @param def        the definition, for its properties and for the log
     * @param target     the item whose default components are patched
     * @param targetPath the item's id path, which is what its generated block tags
     *                   are named after — so {@code survivalpickaxe.xie} writes
     *                   {@code tags/block/tool/wooden_pickaxe/effective}
     */
    public record ItemOverride(ItemDef def, Item target, String targetPath) { }

    private final List<ItemOverride> items = new ArrayList<>();

    /**
     * The id an {@code override} names.
     *
     * <p>Always vanilla's. The legacy format had no namespaces, and an override in
     * 1.6 could only ever land on whatever occupied a numeric id — which, for a pack
     * loading after the game, was a vanilla object. The name goes through the same
     * rename table every other vanilla reference does, so {@code crops} is wheat and
     * {@code pickaxeWood} is the wooden pickaxe.
     */
    public static String targetId(ItemRef override) {
        return "minecraft:" + ResourceNames.toVanillaPath(override);
    }

    /** Records an item override for {@link #install} to apply. */
    public void addItem(ItemDef def, Item target, String targetPath) {
        items.add(new ItemOverride(def, target, targetPath));
    }

    public List<ItemOverride> itemOverrides() {
        return List.copyOf(items);
    }

    /**
     * Listens for the one event that can modify an item after it is registered.
     *
     * <p>On the mod bus, and it fires late: {@code DataComponentModifiers} is applied
     * from {@code DataComponentInitializers.build}, which
     * {@code ReloadableServerResources} calls with {@code lookupWithUpdatedTags} once
     * the datapack has loaded. That timing is what makes this work at all — the block
     * tags a tool's mining rules point at are generated by this mod's own pack, and by
     * the time the patch runs they are loaded and bound.
     */
    public void install(IEventBus modEventBus) {
        if (items.isEmpty()) {
            return;
        }
        modEventBus.addListener(this::onModifyDefaultComponents);
    }

    private void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (ItemOverride override : items) {
            event.modify(override.target(), (components, context, item) -> {
                if (override.def().archetype().equals("sword")) {
                    XieToolProperties.applySwordTo(
                        override.def(), override.targetPath(), components, context);
                } else {
                    XieToolProperties.applyToolTo(
                        override.def(), override.targetPath(), components, context);
                }
            });
            LOGGER.info("Overriding minecraft:{} with {}", override.targetPath(),
                override.def().qualifiedName());
        }
    }
}
