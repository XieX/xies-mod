package mod.xie.xm.reg;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;

import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.Map;

/**
 * {@code fuel}, as the {@code minecraft:cooking_fuel} component vanilla gives its own
 * fuels with {@code Item.Properties.cookingFuel}.
 *
 * <p>The burn times are known only once the pack is generated, after items are
 * registered, so they are set as a default-component patch instead. NeoForge collects
 * patches once and runs them on every datapack load, which comes after generation on
 * either side. The predicate is asked at that point, so it sees the emitted times.
 */
public final class XieFuels {

    /**
     * Item id to the {@code context_int_provider} id its burn time is read from.
     * Written during pack generation and read on every datapack load.
     */
    private static volatile Map<String, String> burnTimes = Map.of();

    private XieFuels() { }

    /** @param fuels from {@link mod.xie.xm.pack.XiePackEmitter#fuels} */
    public static void burnTimes(Map<String, String> fuels) {
        burnTimes = Map.copyOf(fuels);
    }

    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modifyMatching(
            (item, components) -> burnTimes.containsKey(BuiltInRegistries.ITEM.getKey(item).toString()),
            (components, context, item) -> {
                String provider = burnTimes.get(BuiltInRegistries.ITEM.getKey(item).toString());
                components.set(DataComponents.COOKING_FUEL, new CookingFuel(
                    ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, Identifier.parse(provider)),
                    ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER));
            });
    }
}
