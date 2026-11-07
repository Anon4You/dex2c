package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler;
import com.android.tools.smali.dexlib2.immutable.ImmutableField;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marks compiled methods native and wires the runtime loader into the application class.
 */
final class DexPatcher {

    private static final String APPLICATION = "Landroid/app/Application;";

    private DexPatcher() {
    }

    static Result patch(List<DexSource> dexes, Set<String> compiled, String manifestAppClass,
                        String libraryName, String customLoader) throws IOException {
        Set<String> allTypes = new HashSet<>();
        for (DexSource source : dexes) {
            for (ClassDef classDef : source.dexFile().getClasses()) {
                allTypes.add(classDef.getType());
            }
        }

        String appClass = manifestAppClass;
        boolean createLoader = false;
        if (appClass == null) {
            appClass = uniqueLoader(allTypes, customLoader);
            createLoader = true;
        } else if (!allTypes.contains(appClass)) {
            Log.warn("Application class " + appClass + " is not in this apk, adding a loader");
            appClass = uniqueLoader(allTypes, customLoader);
            createLoader = true;
        }

        Map<String, byte[]> output = new LinkedHashMap<>();
        boolean loaderAdded = false;
        for (DexSource source : dexes) {
            List<ImmutableClassDef> classes = new ArrayList<>();
            for (ClassDef classDef : source.dexFile().getClasses()) {
                boolean isApp = classDef.getType().equals(appClass);
                classes.add(rebuild(classDef, compiled, isApp, libraryName));
                if (isApp) {
                    loaderAdded = true;
                }
            }
            if (createLoader && !loaderAdded && output.isEmpty()) {
                classes.add(loaderClass(appClass, libraryName));
                loaderAdded = true;
            }
            output.put(source.entryName(), write(source, classes));
        }

        return new Result(output, appClass, createLoader);
    }

    private static ImmutableClassDef rebuild(ClassDef classDef, Set<String> compiled,
                                             boolean isApp, String libraryName) {
        List<ImmutableField> staticFields = new ArrayList<>();
        for (Field field : classDef.getStaticFields()) {
            staticFields.add(ImmutableField.of(field));
        }
        List<ImmutableField> instanceFields = new ArrayList<>();
        for (Field field : classDef.getInstanceFields()) {
            instanceFields.add(ImmutableField.of(field));
        }

        List<ImmutableMethod> direct = new ArrayList<>();
        boolean hasClinit = false;
        for (Method method : classDef.getDirectMethods()) {
            if (isApp && "<clinit>".equals(method.getName())) {
                hasClinit = true;
            }
            direct.add(transform(method, compiled, isApp, libraryName));
        }
        if (isApp && !hasClinit) {
            direct.add(new ImmutableMethod(
                    classDef.getType(), "<clinit>", List.of(), "V",
                    AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                    Set.of(), Set.of(), prependLibraryLoad(null, libraryName)));
        }
        List<ImmutableMethod> virtual = new ArrayList<>();
        for (Method method : classDef.getVirtualMethods()) {
            virtual.add(transform(method, compiled, isApp, libraryName));
        }

        return new ImmutableClassDef(
                classDef.getType(),
                classDef.getAccessFlags(),
                classDef.getSuperclass(),
                classDef.getInterfaces(),
                classDef.getSourceFile(),
                classDef.getAnnotations(),
                staticFields,
                instanceFields,
                direct,
                virtual);
    }

    private static ImmutableMethod transform(Method method, Set<String> compiled,
                                             boolean isApp, String libraryName) {
        String key = Types.descriptor(method.getDefiningClass(), method.getName(),
                method.getParameterTypes(), method.getReturnType());

        if (compiled.contains(key)) {
            return new ImmutableMethod(
                    method.getDefiningClass(),
                    method.getName(),
                    method.getParameters(),
                    method.getReturnType(),
                    method.getAccessFlags() | AccessFlags.NATIVE.getValue(),
                    method.getAnnotations(),
                    method.getHiddenApiRestrictions(),
                    null);
        }

        if (isApp && "<clinit>".equals(method.getName())) {
            return new ImmutableMethod(
                    method.getDefiningClass(),
                    method.getName(),
                    method.getParameters(),
                    method.getReturnType(),
                    method.getAccessFlags(),
                    method.getAnnotations(),
                    method.getHiddenApiRestrictions(),
                    prependLibraryLoad(method.getImplementation(), libraryName));
        }

        return ImmutableMethod.of(method);
    }

