package alienkrishn.dex2c;

final class Ansi {

    private static final boolean ENABLED = System.getenv("NO_COLOR") == null
            && (System.console() != null || "1".equals(System.getenv("DEX2C_COLOR")));

    private Ansi() {
    }

    private static String wrap(String text, String code) {
        return ENABLED ? code + text + "\u001b[0m" : text;
    }

    static String dim(String text) {
        return wrap(text, "\u001b[2m");
    }

    static String cyan(String text) {
        return wrap(text, "\u001b[36m");
    }

    static String green(String text) {
        return wrap(text, "\u001b[32m");
    }

    static String yellow(String text) {
        return wrap(text, "\u001b[33m");
    }

    static String red(String text) {
        return wrap(text, "\u001b[31m");
    }
}
