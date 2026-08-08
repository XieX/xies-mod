package mod.xie.xm;

import com.mojang.logging.LogUtils;

import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.parse.Diagnostic;
import mod.xie.xm.content.parse.XieParser;
import mod.xie.xm.reg.XieRegistrar;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mod entry point.
 *
 * <p>Content packs are read <em>here</em>, during mod construction, and that timing
 * is not incidental. Block and item registries are populated during mod loading
 * and frozen immediately afterwards, so a definition that is not known by the end
 * of this constructor can never be registered at all.
 *
 * <p>That is the one structural loss against the 2013 engine, which parsed content
 * at runtime and could swap a server's content pack in on join. Modern Minecraft
 * cannot do that for blocks and items; client and server both load the same packs
 * at startup instead.
 *
 * <p>This step only loads and reports. Registration is the next step.
 */
@Mod(XiesMod.MOD_ID)
public class XiesMod {

    public static final String MOD_ID = "xiesmod";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<ContentPack> PACKS = new ArrayList<>();

    private static final XieRegistrar REGISTRAR = new XieRegistrar();

    public XiesMod(IEventBus modEventBus, ModContainer container) {
        loadContentPacks();
        REGISTRAR.register(modEventBus, PACKS);

        // Next step: emit the generated resource and data packs (models, lang,
        // loot tables, recipes) from AddPackFindersEvent.
    }

    public static XieRegistrar registrar() {
        return REGISTRAR;
    }

    /** Every pack that loaded, in load order. Empty until the constructor has run. */
    public static List<ContentPack> packs() {
        return Collections.unmodifiableList(PACKS);
    }

    private static void loadContentPacks() {
        Path contentDir = FMLPaths.GAMEDIR.get().resolve(XieParser.CONTENT_DIR_NAME);

        try {
            // Create it eagerly so there is an obvious place to drop a pack even
            // on a fresh install.
            Files.createDirectories(contentDir);
        } catch (IOException e) {
            LOGGER.error("Could not create content directory {}", contentDir, e);
            return;
        }

        List<XieParser.ParseResult> results;
        try {
            results = XieParser.parseAll(contentDir);
        } catch (IOException e) {
            LOGGER.error("Failed to read content packs from {}", contentDir, e);
            return;
        }

        if (results.isEmpty()) {
            LOGGER.info("No content packs found in {}", contentDir);
            return;
        }

        for (XieParser.ParseResult result : results) {
            ContentPack pack = result.pack();
            PACKS.add(pack);

            LOGGER.info("Loaded content pack {}", pack);

            for (Diagnostic diagnostic : result.diagnostics().all()) {
                switch (diagnostic.severity()) {
                    case ERROR -> LOGGER.error("[{}] {}: {}",
                        pack.name(), diagnostic.where(), diagnostic.message());
                    case WARNING -> LOGGER.warn("[{}] {}: {}",
                        pack.name(), diagnostic.where(), diagnostic.message());
                    case INFO -> LOGGER.info("[{}] {}: {}",
                        pack.name(), diagnostic.where(), diagnostic.message());
                }
            }
        }

        int blocks = PACKS.stream().mapToInt(pack -> pack.blocks().size()).sum();
        int items = PACKS.stream().mapToInt(pack -> pack.items().size()).sum();
        int recipes = PACKS.stream().mapToInt(pack -> pack.recipes().size()).sum();
        LOGGER.info("Xie content: {} packs, {} blocks, {} items, {} recipes (nothing registered yet)",
            PACKS.size(), blocks, items, recipes);
    }
}
