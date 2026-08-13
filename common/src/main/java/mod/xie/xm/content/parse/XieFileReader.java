package mod.xie.xm.content.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import mod.xie.xm.content.model.AliasDef;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.Bounds;
import mod.xie.xm.content.model.DropTable;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.MetaMap;
import mod.xie.xm.content.model.Origin;
import mod.xie.xm.content.model.PropertyMap;
import mod.xie.xm.content.model.RecipeDef;
import mod.xie.xm.content.model.SpawnDef;
import mod.xie.xm.content.model.TexDef;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads one {@code .xie} file into definitions.
 *
 * <p>Unlike the original {@code Work.findWork} plus {@code Job.doWork} pair, this
 * resolves nothing. References stay as {@link ItemRef}s, so there is no job queue,
 * no retry pass and no ordering dependency between files. A file either parses or
 * it does not, and it always parses the same way.
 */
public final class XieFileReader {

    /** Section keys that carry definitions, normalised to lower case. */
    private static final Map<String, Section> SECTIONS = Map.of(
        "blocks", Section.BLOCK,
        "block", Section.BLOCK,
        "items", Section.ITEM,
        "item", Section.ITEM,
        "recipes", Section.RECIPE,
        "recipe", Section.RECIPE,
        "spawns", Section.SPAWN,
        "spawn", Section.SPAWN,
        "aliases", Section.ALIAS,
        "alias", Section.ALIAS
    );

    private enum Section { BLOCK, ITEM, RECIPE, SPAWN, ALIAS }

    /** Keys the block parser consumes structurally rather than as plain properties. */
    // "texfile", "icon" and "texturebase" are read by readTex, which builds a
    // PropertyMap of its own over the same object when a definition declares them flat
    // rather than nested in "tex" — so the definition's own map never sees the read.
    private static final String[] BLOCK_STRUCTURAL =
        {"name", "type", "override", "overide", "drops", "sheds", "tex", "blockbounds",
         "texfile", "icon", "texturebase"};

    private static final String[] ITEM_STRUCTURAL =
        {"name", "type", "override", "overide", "drops", "blocks", "texfile", "icon", "tex",
         "texturebase"};

    private final Diagnostics diagnostics;

