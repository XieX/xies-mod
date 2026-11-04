package mod.xie.xm.content.parse;

import mod.xie.xm.content.model.AliasDef;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.RecipeDef;
import mod.xie.xm.content.model.SpawnDef;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Loads a content pack directory into a {@link ContentPack}.
 *
 * <p>A pack root contains definition files at any depth, optional {@code .loc}
 * files, and a {@code textures/} tree. A definition file is JSON and
 * is named {@code .json}; the older {@code .xie} extension is the same format under
 * a different name and is still read, so existing packs keep working unchanged. Files and
 * directories whose name starts with {@code _} are skipped — that is the
 * convention the existing content already uses for work in progress
 * ({@code _wip/}, {@code _plox_update/}, {@code _campfire.json}).
 *
 * <p>Files are visited in sorted order so a pack always parses identically,
 * which matters because later declarations win on conflict.
 */
public final class XieParser {

    /** The definition file extension. */
    public static final String JSON_EXTENSION = ".json";
    /** The definition file extension before the format admitted it was JSON. */
    public static final String XIE_EXTENSION = ".xie";
    public static final String LOC_EXTENSION = ".loc";

    /** Directory under the game directory that user content packs live in. */
    public static final String CONTENT_DIR_NAME = "xiecontent";

    /** The JSON Schema describing a definition file, as a classpath resource. */
    public static final String SCHEMA_RESOURCE = "/xie-content.schema.json";
    /** The file name the schema is installed under, next to the packs. */
    public static final String SCHEMA_FILE_NAME = "xie-content.schema.json";

    /**
     * @param pack        everything the pack declared that the reader could use
     * @param diagnostics every problem found while reading it
     * @param rejected    how many declarations were reported and thrown away, and so
     *                    appear in no other count. See {@link XieFileReader.Result}.
     * @param fingerprint eight hex digits that change when any definition file does.
     *                    See {@link #fingerprint}.
     */
    public record ParseResult(ContentPack pack, Diagnostics diagnostics, int rejected,
                              String fingerprint) { }

    private XieParser() { }

