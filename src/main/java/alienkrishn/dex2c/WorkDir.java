package alienkrishn.dex2c;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class WorkDir {

    private WorkDir() {
    }

    static File create() throws IOException {
        String tmp = System.getenv("TMPDIR");
        if (tmp == null || tmp.isBlank()) {
            String prefix = System.getenv("PREFIX");
            if (prefix != null && !prefix.isBlank()) {
                tmp = prefix + "/tmp";
            }
        }
        File base = tmp == null || tmp.isBlank()
                ? new File(System.getProperty("java.io.tmpdir"))
                : new File(tmp);
        if (!base.isDirectory() && !base.mkdirs()) {
            throw new IOException("Cannot create temporary directory " + base);
        }
        Path path = Files.createTempDirectory(base.toPath(), "dex2c-");
        return path.toFile();
    }

    static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
