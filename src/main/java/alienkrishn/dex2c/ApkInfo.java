package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ApkInfo {

    private static final Pattern DEX = Pattern.compile("classes(\\d*)\\.dex");
    private static final Set<String> SUPPORTED_ABIS = Set.of(
            "arm64-v8a", "armeabi-v7a", "x86", "x86_64", "riscv64");

    private ApkInfo() {
    }

    static Info read(File apk) throws IOException {
        try (ApkModule module = ApkModule.loadApkFile(apk)) {
            AndroidManifestBlock manifest = module.getAndroidManifest();
            String packageName = manifest.getPackageName();
            if (packageName == null || packageName.isBlank()) {
                throw new Dex2CException("The apk has no package name");
            }
            return new Info(packageName,
                    normalizeClass(manifest.getApplicationClassName(), packageName));
        }
    }

    static String normalizeClass(String name, String packageName) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            return name;
        }
        if (name.startsWith(".")) {
            return "L" + packageName + name.replace('.', '/') + ";";
        }
        if (name.indexOf('.') < 0) {
            return "L" + packageName + "/" + name + ";";
        }
        return "L" + name.replace('.', '/') + ";";
    }

    static List<DexSource> dexes(File apk) throws IOException {
        List<DexEntry> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk)) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                Matcher matcher = DEX.matcher(entry.getName());
                if (!matcher.matches()) {
                    continue;
                }
                int number = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    entries.add(new DexEntry(number, entry.getName(), bytes));
                }
            }
        }
        if (entries.isEmpty()) {
            throw new Dex2CException("No classes.dex in " + apk);
        }
        entries.sort(Comparator.comparingInt(DexEntry::number));

        List<DexSource> out = new ArrayList<>();
        for (DexEntry entry : entries) {
            DexBackedDexFile dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(), new ByteArrayInputStream(entry.bytes()));
            out.add(new DexSource(entry.name(), dex));
        }
        return out;
    }

    static List<String> abis(File apk) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                String name = iterator.nextElement().getName();
                if (!name.startsWith("lib/")) {
                    continue;
                }
                int slash = name.indexOf('/', 4);
                if (slash > 4) {
                    found.add(name.substring(4, slash));
                }
            }
        }
        return new ArrayList<>(found);
    }

    static List<String> chooseAbis(File apk, List<String> requested, boolean ignoreUnsupported)
            throws IOException {
        if (!requested.isEmpty()) {
            for (String abi : requested) {
                if (!SUPPORTED_ABIS.contains(abi)) {
                    throw new Dex2CException("Unsupported abi: " + abi);
                }
            }
            return requested;
        }

        List<String> present = abis(apk);
        if (present.isEmpty()) {
            return List.of("arm64-v8a", "armeabi-v7a");
        }

        List<String> out = new ArrayList<>();
        for (String abi : present) {
            if ("armeabi".equals(abi)) {
                abi = "armeabi-v7a";
            }
            if (SUPPORTED_ABIS.contains(abi)) {
                if (!out.contains(abi)) {
                    out.add(abi);
                }
            } else if (!ignoreUnsupported) {
                throw new Dex2CException("The apk contains the unsupported abi " + abi
                        + ". Pass --abi or --ignore-unsupported-abis.");
            } else {
                Log.warn("Skipping unsupported abi " + abi);
            }
        }
        if (out.isEmpty()) {
            throw new Dex2CException("The apk has no supported abi. Pass --abi.");
        }
        return out;
    }

    static final class Info {

        private final String packageName;
        private final String appClass;

        Info(String packageName, String appClass) {
            this.packageName = packageName;
            this.appClass = appClass;
        }

        String packageName() {
            return packageName;
        }

        String appClass() {
            return appClass;
        }

    }

    private static final class DexEntry {

        private final int number;
        private final String name;
        private final byte[] bytes;

        DexEntry(int number, String name, byte[] bytes) {
            this.number = number;
            this.name = name;
            this.bytes = bytes;
        }

        int number() {
            return number;
        }

        String name() {
            return name;
        }

        byte[] bytes() {
            return bytes;
        }
    }
}