    public XieFileReader(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Everything one file contributed.
     *
     * @param rejected how many declarations were thrown away, having been reported as
     *                 an error. A declaration the reader refuses is in none of the
     *                 lists above and in no count the registrar or the emitters keep,
     *                 so without this it leaves the totals silently one short: three of
     *                 {@code _wip/steel/steel.xie}'s six recipes write {@code contents}
     *                 where the format wants {@code content} — as they did in 1.6,
     *                 where {@code JobRecipe} failed the job outright — and the pack
     *                 then reported one recipe not emitted rather than four missing.
     */
    public record Result(
        Map<String, BlockDef> blocks,
        Map<String, ItemDef> items,
        List<RecipeDef> recipes,
        List<SpawnDef> spawns,
        List<AliasDef> aliases,
        Map<String, Integer> ids,
        Map<String, String> texturePaths,
        int rejected
    ) {
        public static Result empty() {
            return new Result(Map.of(), Map.of(), List.of(), List.of(), List.of(),
                Map.of(), Map.of(), 0);
        }
    }

    /**
     * Parses one file.
     *
     * @param path relative path used in diagnostics, e.g. {@code farming/crops/tomato.xie}
     * @param file the basename without extension; becomes the {@code parent} in
     *             {@code parent@name} references
     */
    public Result read(String path, String file, Reader source) {
        JsonElement root;
        try {
            root = JsonParser.parseReader(source);
        } catch (JsonSyntaxException | com.google.gson.JsonIOException e) {
            diagnostics.error(path, "not valid JSON: " + e.getMessage());
            return Result.empty();
        }

        if (root == null || !root.isJsonObject()) {
            diagnostics.error(path, "top level must be a JSON object");
            return Result.empty();
        }

        Map<String, BlockDef> blocks = new LinkedHashMap<>();
        Map<String, ItemDef> items = new LinkedHashMap<>();
        List<RecipeDef> recipes = new ArrayList<>();
        List<SpawnDef> spawns = new ArrayList<>();
        List<AliasDef> aliases = new ArrayList<>();
        Map<String, Integer> ids = new LinkedHashMap<>();
        Map<String, String> texturePaths = new LinkedHashMap<>();
        int rejected = 0;

        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            String rawKey = entry.getKey();
            String key = rawKey.trim().toLowerCase(Locale.ROOT);
            JsonElement value = entry.getValue();

            // Authors disable a section by prefixing it with an underscore.
            if (rawKey.startsWith("_")) {
                continue;
            }
            if (key.equals("version")) {
                continue;
            }
            if (key.equals("ids")) {
                readIds(path, value, ids);
                continue;
            }
            if (key.equals("texturepaths")) {
                readTexturePaths(path, value, texturePaths);
                continue;
            }

            Section section = SECTIONS.get(key);
            if (section == null) {
                diagnostics.warning(path, "unknown section \"" + rawKey + "\", ignored");
                continue;
            }
            if (!value.isJsonObject()) {
                diagnostics.error(path, "section \"" + rawKey + "\" must be an object");
                rejected++;
                continue;
            }

            for (Map.Entry<String, JsonElement> def : value.getAsJsonObject().entrySet()) {
                String name = def.getKey().trim();
                if (name.startsWith("_")) {
                    continue;
                }
                Origin origin = new Origin(file, path, name);
                // Every reader answers with an empty Optional for a declaration it has
                // refused and reported, which is the one place a definition can go
                // missing without anything downstream ever hearing of it.
                boolean kept = switch (section) {
                    case BLOCK -> read(readBlock(origin, def.getValue()),
                        b -> put(blocks, b.name(), b, origin));
                    case ITEM -> read(readItem(origin, def.getValue()),
                        i -> put(items, i.name(), i, origin));
                    case RECIPE -> read(readRecipe(origin, def.getValue()), recipes::add);
                    case SPAWN -> read(readSpawn(origin, def.getValue()), spawns::add);
                    case ALIAS -> read(readAlias(origin, def.getValue()), aliases::add);
                };
                if (!kept) {
                    rejected++;
                }
            }
        }

        return new Result(blocks, items, recipes, spawns, aliases, ids, texturePaths, rejected);
    }

    /** Keeps what a reader produced, answering whether there was anything to keep. */
    private static <T> boolean read(Optional<T> parsed, java.util.function.Consumer<T> keep) {
        parsed.ifPresent(keep);
        return parsed.isPresent();
    }

    private <T> void put(Map<String, T> target, String name, T value, Origin origin) {
        if (target.put(name, value) != null) {
            diagnostics.warning(origin.toString(), "declared twice in this file; later wins");
        }
    }

    // ---------------------------------------------------------------- blocks