    /**
     * Parses every pack directory directly under {@code container}. Missing or
     * empty containers yield an empty list rather than an error — a fresh install
     * has no user content and that is not a problem.
     */
    public static List<ParseResult> parseAll(Path container) throws IOException {
        List<ParseResult> results = new ArrayList<>();
        if (!Files.isDirectory(container)) {
            return results;
        }
        try (Stream<Path> entries = Files.list(container)) {
            List<Path> packs = entries
                .filter(Files::isDirectory)
                .filter(path -> !path.getFileName().toString().startsWith("_"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
            for (Path pack : packs) {
                results.add(parsePack(pack));
            }
        }
        return results;
    }

    /**
     * Writes the schema into {@code container}, next to the packs, so a definition
     * file can point its editor at it with {@code "$schema": "../xie-content.schema.json"}.
     *
     * <p>Overwritten on every call, because the schema tracks this build of the mod
     * and an author who upgrades should see the new properties, not last year's.
     *
     * @return where it was written
     */
    public static Path installSchema(Path container) throws IOException {
        Path target = container.resolve(SCHEMA_FILE_NAME);
        try (InputStream in = XieParser.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IOException("schema resource " + SCHEMA_RESOURCE + " is not on the classpath");
            }
            Files.createDirectories(container);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** Parses one pack directory. Never throws for content problems, only for IO. */
    public static ParseResult parsePack(Path root) throws IOException {
        Diagnostics diagnostics = new Diagnostics();
        String packName = root.getFileName() == null ? "pack" : root.getFileName().toString();

        if (!Files.isDirectory(root)) {
            diagnostics.error(packName, "not a directory: " + root);
            return new ParseResult(ContentPack.empty(packName), diagnostics, 0, fingerprint(Map.of()));
        }

        Map<String, BlockDef> blocks = new LinkedHashMap<>();
        Map<String, ItemDef> items = new LinkedHashMap<>();
        List<RecipeDef> recipes = new ArrayList<>();
        List<SpawnDef> spawns = new ArrayList<>();
        Map<String, AliasDef> aliases = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        Map<String, String> texturePaths = new LinkedHashMap<>();
        Map<String, String> definitionTexts = new LinkedHashMap<>();
        int rejected = 0;

        XieFileReader xieReader = new XieFileReader(diagnostics);
        LocReader locReader = new LocReader(diagnostics);

        for (Path file : collectFiles(root)) {
            String relative = relativise(root, file);
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            String text = readText(file, relative, diagnostics);

            if (isDefinitionFile(lower)) {
                definitionTexts.put(relative, text);
                String basename = stripExtension(file.getFileName().toString());
                XieFileReader.Result result = xieReader.read(relative, basename, new StringReader(text));
                rejected += result.rejected();
                mergeFile(result, relative, diagnostics,
                    blocks, items, recipes, spawns, aliases, texturePaths);
            } else if (lower.endsWith(LOC_EXTENSION)) {
                displayNames.putAll(locReader.read(relative, new StringReader(text)));
            }
        }

        ContentPack pack = new ContentPack(packName, blocks, items, recipes, spawns,
            aliases, displayNames, texturePaths);
        return new ParseResult(pack, diagnostics, rejected, fingerprint(definitionTexts));
    }

    /**
     * A short hash of a pack's definition files, for telling whether a client and a
     * server have the same content.
     *
     * <p>Only definition files count. Blocks and items are registered from them, and
     * the properties a client predicts with, such as hardness, come from them too, so
     * a difference there is a broken connection. {@code .loc} names and textures only
     * reach the client's resource pack, which vanilla already lets a player swap.
     *
     * <p>Line endings are normalised, because a pack copied through git on Windows can
     * come out CRLF on one side and LF on the other with nothing else changed. Files are
     * taken in order of their {@code /}-separated relative path rather than the order
     * they were read in, which sorts by the platform's separator and so differs
     * between a Windows client and a Linux server.
     *
     * @param texts relative path to file text
     */
    static String fingerprint(Map<String, String> texts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM provides SHA-256", e);
        }
        for (Map.Entry<String, String> entry : new TreeMap<>(texts).entrySet()) {
            digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(entry.getValue().replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest(), 0, 4);
    }

    private static void mergeFile(
        XieFileReader.Result result,
        String relative,
        Diagnostics diagnostics,
        Map<String, BlockDef> blocks,
        Map<String, ItemDef> items,
        List<RecipeDef> recipes,
        List<SpawnDef> spawns,
        Map<String, AliasDef> aliases,
        Map<String, String> texturePaths
    ) {
        result.blocks().forEach((name, block) -> {
            BlockDef previous = blocks.put(name, block);
            if (previous != null) {
                diagnostics.warning(relative, "block \"" + name + "\" also declared in "
                    + previous.origin().path() + "; later wins");
            }
        });
        result.items().forEach((name, item) -> {
            ItemDef previous = items.put(name, item);
            if (previous != null) {
                diagnostics.warning(relative, "item \"" + name + "\" also declared in "
                    + previous.origin().path() + "; later wins");
            }
        });
        recipes.addAll(result.recipes());
        spawns.addAll(result.spawns());
        // Aliases accumulate across files rather than overwriting, matching JobAlias.
        for (AliasDef alias : result.aliases()) {
            aliases.merge(alias.name(), alias, AliasDef::merge);
        }
        texturePaths.putAll(result.texturePaths());
    }

    /**
     * Reads a file as text, tolerating the encodings 2013-era packs were written in.
     *
     * <p>Decoding has to happen here rather than being left to a {@code Reader},
     * because Gson wraps an {@code IOException} from the reader in a
     * {@code JsonIOException} — so a malformed-input failure would surface as
     * "not valid JSON" and the real cause would be lost.
     */
    private static String readText(Path file, String relative, Diagnostics diagnostics) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            diagnostics.warning(relative, "not valid UTF-8, decoded as ISO-8859-1 instead");
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static List<Path> collectFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> !isHidden(root, path))
                .filter(XieParser::isContentFile)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    /** True when any path segment below the root starts with {@code _}. */
    private static boolean isHidden(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            if (segment.toString().startsWith("_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContentFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return isDefinitionFile(name) || name.endsWith(LOC_EXTENSION);
    }

    /** @param lowerName a file name already lower-cased */
    private static boolean isDefinitionFile(String lowerName) {
        return lowerName.endsWith(JSON_EXTENSION) || lowerName.endsWith(XIE_EXTENSION);
    }

    private static String relativise(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
