package alienkrishn.dex2c;

final class Log {

    private static boolean verbose;

    private Log() {
    }

    static void setVerbose(boolean value) {
        verbose = value;
    }

    static boolean verbose() {
        return verbose;
    }

    static void debug(String message) {
        if (verbose) {
            System.err.println(Ansi.dim("[debug] " + message));
        }
    }

    static void info(String message) {
        System.err.println(Ansi.cyan("[*] ") + message);
    }

    static void success(String message) {
        System.err.println(Ansi.green("[+] ") + message);
    }

    static void warn(String message) {
        System.err.println(Ansi.yellow("[!] ") + message);
    }

    static void error(String message) {
        System.err.println(Ansi.red("[-] ") + message);
    }

    static void raw(String message) {
        System.err.println(message);
    }
}
