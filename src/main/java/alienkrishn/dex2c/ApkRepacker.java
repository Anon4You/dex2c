package alienkrishn.dex2c;

import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.InputSource;

import java.io.File;
import java.io.IOException;
import java.util.Map;

final class ApkRepacker {

    private ApkRepacker() {
    }

    static void repack(File input, File output, Map<String, byte[]> dexFiles,
                       Map<String, File> libraries, String appClass) throws IOException {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (ApkModule module = ApkModule.loadApkFile(input)) {
            if (appClass != null) {
                module.getAndroidManifest().setApplicationClassName(dotted(appClass));
            }
            module.setExtractNativeLibs(Boolean.TRUE);

            module.getZipEntryMap().removeIf(source -> isSignature(source.getName()));

            for (Map.Entry<String, byte[]> entry : dexFiles.entrySet()) {
                module.removeInputSource(entry.getKey());
                module.add(new ByteInputSource(entry.getValue(), entry.getKey()));
            }

            for (Map.Entry<String, File> entry : libraries.entrySet()) {
                String path = "lib/" + entry.getKey() + "/libdex2c.so";
                module.removeInputSource(path);
                ByteInputSource source = new ByteInputSource(
                        java.nio.file.Files.readAllBytes(entry.getValue().toPath()), path);
                source.setUncompressed(true);
                module.add(source);
            }

            module.writeApk(output);
        }
    }

    private static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.equals("META-INF/MANIFEST.MF")
                || upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC");
    }

    private static String dotted(String descriptor) {
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }
}
