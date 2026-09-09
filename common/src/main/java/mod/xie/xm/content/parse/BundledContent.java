package mod.xie.xm.content.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Installs the content pack bundled in the jar into the content directory, once.
 *
 * <p>The mod ships its own content inside the jar, under {@value #RESOURCE_ROOT},
 * beside an index listing every file and a version stamp. A jar cannot be listed
 * as a directory from the classpath, which is what the index is for: it is written
 * at build time and read here, one relative path per line.
 *
 * <p>The pack is extracted to {@code <container>/mod-content/} on the first launch
 * that finds no such directory, and never touched again. That is deliberate: the
 * content is opt-out rather than opt-in, so a user who deletes a definition file or
 * renames a directory with a leading underscore has made an edit, and an upgrade
 * that put the file back would undo it in silence. A newer jar over an older
 * install says so in the log instead, and the user gets the new content by moving
 * the directory away and relaunching.
 *
 * <p>Extraction goes to a sibling directory whose name starts with an underscore,
 * then is renamed into place, so a crash halfway leaves something the parser skips
 * rather than half a pack that loads.
 */
public final class BundledContent {

    /** The directory the bundled pack is installed as, under the content directory. */
    public static final String PACK_NAME = "mod-content";

    /** Where the build puts the bundled files on the classpath. */
    public static final String RESOURCE_ROOT = "/bundled/";

    /** One relative path per line, forward slashes, written by the build. */
    public static final String INDEX_RESOURCE = RESOURCE_ROOT + PACK_NAME + ".index";

    /** The version the bundled content was built into, written by the build. */
    public static final String VERSION_RESOURCE = RESOURCE_ROOT + PACK_NAME + ".version";

    /**
     * Written into the installed pack so a later jar can tell what it is looking at.
     * Starts with an underscore so the parser never reads it as content.
     */
    public static final String MARKER_FILE = "_bundled.version";

    /** What {@link #install} found and did. */
    public enum Outcome {
        /** The pack was not there and has been extracted. */
        INSTALLED,
        /** The pack was already there, at the bundled version or user-made. */
        PRESENT,
        /** The pack was already there, installed from an older jar. */
        OUTDATED,
        /** This build carries no content. */
        NOT_BUNDLED
    }

    /**
     * @param files            how many were written; zero unless {@link Outcome#INSTALLED}
     * @param bundledVersion   what the jar carries; null when nothing is bundled
     * @param installedVersion what the marker on disk says; null when there is no
     *                         marker, which means the directory was not installed
     *                         from a jar at all
     */
    public record Result(Outcome outcome, Path packDir, int files,
                         String bundledVersion, String installedVersion) { }

    private BundledContent() { }

    /** Installs from this class's own classpath. */
    public static Result install(Path container) throws IOException {
        return install(container, BundledContent.class::getResourceAsStream);
    }

    /**
     * Installs from wherever {@code resources} answers for a resource name, which
     * is the seam the tests use.
     */
    public static Result install(Path container, Function<String, InputStream> resources)
            throws IOException {
        Path packDir = container.resolve(PACK_NAME);

        String bundledVersion = readText(resources, VERSION_RESOURCE);
        if (bundledVersion == null) {
            return new Result(Outcome.NOT_BUNDLED, packDir, 0, null, null);
        }

        if (Files.isDirectory(packDir)) {
            Path marker = packDir.resolve(MARKER_FILE);
            String installedVersion = Files.isRegularFile(marker)
                ? Files.readString(marker, StandardCharsets.UTF_8).trim()
                : null;
            Outcome outcome = installedVersion != null && !installedVersion.equals(bundledVersion)
                ? Outcome.OUTDATED
                : Outcome.PRESENT;
            return new Result(outcome, packDir, 0, bundledVersion, installedVersion);
        }

        List<String> index = readIndex(resources);
        if (index == null) {
            return new Result(Outcome.NOT_BUNDLED, packDir, 0, bundledVersion, null);
        }

        Files.createDirectories(container);
        Path staging = container.resolve("_" + PACK_NAME + ".installing");
        deleteTree(staging);
        Files.createDirectories(staging);

        int written = 0;
        for (String relative : index) {
            Path target = staging.resolve(relative.replace('/', java.io.File.separatorChar))
                .normalize();
            if (!target.startsWith(staging)) {
                throw new IOException("bundled index escapes the pack: " + relative);
            }
            try (InputStream in = resources.apply(RESOURCE_ROOT + PACK_NAME + "/" + relative)) {
                if (in == null) {
                    throw new IOException("bundled index names " + relative
                        + ", which is not on the classpath");
                }
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            written++;
        }
        Files.writeString(staging.resolve(MARKER_FILE), bundledVersion + System.lineSeparator(),
            StandardCharsets.UTF_8);

        Files.move(staging, packDir);
        return new Result(Outcome.INSTALLED, packDir, written, bundledVersion, null);
    }

    private static List<String> readIndex(Function<String, InputStream> resources)
            throws IOException {
        try (InputStream in = resources.apply(INDEX_RESOURCE)) {
            if (in == null) {
                return null;
            }
            List<String> lines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
            return lines;
        }
    }

    private static String readText(Function<String, InputStream> resources, String name)
            throws IOException {
        try (InputStream in = resources.apply(name)) {
            if (in == null) {
                return null;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : text;
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
            for (Path path : paths) {
                Files.delete(path);
            }
        }
    }
}
