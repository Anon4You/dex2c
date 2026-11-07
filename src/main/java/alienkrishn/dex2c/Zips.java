package alienkrishn.dex2c;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class Zips {

    private Zips() {
    }

    static void zip(File directory, File output) throws IOException {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            add(zip, directory, directory);
        }
    }

    private static void add(ZipOutputStream zip, File root, File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                add(zip, root, child);
            }
        }
        if (!file.isFile()) {
            return;
        }
        String name = root.toPath().relativize(file.toPath()).toString()
                .replace(File.separatorChar, '/');
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file.toPath(), zip);
        zip.closeEntry();
    }
}
