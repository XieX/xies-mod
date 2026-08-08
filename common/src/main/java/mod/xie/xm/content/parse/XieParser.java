package mod.xie.xm.content.parse;

import mod.xie.xm.content.model.AliasDef;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.RecipeDef;
import mod.xie.xm.content.model.SpawnDef;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads a content pack directory into a {@link ContentPack}.
 *
 * <p>A pack root contains {@code .xie} definition files at any depth, optional
 * {@code .xid} and {@code .loc} files, and a {@code textures/} tree. Files and
 * directories whose name starts with {@code _} are skipped — that is the
 * convention the existing content already uses for work in progress
 * ({@code _wip/}, {@code _plox_update/}, {@code _campfire.xie}).
 *
 * <p>Files are visited in sorted order so a pack always parses identically,
 * which matters because later declarations win on conflict.
 */
public final class XieParser {

    public static final String XIE_EXTENSION = ".xie";
    public static final String XID_EXTENSION = ".xid";
    public static final String LOC_EXTENSION = ".loc";

    /** Directory under the game directory that user content packs live in. */
    public static final String CONTENT_DIR_NAME = "xiecontent";

    public record ParseResult(ContentPack pack, Diagnostics diagnostics) { }

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

    /** Parses one pack directory. Never throws for content problems, only for IO. */
    public static ParseResult parsePack(Path root) throws IOException {
        Diagnostics diagnostics = new Diagnostics();
        String packName = root.getFileName() == null ? "pack" : root.getFileName().toString();

        if (!Files.isDirectory(root)) {
            diagnostics.error(packName, "not a directory: " + root);
            return new ParseResult(ContentPack.empty(packName), diagnostics);
        }

        Map<String, BlockDef> blocks = new LinkedHashMap<>();
        Map<String, ItemDef> items = new LinkedHashMap<>();
        List<RecipeDef> recipes = new ArrayList<>();
        List<SpawnDef> spawns = new ArrayList<>();
        Map<String, AliasDef> aliases = new LinkedHashMap<>();
        Map<String, Integer> legacyIds = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        Map<String, String> texturePaths = new LinkedHashMap<>();

        XieFileReader xieReader = new XieFileReader(diagnostics);
        XidReader xidReader = new XidReader(diagnostics);
        LocReader locReader = new LocReader(diagnostics);

        for (Path file : collectFiles(root)) {
            String relative = relativise(root, file);
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            String text = readText(file, relative, diagnostics);

            if (lower.endsWith(XIE_EXTENSION)) {
                String basename = stripExtension(file.getFileName().toString());
                XieFileReader.Result result = xieReader.read(relative, basename, new StringReader(text));
                mergeFile(result, relative, diagnostics,
                    blocks, items, recipes, spawns, aliases, legacyIds, texturePaths);
            } else if (lower.endsWith(XID_EXTENSION)) {
                legacyIds.putAll(xidReader.read(relative, new StringReader(text)));
            } else if (lower.endsWith(LOC_EXTENSION)) {
                displayNames.putAll(locReader.read(relative, new StringReader(text)));
            }
        }

        ContentPack pack = new ContentPack(packName, blocks, items, recipes, spawns,
            aliases, legacyIds, displayNames, texturePaths);
        return new ParseResult(pack, diagnostics);
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
        Map<String, Integer> legacyIds,
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
        legacyIds.putAll(result.ids());
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
        return name.endsWith(XIE_EXTENSION)
            || name.endsWith(XID_EXTENSION)
            || name.endsWith(LOC_EXTENSION);
    }

    private static String relativise(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
