package mod.xie.xm.pack;

import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.content.parse.XieParser;
import mod.xie.xm.recipe.XieRecipeCuller;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 *
 * <p>The event fires once per {@link PackType}, and one directory serves as both
 * resource pack and datapack, so the generation itself happens on whichever fires
 * first and both registrations point at the same output. Regenerating on the
 * second event would delete files out from under the pack opened by the first.
 */
public final class XiePackProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Shown on the resource pack and datapack screens. */
    public static final String PACK_ID = XiesMod.MOD_ID + "_generated";

    /** The override pack, which is a separate pack because it needs to outrank vanilla. */
    public static final String OVERRIDE_PACK_ID = PACK_ID + "_overrides";

    /** The worldgen pack, which is a separate pack because a world may leave it out. */
    public static final String WORLDGEN_PACK_ID = PACK_ID + "_worldgen";

    /** The generated output, once written. Guarded by {@link #LOCK}. */
    private static Path generatedRoot;
    private static Path overrideRoot;
    private static Path worldgenRoot;
    private static boolean generationFailed;

    private static final Object LOCK = new Object();

    private XiePackProvider() { }

    public static void addPackFinders(AddPackFindersEvent event) {
        Path root = generate();
        if (root == null) {
            return;
        }
        PackType type = event.getPackType();

        // Bottom position and always-active: the generated pack is a floor that a
        // user pack can override, not something that overrides them. For the
        // datapack side always-active also means required, which is what the blocks
        // need — a registered block whose loot table is switched off drops nothing.
        addPack(event, type, PACK_ID, "Xie's Mod content", root, Pack.Position.BOTTOM, true);

        // The exact opposite, which is why it is a pack of its own. A definition that
        // declares "override" writes a file at a vanilla id, and a pack below vanilla
        // loses that argument every time — the generated table is read and then
        // discarded, which looks precisely like the override not working. Only the
        // files that replace something are up here; everything additive stays at the
        // bottom where it cannot trample a user's pack.
        //
        // Registered even when it is empty, which is the usual case: a pack that
        // appears and disappears with the content leaves "Missing data pack" behind in
        // every world that once loaded it.
        if (type == PackType.SERVER_DATA && overrideRoot != null) {
            addPack(event, type, OVERRIDE_PACK_ID, "Xie's Mod vanilla overrides",
                overrideRoot, Pack.Position.TOP, true);
        }

        // The one pack a world may do without. Not required, so it is listed on the
        // Create World screen's data packs and /datapack can disable it; built in, so
        // the server enables it in any world that has not disabled it, including an
        // existing world the first time it loads with the mod. Its biome modifiers are
        // read when a world opens, so switching it takes a reopen, not a /reload.
        if (type == PackType.SERVER_DATA && worldgenRoot != null) {
            addPack(event, type, WORLDGEN_PACK_ID, "Xie's Mod worldgen",
                worldgenRoot, Pack.Position.BOTTOM, false);
        }
    }

    private static void addPack(AddPackFindersEvent event, PackType type, String id,
                                String title, Path root, Pack.Position position,
                                boolean required) {
        PackLocationInfo location = new PackLocationInfo(
            id,
            Component.literal(title),
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

        Pack pack = Pack.readMetaAndCreate(
            location, resources, type, new PackSelectionConfig(required, position, false));

        if (pack == null) {
            LOGGER.error("Generated pack at {} could not be read back as {}", root, type);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
        LOGGER.info("Registered generated {} pack {} from {}", type.getDirectory(), id, root);
    }

    /** Writes the pack if it has not been written yet. Null when generation failed. */
    private static Path generate() {
        synchronized (LOCK) {
            if (generatedRoot != null || generationFailed) {
                return generatedRoot;
            }

            Path contentDir = FMLPaths.GAMEDIR.get().resolve(XieParser.CONTENT_DIR_NAME);
            XiePackEmitter emitter = new XiePackEmitter(
                contentDir, XiePackProvider::itemExists, XiePackProvider::blockExists);

            PackFormat resources = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
            PackFormat data = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);

            try {
                emitter.emit(
                    XiesMod.packs(),
                    XiesMod.registrar().registered(),
                    new XiePackEmitter.PackFormats(
                        resources.major(), resources.minor(), data.major(), data.minor()));
            } catch (IOException e) {
                LOGGER.error("Failed to generate the Xie content pack", e);
                generationFailed = true;
                return null;
            }

            // Nothing on disk carries a cull, so it is handed over directly. This
            // runs before any datapack is read, on either side, which is what the
            // culler needs — it acts on the recipes of the first load as well as of
            // every later /reload.
            XieRecipeCuller.targets(emitter.culledItems());

            overrideRoot = emitter.overrideRoot();
            worldgenRoot = emitter.worldgenRoot();
            generatedRoot = emitter.outputRoot();
            return generatedRoot;
        }
    }

    /**
     * Whether an item id is actually registered.
     *
     * <p>The datapack half checks every reference against this before writing it.
     * Item and block registries are frozen by the time pack finders are added, so
     * the answer here is final — which is what lets a recipe naming an unported
     * item be dropped quietly instead of failing datapack load.
     */
    private static boolean itemExists(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier != null && BuiltInRegistries.ITEM.containsKey(identifier);
    }

    /**
     * Whether a block id is actually registered.
     *
     * <p>Worldgen needs this rather than {@link #itemExists}: no Xie block registers
     * a {@code BlockItem}, so the blocks worldgen exists to place are absent from the
     * item registry entirely.
     */
    private static boolean blockExists(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier != null && BuiltInRegistries.BLOCK.containsKey(identifier);
    }
}
