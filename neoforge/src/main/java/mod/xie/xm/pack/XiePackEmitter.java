package mod.xie.xm.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ResourceNames;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Writes the generated resource pack.
 *
 * <p>This is the half of the port that did not exist in 2013: rather than
 * implementing rendering, drops and naming itself, the engine emits the JSON that
 * vanilla's own systems already consume, and hands it over as a pack.
 *
 * <p>Output goes to disk rather than being served from memory on purpose. A
 * content author who writes a {@code .xie} file and gets a purple-and-black block
 * needs to see what was generated for it, and the underscore prefix keeps the
 * parser from trying to load the output back in as a content pack.
 */
public final class XiePackEmitter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Under {@code <gamedir>/xiecontent/}; the underscore keeps the parser out. */
    public static final String OUTPUT_DIR_NAME = "_generated";

    private static final com.google.gson.Gson GSON =
        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Bushes render as a cross, matching the original's {@code setRenderType(1)}.
     * Crops use {@code minecraft:block/crop} instead, which is render type 6.
     */
    private static final Map<String, String> ARCHETYPE_PARENTS = Map.of(
        "bush", "minecraft:block/cross",
        "plant", "minecraft:block/cross",
        "crop", "minecraft:block/crop"
    );

    private final Path outputRoot;
    private final Path contentRoot;

    public XiePackEmitter(Path contentRoot) {
        this.contentRoot = contentRoot;
        this.outputRoot = contentRoot.resolve(OUTPUT_DIR_NAME);
    }

    public Path outputRoot() {
        return outputRoot;
    }

    /**
     * Regenerates the pack from scratch.
     *
     * @param packs        the loaded content
     * @param blockMaxAges definition name to highest age, for blockstate variants
     * @param translations translation key to display name
     * @param packFormat   the pack format the running game expects
     */
    public void emit(List<ContentPack> packs,
                     Map<String, Integer> blockMaxAges,
                     Map<String, String> translations,
                     int packFormatMajor,
                     int packFormatMinor) throws IOException {

        deleteRecursively(outputRoot);
        Files.createDirectories(outputRoot);

        writePackMeta(packFormatMajor, packFormatMinor);
        writeLanguage(translations);

        int blockModels = 0;
        int itemModels = 0;
        int textures = 0;

        for (ContentPack pack : packs) {
            Path packRoot = contentRoot.resolve(pack.name());

            for (BlockDef def : pack.blocks().values()) {
                Integer maxAge = blockMaxAges.get(def.name());
                if (maxAge == null) {
                    continue;
                }
                textures += copyBlockTextures(packRoot, def, maxAge);
                writeBlockState(def, maxAge);
                blockModels += writeBlockModels(def, maxAge);
            }

            for (ItemDef def : pack.items().values()) {
                if (!translations.containsKey("item." + XiesMod.MOD_ID + "." + ResourceNames.toPath(def.name()))) {
                    continue;
                }
                textures += copyItemTexture(packRoot, def);
                writeItemModel(def);
                itemModels++;
            }
        }

        LOGGER.info("Emitted pack to {}: {} block models, {} item models, {} textures",
            outputRoot, blockModels, itemModels, textures);
    }

    // ------------------------------------------------------------- pack meta

    private void writePackMeta(int major, int minor) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Xie's Mod generated content");

        // "pack_format" is Codec.INT — a bare major version, never a list. From
        // format 65 onwards it is not sufficient on its own: minor versions exist,
        // so "min_format" and "max_format" become mandatory. Those two do take the
        // [major, minor] list form.
        //
        // The range declared is 84.0 up to whatever the running game is, which is
        // honest: the pack was generated against this exact version.
        pack.addProperty("pack_format", major);
        pack.add("min_format", packFormat(major, 0));
        pack.add("max_format", packFormat(major, minor));

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        write(outputRoot.resolve("pack.mcmeta"), root);
    }

    private static JsonArray packFormat(int major, int minor) {
        JsonArray version = new JsonArray();
        version.add(major);
        version.add(minor);
        return version;
    }

    private void writeLanguage(Map<String, String> translations) throws IOException {
        JsonObject lang = new JsonObject();
        translations.forEach(lang::addProperty);
        write(assets().resolve("lang").resolve("en_us.json"), lang);
    }

    // ---------------------------------------------------------------- blocks

    private void writeBlockState(BlockDef def, int maxAge) throws IOException {
        String path = ResourceNames.toPath(def.name());
        JsonObject variants = new JsonObject();
        for (int age = 0; age <= maxAge; age++) {
            JsonObject variant = new JsonObject();
            variant.addProperty("model", XiesMod.MOD_ID + ":block/" + path + "_stage" + age);
            variants.add("age=" + age, variant);
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        write(assets().resolve("blockstates").resolve(path + ".json"), root);
    }

    private int writeBlockModels(BlockDef def, int maxAge) throws IOException {
        String path = ResourceNames.toPath(def.name());
        String parent = ARCHETYPE_PARENTS.getOrDefault(def.archetype(), "minecraft:block/cross");
        String textureKey = parent.endsWith("/crop") ? "crop" : "cross";

        for (int age = 0; age <= maxAge; age++) {
            JsonObject textures = new JsonObject();
            textures.addProperty(textureKey, XiesMod.MOD_ID + ":block/" + path + "_stage" + age);

            JsonObject model = new JsonObject();
            model.addProperty("parent", parent);
            model.add("textures", textures);
            write(assets().resolve("models").resolve("block").resolve(path + "_stage" + age + ".json"), model);
        }
        return maxAge + 1;
    }

    /**
     * Copies one PNG per growth stage.
     *
     * <p>Content textures are already one file per sprite, named after the
     * definition with an optional {@code _<meta>} suffix — the 1.5 migration did
     * that work years ago. A stage with no texture of its own reuses the closest
     * earlier one, which is how content that draws four stages across six ages
     * was always meant to look.
     */
    private int copyBlockTextures(Path packRoot, BlockDef def, int maxAge) throws IOException {
        Path source = packRoot.resolve("textures").resolve("blocks");
        String path = ResourceNames.toPath(def.name());
        Path target = assets().resolve("textures").resolve("block");

        int copied = 0;
        Path lastFound = null;
        for (int age = 0; age <= maxAge; age++) {
            Path candidate = findTexture(source, def.name() + "_" + age)
                .or(() -> findTexture(source, def.name()))
                .orElse(lastFound);
            if (candidate == null) {
                continue;
            }
            lastFound = candidate;
            Files.createDirectories(target);
            Files.copy(candidate, target.resolve(path + "_stage" + age + ".png"),
                StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }
        return copied;
    }

    // ----------------------------------------------------------------- items

    private void writeItemModel(ItemDef def) throws IOException {
        String path = ResourceNames.toPath(def.name());

        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", XiesMod.MOD_ID + ":item/" + path);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", textures);
        write(assets().resolve("models").resolve("item").resolve(path + ".json"), model);

        // Since 1.21.4 an item also needs a model *definition* under items/,
        // which is the indirection that lets a single item swap models by state.
        JsonObject reference = new JsonObject();
        reference.addProperty("type", "minecraft:model");
        reference.addProperty("model", XiesMod.MOD_ID + ":item/" + path);
        JsonObject definition = new JsonObject();
        definition.add("model", reference);
        write(assets().resolve("items").resolve(path + ".json"), definition);
    }

    private int copyItemTexture(Path packRoot, ItemDef def) throws IOException {
        Path items = packRoot.resolve("textures").resolve("items");
        Path blocks = packRoot.resolve("textures").resolve("blocks");

        Optional<Path> found = findTexture(items, def.name());
        if (found.isEmpty()) {
            // Items declaring "names" are one legacy item with several metadata
            // subtypes, so they ship numbered sprites and no bare one. Until those
            // are split into separate items, the first subtype stands in.
            found = findTexture(items, def.name() + "_0");
        }
        if (found.isEmpty()) {
            // A few textures are filed under blocks/ by mistake; look there too.
            found = findTexture(blocks, def.name());
        }
        if (found.isEmpty()) {
            return 0;
        }
        Path target = assets().resolve("textures").resolve("item");
        Files.createDirectories(target);
        Files.copy(found.get(), target.resolve(ResourceNames.toPath(def.name()) + ".png"),
            StandardCopyOption.REPLACE_EXISTING);
        return 1;
    }

    // ----------------------------------------------------------------- plumbing

    /** Case-insensitive lookup: content names are camelCase, filesystems vary. */
    private Optional<Path> findTexture(Path directory, String baseName) {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        String wanted = (baseName + ".png").toLowerCase(Locale.ROOT);
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Path assets() {
        return outputRoot.resolve("assets").resolve(XiesMod.MOD_ID);
    }

    private void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> entries;
        try (Stream<Path> walk = Files.walk(root)) {
            entries = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
        }
        for (Path entry : entries) {
            Files.deleteIfExists(entry);
        }
    }
}
