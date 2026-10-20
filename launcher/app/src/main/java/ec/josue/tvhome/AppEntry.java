package ec.josue.tvhome;

/** Una app instalada que se puede abrir desde el TV. */
final class AppEntry {
    final String pkg;
    final String label;
    final int accent;

    AppEntry(String pkg, String label, int accent) {
        this.pkg = pkg;
        this.label = label;
        this.accent = accent;
    }
}
