package alienkrishn.dex2c;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "dex2c",
        mixinStandardHelpOptions = true,
        versionProvider = Dex2C.Version.class,
        description = "Compiles Dalvik bytecode to C++ JNI methods.")
public final class Dex2C implements Callable<Integer> {

    private static final String[] LOGO = {
            " \u2584\u2584\u2584\u2584   \u2584\u2584\u2584\u2584\u2584\u2584 \u2584    \u2584  \u2584\u2584\u2584\u2584    \u2584\u2584\u2584 ",
            " \u2588   \u2580\u2584 \u2588       \u2588  \u2588  \u2580   \u2580\u2588 \u2584\u2580   \u2580",
            " \u2588    \u2588 \u2588\u2584\u2584\u2584\u2584\u2584   \u2588\u2588       \u2584\u2580 \u2588     ",
            " \u2588    \u2588 \u2588       \u2584\u2580\u2580\u2584    \u2584\u2580   \u2588     ",
            " \u2588\u2584\u2584\u2584\u2580  \u2588\u2584\u2584\u2584\u2584\u2584 \u2584\u2580  \u2580\u2584 \u2584\u2588\u2584\u2584\u2584\u2584  \u2580\u2584\u2584\u2584\u2580"
    };
    private static final String DESCRIPTION = "Dalvik-to-C++ JNI compiler for Termux";

    @Parameters(index = "0", arity = "0..1", paramLabel = "APK",
            description = "Input apk file.")
    private File positional;

    @Option(names = {"-a", "--input"}, paramLabel = "APK", description = "Input apk file.")
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "APK",
            description = "Output apk (default: <input>-dex2c.apk).")
    private File output;

    @Option(names = {"-f", "--filter"}, paramLabel = "FILE",
            description = "Method filter file (one regex per line, ! to keep, = for full match).")
    private File filter;

    @Option(names = "--filter-line", paramLabel = "REGEX",
            description = "Extra filter rule, can be repeated.")
    private final List<String> filterLines = new ArrayList<>();

    @Option(names = "--allow-global",
            description = "Allow the .* catch-all rule (not recommended).")
    private boolean allowGlobal;

    @Option(names = "--skip-synthetic", description = "Skip synthetic methods.")
    private boolean skipSynthetic;

    @Option(names = "--no-build", description = "Only generate the native sources.")
    private boolean noBuild;

    @Option(names = {"-s", "--sign"},
            description = "Sign the output apk with the bundled Android test key.")
    private boolean sign;

    @Option(names = {"-p", "--obfuscate"}, description = "Obfuscate string constants.")
    private boolean obfuscate;

    @Option(names = {"-d", "--dynamic-register"},
            description = "Export native methods using RegisterNatives.")
    private boolean dynamicRegister;

    @Option(names = "--custom-loader", paramLabel = "CLASS",
            description = "Loader class used when the app has no Application class.")
    private String customLoader = "alienkrishn.dex2c.Loader";

    @Option(names = "--abi", paramLabel = "ABI", split = ",",
            description = "Abis to build (default: the abis present in the apk).")
    private final List<String> abis = new ArrayList<>();

    @Option(names = "--ignore-unsupported-abis",
            description = "Skip apk abis the NDK cannot build instead of failing.")
    private boolean ignoreUnsupportedAbis;

    @Option(names = "--api", paramLabel = "LEVEL", description = "Minimum Android api (default: 24).")
    private int api = 24;

    @Option(names = "--sdk", paramLabel = "DIR", description = "Android SDK directory.")
    private File sdk;

    @Option(names = "--ndk", paramLabel = "DIR", description = "Android NDK directory.")
    private File ndk;

    @Option(names = "--cmake", paramLabel = "FILE", description = "cmake executable.")
    private File cmake;

    @Option(names = "--source-dir", paramLabel = "DIR",
            description = "Write the native project here and keep it.")
    private File sourceDir;

    @Option(names = "--project-archive", paramLabel = "ZIP",
            description = "Write the generated native sources to this zip.")
    private File projectArchive;

    @Option(names = "--keep-work", description = "Keep the temporary native project.")
    private boolean keepWork;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output.")
    private boolean verbose;

    @Override
    public Integer call() {
        Log.setVerbose(verbose);
        try {
            Pipeline.Options options = new Pipeline.Options();
            options.input = input != null ? input : positional;
            if (options.input == null) {
                throw new Dex2CException("An input apk is required");
            }
            if (!options.input.isFile()) {
                throw new Dex2CException("Input apk not found: " + options.input);
            }
            if (!options.input.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                throw new Dex2CException("Only apk input is supported for now");
            }
            options.output = output != null ? output : defaultOutput(options.input);
            options.filter = filter;
            options.filterLines.addAll(filterLines);
            options.allowGlobal = allowGlobal;
            options.skipSynthetic = skipSynthetic;
            options.noBuild = noBuild;
            options.sign = sign;
            options.obfuscate = obfuscate;
            options.dynamicRegister = dynamicRegister;
            options.abis.addAll(abis);
            options.ignoreUnsupportedAbis = ignoreUnsupportedAbis;
            options.api = api;
            options.sdk = sdk;
            options.ndk = ndk;
            options.cmake = cmake;
            options.sourceDir = sourceDir;
            options.projectArchive = projectArchive;
            options.keepWork = keepWork;
            options.customLoader = customLoader;
            if (!customLoader.contains(".") || customLoader.startsWith(".")) {
                throw new Dex2CException("The loader class must be fully qualified");
            }
            Pipeline.run(options);
            return 0;
        } catch (Dex2CException e) {
            Log.error(e.getMessage());
            if (verbose) {
                e.printStackTrace(System.err);
            }
            return 1;
        } catch (Exception e) {
            Log.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            if (verbose) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    private static File defaultOutput(File input) {
        String name = input.getName();
        if (name.endsWith(".apk")) {
            name = name.substring(0, name.length() - 4);
        }
        return new File(input.getAbsoluteFile().getParentFile(), name + "-dex2c.apk");
    }

    public static void main(String[] args) {
        Identity.ensureValid();
        printBanner();
        System.exit(new CommandLine(new Dex2C()).execute(args));
    }

    private static void printBanner() {
        String[] info = {version(),
                Identity.author() + " [" + Identity.handle() + "]",
                Identity.repo()};
        for (int i = 0; i < LOGO.length; i++) {
            String line = Ansi.cyan(LOGO[i]);
            if (i < info.length) {
                line += "  " + info[i];
            }
            System.out.println(line);
        }
        System.out.println();
        System.out.println(" " + DESCRIPTION);
        System.out.println();
    }

    private static String version() {
        try (InputStream in = Dex2C.class.getResourceAsStream("/dex2c-version.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "dev";
    }

    static final class Version implements IVersionProvider {

        @Override
        public String[] getVersion() {
            return new String[]{
                    "dex2c " + version(),
                    DESCRIPTION,
                    Identity.author() + " [" + Identity.handle() + "]",
                    "https://" + Identity.repo()
            };
        }
    }
}