    private Optional<BlockDef> readBlock(Origin origin, JsonElement element) {
        if (!element.isJsonObject()) {
            diagnostics.error(origin.toString(), "block definition must be an object");
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        PropertyMap props = PropertyMap.of(object);

        String displayName = props.getString("name", origin.name());
        String archetype = props.getString("type", BlockDef.DEFAULT_ARCHETYPE)
            .trim().toLowerCase(Locale.ROOT);
        Optional<ItemRef> override = readOverride(props);

        DropTable drops = readDrops(origin, props.raw("drops").orElse(null));
        // "sheds" is written exactly like "drops" — the original built both with
        // expandDropsNode — but means something else: what a tree lets fall of its
        // own accord rather than what breaking it yields.
        DropTable sheds = readDrops(origin, props.raw("sheds").orElse(null));
        TexDef tex = readTex(origin, object, "block");
        Optional<MetaMap<Bounds>> bounds = readBounds(origin, props);

        props.markUsed(BLOCK_STRUCTURAL);

        BlockDef block = new BlockDef(origin, origin.name(), displayName, archetype,
            override, drops, sheds, tex, bounds, props);

        if (!block.hasKnownArchetype()) {
            diagnostics.warning(origin.toString(),
                "unknown block type \"" + archetype + "\"; will need a registrar to claim it");
        }
        return Optional.of(block);
    }

    private Optional<MetaMap<Bounds>> readBounds(Origin origin, PropertyMap props) {
        for (String bad : props.badMetaKeys("blockBounds")) {
            diagnostics.warning(origin.toString(), "blockBounds has unparseable range \"" + bad + "\"");
        }
        return props.getMetaMap("blockBounds", value -> parseBounds(origin, value));
    }

    private Bounds parseBounds(Origin origin, JsonElement element) {
        if (!element.isJsonPrimitive()) {
            diagnostics.warning(origin.toString(), "blockBounds entry is not a string");
            return null;
        }
        String[] parts = element.getAsString().split(",");
        if (parts.length != 6) {
            diagnostics.warning(origin.toString(),
                "blockBounds needs 6 numbers, got " + parts.length + ": " + element.getAsString());
            return null;
        }
        try {
            float[] n = new float[6];
            for (int i = 0; i < 6; i++) {
                n[i] = Float.parseFloat(parts[i].trim());
            }
            return new Bounds(n[0], n[1], n[2], n[3], n[4], n[5]);
        } catch (NumberFormatException e) {
            diagnostics.warning(origin.toString(), "blockBounds is not numeric: " + element.getAsString());
            return null;
        }
    }

    // ----------------------------------------------------------------- items

    private Optional<ItemDef> readItem(Origin origin, JsonElement element) {
        if (!element.isJsonObject()) {
            diagnostics.error(origin.toString(), "item definition must be an object");
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        PropertyMap props = PropertyMap.of(object);

        String displayName = props.getString("name", origin.name());
        String archetype = props.getString("type", ItemDef.DEFAULT_ARCHETYPE)
            .trim().toLowerCase(Locale.ROOT);
        Optional<ItemRef> override = readOverride(props);
        // Written exactly as a block's "drops", and meaning the same thing one step
        // along: what you are left holding once the item has been used up. Only food
        // ever declares it, and XieItemFood.onFoodEaten handed it to the player.
        DropTable drops = readDrops(origin, props.raw("drops").orElse(null));
        List<ItemDef.BlockDrops> blockDrops = readBlockDrops(origin, props.raw("blocks").orElse(null));
        TexDef tex = readTex(origin, object, "item");

        props.markUsed(ITEM_STRUCTURAL);

        ItemDef item = new ItemDef(origin, origin.name(), displayName, archetype, override,
            drops, blockDrops, tex, props);

        if (!item.hasKnownArchetype()) {
            diagnostics.warning(origin.toString(),
                "unknown item type \"" + archetype + "\"; will need a registrar to claim it");
        }
        return Optional.of(item);
    }

    /** Handles both spellings; the original accepted the {@code overide} typo. */
    private Optional<ItemRef> readOverride(PropertyMap props) {
        Optional<String> value = props.getString("override");
        if (value.isEmpty()) {
            value = props.getString("overide");
        }
        return value.map(ItemRef::parse);
    }

    // ----------------------------------------------------------------- drops

    /**
     * The {@code blocks} map of a {@code stick}: block reference to the drop table
     * that block yields when the stick is used on it.
     *
     * <p>Each value is written exactly as a block's own {@code drops}, and
     * {@code JobItem.doStick} read it with the same {@code expandDropsNode} the block
     * side uses — so it is the same parse here too. Declaration order is preserved
     * because {@code XieItemStick} took the first entry that matched.
     */
    private List<ItemDef.BlockDrops> readBlockDrops(Origin origin, JsonElement element) {
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonObject()) {
            diagnostics.warning(origin.toString(), "blocks must be an object");
            return List.of();
        }
        List<ItemDef.BlockDrops> entries = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            entries.add(new ItemDef.BlockDrops(ItemRef.parse(entry.getKey()),
                readDrops(origin, entry.getValue())));
        }
        return entries;
    }

    private DropTable readDrops(Origin origin, JsonElement element) {
        if (element == null) {
            return DropTable.EMPTY;
        }
        List<DropTable.Drop> drops = new ArrayList<>();

        if (element.isJsonPrimitive()) {
            for (String name : element.getAsString().split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    drops.add(DropTable.Drop.always(ItemRef.parse(trimmed)));
                }
            }
            return new DropTable(drops);
        }

        if (!element.isJsonObject()) {
            diagnostics.warning(origin.toString(), "drops must be a string or an object");
            return DropTable.EMPTY;
        }

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            ItemRef item = ItemRef.parse(entry.getKey());
            JsonElement body = entry.getValue();