    private static ImmutableMethodImplementation prependLibraryLoad(MethodImplementation original,
                                                                    String libraryName) {
        int scratch;
        int registerCount;
        List<Instruction> instructions = new ArrayList<>();
        List<ImmutableTryBlock> tryBlocks = new ArrayList<>();

        if (original == null) {
            scratch = 0;
            registerCount = 1;
            instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        } else {
            scratch = original.getRegisterCount();
            registerCount = original.getRegisterCount() + 1;
            for (Instruction instruction : original.getInstructions()) {
                instructions.add(instruction);
            }
        }

        int addedUnits = 5;
        for (TryBlock<? extends ExceptionHandler> tryBlock : original == null
                ? List.<TryBlock<? extends ExceptionHandler>>of() : original.getTryBlocks()) {
            List<ImmutableExceptionHandler> handlers = new ArrayList<>();
            for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                handlers.add(new ImmutableExceptionHandler(handler.getExceptionType(),
                        handler.getHandlerCodeAddress() + addedUnits));
            }
            tryBlocks.add(new ImmutableTryBlock(tryBlock.getStartCodeAddress() + addedUnits,
                    tryBlock.getCodeUnitCount(), handlers));
        }

        instructions.add(0, new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC, 1, scratch, 0, 0, 0, 0,
                new ImmutableMethodReference("Ljava/lang/System;", "loadLibrary",
                        List.of("Ljava/lang/String;"), "V")));
        instructions.add(0, new ImmutableInstruction21c(
                Opcode.CONST_STRING, scratch, new ImmutableStringReference(libraryName)));

        return new ImmutableMethodImplementation(registerCount, instructions, tryBlocks, List.of());
    }

    private static ImmutableClassDef loaderClass(String type, String libraryName) {
        ImmutableMethod clinit = new ImmutableMethod(
                type, "<clinit>", List.of(), "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Set.of(), Set.of(),
                prependLibraryLoad(null, libraryName));

        ImmutableMethod init = new ImmutableMethod(
                type, "<init>", List.of(), "V",
                AccessFlags.PUBLIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Set.of(), Set.of(),
                new ImmutableMethodImplementation(1, List.of(
                        new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                                new ImmutableMethodReference(APPLICATION, "<init>",
                                        List.of(), "V")),
                        new ImmutableInstruction10x(Opcode.RETURN_VOID)),
                        List.of(), List.of()));

        return new ImmutableClassDef(
                type,
                AccessFlags.PUBLIC.getValue(),
                APPLICATION,
                List.of(),
                "Loader.java",
                Set.of(),
                List.of(),
                List.of(),
                List.of(clinit, init),
                List.of());
    }

    private static String uniqueLoader(Collection<String> types, String customLoader) {
        String base = customLoader.replace('.', '/');
        if (!base.startsWith("L")) {
            base = "L" + base;
        }
        if (!base.endsWith(";")) {
            base = base + ";";
        }
        String candidate = base;
        int index = 2;
        while (types.contains(candidate)) {
            candidate = base.substring(0, base.length() - 1) + "_" + index + ";";
            index++;
        }
        return candidate;
    }

    private static byte[] write(DexSource source, List<ImmutableClassDef> classes)
            throws IOException {
        ImmutableDexFile dex = new ImmutableDexFile(source.dexFile().getOpcodes(), classes);
        File temporary = File.createTempFile("dex2c-", ".dex");
        try {
            DexFileFactory.writeDexFile(temporary.getAbsolutePath(), dex);
            return Files.readAllBytes(temporary.toPath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
        }
    }

    static final class Result {

        private final Map<String, byte[]> dexFiles;
        private final String appClass;
        private final boolean createdLoader;

        Result(Map<String, byte[]> dexFiles, String appClass, boolean createdLoader) {
            this.dexFiles = dexFiles;
            this.appClass = appClass;
            this.createdLoader = createdLoader;
        }

        Map<String, byte[]> dexFiles() {
            return dexFiles;
        }

        String appClass() {
            return appClass;
        }

        boolean createdLoader() {
            return createdLoader;
        }
    }
}
