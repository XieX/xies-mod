package mod.xie.xm.content.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects {@link Diagnostic}s during a parse. Not thread-safe; one per parse. */
public final class Diagnostics {

    private final List<Diagnostic> entries = new ArrayList<>();

    public void error(String where, String message) {
        entries.add(Diagnostic.error(where, message));
    }

    public void warning(String where, String message) {
        entries.add(Diagnostic.warning(where, message));
    }

    public void info(String where, String message) {
        entries.add(Diagnostic.info(where, message));
    }

    public void addAll(Diagnostics other) {
        entries.addAll(other.entries);
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(entries);
    }

    public List<Diagnostic> of(Diagnostic.Severity severity) {
        return entries.stream().filter(d -> d.severity() == severity).toList();
    }

    public List<Diagnostic> errors() {
        return of(Diagnostic.Severity.ERROR);
    }

    public List<Diagnostic> warnings() {
        return of(Diagnostic.Severity.WARNING);
    }

    public boolean hasErrors() {
        return !errors().isEmpty();
    }

    public int count() {
        return entries.size();
    }

    /** A short summary suitable for a single log line. */
    public String summary() {
        return errors().size() + " errors, " + warnings().size() + " warnings";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(summary());
        for (Diagnostic entry : entries) {
            sb.append(System.lineSeparator()).append("  ").append(entry);
        }
        return sb.toString();
    }
}
