package alienkrishn.dex2c;

import java.util.ArrayList;
import java.util.List;

/**
 * Dalvik descriptor helpers, ported from the upstream dex2c util module.
 */
final class Types {

    private Types() {
    }

    static boolean isPrimitiveType(String type) {
        return type != null && type.length() == 1 && "ZBCSIJFD".indexOf(type.charAt(0)) >= 0;
    }

    static boolean isInt(String type) {
        return type != null && type.length() == 1 && "ZBCSIJ".indexOf(type.charAt(0)) >= 0;
    }

    static boolean isLong(String type) {
        return "J".equals(type);
    }

    static boolean isFloat(String type) {
        return type != null && type.length() == 1 && "FD".indexOf(type.charAt(0)) >= 0;
    }

    static boolean isRef(String type) {
        return type != null && (type.charAt(0) == 'L' || type.charAt(0) == '[');
    }

    static boolean isArray(String type) {
        return type != null && type.charAt(0) == '[';
    }

    static boolean isJavaLangObject(String type) {
        return "Ljava/lang/Object;".equals(type);
    }

    static boolean isJavaLangObjectArray(String type) {
        return isArray(type) && type.endsWith("Ljava/lang/Object;");
    }

    static int typeSize(String type) {
        return "J".equals(type) || "D".equals(type) ? 2 : 1;
    }

    static List<String> paramsType(String descriptor) {
        List<String> out = new ArrayList<>();
        int index = descriptor.indexOf('(') + 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            int start = index;
            while (descriptor.charAt(index) == '[') {
                index++;
            }
            if (descriptor.charAt(index) == 'L') {
                index = descriptor.indexOf(';', index) + 1;
            } else {
                index++;
            }
            out.add(descriptor.substring(start, index));
        }
        return out;
    }

    static String returnType(String descriptor) {
        return descriptor.substring(descriptor.indexOf(')') + 1);
    }

    static String methodDescriptor(List<? extends CharSequence> parameterTypes, String returnType) {
        StringBuilder out = new StringBuilder("(");
        for (CharSequence type : parameterTypes) {
            out.append(type);
        }
        return out.append(')').append(returnType).toString();
    }

    static String descriptor(String type, String name, List<? extends CharSequence> parameters,
                             String returnType) {
        return type + "->" + name + methodDescriptor(parameters, returnType);
    }

    static String typeName(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return descriptor;
        }
        switch (descriptor.charAt(0)) {
            case 'V':
                return "Void";
            case 'Z':
                return "Boolean";
            case 'B':
                return "Byte";
            case 'S':
                return "Short";
            case 'C':
                return "Char";
            case 'I':
                return "Int";
            case 'J':
                return "Long";
            case 'F':
                return "Float";
            case 'D':
                return "Double";
            case 'L':
                return descriptor.substring(1, descriptor.length() - 1);
            case '[':
                return descriptor;
            default:
                return descriptor;
        }
    }

    /** The type used in generated C++ source. */
    static String cdeclType(String type) {
        if (type == null || type.isEmpty()) {
            return "jobject";
        }
        switch (type.charAt(0)) {
            case 'Z':
            case 'B':
            case 'S':
            case 'C':
            case 'I':
                return "jint";
            case 'J':
                return "jlong";
            case 'F':
                return "jfloat";
            case 'D':
                return "jdouble";
            default:
                return "jobject";
        }
    }

    /** The type used in a JNI prototype. */
    static String nativeType(String type) {
        if (type == null || type.isEmpty()) {
            return "jobject";
        }
        switch (type.charAt(0)) {
            case 'Z':
                return "jboolean";
            case 'B':
                return "jbyte";
            case 'S':
                return "jshort";
            case 'C':
                return "jchar";
            case 'I':
                return "jint";
            case 'J':
                return "jlong";
            case 'F':
                return "jfloat";
            case 'D':
                return "jdouble";
            case 'V':
                return "void";
            case 'L':
                return "Ljava/lang/String;".equals(type) ? "jstring" : "jobject";
            case '[':
                return "jarray";
            default:
                return "jobject";
        }
    }

    /** The class name used by JNI FindClass / field and method lookups. */
    static String jniClass(String type) {
        if (type == null) {
            return null;
        }
        if (type.startsWith("L") && type.endsWith(";")) {
            return type.substring(1, type.length() - 1);
        }
        return type;
    }

    static String typeDescriptor(String type) {
        if (type == null || type.isEmpty()) {
            return "Object";
        }
        switch (type.charAt(0)) {
            case 'V':
                return "Void";
            case 'Z':
                return "Boolean";
            case 'B':
                return "Byte";
            case 'S':
                return "Short";
            case 'C':
                return "Char";
            case 'I':
                return "Int";
            case 'J':
                return "Long";
            case 'F':
                return "Float";
            case 'D':
                return "Double";
            default:
                return "Object";
        }
    }

    private static int primitiveOrder(String type) {
        switch (type) {
            case "Z":
                return 1;
            case "B":
                return 2;
            case "S":
            case "C":
                return 3;
            case "I":
                return 5;
            case "J":
                return 6;
            case "F":
                return 8;
            case "D":
                return 9;
            default:
                return 0;
        }
    }

    static String smallerType(String first, String second) {
        return primitiveOrder(first) < primitiveOrder(second) ? first : second;
    }

    static String biggerType(String first, String second) {
        return primitiveOrder(first) > primitiveOrder(second) ? first : second;
    }

    static String mergeType(String first, String second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        if ((isInt(first) && isInt(second)) || (isFloat(first) && isFloat(second))) {
            return biggerType(first, second);
        }
        if (isArray(first) || isArray(second)) {
            String merged = mergeArrayType(first, second);
            return merged == null ? "Ljava/lang/Object;" : merged;
        }
        if (isRef(first) || isRef(second)) {
            return mergeReferenceType(first, second);
        }
        return null;
    }

    private static String mergeArrayType(String first, String second) {
        if (isJavaLangObject(second)) {
            return second;
        }
        if (isJavaLangObject(first)) {
            return first;
        }
        if (isArray(first)) {
            if (isArray(second)) {
                String merged = mergeType(first.substring(1), second.substring(1));
                return merged == null ? null : "[" + merged;
            }
            return "Ljava/lang/Object;";
        }
        return mergeArrayType(second, first);
    }

    private static String mergeReferenceType(String first, String second) {
        if (first.equals(second)) {
            return first;
        }
        if (isJavaLangObject(first) && isRef(second)) {
            return first;
        }
        if (isJavaLangObject(second) && isRef(first)) {
            return second;
        }
        return "Ljava/lang/Object;";
    }

    /**
     * C++ string literal body. Non-ASCII and control characters become \xNN so the
     * generated source is plain ASCII.
     */
    static String hexEscapeString(String value) {
        StringBuilder out = new StringBuilder(value.length() * 4);
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            out.append(String.format("\\x%02x", b & 0xff));
        }
        return out.toString();
    }
}