            if (!body.isJsonObject()) {
                // "drops": { "potash": 2 } — a bare number is the amount.
                int amount = body.isJsonPrimitive()
                    ? PropertyMap.asInt(body).orElse(1)
                    : 1;
                drops.add(new DropTable.Drop(item, Optional.empty(), amount, PropertyMap.empty()));
                continue;
            }

            PropertyMap dropProps = PropertyMap.of(body.getAsJsonObject());
            for (String bad : dropProps.badMetaKeys("chance")) {
                diagnostics.warning(origin.toString(),
                    "drop \"" + entry.getKey() + "\" has unparseable chance range \"" + bad + "\"");
            }
            Optional<MetaMap<Integer>> chance = dropProps.getMetaMap("chance",
                value -> PropertyMap.asInt(value).orElse(null));
            int amount = dropProps.getInt("amount", 1);
            dropProps.markUsed("chance", "amount");

            drops.add(new DropTable.Drop(item, chance, amount, dropProps));
        }
        return new DropTable(drops);
    }

    // -------------------------------------------------------------- textures

    /**
     * Reads a texture assignment from either an item-style flat declaration
     * ({@code texFile} plus {@code icon}) or a block-style nested {@code tex} object.
     */
    private TexDef readTex(Origin origin, JsonObject object, String kind) {
        JsonObject texSource = object;
        JsonElement nested = findIgnoringCase(object, "tex");
        if (nested != null) {
            if (!nested.isJsonObject()) {
                diagnostics.warning(origin.toString(), "tex must be an object");
                return TexDef.EMPTY;
            }
            texSource = nested.getAsJsonObject();
        }

        PropertyMap texProps = PropertyMap.of(texSource);
        Optional<String> sheet = texProps.getString("texFile");
        Optional<String> base = texProps.getString("textureBase");

        Map<String, MetaMap<Integer>> faces = new LinkedHashMap<>();
        for (String key : texProps.keys()) {
            if (key.equals("texfile") || key.equals("texturebase")) {
                continue;
            }
            // When reading the outer object, only "icon" is a texture key; every
            // other property belongs to the definition itself.
            if (nested == null && !key.equals(TexDef.FACE_ICON)) {
                continue;
            }
            for (String bad : texProps.badMetaKeys(key)) {
                diagnostics.warning(origin.toString(),
                    "texture face \"" + key + "\" has unparseable range \"" + bad + "\"");
            }
            texProps.getMetaMap(key, value -> PropertyMap.asInt(value).orElse(null))
                .ifPresent(map -> faces.put(key, map));
        }

        if (sheet.isEmpty() && faces.isEmpty() && base.isEmpty()) {
            return TexDef.EMPTY;
        }
        if (sheet.isEmpty() && base.isEmpty() && !faces.isEmpty()) {
            diagnostics.warning(origin.toString(),
                kind + " gives texture indices but no texFile");
        }
        return new TexDef(sheet, faces, base);
    }

    private static JsonElement findIgnoringCase(JsonObject object, String key) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().trim().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // --------------------------------------------------------------- recipes

    private Optional<RecipeDef> readRecipe(Origin origin, JsonElement element) {
        String name = origin.name();

        // A bare string value is the ingredient list: "tomatoSeeds": "tomato".
        if (element.isJsonPrimitive()) {
            if (name.equalsIgnoreCase("cull")) {
                return Optional.of(cullRecipe(origin, element.getAsString()));
            }
            List<ItemRef> ingredients = refList(element.getAsString());
            return Optional.of(new RecipeDef(origin, name, RecipeDef.Kind.SHAPELESS,
                Optional.of(ItemRef.parse(name)), List.of(), ingredients, List.of(),
                PropertyMap.empty()));
        }

        if (!element.isJsonObject()) {
            diagnostics.error(origin.toString(), "recipe must be a string or an object");
            return Optional.empty();
        }

        PropertyMap props = PropertyMap.of(element.getAsJsonObject());

        if (name.equalsIgnoreCase("cull") || props.has("cull")) {
            String targets = props.getString("cull").orElse("");
            props.markUsed("cull");
            return Optional.of(cullRecipe(origin, targets));
        }

        ItemRef product = ItemRef.parse(props.getString("product").orElse(name));

        if (props.has("smelt")) {
            List<ItemRef> input = refList(props.getString("smelt").orElse(""));
            props.markUsed("smelt", "product");
            return Optional.of(new RecipeDef(origin, name, RecipeDef.Kind.SMELTING,
                Optional.of(product), List.of(), input, List.of(), props));
        }

        Optional<String> content = props.getString("content");
        if (content.isEmpty()) {
            diagnostics.error(origin.toString(), "recipe has no \"content\"");
            return Optional.empty();
        }
        List<ItemRef> ingredients = refList(content.get());

        List<String> pattern = List.of();
        RecipeDef.Kind kind = RecipeDef.Kind.SHAPELESS;
        Optional<String> patternText = props.getString("pattern");
        if (patternText.isPresent()) {
            kind = RecipeDef.Kind.SHAPED;
            pattern = new ArrayList<>();
            for (String row : patternText.get().split(",")) {
                pattern.add(row);
            }
        }
        props.markUsed("product", "content", "pattern");

        RecipeDef recipe = new RecipeDef(origin, name, kind, Optional.of(product),
            pattern, ingredients, List.of(), props);

        for (String problem : recipe.problems()) {
            diagnostics.warning(origin.toString(), problem);
        }
        return Optional.of(recipe);
    }

    private RecipeDef cullRecipe(Origin origin, String targets) {
        return new RecipeDef(origin, origin.name(), RecipeDef.Kind.CULL,
            Optional.empty(), List.of(), List.of(), refList(targets), PropertyMap.empty());
    }

    // ---------------------------------------------------------------- spawns

    private Optional<SpawnDef> readSpawn(Origin origin, JsonElement element) {
        if (!element.isJsonObject()) {
            diagnostics.error(origin.toString(), "spawn definition must be an object");
            return Optional.empty();
        }
        PropertyMap props = PropertyMap.of(element.getAsJsonObject());

        ItemRef target = ItemRef.parse(props.getString("block").orElse(origin.name()));
        List<ItemRef> in = props.getRefList("spawnsIn");
        List<ItemRef> on = props.getRefList("spawnsOn");
        List<ItemRef> under = props.getRefList("spawnsUnder");
        List<ItemRef> near = props.getRefList("spawnsNear");
        List<String> biomes = props.getStringList("biomes");
        props.markUsed("block");

        SpawnDef spawn = new SpawnDef(origin, origin.name(), target, in, on, under, near, biomes, props);
        if (!spawn.hasPlacementRules() && biomes.isEmpty()) {
            diagnostics.warning(origin.toString(), "spawn declares no placement rules");
        }
        return Optional.of(spawn);
    }

    // --------------------------------------------------------------- aliases

    private Optional<AliasDef> readAlias(Origin origin, JsonElement element) {
        List<ItemRef> members = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            members = refList(element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    members.add(ItemRef.parse(item.getAsString()));
                }
            }
        } else {
            diagnostics.error(origin.toString(), "alias must be a string or an array");
            return Optional.empty();
        }

        if (members.isEmpty()) {
            diagnostics.warning(origin.toString(), "alias is empty");
        }
        return Optional.of(new AliasDef(origin, origin.name(), members));
    }

    // ------------------------------------------------------------ file-level

    private void readIds(String path, JsonElement element, Map<String, Integer> target) {
        if (!element.isJsonObject()) {
            diagnostics.warning(path, "\"ids\" must be an object");
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            PropertyMap.asInt(entry.getValue()).ifPresentOrElse(
                id -> target.put(entry.getKey().trim(), id),
                () -> diagnostics.warning(path,
                    "id for \"" + entry.getKey() + "\" is not an integer"));
        }
    }

    private void readTexturePaths(String path, JsonElement element, Map<String, String> target) {
        if (!element.isJsonObject()) {
            diagnostics.warning(path, "\"texturePaths\" must be an object");
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                target.put(entry.getKey().trim(), entry.getValue().getAsString().trim());
            }
        }
    }

    private static List<ItemRef> refList(String csv) {
        List<ItemRef> refs = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                refs.add(ItemRef.parse(trimmed));
            }
        }
        return refs;
    }
}
