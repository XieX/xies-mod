package mod.xie.xm.client;

import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;

import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import org.slf4j.Logger;

import java.util.List;

/**
 * The one thing about this mod's blocks that is decided on the client.
 *
 * <p>A stem is grey. Vanilla's {@code melon_stem} sprite carries no colour of its
 * own — every face of every one of the nine models is tinted, and the tint is what
 * turns a stem green while it grows and orange once it has fruited. That is a
 * registration in code rather than anything a pack can write, so a Xie stem pointed
 * at vanilla's models without this draws in flat grey and looks broken.
 *
 * <p>It is the only colour handler the port has needed. {@code enableColorisation}
 * defaulted off in 2013 and no definition turns it on, which is why leaves are
 * emitted as a plain untinted cube — see {@code XiePackEmitter}. A stem is the
 * opposite case and not a choice: the models were borrowed whole, so their tint has
 * to be borrowed with them.
 */
@EventBusSubscriber(modid = XiesMod.MOD_ID, value = Dist.CLIENT)
public final class XieBlockColors {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * {@code BlockColors.createDefault}'s own constant for an attached stem, which is
     * the colour a ripe one holds rather than a function of anything.
     */
    private static final int ATTACHED_STEM_COLOUR = -2046180;

    private XieBlockColors() { }

    @SubscribeEvent
    public static void registerTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        List<Block> stems = XiesMod.registrar().registeredBlocks().values().stream()
            .map(holder -> (Block) holder.get())
            .filter(block -> block instanceof StemBlock || block instanceof AttachedStemBlock)
            .toList();

        for (Block block : stems) {
            // Exactly what vanilla registers for its own two: the growing stem's
            // colour is a function of its age and the attached one's is fixed.
            event.register(block instanceof StemBlock
                    ? List.of(BlockTintSources.stem())
                    : List.of(BlockTintSources.constant(ATTACHED_STEM_COLOUR)),
                block);
        }

        if (!stems.isEmpty()) {
            LOGGER.debug("Registered vanilla's stem tint for {} blocks", stems.size());
        }
    }
}
