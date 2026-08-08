package mod.xie.xm.content.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a {@code .xid} file: legacy numeric block and item id assignments.
 *
 * <pre>
 *   # Xie's Mod SMP v0.3
 *   # Block IDs
 *   fruitLeaves=211
 *   tomatoBush=213
 * </pre>
 *
 * <p>These ids are <em>obsolete</em>. Minecraft dropped numeric block and item ids
 * in 1.7 in favour of namespaced identifiers, so nothing consumes them any more.
 * They are still parsed for two reasons: a pack that ships one should not produce
 * an "unrecognised file" warning, and the id list is a useful cross-check that
 * every declared name is accounted for.
 */
public final class XidReader {

    private final Diagnostics diagnostics;

    public XidReader(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Map<String, Integer> read(String path, Reader source) throws IOException {
        Map<String, Integer> ids = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals <= 0) {
                    diagnostics.warning(path + ":" + lineNumber, "expected name=id, got \"" + trimmed + "\"");
                    continue;
                }
                String name = trimmed.substring(0, equals).trim();
                String value = trimmed.substring(equals + 1).trim();
                try {
                    ids.put(name, Integer.valueOf(value));
                } catch (NumberFormatException e) {
                    diagnostics.warning(path + ":" + lineNumber, "id \"" + value + "\" is not an integer");
                }
            }
        }
        return ids;
    }
}
