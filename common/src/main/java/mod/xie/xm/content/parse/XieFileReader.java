package mod.xie.xm.content.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads one definition file into definitions.
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

    /** Every spelling of a section key the reader accepts. */
    public static final java.util.Set<String> SECTION_NAMES = SECTIONS.keySet();

    private enum Section { BLOCK, ITEM, RECIPE, SPAWN, ALIAS }

    /** Keys the block parser consumes structurally rather than as plain properties. */
    // "texfile", "icon" and "texturebase" are read by readTex, which builds a
    // PropertyMap of its own over the same object when a definition declares them flat
    // rather than nested in "tex" — so the definition's own map never sees the read.
    private static final String[] BLOCK_STRUCTURAL =
        {"name", "type", "override", "overide", "drops", "fruit", "tex", "blockbounds",
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
     *                 {@code _wip/steel/steel.json}'s six recipes write {@code contents}
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
        Map<String, String> texturePaths,
        int rejected
    ) {
        public static Result empty() {
            return new Result(Map.of(), Map.of(), List.of(), List.of(), List.of(),
                Map.of(), 0);
        }
    }

    /** Thrown by {@link #readTree} when an object repeats a key. */
    private static final class DuplicateKeyException extends RuntimeException {
        final String key;
        final String where;

        DuplicateKeyException(String key, String where) {
            super("duplicate key \"" + key + "\" at " + where);
            this.key = key;
            this.where = where;
        }
    }

    /**
     * Builds a {@link JsonElement} tree the way {@code JsonParser.parseReader} does,
     * except that a key repeated within one object is an error rather than a silent
     * last-one-wins. Gson's own tree builder keeps the last value, which hides typos
     * like two {@code "4"} entries in a {@code blockBounds} table.
     */
    private static JsonElement readTree(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        switch (token) {
            case BEGIN_OBJECT: {
                JsonObject object = new JsonObject();
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    if (object.has(name)) {
                        throw new DuplicateKeyException(name, in.getPath());
                    }
                    object.add(name, readTree(in));
                }
                in.endObject();
                return object;
            }
            case BEGIN_ARRAY: {
                JsonArray array = new JsonArray();
                in.beginArray();
                while (in.hasNext()) {
                    array.add(readTree(in));
                }
                in.endArray();
                return array;
            }
            case STRING:
                return new JsonPrimitive(in.nextString());
            case NUMBER:
                return new JsonPrimitive(new LazilyParsedNumber(in.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(in.nextBoolean());
            case NULL:
                in.nextNull();
                return JsonNull.INSTANCE;
            case END_DOCUMENT:
                return JsonNull.INSTANCE;
            default:
                throw new IllegalStateException("unexpected " + token + " at " + in.getPath());
        }
    }

    /**
     * Parses one file.
     *
     * @param path relative path used in diagnostics, e.g. {@code farming/crops/tomato.json}
     * @param file the basename without extension; becomes the {@code parent} in
     *             {@code parent@name} references
     */
    public Result read(String path, String file, Reader source) {
        JsonElement root;
        try {
            JsonReader reader = new JsonReader(source);
            reader.setStrictness(Strictness.LENIENT);
            root = readTree(reader);
        } catch (DuplicateKeyException e) {
            diagnostics.error(path, "duplicate key \"" + e.key + "\" at " + e.where);
            return Result.empty();
        } catch (IOException | IllegalStateException | NumberFormatException e) {
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
            // "$schema" points an editor at xie-content.schema.json; it says nothing
            // about the content. Any "$" key is the editor's business, not ours.
            if (rawKey.startsWith("$")) {
                continue;
            }
            if (key.equals("version")) {
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

        return new Result(blocks, items, recipes, spawns, aliases, texturePaths, rejected);
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

    /**
     * Hands a structural key back to the unread-property report, for whoever consumes
     * what it parsed into.
     *
     * <p>Reading a key here proves the parser understood it and nothing more. Half of
     * {@link #BLOCK_STRUCTURAL} produces a value that some archetypes want and others
     * never ask for — {@code fruit} is a {@code tree}'s alone, and a {@code stem} takes
     * neither a {@code blockBounds} nor a sprite — so marking them consumed at parse
     * time hid exactly the gap {@code blazeTree}'s {@code blockBounds} was: parsed,
     * correct, and asked for by nobody. See {@link PropertyMap#markStructural}.
     *
     * @param parsed whether the key produced anything. A key that produced nothing has
     *               nothing to consume, and has already been reported if it was wrong
     */
    private static void deferred(PropertyMap props, boolean parsed, String... keys) {
        if (parsed) {
            props.markStructural(keys);
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
        // "fruit" is written exactly like "drops" — the original built its "sheds"
        // with the same expandDropsNode — but means something else: what picking a
        // ripe leaf yields rather than what breaking it does.
        DropTable fruit = readDrops(origin, props.raw("fruit").orElse(null));
        TexDef tex = readTex(origin, object, "block");
        Optional<MetaMap<Bounds>> bounds = readBounds(origin, props);

        props.markUsed(BLOCK_STRUCTURAL);
        deferred(props, !drops.isEmpty(), "drops");
        deferred(props, !fruit.isEmpty(), "fruit");
        deferred(props, bounds.isPresent(), "blockBounds");
        deferred(props, tex.hasSprites(), "tex", "texFile", "icon");
        deferred(props, tex.base().isPresent(), "textureBase");

        BlockDef block = new BlockDef(origin, origin.name(), displayName, archetype,
            override, drops, fruit, tex, bounds, props);

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
        deferred(props, !drops.isEmpty(), "drops");
        deferred(props, !blockDrops.isEmpty(), "blocks");
        deferred(props, tex.hasSprites(), "tex", "texFile", "icon");
        // No item ever consumes a textureBase, and none ever did: setTextureBase is on
        // IXieBlock and JobItem has no branch for the key. Deferred all the same, so
        // that the report says which of the two it is.
        deferred(props, tex.base().isPresent(), "textureBase");

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
