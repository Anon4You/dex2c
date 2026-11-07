package alienkrishn.dex2c;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Pipeline {

    private Pipeline() {
    }

    static void run(Options options) throws Exception {
        List<String> rules = loadRules(options);
        if (!options.allowGlobal && rules.stream().map(String::trim)
                .anyMatch(rule -> rule.equals(".*") || rule.equals("=.*"))) {
            throw new Dex2CException("A global catch-all rule (.*) is not allowed without --allow-global");
        }

        Log.info("Reading " + options.input);
        ApkInfo.Info info = ApkInfo.read(options.input);
        List<DexSource> dexes = ApkInfo.dexes(options.input);
        List<MethodRef> methods = new ArrayList<>();
        for (DexSource dex : dexes) {
            for (var classDef : dex.dexFile().getClasses()) {
                methods.addAll(MethodFilter.methods(dex.entryName(), classDef));
            }
        }
        Log.debug("Classes: " + (methods.isEmpty() ? 0 : methods.stream()
                .map(MethodRef::className).distinct().count()) + ", methods: " + methods.size());

        MethodFilter filter = new MethodFilter(rules, methods, options.skipSynthetic);
        List<MethodRef> candidates = new ArrayList<>();
        for (MethodRef method : methods) {
            if (filter.shouldCompile(method) && method.method().getImplementation() != null) {
                candidates.add(method);
            }
        }
        Log.info("Methods selected: " + candidates.size());
        if (candidates.isEmpty()) {
            throw new Dex2CException("No methods match the filter");
        }

        Compiler compiler = new Dex2CCompiler(options.obfuscate, options.dynamicRegister);
        Map<MethodRef, CompiledMethod> compiled = new LinkedHashMap<>();
        int failures = 0;
        for (MethodRef method : candidates) {
            if (method.jniLongName().length() > 220) {
                Log.warn("Skipping " + method + " (JNI name too long)");
                continue;
            }
            try {
                CompiledMethod result = compiler.compile(method);
                if (result == null || result.source() == null) {
                    continue;
                }
                compiled.put(method, result);
            } catch (Exception e) {
                failures++;
                if (failures <= 25) {
                    Log.warn("Failed " + method + ": " + e.getMessage());
                    if (Log.verbose()) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
        if (compiled.isEmpty()) {
            throw new Dex2CException("No method could be compiled");
        }
        Log.success("Compiled " + compiled.size() + " methods"
                + (failures == 0 ? "" : " (" + failures + " failed)"));

        File work = options.sourceDir != null
                ? options.sourceDir
                : WorkDir.create();
        boolean temporary = options.sourceDir == null;
        try {
            NativeProject project = NativeProject.create(work);
            for (Map.Entry<MethodRef, CompiledMethod> entry : compiled.entrySet()) {
                project.writeMethod(entry.getKey().jniLongName(), entry.getValue().source());
            }
            if (options.dynamicRegister) {
                project.writeDynamicRegister(compiled);
            }

            if (options.projectArchive != null) {
                Log.info("Writing native sources to " + options.projectArchive);
                Zips.zip(project.root(), options.projectArchive);
            }

            Map<String, File> libraries = new LinkedHashMap<>();
            if (!options.noBuild) {
                Toolchain toolchain = Toolchain.locate(options.sdk, options.ndk, options.cmake);
                List<String> abis = ApkInfo.chooseAbis(options.input, options.abis,
                        options.ignoreUnsupportedAbis);
                for (String abi : abis) {
                    Log.info("Building " + abi);
                    libraries.put(abi, toolchain.build(project.root(),
                            new File(work, "libs/" + abi), abi, options.api));
                }
            } else {
                Log.warn("Skipping the native build (--no-build)");
            }

            Set<String> keys = new LinkedHashSet<>();
            for (MethodRef method : compiled.keySet()) {
                keys.add(method.key());
            }
            DexPatcher.Result patched = DexPatcher.patch(dexes, keys, info.appClass(),
                    options.libraryName, options.customLoader);

            File output = options.output;
            File unsigned = File.createTempFile("dex2c-unsigned-", ".apk",
                    output.getAbsoluteFile().getParentFile());
            try {
                ApkRepacker.repack(options.input, unsigned, patched.dexFiles(), libraries,
                        patched.createdLoader() ? patched.appClass() : null);
                if (!options.sign) {
                    Files.move(unsigned.toPath(), output.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Log.info("Signing " + output);
                    try {
                        Signer.sign(unsigned, output);
                    } catch (Exception e) {
                        throw new Dex2CException("Failed to sign " + output
                                + ": " + e.getMessage(), e);
                    }
                }
            } finally {
                //noinspection ResultOfMethodCallIgnored
                unsigned.delete();
            }
            Log.success("Wrote " + output);
            if (!options.sign) {
                Log.warn("Output is unsigned. Sign " + output
                        + " before installing, or rerun with -s/--sign.");
            }
        } finally {
            if (temporary && !options.keepWork) {
                WorkDir.delete(work);
            } else if (temporary) {
                Log.info("Native project kept at " + work);
            }
        }
    }

    private static List<String> loadRules(Options options) throws IOException {
        List<String> rules = new ArrayList<>();
        if (options.filter != null) {
            if (!options.filter.isFile()) {
                throw new Dex2CException("Filter file not found: " + options.filter);
            }
            rules.addAll(Files.readAllLines(options.filter.toPath(), StandardCharsets.UTF_8));
        }
        rules.addAll(options.filterLines);
        if (rules.isEmpty()) {
            throw new Dex2CException("A filter is required: --filter FILE or --filter-line REGEX");
        }
        return rules;
    }

    static final class Options {

        File input;
        File output;
        File filter;
        File sdk;
        File ndk;
        File cmake;
        File sourceDir;
        File projectArchive;
        final List<String> filterLines = new ArrayList<>();
        final List<String> abis = new ArrayList<>();
        boolean allowGlobal;
        boolean skipSynthetic;
        boolean noBuild;
        boolean sign;
        boolean verbose;
        boolean keepWork;
        boolean ignoreUnsupportedAbis;
        boolean obfuscate;
        boolean dynamicRegister;
        int api = 24;
        String customLoader = "alienkrishn.dex2c.Loader";
        String libraryName = "dex2c";
    }
}
