package mod.xie.xm.content.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a {@code .loc} file: display names, kept separate from definitions so a
 * pack can be translated without touching its {@code .xie} files.
 *
 * <pre>
 *   greySteelOre="Grey Steel Ore"
 *   steelIngot="Steel Ingot"
 * </pre>
 *
 * <p>Becomes {@code assets/&lt;namespace&gt;/lang/en_us.json} on the modern side. The
 * per-language split the format never had comes free: a {@code .loc} file can be
 * named for its locale and emitted to the matching lang file.
 */
public final class LocReader {

    private final Diagnostics diagnostics;

    public LocReader(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Map<String, String> read(String path, Reader source) throws IOException {
        Map<String, String> names = new LinkedHashMap<>();
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
                    diagnostics.warning(path + ":" + lineNumber,
                        "expected name=\"Display Name\", got \"" + trimmed + "\"");
                    continue;
                }
                String name = trimmed.substring(0, equals).trim();
                String value = unquote(trimmed.substring(equals + 1).trim());
                if (value.isEmpty()) {
                    diagnostics.warning(path + ":" + lineNumber, "empty display name for \"" + name + "\"");
                    continue;
                }
                names.put(name, value);
            }
        }
        return names;
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
