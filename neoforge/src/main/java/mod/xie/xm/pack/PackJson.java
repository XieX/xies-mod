package mod.xie.xm.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON writing shared by the two halves of the emitter.
 *
 * <p>Pretty-printed rather than compact because the whole point of writing the
 * pack to disk is that a content author can open it and read what their
 * {@code .xie} file produced.
 */
final class PackJson {

    private static final Gson GSON =
        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PackJson() { }

    static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
