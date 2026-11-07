package alienkrishn.dex2c;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Locates the Android SDK, NDK and cmake, then builds one ABI.
 */
final class Toolchain {

    private final File sdk;
    private final File ndk;
    private final File cmake;

    private Toolchain(File sdk, File ndk, File cmake) {
        this.sdk = sdk;
        this.ndk = ndk;
        this.cmake = cmake;
    }

    File ndk() {
        return ndk;
    }

    File sdk() {
        return sdk;
    }

    static Toolchain locate(File sdkOption, File ndkOption, File cmakeOption) {
        File sdk = sdkOption != null ? sdkOption : defaultSdk();
        File ndk = ndkOption != null ? ndkOption : defaultNdk(sdk);
        File cmake = cmakeOption != null ? cmakeOption : defaultCmake(sdk);

        if (ndk == null || !new File(ndk, "build/cmake/android.toolchain.cmake").isFile()) {
            throw new Dex2CException("Android NDK not found. Set ANDROID_NDK_HOME or pass --ndk.");
        }
        if (cmake == null) {
            throw new Dex2CException("cmake not found. Install cmake or pass --cmake.");
        }
        Log.debug("SDK: " + (sdk == null ? "(none)" : sdk));
        Log.debug("NDK: " + ndk);
        Log.debug("cmake: " + cmake);
        return new Toolchain(sdk, ndk, cmake);
    }

    private static File defaultSdk() {
        for (String name : new String[]{"ANDROID_HOME", "ANDROID_SDK_ROOT"}) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return new File(value);
            }
        }
        String prefix = System.getenv("PREFIX");
        if (prefix == null || prefix.isBlank()) {
            prefix = "/data/data/com.termux/files/usr";
        }
        File termux = new File(prefix, "opt/android-sdk");
        return termux.isDirectory() ? termux : null;
    }

    static File defaultNdk(File sdk) {
        for (String name : new String[]{"ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "ANDROID_NDK"}) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return new File(value);
            }
        }
        if (sdk != null) {
            File latest = latestDirectory(new File(sdk, "ndk"));
            if (latest != null) {
                return latest;
            }
            File sibling = new File(sdk.getParentFile(), "android-ndk");
            if (new File(sibling, "source.properties").isFile()) {
                return sibling;
            }
        }
        return null;
    }

    static File defaultCmake(File sdk) {
        if (sdk != null) {
            for (File root : new File[]{new File(sdk, "cmake"),
                    new File(sdk.getParentFile(), "android-cmake")}) {
                File latest = latestDirectory(root);
                if (latest != null) {
                    File bin = new File(latest, "bin/cmake");
                    if (bin.isFile()) {
                        return bin;
                    }
                }
            }
        }
        return findOnPath("cmake");
    }

    private static File latestDirectory(File root) {
        File[] files = root.listFiles();
        if (files == null) {
            return null;
        }
        return Arrays.stream(files)
                .filter(File::isDirectory)
                .max((a, b) -> compareVersions(a.getName(), b.getName()))
                .orElse(null);
    }

    private static int compareVersions(String first, String second) {
        String[] a = first.split("[^0-9]+");
        String[] b = second.split("[^0-9]+");
        int count = Math.min(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int left = parseNumber(a[i]);
            int right = parseNumber(b[i]);
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private static int parseNumber(String value) {
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static File findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(File.pathSeparator)) {
            File candidate = new File(dir, name);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    File build(File projectDir, File outputDir, String abi, int api) {
        File buildDir = new File(projectDir.getParentFile(), "build-" + abi);
        boolean ninja = findOnPath("ninja") != null;
        List<String> configure = new ArrayList<>(Arrays.asList(
                cmake.getAbsolutePath(),
                "-S", projectDir.getAbsolutePath(),
                "-B", buildDir.getAbsolutePath(),
                "-DCMAKE_TOOLCHAIN_FILE=" + new File(ndk, "build/cmake/android.toolchain.cmake"),
                "-DANDROID_ABI=" + abi,
                "-DANDROID_PLATFORM=android-" + api,
                "-DANDROID_STL=c++_static",
                "-DCMAKE_BUILD_TYPE=Release"));
        if (ninja) {
            configure.add("-G");
            configure.add("Ninja");
            configure.add("-DCMAKE_NINJA_FORCE_RESPONSE_FILE=ON");
        }
        run(configure, projectDir);

        List<String> build = Arrays.asList(
                cmake.getAbsolutePath(), "--build", buildDir.getAbsolutePath(),
                "--parallel", String.valueOf(Math.max(1,
                Runtime.getRuntime().availableProcessors() - 1)));
        run(build, projectDir);

        File so = findLibrary(buildDir, "libdex2c.so");
        if (so == null) {
            throw new Dex2CException("Native build did not produce libdex2c.so for " + abi);
        }
        outputDir.mkdirs();
        File target = new File(outputDir, "libdex2c.so");
        try {
            java.nio.file.Files.copy(so.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new Dex2CException("Cannot copy " + so + " to " + target, e);
        }
        return target;
    }

    private static File findLibrary(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findLibrary(file, name);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(name)) {
                return file;
            }
        }
        return null;
    }

    private void run(List<String> command, File workingDir) {
        Log.debug("exec: " + String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true);
        List<String> tail = new ArrayList<>();
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    tail.add(line);
                    if (tail.size() > 60) {
                        tail.remove(0);
                    }
                    if (Log.verbose()) {
                        Log.raw(Ansi.dim("  " + line));
                    }
                }
            }
            int code = process.waitFor();
            if (code != 0) {
                StringBuilder message = new StringBuilder(
                        "Native command failed (exit " + code + "): "
                                + String.join(" ", command));
                for (String line : tail) {
                    message.append('\n').append("  ").append(line);
                }
                throw new Dex2CException(message.toString());
            }
        } catch (IOException e) {
            throw new Dex2CException("Cannot run " + command.get(0), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Dex2CException("Interrupted while running " + command.get(0), e);
        }
    }
}
