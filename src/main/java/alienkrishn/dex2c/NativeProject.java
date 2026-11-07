package alienkrishn.dex2c;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates the CMake project that carries the bundled runtime and the generated methods.
 */
final class NativeProject {

    private static final List<String> RUNTIME_FILES = List.of(
            "CMakeLists.txt",
            "nc/Dex2C.cpp",
            "nc/Dex2C.h",
            "nc/DynamicRegister.h",
            "nc/ScopedLocalRef.h",
            "nc/ScopedPthreadMutexLock.h",
            "nc/obfuscate.h",
            "nc/well_known_classes.cpp",
            "nc/well_known_classes.h");

    private final File root;
    private final File ncDir;

    private NativeProject(File root, File ncDir) {
        this.root = root;
        this.ncDir = ncDir;
    }

    static NativeProject create(File workDir) throws IOException {
        File root = new File(workDir, "native");
        File nc = new File(root, "nc");
        if (!nc.mkdirs() && !nc.isDirectory()) {
            throw new IOException("Cannot create " + nc);
        }
        for (String name : RUNTIME_FILES) {
            File target = new File(root, name);
            target.getParentFile().mkdirs();
            try (InputStream in = NativeProject.class.getResourceAsStream("/runtime/" + name)) {
                if (in == null) {
                    throw new IOException("Bundled runtime file missing: " + name);
                }
                Files.copy(in, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        NativeProject project = new NativeProject(root, nc);
        project.writeDummyDynamicRegister();
        return project;
    }

    private void writeDummyDynamicRegister() throws IOException {
        String source = """
                #include "DynamicRegister.h"

                const char *dynamic_register_compile_methods(JNIEnv *env) {
                    (void) env;
                    return nullptr;
                }
                """;
        Files.writeString(new File(ncDir, "DynamicRegister.cpp").toPath(), source,
                StandardCharsets.UTF_8);
    }

    File root() {
        return root;
    }

    void writeMethod(String jniLongName, String source) throws IOException {
        File file = new File(ncDir, jniLongName + ".cpp");
        Files.writeString(file.toPath(), "#include \"Dex2C.h\"\n" + source,
                StandardCharsets.UTF_8);
    }

    void writeDynamicRegister(Map<MethodRef, CompiledMethod> methods) throws IOException {
        Map<String, List<MethodRef>> byClass = new TreeMap<>();
        for (MethodRef method : methods.keySet()) {
            String className = method.className();
            byClass.computeIfAbsent(className.substring(1, className.length() - 1),
                    key -> new ArrayList<>()).add(method);
        }
        StringBuilder externs = new StringBuilder();
        StringBuilder blocks = new StringBuilder();
        int index = 0;
        for (Map.Entry<String, List<MethodRef>> entry : byClass.entrySet()) {
            List<MethodRef> classMethods = entry.getValue();
            classMethods.sort(Comparator.comparing(MethodRef::name)
                    .thenComparing(MethodRef::descriptor));
            for (MethodRef method : classMethods) {
                externs.append("extern ").append(methods.get(method).prototype())
                        .append(";\n");
            }
            blocks.append("clazz = env->FindClass(\"").append(entry.getKey())
                    .append("\");\nif (clazz == nullptr)\n    return \"Class not found: ")
                    .append(entry.getKey()).append("\";\n");
            blocks.append("const JNINativeMethod export_method_").append(index)
                    .append("[] = {\n");
            for (int i = 0; i < classMethods.size(); i++) {
                MethodRef method = classMethods.get(i);
                if (i > 0) {
                    blocks.append(",\n");
                }
                blocks.append("{\"").append(method.name()).append("\", \"")
                        .append(method.descriptor()).append("\", (void *)")
                        .append(method.jniLongName()).append('}');
            }
            blocks.append("\n};\nenv->RegisterNatives(clazz, export_method_")
                    .append(index).append(", ").append(classMethods.size())
                    .append(");\nenv->DeleteLocalRef(clazz);\n");
            index++;
        }
        String body = byClass.isEmpty()
                ? "    (void) env;\n    return nullptr;\n"
                : "    jclass clazz;\n" + blocks + "    return nullptr;\n";
        String source = "#include \"DynamicRegister.h\"\n\n" + externs
                + "\nconst char *dynamic_register_compile_methods(JNIEnv *env) {\n"
                + body + "}\n";
        Files.writeString(new File(ncDir, "DynamicRegister.cpp").toPath(), source,
                StandardCharsets.UTF_8);
    }
}
