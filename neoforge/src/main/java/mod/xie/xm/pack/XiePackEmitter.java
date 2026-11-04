package mod.xie.xm.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.Bounds;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.LegacyTerrain;
import mod.xie.xm.content.model.MetaMap;
import mod.xie.xm.content.model.RenderStyle;
import mod.xie.xm.content.model.ResourceNames;
import mod.xie.xm.content.model.TexDef;
import mod.xie.xm.content.model.Variants;
import mod.xie.xm.reg.RegisteredContent;
import mod.xie.xm.reg.XieUnreadProperties;

import org.slf4j.Logger;

import javax.imageio.ImageIO;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Writes the generated resource pack.
 *
 * <p>This is the half of the port that did not exist in 2013: rather than
 * implementing rendering, drops and naming itself, the engine emits the JSON that
 * vanilla's own systems already consume, and hands it over as a pack.
 *
 * <p>Output goes to disk rather than being served from memory on purpose. A
 * content author who writes a definition file and gets a purple-and-black block
 * needs to see what was generated for it, and the underscore prefix keeps the
 * parser from trying to load the output back in as a content pack.
 *
 * <p>One directory serves as both resource pack and datapack. This half writes
 * {@code assets/}; {@link XieDataEmitter} writes {@code data/}.
 */
public final class XiePackEmitter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Under {@code <gamedir>/xiecontent/}; the underscore keeps the parser out. */
    public static final String OUTPUT_DIR_NAME = "_generated";

    /** The two archetypes whose blockstate is not the general "one model per age". */
    private static final String LOG = "log";
    private static final String STEM = "stem";

    /** The mod's logo, copied into each generated pack as the pack screens' icon. */
    private static final String PACK_ICON = "/logo.png";

    /**
     * A second pack root inside the first, holding only the files that replace a
     * vanilla id rather than adding one of this mod's.
     *
     * <p>It exists because the two need opposite priorities. The content pack is
     * registered at the bottom, below vanilla, so that a user pack can override it;
     * an override has to sit at the top or it cannot do the one thing it is for.
     * Keeping them apart is also the readable arrangement — everything in here is,
     * by construction, something a definition file took away from vanilla.
     *
     * <p>{@code PathPackResources} reads only {@code assets/}, {@code data/} and
     * {@code pack.mcmeta}, so this directory is invisible to the pack that contains
     * it. See {@link XiePackProvider}.
     */
    public static final String OVERRIDE_DIR_NAME = "_overrides";

    /**
     * A third pack root, holding what the {@code spawns} sections put into the world:
     * placed features, biome modifiers and the features only they use. It is separate
     * so that a world can leave it out, for anyone who would rather not see new
     * chunks change, while the tree features a sapling grows stay in the content pack,
     * where nothing switches them off.
     */
    public static final String WORLDGEN_DIR_NAME = "_worldgen";

    /** The vanilla parent model each {@link RenderStyle} is drawn with. */
    private static final Map<RenderStyle, String> STYLE_PARENTS = Map.of(
        RenderStyle.CROSS, "minecraft:block/cross",
        RenderStyle.CROP, "minecraft:block/crop",
        RenderStyle.CUBE, "minecraft:block/cube"
    );

    /** The texture key each parent expects for its single sprite. */
    private static final Map<RenderStyle, String> STYLE_TEXTURE_KEYS = Map.of(
        RenderStyle.CROSS, "cross",
        RenderStyle.CROP, "crop"
    );

    /** The faces of {@code minecraft:block/cube}, in the order it declares them. */
    private static final List<String> CUBE_FACES =
        List.of("down", "up", "north", "south", "east", "west");

    /**
     * The face names a texture file may be suffixed with, from the original's
     * {@code BlockSide} enum, mapped onto the faces of {@code minecraft:block/cube}.
     *
     * <p>Iteration order is deliberate: the broad names come first so that a
     * definition shipping both {@code _sides} and {@code _front} gets the specific
     * one where they overlap.
     */
    private static final Map<String, List<String>> FACE_ALIASES = new LinkedHashMap<>();

    static {
        FACE_ALIASES.put("all", CUBE_FACES);
        FACE_ALIASES.put("ends", List.of("down", "up"));
        FACE_ALIASES.put("sides", List.of("north", "south", "east", "west"));
        FACE_ALIASES.put("bottom", List.of("down"));
        FACE_ALIASES.put("top", List.of("up"));
        FACE_ALIASES.put("front", List.of("north"));
        FACE_ALIASES.put("north", List.of("north"));
        FACE_ALIASES.put("back", List.of("south"));
        FACE_ALIASES.put("south", List.of("south"));
        FACE_ALIASES.put("left", List.of("east"));
        FACE_ALIASES.put("east", List.of("east"));
        FACE_ALIASES.put("right", List.of("west"));
        FACE_ALIASES.put("west", List.of("west"));
    }

    /**
     * The pack formats the running game expects. Resource packs and datapacks are
     * versioned separately — 84.0 and 101.1 in 26.1.2 — and one {@code pack.mcmeta}
     * has to satisfy both readers, so the declared range spans them.
     */
    public record PackFormats(int resourceMajor, int resourceMinor, int dataMajor, int dataMinor) { }

    private final Path outputRoot;
    private final Path overrideRoot;
    private final Path worldgenRoot;
    private final Path contentRoot;
    private final XieDataEmitter dataEmitter;

    public XiePackEmitter(Path contentRoot,
                          XieDataEmitter.ItemLookup itemLookup,
                          XieDataEmitter.BlockLookup blockLookup) {
        this.contentRoot = contentRoot;
        this.outputRoot = contentRoot.resolve(OUTPUT_DIR_NAME);
        this.overrideRoot = outputRoot.resolve(OVERRIDE_DIR_NAME);
        this.worldgenRoot = outputRoot.resolve(WORLDGEN_DIR_NAME);
        this.dataEmitter = new XieDataEmitter(outputRoot, overrideRoot, worldgenRoot,
            itemLookup, blockLookup);
    }

    public Path outputRoot() {
        return outputRoot;
    }

    /** The worldgen pack root, which like the override pack exists even when empty. */
    public Path worldgenRoot() {
        return worldgenRoot;
    }

    /**
     * The override pack root, which exists whether or not anything overrides
     * something.
     *
     * <p>An empty one is the usual answer, since none of the live content declares
     * {@code override} — and it is registered anyway. A pack that comes and goes with
     * the content leaves "Missing data pack" in the log of every world that once had
     * it, which reads like a fault and is not one; an inert pack that always exists
     * says the same thing more quietly, and its emptiness is itself the report that
     * nothing was taken from vanilla.
     */
    public Path overrideRoot() {
        return overrideRoot;
    }

    /**
     * Regenerates the pack from scratch.
     *
     * @param packs      the loaded content
     * @param registered what the registrar actually registered
     * @param formats    the pack formats the running game expects
     */
    public void emit(List<ContentPack> packs,
                     RegisteredContent registered,
                     PackFormats formats) throws IOException {

        deleteRecursively(outputRoot);
        Files.createDirectories(outputRoot);

        writePackMeta(formats);
        writeLanguage(registered.translations());

        int blockModels = 0;
        int itemModels = 0;
        int textures = 0;
        int borrowed = 0;

        for (ContentPack pack : packs) {
            Path packRoot = contentRoot.resolve(pack.name());

            for (BlockDef def : pack.blocks().values()) {
                List<String> paths = registered.blockPaths().get(def.name());
                if (paths == null) {
                    continue;
                }
                List<String> names = Variants.namesIn(def.props());
                for (int meta = 0; meta < paths.size(); meta++) {
                    String path = paths.get(meta);
                    Stages stages = new Stages(path, registered.hasAge(path),
                        registered.maxAges().getOrDefault(path, 0));

                    if (def.archetype().equals(STEM)) {
                        // A stem draws itself with vanilla's own melon stem, which is
                        // what its "tex": "#melonstem" asks for and the only drawing
                        // there is: the pack ships no stem sprite. See writeStem.
                        writeStem(stages);
                        continue;
                    }

                    Sprites sprites = copyBlockTextures(packRoot, def, names, meta, stages);
                    textures += sprites.copied();
                    borrowed += sprites.borrowed().size();
                    writeBlockState(stages, def);
                    blockModels += writeBlockModels(pack, def, stages, sprites);

                    if (registered.blockItems().contains(path)) {
                        writeBlockItemModel(path, def, stages, sprites);
                        itemModels++;
                    }
                }
            }

            for (ItemDef def : pack.items().values()) {
                List<String> paths = registered.itemPaths().get(def.name());
                if (paths == null) {
                    continue;
                }
                List<String> names = Variants.namesIn(def.props());
                for (int meta = 0; meta < paths.size(); meta++) {
                    String path = paths.get(meta);
                    textures += copyItemTexture(packRoot, def, names, meta, path);
                    writeItemModel(path, XiesMod.MOD_ID + ":item/" + path);
                    itemModels++;
                }
            }
        }

        XieDataEmitter.Counts data = dataEmitter.emit(packs, registered);
        writeDataPackMeta(overrideRoot, "Xie's Mod vanilla overrides", formats);
        writeDataPackMeta(worldgenRoot, "What Xie's Mod content spawns in new chunks", formats);

        LOGGER.info("Emitted pack to {}: {} block models, {} item models, {} textures, "
                + "{} loot tables, {} recipes, {} recipe advancements, {} alias tags, "
                + "{} block tags, {} worldgen features, {} trees, {} burn times, "
                + "{} vanilla files overridden",
            outputRoot, blockModels, itemModels, textures,
            data.lootTables(), data.recipes(), data.advancements(), data.tags(), data.blockTags(),
            data.features(), data.trees(), data.fuels(), data.overrides());

        if (borrowed > 0) {
            // Not counted among the textures above, because no file was written: the
            // model points at Minecraft's own sprite. See borrowedSprite.
            LOGGER.info("Borrowed a vanilla texture for {} models, which is what a sprite "
                + "index with no texFile asks for", borrowed);
        }

        if (data.skippedDrops() > 0 || data.skippedRecipes() > 0
            || data.skippedSpawns() > 0 || data.skippedCulls() > 0) {
            LOGGER.info("Not emitted: {} drops, {} recipes, {} worldgen entries and {} culls name "
                    + "content this build cannot register yet",
                data.skippedDrops(), data.skippedRecipes(), data.skippedSpawns(),
                data.skippedCulls());
        }

        // Last, because it reports what nobody read and everything that reads a
        // definition has now run: the registrar to build the block or item, and these
        // emitters to draw and describe it.
        XieUnreadProperties.report(packs);
    }

    /**
     * The items the content's {@code cull} entries named, once {@link #emit} has run.
     *
     * <p>The one thing a definition file asks for that no generated file can carry:
     * it removes recipes rather than adding any, so it leaves the pack by this route
     * instead. See {@link mod.xie.xm.recipe.XieRecipeCuller}.
     */
    public Set<String> culledItems() {
        return dataEmitter.culledItems();
    }

    // ------------------------------------------------------------- pack meta

    private void writePackMeta(PackFormats formats) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Xie's Mod generated content");

        // "pack_format" is Codec.INT — a bare major version, never a list. From
        // format 65 onwards it is not sufficient on its own: minor versions exist,
        // so "min_format" and "max_format" become mandatory. Those two do take the
        // [major, minor] list form.
        //
        // The same file is read once as a resource pack and once as a datapack, and
        // those are numbered independently — 84.0 against 101.1 in 26.1.2. Only the
        // range has to contain the running game's format for the pack to count as
        // compatible, and "pack_format" only has to fall inside the range, so
        // spanning both satisfies both readers. The pack is regenerated on every
        // launch, so the breadth of the range never gets a chance to mislead.
        pack.addProperty("pack_format", formats.resourceMajor());
        pack.add("min_format", packFormat(formats.resourceMajor(), 0));
        pack.add("max_format", packFormat(formats.dataMajor(), formats.dataMinor()));

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        write(outputRoot.resolve("pack.mcmeta"), root);
        writePackIcon(outputRoot);
    }

    /**
     * Metadata for the override and worldgen packs. Datapack formats only — neither
     * holds {@code assets/}: an override needs only to outrank vanilla, and nothing
     * on the resource side has asked for that, while worldgen is data by nature.
     */
    private void writeDataPackMeta(Path root, String description, PackFormats formats)
            throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        pack.addProperty("pack_format", formats.dataMajor());
        pack.add("min_format", packFormat(formats.dataMajor(), 0));
        pack.add("max_format", packFormat(formats.dataMajor(), formats.dataMinor()));

        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        write(root.resolve("pack.mcmeta"), meta);
        writePackIcon(root);
    }

    private static void writePackIcon(Path root) throws IOException {
        try (InputStream in = XiePackEmitter.class.getResourceAsStream(PACK_ICON)) {
            if (in != null) {
                Files.copy(in, root.resolve("pack.png"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
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

    /**
     * How many models a block needs and what to call them.
     *
     * <p>A growing block has one per age and its blockstate selects between them on
     * the {@code age} property. A block with no age has exactly one, named after the
     * block itself — a {@code salt_deposit_stage0} would be a lie about a block that
     * has no stages.
     */
    private record Stages(String path, boolean aged, int maxAge) {

        int count() {
            return aged ? maxAge + 1 : 1;
        }

        String modelName(int stage) {
            return aged ? path + "_stage" + stage : path;
        }
    }

    /**
     * The sprites drawn for one block: the files copied into the pack, and the vanilla
     * textures borrowed instead of copied.
     *
     * @param copied   how many files were written, for the summary line
     * @param faces    cube face name ({@code up}, {@code north}, …) to texture id, for
     *                 a block that ships face-specific sprites
     * @param borrowed model name to vanilla texture id, for a block whose drawing is
     *                 vanilla's own — see {@link LegacyTerrain}. Keyed by model rather
     *                 than by block because a growing block has one per stage.
     */
    private record Sprites(int copied, Map<String, String> faces, Map<String, String> borrowed) {

        static final Sprites NONE = new Sprites(0, Map.of(), Map.of());

        /**
         * The texture a model draws where no face-specific sprite says otherwise: the
         * borrowed one if there is one, and otherwise the sprite written under the
         * model's own name.
         */
        String drawing(String modelName) {
            return borrowed.getOrDefault(modelName, XiesMod.MOD_ID + ":block/" + modelName);
        }
    }

    /**
     * The two blockstate files a {@code stem} needs, both pointing straight at
     * vanilla's melon stem models.
     *
     * <p>Nothing is generated for it because there is nothing to generate from.
     * {@code zombiePigmelonStem} writes {@code "tex": "#melonstem"} and ships no
     * sprite of its own, and a stem is not a shape a texture can be pasted onto — it
     * is eight hand-built models of crossed quads that lengthen as the plant grows,
     * plus a ninth that bends towards the fruit. Vanilla has all nine, and a stem with
     * no drawing of its own is exactly what they are for.
     *
     * <p>Their green comes from a tint rather than the sprite, which is why
     * {@link mod.xie.xm.client.XieBlockColors} exists: pointed at untinted, these
     * models draw a grey stem.
     */
    private void writeStem(Stages stages) throws IOException {
        JsonObject variants = new JsonObject();
        for (int age = 0; age <= stages.maxAge(); age++) {
            JsonObject variant = new JsonObject();
            variant.addProperty("model", "minecraft:block/melon_stem_stage" + age);
            variants.add("age=" + age, variant);
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        write(assets().resolve("blockstates").resolve(stages.path() + ".json"), root);

        // The bent half, whose four variants are one model turned about y — vanilla's
        // own attached_melon_stem blockstate, rotations included.
        JsonObject attached = new JsonObject();
        for (Map.Entry<String, Integer> facing : ATTACHED_STEM_ROTATIONS.entrySet()) {
            JsonObject variant = new JsonObject();
            variant.addProperty("model", "minecraft:block/attached_melon_stem");
            if (facing.getValue() != 0) {
                variant.addProperty("y", facing.getValue());
            }
            attached.add("facing=" + facing.getKey(), variant);
        }
        JsonObject attachedRoot = new JsonObject();
        attachedRoot.add("variants", attached);
        write(assets().resolve("blockstates")
            .resolve(RegisteredContent.attachedStemPath(stages.path()) + ".json"), attachedRoot);
    }

    /** {@code attached_melon_stem.json}'s own rotations, west being the model as drawn. */
    private static final Map<String, Integer> ATTACHED_STEM_ROTATIONS =
        new LinkedHashMap<>(Map.of("north", 90, "south", 270, "east", 180, "west", 0));

    /**
     * A log's three axes, from vanilla's own {@code oak_log} blockstate. The model is
     * drawn upright and turned onto its side for the other two, which puts the two
     * {@code ends} faces on the ends of the trunk whichever way it runs.
     */
    private static final Map<String, int[]> LOG_AXIS_ROTATIONS =
        new LinkedHashMap<>(Map.of("y", new int[] {0, 0},
                                   "x", new int[] {90, 90},
                                   "z", new int[] {90, 0}));

    private void writeBlockState(Stages stages, BlockDef def) throws IOException {
        if (def.archetype().equals(STOVE)) {
            writeStoveBlockState(stages);
            return;
        }
        if (def.archetype().equals(LOG)) {
            JsonObject variants = new JsonObject();
            for (Map.Entry<String, int[]> axis : LOG_AXIS_ROTATIONS.entrySet()) {
                JsonObject variant = new JsonObject();
                variant.addProperty("model", XiesMod.MOD_ID + ":block/" + stages.modelName(0));
                if (axis.getValue()[0] != 0) {
                    variant.addProperty("x", axis.getValue()[0]);
                }
                if (axis.getValue()[1] != 0) {
                    variant.addProperty("y", axis.getValue()[1]);
                }
                variants.add("axis=" + axis.getKey(), variant);
            }
            JsonObject root = new JsonObject();
            root.add("variants", variants);
            write(assets().resolve("blockstates").resolve(stages.path() + ".json"), root);
            return;
        }
        writeBlockState(stages);
    }

    private void writeBlockState(Stages stages) throws IOException {
        JsonObject variants = new JsonObject();
        if (stages.aged()) {
            for (int age = 0; age <= stages.maxAge(); age++) {
                JsonObject variant = new JsonObject();
                variant.addProperty("model", XiesMod.MOD_ID + ":block/" + stages.modelName(age));
                variants.add("age=" + age, variant);
            }
        } else {
            // The empty key is vanilla's "this block has one state"; naming a
            // property the block does not have would fail to load.
            JsonObject variant = new JsonObject();
            variant.addProperty("model", XiesMod.MOD_ID + ":block/" + stages.modelName(0));
            variants.add("", variant);
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        write(assets().resolve("blockstates").resolve(stages.path() + ".json"), root);
    }

    private int writeBlockModels(ContentPack pack, BlockDef def,
                                 Stages stages, Sprites sprites) throws IOException {
        if (def.archetype().equals(STOVE)) {
            return writeStoveModels(stages);
        }
        RenderStyle style = RenderStyle.forBlock(def);
        String parent = STYLE_PARENTS.get(style);
        Optional<Bounds> box = style.isCube() ? partialCube(def) : Optional.empty();

        for (int stage = 0; stage < stages.count(); stage++) {
            String name = stages.modelName(stage);

            JsonObject model = new JsonObject();
            if (box.isPresent()) {
                model.add("elements", oneElement(cubeElement(box.get())));
            } else {
                model.addProperty("parent", parent);
            }
            model.add("textures", style.isCube()
                ? cubeTextures(pack, def, name, sprites)
                : single(STYLE_TEXTURE_KEYS.get(style), sprites.drawing(name)));
            write(assets().resolve("models").resolve("block").resolve(name + ".json"), model);
        }
        return stages.count();
    }

    /**
     * A cube-shaped block's {@code blockBounds} when they do not fill the cube.
     *
     * <p>1.6's standard block renderer drew the block's {@code AxisAlignedBB}, so
     * {@code blockBounds} was its shape on screen as much as its collision box —
     * {@code blazeLog} is a four-pixel trunk and looked like one. {@code block/cube}
     * cannot say that, so the model is written out with an element of its own instead.
     *
     * <p>Only the cube styles are asked. A cross or a crop is two or four flat quads
     * whatever box it declares, which is what vanilla's own wheat does with a shape
     * shorter than its model.
     */
    private static Optional<Bounds> partialCube(BlockDef def) {
        return def.bounds().flatMap(MetaMap::first).filter(box -> !box.isFullBlock());
    }

    /**
     * One box, with a texture per face and a cull only where the face is flush with
     * the block's own edge.
     *
     * <p>UVs are left out on purpose: a model with none takes them from where the
     * element sits, which is what a renderer drawing an AABB directly did.
     */
    private static JsonObject cubeElement(Bounds box) {
        float[] px = box.toPixels();

        JsonObject faces = new JsonObject();
        for (String face : CUBE_FACES) {
            JsonObject entry = new JsonObject();
            entry.addProperty("texture", "#" + face);
            if (isFlush(px, face)) {
                entry.addProperty("cullface", face);
            }
            faces.add(face, entry);
        }

        JsonObject element = new JsonObject();
        element.add("from", pixels(px[0], px[1], px[2]));
        element.add("to", pixels(px[3], px[4], px[5]));
        element.add("faces", faces);
        return element;
    }

    /** Whether a face of the box lies on the boundary of its block, so it may cull. */
    private static boolean isFlush(float[] px, String face) {
        return switch (face) {
            case "down" -> px[1] <= 0f;
            case "up" -> px[4] >= 16f;
            case "north" -> px[2] <= 0f;
            case "south" -> px[5] >= 16f;
            case "west" -> px[0] <= 0f;
            case "east" -> px[3] >= 16f;
            default -> false;
        };
    }

    /** A one-entry {@code elements} array, which is all a single box needs. */
    private static JsonArray oneElement(JsonObject element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    private static JsonArray pixels(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    /**
     * The six faces of a cube model.
     *
     * <p>{@code textureBase} is the interesting part: it names a block whose texture
     * fills in every face this definition does not draw itself, which is how
     * {@code saltBlock} ships no PNG at all and draws with {@code saltDeposit}'s.
     * In 1.6 that was {@code XieTex.setTextureBase}, a fallback consulted
     * whenever a sprite lookup came up empty; here it is the same idea resolved once,
     * at emit time, into the face entries of a model.
     */
    private JsonObject cubeTextures(ContentPack pack, BlockDef def, String name, Sprites sprites) {
        Optional<String> base = def.textureBase().map(baseName -> resolveTexture(pack, baseName));
        String own = sprites.drawing(name);

        JsonObject textures = new JsonObject();
        for (String face : CUBE_FACES) {
            String texture = sprites.faces().get(face);
            if (texture == null) {
                texture = base.orElse(own);
            }
            textures.addProperty(face, texture);
        }
        // Break particles and the inventory tint sample from this one.
        textures.addProperty("particle", textures.get("up").getAsString());
        return textures;
    }

    /**
     * A {@code textureBase} name as a texture id. Block texture names track block
     * ids closely enough in modern Minecraft that the id table does the job, and
     * {@code sand} — the only one live content uses — is one of them.
     */
    private String resolveTexture(ContentPack pack, String baseName) {
        if (pack.blocks().containsKey(baseName)) {
            return XiesMod.MOD_ID + ":block/" + ResourceNames.toPath(baseName);
        }
        return "minecraft:block/" + ResourceNames.toVanillaPath(ItemRef.parse(baseName));
    }

    /**
     * Copies this block's PNGs into the pack.
     *
     * <p>Content textures are already one file per sprite, named after the
     * definition with an optional suffix — the 1.5 migration did that work years
     * ago. The suffix is either a metadata value or a face name, and metadata meant
     * growth stage for most blocks but subtype for a block declaring {@code names}:
     *
     * <ul>
     *   <li>a growing block takes one sprite per stage, {@code cornCrop_0} through
     *       {@code cornCrop_6}, and a stage with no sprite of its own reuses the
     *       closest earlier one — which is how content that draws four stages across
     *       six ages was always meant to look;</li>
     *   <li>a split block has one sprite, the one for its own subtype, repeated
     *       across whatever stages it has;</li>
     *   <li>anything else takes a bare sprite and any face-suffixed ones, so a
     *       {@code saltDeposit_top} would become the top face and the rest of the
     *       cube fall through to {@code textureBase}.</li>
     * </ul>
     *
     * <p>A pack that never migrated has none of those files and addresses a
     * spritesheet by index instead, exactly as its items do. Every lookup below
     * therefore falls back to cutting the sprite out of the sheet — see
     * {@link #sliceSprite}. Live content is untouched by that, because it ships the
     * files and a file always wins.
     */
    private Sprites copyBlockTextures(Path packRoot, BlockDef def, List<String> names,
                                      int meta, Stages stages) throws IOException {
        Path source = packRoot.resolve("textures").resolve("blocks");
        Path target = assets().resolve("textures").resolve("block");
        boolean split = !names.isEmpty();

        if (def.archetype().equals(STOVE)) {
            return copyStoveTextures(source, target, def, stages);
        }

        // Asked for here rather than down in the slicer, because this is the point at
        // which the definition's texture assignment is consulted whether or not the
        // indices turn out to be needed — a migrated pack ships the files and they are
        // only the fallback. A block whose sprites are never copied at all is a stem,
        // which draws with vanilla's models and never reaches this method.
        TexDef tex = def.tex();

        if (stages.aged()) {
            return copyStageTextures(packRoot, source, target, def, tex, split, meta, stages);
        }

        int copied = 0;
        Map<String, String> faces = new LinkedHashMap<>();
        Map<String, String> borrowed = new LinkedHashMap<>();

        Optional<Path> bare = split
            ? findSprite(source, def.name(), meta).or(() -> findTexture(source, def.name()))
            : findTexture(source, def.name());
        // "all" and a bare file say the same thing — one drawing for the whole block —
        // so the sheet's "all" is written under the bare name. A cross or crop model
        // asks for exactly that name, and cubeTextures falls back to it.
        boolean drawn = bare.isPresent()
            ? copy(bare.get(), target, stages.path() + ".png")
            : sliceSprite(packRoot, def, tex, TexDef.FACE_ALL, split ? meta : 0, target,
                stages.path());
        if (drawn) {
            copied++;
            CUBE_FACES.forEach(face -> faces.put(face, XiesMod.MOD_ID + ":block/" + stages.path()));
        } else {
            Optional<String> vanilla =
                borrowedSprite(def, tex, TexDef.FACE_ALL, split ? meta : 0);
            vanilla.ifPresent(texture -> {
                borrowed.put(stages.path(), texture);
                CUBE_FACES.forEach(face -> faces.put(face, texture));
            });
        }

        for (Map.Entry<String, List<String>> alias : FACE_ALIASES.entrySet()) {
            if (alias.getKey().equals(TexDef.FACE_ALL)) {
                continue;
            }
            String spriteName = stages.path() + "_" + alias.getKey();
            Optional<Path> found = findTexture(source, def.name() + "_" + alias.getKey());
            boolean written = found.isPresent()
                ? copy(found.get(), target, spriteName + ".png")
                : sliceSprite(packRoot, def, tex, alias.getKey(), split ? meta : 0, target,
                    spriteName);
            if (!written) {
                // A face can borrow on its own, and only that face's entries move: the
                // block still draws its own sprite, or its textureBase, everywhere else.
                Optional<String> vanilla =
                    borrowedSprite(def, tex, alias.getKey(), split ? meta : 0);
                vanilla.ifPresent(texture -> alias.getValue().forEach(face -> faces.put(face, texture)));
                continue;
            }
            copied++;
            alias.getValue().forEach(face -> faces.put(face, XiesMod.MOD_ID + ":block/" + spriteName));
        }

        return copied == 0 && faces.isEmpty()
            ? Sprites.NONE
            : new Sprites(copied, faces, borrowed);
    }

    /** One sprite per growth stage, named {@code <path>_stage<n>.png}. */
    private Sprites copyStageTextures(Path packRoot, Path source, Path target, BlockDef def,
                                      TexDef tex, boolean split, int meta, Stages stages)
                                      throws IOException {
        int copied = 0;
        Path lastFound = null;
        Map<String, String> borrowed = new LinkedHashMap<>();
        for (int age = 0; age <= stages.maxAge(); age++) {
            int sprite = split ? meta : age;
            // A split block that also ages — a fruit tree's leaves — draws one sprite
            // per subtype per stage, fruitLeaves_0_1 for the apple tree's ripe leaf.
            // A stage with no sprite of its own is the subtype's bare one.
            Optional<Path> staged = split && age > 0
                ? findSprite(source, def.name() + "_" + meta, age)
                : Optional.empty();
            Path candidate = staged
                .or(() -> findSprite(source, def.name(), sprite))
                .or(() -> findTexture(source, def.name()))
                .orElse(lastFound);
            if (candidate == null) {
                // No file for this stage, and possibly none for any: a sheet index is
                // the other way a growing block says what it looks like, and an index
                // with no sheet is the third — vanilla's own drawing.
                if (sliceSprite(packRoot, def, tex, TexDef.FACE_ALL, sprite, target,
                        stages.modelName(age))) {
                    copied++;
                } else {
                    Optional<String> vanilla = borrowedSprite(def, tex, TexDef.FACE_ALL, sprite);
                    if (vanilla.isPresent()) {
                        borrowed.put(stages.modelName(age), vanilla.get());
                    }
                }
                continue;
            }
            lastFound = candidate;
            copy(candidate, target, stages.modelName(age) + ".png");
            copied++;
        }
        return new Sprites(copied, Map.of(), borrowed);
    }

    /**
     * One numbered sprite of a definition.
     *
     * <p>The number is usually written bare — {@code cornCrop_3} — but
     * {@code hybridWheat} zero-pads to two digits, which was equally valid in 2013
     * because the loader matched a filename regex rather than composing a name.
     */
    private Optional<Path> findSprite(Path directory, String baseName, int index) {
        return findTexture(directory, baseName + "_" + index)
            .or(() -> index < 10
                ? findTexture(directory, baseName + "_0" + index)
                : Optional.empty());
    }

    /** @return always true, so a caller can write {@code file ? copy(..) : slice(..)} */
    private boolean copy(Path source, Path targetDirectory, String fileName) throws IOException {
        Files.createDirectories(targetDirectory);
        Files.copy(source, targetDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    // ----------------------------------------------------------------- items

    /**
     * The two files an item needs: a model, and the model <em>definition</em> that
     * points at it.
     *
     * @param texture the sprite the flat model draws, which is an item sprite for an
     *                item and a block sprite for a block's item form
     */
    /**
     * The item form of a block, which has no item sprite of its own to draw.
     *
     * <p>Vanilla answers this two different ways and the split is the block's shape.
     * A sapling's item model is {@code item/generated} over the block sprite, because
     * a cross has no thickness to show in an inventory slot. A melon's is the
     * <em>block</em> model as its parent, so it turns up in the hand and the slot as
     * the cube it is.
     *
     * <p>Taking the flat route for both is what {@code zombiePigmelon} exposed: its
     * {@code tex} gives a {@code sides} and an {@code ends}, so its sprites are
     * {@code zombie_pigmelon_sides.png} and {@code zombie_pigmelon_ends.png} and there
     * is no bare {@code zombie_pigmelon.png} for a flat model to draw — the item model
     * named a sprite that was never written, and the client said so. Only
     * {@code cheeseBlock}, which declares one {@code all} face, happened to work.
     */
    private void writeBlockItemModel(String path, BlockDef def, Stages stages, Sprites sprites)
            throws IOException {
        if (RenderStyle.forBlock(def).isCube()) {
            JsonObject model = new JsonObject();
            model.addProperty("parent", XiesMod.MOD_ID + ":block/" + stages.modelName(0));
            write(assets().resolve("models").resolve("item").resolve(path + ".json"), model);
            writeItemModelReference(path);
            return;
        }
        // The flat form draws the block's sprite rather than its model, so a block whose
        // drawing is vanilla's hands the item that one and not a name nothing wrote.
        writeItemModel(path, sprites.drawing(stages.modelName(0)));
    }

    private void writeItemModel(String path, String texture) throws IOException {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", textures);
        write(assets().resolve("models").resolve("item").resolve(path + ".json"), model);
        writeItemModelReference(path);
    }

    /**
     * Since 1.21.4 an item also needs a model <em>definition</em> under {@code items/},
     * which is the indirection that lets a single item swap models by state.
     */
    private void writeItemModelReference(String path) throws IOException {
        JsonObject reference = new JsonObject();
        reference.addProperty("type", "minecraft:model");
        reference.addProperty("model", XiesMod.MOD_ID + ":item/" + path);
        JsonObject definition = new JsonObject();
        definition.add("model", reference);
        write(assets().resolve("items").resolve(path + ".json"), definition);
    }

    /**
     * Copies the sprite for one item, or for one subtype of a split item.
     *
     * <p>An item declaring {@code names} was one legacy item with a subtype per
     * damage value, and ships one numbered sprite per subtype: {@code fruitSeed_0}
     * through {@code fruitSeed_3}. Each is now its own item, so each takes its own
     * sprite. A split item that ships only a bare sprite — {@code oil} has one
     * drawing for both subtypes — falls back to it rather than going untextured.
     */
    private int copyItemTexture(Path packRoot, ItemDef def, List<String> names,
                                int meta, String path) throws IOException {
        Path items = packRoot.resolve("textures").resolve("items");
        Path blocks = packRoot.resolve("textures").resolve("blocks");

        // See copyBlockTextures: consulted here, needed only where the pack shipped no
        // migrated file. Every registered item passes through this method.
        TexDef tex = def.tex();

        Optional<Path> found = names.isEmpty()
            ? findTexture(items, def.name())
            : findTexture(items, def.name() + "_" + meta).or(() -> findTexture(items, def.name()));
        if (found.isEmpty()) {
            // A few textures are filed under blocks/ by mistake; look there too.
            found = findTexture(blocks, def.name());
        }
        if (found.isEmpty()) {
            return sliceItemSprite(packRoot, def, tex, meta, path);
        }
        Path target = assets().resolve("textures").resolve("item");
        Files.createDirectories(target);
        Files.copy(found.get(), target.resolve(path + ".png"), StandardCopyOption.REPLACE_EXISTING);
        return 1;
    }

    /**
     * How many sprites a 2013 spritesheet holds across, which is how a sprite index
     * turns into a position. {@code Icon} indices were {@code i % 16} across and
     * {@code i / 16} down whatever the sheet's resolution, and every sheet in the
     * content is square, so the tile size follows from the width.
     */
    private static final int SHEET_COLUMNS = 16;

    /**
     * Cuts one sprite out of a spritesheet, for a pack that never migrated.
     *
     * <p>The live content addresses sheet indices <em>and</em> ships one PNG per
     * sprite — the 1.5 migration did that work years ago — so this never runs for it.
     * The packs that predate the migration are the ones that need it: {@code weapons}
     * was written for Minecraft 1.2.5 and ships only {@code img/xieweapons.png} with
     * an {@code icon} per definition, and {@code nether_crops} ships one
     * {@code nether_crops.png} with a {@code sides} and an {@code ends} per block.
     * That is exactly the declaration {@link TexDef} keeps the indices around for.
     *
     * <p>Blocks and items ask the same question of it — {@code Icon} indices were one
     * numbering across one sheet, and 1.6 drew a block face and an inventory icon from
     * the same kind of index — so this is one method with the face name as its only
     * parameter of difference.
     *
     * @param face      the texture key to read: {@code icon} for an item, and a face
     *                  name or {@code all} for a block
     * @param spriteName the name to write it under, without the extension
     * @return whether a sprite was written
     */
    private boolean sliceSprite(Path packRoot, String origin, TexDef tex, String face, int meta,
                                Path target, String spriteName) throws IOException {
        Optional<String> sheetName = tex.sheet();
        Optional<Integer> index = spriteIndex(tex, face, meta);
        if (sheetName.isEmpty() || index.isEmpty()) {
            return false;
        }

        Optional<Path> sheetFile = findSheet(packRoot, sheetName.get());
        if (sheetFile.isEmpty()) {
            LOGGER.warn("{} draws sprite {} of \"{}\", which is not in the pack",
                origin, index.get(), sheetName.get());
            return false;
        }

        BufferedImage sheet = ImageIO.read(sheetFile.get().toFile());
        if (sheet == null) {
            LOGGER.warn("{} names \"{}\" as its spritesheet, which is not readable as an image",
                origin, sheetName.get());
            return false;
        }

        int size = sheet.getWidth() / SHEET_COLUMNS;
        int x = (index.get() % SHEET_COLUMNS) * size;
        int y = (index.get() / SHEET_COLUMNS) * size;
        if (size <= 0 || y + size > sheet.getHeight()) {
            LOGGER.warn("{} draws sprite {} of \"{}\", which is past the end of a {}x{} sheet",
                origin, index.get(), sheetName.get(), sheet.getWidth(), sheet.getHeight());
            return false;
        }

        // Drawn into a fresh image rather than written as a subimage: a subimage
        // shares the sheet's raster, and PNG writers have been known to follow it.
        BufferedImage sprite = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sprite.createGraphics();
        graphics.drawImage(sheet, 0, 0, size, size, x, y, x + size, y + size, null);
        graphics.dispose();

        Files.createDirectories(target);
        ImageIO.write(sprite, "PNG", target.resolve(spriteName + ".png").toFile());
        return true;
    }

    /** {@link #sliceSprite} for one face of a block. */
    private boolean sliceSprite(Path packRoot, BlockDef def, TexDef tex, String face, int meta,
                                Path target, String spriteName) throws IOException {
        return sliceSprite(packRoot, def.qualifiedName(), tex, face, meta, target, spriteName);
    }

    /**
     * The index one face draws, asked for exactly rather than through
     * {@link TexDef#spriteFor}'s all-then-icon fallback: a block declaring only
     * {@code sides} must not have that sprite put on its ends as well. The one
     * fallback kept is {@code icon} for {@code all}, since an item's single index and
     * a block's whole-block one are the same declaration written two ways.
     */
    private static Optional<Integer> spriteIndex(TexDef tex, String face, int meta) {
        return tex.face(face).flatMap(map -> map.get(meta))
            .or(() -> face.equals(TexDef.FACE_ALL)
                ? tex.face(TexDef.FACE_ICON).flatMap(map -> map.get(meta))
                : Optional.empty());
    }

    /**
     * The vanilla texture a face borrows, for an index that belongs to no sheet of the
     * pack's own.
     *
     * <p>A definition that names no {@code texFile} is not one that forgot to. In 1.6
     * {@code SpritesheetManager} answered a definition with no sheet with
     * {@code /terrain.png}, so its indices address the game's own sheet and the sprite
     * it wants is one Minecraft still ships — under a name now rather than a number.
     * {@link LegacyTerrain} is that translation, and it is why {@code blazeTree} is a
     * tree with leaves of fire rather than a tree with no leaves at all.
     *
     * <p>Nothing is written to the pack for one of these: the model points straight at
     * {@code minecraft:block/…}, which keeps the sprite animated where vanilla animates
     * it, and lets a resource pack redraw this mod's block by redrawing the vanilla
     * texture it borrows — both of which a copy taken at emit time would lose.
     *
     * <p>Only a definition with no sheet at all reaches here. One that names a sheet
     * the pack does not ship is reported by {@link #sliceSprite} instead: 1.6 fell back
     * to {@code terrain.png} for that case too, but a warning naming the file somebody
     * meant to ship is worth more than a drawing nobody chose.
     */
    private Optional<String> borrowedSprite(BlockDef def, TexDef tex, String face, int meta) {
        if (tex.sheet().isPresent()) {
            return Optional.empty();
        }
        Optional<Integer> index = spriteIndex(tex, face, meta);
        if (index.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> texture = LegacyTerrain.texture(index.get());
        if (texture.isEmpty()) {
            LOGGER.warn("{} draws sprite {} of vanilla's terrain.png, which LegacyTerrain "
                    + "has no modern texture for",
                def.qualifiedName(), index.get());
            return Optional.empty();
        }
        return texture.map(path -> "minecraft:block/" + path);
    }

    /** {@link #sliceSprite} for an item, whose one sprite is its {@code icon}. */
    private int sliceItemSprite(Path packRoot, ItemDef def, TexDef tex, int meta, String path)
            throws IOException {
        Path target = assets().resolve("textures").resolve("item");
        return sliceSprite(packRoot, def.qualifiedName(), tex, TexDef.FACE_ICON, meta,
            target, path) ? 1 : 0;
    }

    /**
     * A spritesheet by the name a definition gives it, which is a path relative to
     * the pack — {@code img/xieweapons.png} — or a bare filename.
     */
    private Optional<Path> findSheet(Path packRoot, String name) {
        Path declared = packRoot.resolve(name.replace('\\', '/'));
        if (Files.isRegularFile(declared)) {
            return Optional.of(declared);
        }
        String bare = name.substring(name.lastIndexOf('/') + 1);
        return findTexture(packRoot, bare.replaceFirst("(?i)\\.png$", ""))
            .or(() -> findTexture(packRoot.resolve("textures"),
                bare.replaceFirst("(?i)\\.png$", "")));
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

    /** A one-entry {@code textures} object, which is all a cross or crop model needs. */
    private static JsonObject single(String key, String value) {
        JsonObject json = new JsonObject();
        json.addProperty(key, value);
        return json;
    }

    private Path assets() {
        return outputRoot.resolve("assets").resolve(XiesMod.MOD_ID);
    }

    private void write(Path file, JsonObject json) throws IOException {
        PackJson.write(file, json);
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

    // -- stove ----------------------------------------------------------------

    /** The archetype whose drawing is entirely vanilla's, borrowed rather than shipped. */
    private static final String STOVE = "stove";

    /**
     * What fills the pot in one {@code pot} blockstate value.
     *
     * @param sprite  the suffix of the content's own sprite, {@code cookpot_stew.png}
     *                for a block named {@code cookpot}
     * @param vanilla what is drawn when the content ships no such sprite
     */
    private record PotContent(String sprite, String vanilla) {}

    /**
     * The {@code content} texture for each {@code pot} blockstate value.
     *
     * <p>This table <em>is</em> the mod posting's "wait until the pot changes
     * colour". Vanilla's cauldron models take an arbitrary {@code content} sprite, so
     * each state can be its own colour: water goes in clear and comes out as
     * whichever meal the next bowl will be.
     *
     * <p>Index is the {@code pot} value. Index 0 is empty and has no content layer at
     * all, which is why it is null. A pack without the sprites still draws every
     * state, as vanilla water and one mud for all three meals.
     *
     * <p>Each state may also have a {@code _cooking} sprite, {@code cookpot_stew_cooking.png},
     * drawn while {@code cooking} is true. It is optional: without it a cooking pot
     * draws its still sprite, and the steam alone says it is not ready. Either sprite
     * may be animated, with the {@code .png.mcmeta} beside it that vanilla expects.
     *
     * <p>An animated sprite stops when the fire goes out. Vanilla runs an animation
     * for every block that draws it, so a pot cannot pause its own; instead an unlit
     * pot draws a frozen copy of the first frame, cut here from the strip, which is
     * what makes a cold pot look dead rather than merely quiet.
     */
    private static final PotContent[] STOVE_CONTENTS = {
        null,
        new PotContent("water", "minecraft:block/water_still"),
        new PotContent("weakSoup", "minecraft:block/mud"),
        new PotContent("soup", "minecraft:block/mud"),
        new PotContent("stew", "minecraft:block/mud")
    };

    /**
     * Copies whichever pot sprites the content ships, each under its model's name,
     * with its animation if it has one. The rest are left to {@link #writeStoveModels},
     * which draws vanilla's instead.
     */
    private Sprites copyStoveTextures(Path source, Path target, BlockDef def, Stages stages)
            throws IOException {
        int copied = 0;
        for (int pot = 0; pot < STOVE_CONTENTS.length; pot++) {
            if (STOVE_CONTENTS[pot] == null) {
                continue;
            }
            for (boolean cooking : new boolean[] {false, true}) {
                String sprite = def.name() + "_" + STOVE_CONTENTS[pot].sprite() + (cooking ? "_cooking" : "");
                Optional<Path> found = findTexture(source, sprite);
                if (found.isEmpty()) {
                    continue;
                }
                String name = stoveModelName(stages, pot, cooking, false);
                copy(found.get(), target, name + ".png");
                copied++;
                Path animation = found.get().resolveSibling(found.get().getFileName() + ".mcmeta");
                if (Files.isRegularFile(animation)) {
                    copy(animation, target, name + ".png.mcmeta");
                    writeFirstFrame(found.get(), animation, target,
                        stoveModelName(stages, pot, cooking, true) + ".png");
                    copied++;
                }
            }
        }
        return copied == 0 ? Sprites.NONE : new Sprites(copied, Map.of(), Map.of());
    }

    /**
     * The first frame of an animation strip, as a sprite of its own. A frame is a
     * square as wide as the strip's shorter side, unless the {@code .mcmeta} gives
     * {@code width} or {@code height}, when the other one is the whole strip's — as
     * vanilla's {@code AnimationMetadataSection.calculateFrameSize} reads it.
     */
    private static void writeFirstFrame(Path strip, Path animation, Path target, String fileName)
            throws IOException {
        BufferedImage image = ImageIO.read(strip.toFile());
        JsonObject meta = JsonParser.parseString(Files.readString(animation)).getAsJsonObject()
            .getAsJsonObject("animation");
        boolean hasWidth = meta != null && meta.has("width");
        boolean hasHeight = meta != null && meta.has("height");
        int side = Math.min(image.getWidth(), image.getHeight());
        int width = hasWidth ? meta.get("width").getAsInt() : hasHeight ? image.getWidth() : side;
        int height = hasHeight ? meta.get("height").getAsInt() : hasWidth ? image.getHeight() : side;
        Files.createDirectories(target);
        ImageIO.write(image.getSubimage(0, 0, Math.min(width, image.getWidth()),
            Math.min(height, image.getHeight())), "PNG", target.resolve(fileName).toFile());
    }

    /** Whether this pot sprite exists in the pack, which {@link #copyStoveTextures} has decided by now. */
    private boolean hasStoveSprite(Stages stages, int pot, boolean cooking, boolean frozen) {
        return Files.exists(assets().resolve("textures").resolve("block")
            .resolve(stoveModelName(stages, pot, cooking, frozen) + ".png"));
    }

    /**
     * The stove's blockstate: every {@code pot} value against both {@code lit} and
     * both {@code cooking} values.
     *
     * <p>Twenty entries, because a missing variant is a hard load error rather than
     * a fallback. {@code lit} does not change the model — a cauldron has no furnace
     * front to light up, and the fire is particles from
     * {@link mod.xie.xm.block.XieStoveBlock#animateTick} — but it has to be named
     * here because the block carries it. {@code cooking} changes it only where the
     * content drew a cooking sprite; an empty pot is never cooking, but is listed.
     * {@code lit} matters only to an animated sprite, which is frozen while unlit.
     */
    private void writeStoveBlockState(Stages stages) throws IOException {
        JsonObject variants = new JsonObject();

        for (int pot = 0; pot < STOVE_CONTENTS.length; pot++) {
            for (boolean cooking : new boolean[] {false, true}) {
                boolean drawn = cooking && STOVE_CONTENTS[pot] != null
                    && hasStoveSprite(stages, pot, true, false);
                for (boolean lit : new boolean[] {false, true}) {
                    boolean frozen = !lit && hasStoveSprite(stages, pot, drawn, true);
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model",
                        XiesMod.MOD_ID + ":block/" + stoveModelName(stages, pot, drawn, frozen));
                    variants.add("cooking=" + cooking + ",lit=" + lit + ",pot=" + pot, variant);
                }
            }
        }

        JsonObject root = new JsonObject();
        root.add("variants", variants);
        write(assets().resolve("blockstates").resolve(stages.path() + ".json"), root);
    }

    /**
     * One cauldron model per pot state, one more per cooking sprite the content drew,
     * and a frozen one beside each of those that is animated.
     *
     * <p>The pot itself is vanilla's, so only the contents are ever the pack's art.
     * The empty state is vanilla's own {@code block/cauldron}; the filled ones are
     * {@code block/template_cauldron_full} with the content sprite swapped, which is
     * exactly how vanilla builds its water, lava and powder snow cauldrons.
     *
     * <p>The stove was a furnace with an iron top in 2013 because the cauldron did
     * not exist yet. It does now, and a pot you drop ingredients into should look
     * like a pot.
     */
    private int writeStoveModels(Stages stages) throws IOException {
        int written = 0;

        for (int pot = 0; pot < STOVE_CONTENTS.length; pot++) {
            for (boolean cooking : new boolean[] {false, true}) {
                if (cooking && (STOVE_CONTENTS[pot] == null || !hasStoveSprite(stages, pot, true, false))) {
                    continue;
                }
                writeStoveModel(stages, pot, cooking, false);
                written++;
                if (STOVE_CONTENTS[pot] != null && hasStoveSprite(stages, pot, cooking, true)) {
                    writeStoveModel(stages, pot, cooking, true);
                    written++;
                }
            }
        }
        return written;
    }

    private void writeStoveModel(Stages stages, int pot, boolean cooking, boolean frozen)
            throws IOException {
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject();

        String name = stoveModelName(stages, pot, cooking, frozen);
        if (STOVE_CONTENTS[pot] == null) {
            model.addProperty("parent", "minecraft:block/cauldron");
        } else {
            model.addProperty("parent", "minecraft:block/template_cauldron_full");
            textures.addProperty("content", hasStoveSprite(stages, pot, cooking, frozen)
                ? XiesMod.MOD_ID + ":block/" + name
                : STOVE_CONTENTS[pot].vanilla());
        }

        textures.addProperty("particle", "minecraft:block/cauldron_side");
        textures.addProperty("top", "minecraft:block/cauldron_top");
        textures.addProperty("bottom", "minecraft:block/cauldron_bottom");
        textures.addProperty("side", "minecraft:block/cauldron_side");
        textures.addProperty("inside", "minecraft:block/cauldron_inner");
        model.add("textures", textures);

        write(assets().resolve("models").resolve("block").resolve(name + ".json"), model);
    }

    /**
     * The name a pot model and its sprite share. Empty keeps the block's own name so
     * the block item has an obvious parent.
     */
    private static String stoveModelName(Stages stages, int pot, boolean cooking, boolean frozen) {
        String name = pot == 0 ? stages.modelName(0) : stages.modelName(0) + "_pot" + pot;
        return name + (cooking ? "_cooking" : "") + (frozen ? "_unlit" : "");
    }
}
