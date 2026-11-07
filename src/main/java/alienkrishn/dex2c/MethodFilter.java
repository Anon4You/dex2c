package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Whitelist / blacklist selection, ported from the upstream dex2c MethodFilter.
 */
final class MethodFilter {

    private final List<Pattern> compileFilters = new ArrayList<>();
    private final List<Pattern> keepFilters = new ArrayList<>();
    private final Set<String> compileFullMatch = new HashSet<>();

    private final Set<String> conflictMethods = new HashSet<>();
    private final Set<String> nativeMethods = new HashSet<>();
    private final Set<String> annotatedMethods = new HashSet<>();

    private final boolean skipSynthetic;

    MethodFilter(List<String> rules, Collection<MethodRef> methods, boolean skipSynthetic) {
        this.skipSynthetic = skipSynthetic;
        loadRules(rules);
        initConflicts(methods);
        initNativeMethods(methods);
        initAnnotatedMethods(methods);
    }

    private void loadRules(List<String> rules) {
        for (String raw : rules) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            boolean keep = line.startsWith("!");
            boolean full = line.startsWith("=");
            if (keep || full) {
                line = line.substring(1).trim();
            }
            try {
                if (full) {
                    compileFullMatch.add(line);
                } else {
                    Pattern pattern = Pattern.compile(line);
                    if (keep) {
                        keepFilters.add(pattern);
                    } else {
                        compileFilters.add(pattern);
                    }
                }
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid filter rule: " + raw, e);
            }
        }
    }

    private void initConflicts(Collection<MethodRef> methods) {
        Map<String, MethodRef> seen = new HashMap<>();
        for (MethodRef method : methods) {
            MethodRef previous = seen.putIfAbsent(method.overloadKey(), method);
            if (previous != null) {
                conflictMethods.add(previous.key());
                conflictMethods.add(method.key());
            }
        }
    }

    private void initNativeMethods(Collection<MethodRef> methods) {
        for (MethodRef method : methods) {
            if (method.isNative()) {
                nativeMethods.add(method.className() + "->" + method.name());
            }
        }
    }

    private void initAnnotatedMethods(Collection<MethodRef> methods) {
        Set<String> annotatedClasses = new HashSet<>();
        for (MethodRef ref : methods) {
            ClassDef classDef = ref.classDef();
            boolean annotatedClass = annotatedClasses.contains(classDef.getType());
            if (!annotatedClass) {
                for (Annotation annotation : classDef.getAnnotations()) {
                    if (annotation.getType().endsWith("Dex2C;")) {
                        annotatedClass = true;
                        annotatedClasses.add(classDef.getType());
                        break;
                    }
                }
            }
            if (annotatedClass) {
                if (!ref.isSynthetic() && !ref.isNative()) {
                    annotatedMethods.add(ref.key());
                }
                continue;
            }
            for (Annotation annotation : ref.method().getAnnotations()) {
                if (annotation.getType().endsWith("Dex2C;")) {
                    if (!ref.isSynthetic() && !ref.isNative()) {
                        annotatedMethods.add(ref.key());
                    }
                    break;
                }
            }
        }
    }

    boolean shouldCompile(MethodRef method) {
        if (conflictMethods.contains(method.key())) {
            return false;
        }
        if (skipSynthetic && method.isSynthetic()) {
            return false;
        }
        if (method.isNative()) {
            return false;
        }
        if ("<clinit>".equals(method.name())) {
            return false;
        }
        if (nativeMethods.contains(method.className() + "->" + method.name())) {
            return false;
        }
        String fullName = method.filterName();
        for (Pattern keep : keepFilters) {
            if (keep.matcher(fullName).find()) {
                return false;
            }
        }
        if (compileFullMatch.contains(fullName)) {
            return true;
        }
        if (annotatedMethods.contains(method.key())) {
            return true;
        }
        for (Pattern compile : compileFilters) {
            if (compile.matcher(fullName).find()) {
                return true;
            }
        }
        return false;
    }

    static List<MethodRef> methods(String dexEntry, ClassDef classDef) {
        List<MethodRef> out = new ArrayList<>();
        for (Method method : classDef.getMethods()) {
            out.add(new MethodRef(dexEntry, classDef, method));
        }
        return out;
    }
}
