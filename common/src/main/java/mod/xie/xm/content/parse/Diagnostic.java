package mod.xie.xm.content.parse;

/**
 * One problem found while parsing.
 *
 * <p>Replaces {@code XieLog}'s failure/incomplete/delayed levels. The "delayed"
 * level existed because the original resolved references during parsing and had to
 * retry; this parser resolves nothing, so a problem here is always a real problem
 * with the file rather than an ordering artefact.
 *
 * @param severity how bad it is
 * @param where    the file and definition it came from
 * @param message  what went wrong
 */
public record Diagnostic(Severity severity, String where, String message) {

    public enum Severity {
        /** The definition was discarded. */
        ERROR,
        /** The definition loaded, but something in it was ignored or guessed. */
        WARNING,
        /** Worth reporting, nothing wrong. */
        INFO
    }

    public static Diagnostic error(String where, String message) {
        return new Diagnostic(Severity.ERROR, where, message);
    }

    public static Diagnostic warning(String where, String message) {
        return new Diagnostic(Severity.WARNING, where, message);
    }

    public static Diagnostic info(String where, String message) {
        return new Diagnostic(Severity.INFO, where, message);
    }

    @Override
    public String toString() {
        return severity + " " + where + ": " + message;
    }
}
