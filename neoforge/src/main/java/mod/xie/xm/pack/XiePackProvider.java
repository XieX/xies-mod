package mod.xie.xm.pack;

import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.content.parse.XieParser;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Generates the content pack and hands it to the resource loader.
 *
 * <p>Runs on {@link AddPackFindersEvent}, which fires before the game reads
 * resources but after the registries this depends on exist. Regenerating on every
 * launch rather than caching is deliberate: content files change far more often
 * than the game restarts, and a stale generated pack is a confusing failure.
 */
public final class XiePackProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Shown on the resource pack screen. */
    public static final String PACK_ID = XiesMod.MOD_ID + "_generated";

    private XiePackProvider() { }

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        Path contentDir = FMLPaths.GAMEDIR.get().resolve(XieParser.CONTENT_DIR_NAME);
        XiePackEmitter emitter = new XiePackEmitter(contentDir);

        PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        try {
            emitter.emit(
                XiesMod.packs(),
                XiesMod.registrar().blockMaxAges(),
                XiesMod.registrar().translations(),
                format.major(),
                format.minor());
        } catch (IOException e) {
            LOGGER.error("Failed to generate the Xie content pack", e);
            return;
        }

        Path root = emitter.outputRoot();
        PackLocationInfo location = new PackLocationInfo(
            PACK_ID,
            Component.literal("Xie's Mod content"),
            PackSource.BUILT_IN,
            Optional.empty());

        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new PathPackResources(info, root);
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new PathPackResources(info, root);
            }
        };

        // Bottom position and always-active: the generated pack is a floor that a
        // user resource pack can override, not something that overrides them.
        Pack pack = Pack.readMetaAndCreate(
            location,
            resources,
            PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(true, Pack.Position.BOTTOM, false));

        if (pack == null) {
            LOGGER.error("Generated pack at {} could not be read back", root);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
        LOGGER.info("Registered generated resource pack from {}", root);
    }
}
