package alienkrishn.dex2c;

/**
 * JNI name mangling, ported from the upstream dex2c util module.
 */
final class JniNames {

    private JniNames() {
    }

    static String mangle(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if (ch == '.' || ch == '/') {
                out.append('_');
            } else if (ch == '_') {
                out.append("_1");
            } else if (ch == ';') {
                out.append("_2");
            } else if (ch == '[') {
                out.append("_3");
            } else {
                out.append(String.format("_0%04x", (int) ch));
            }
        }
        return out.toString();
    }

    static String shortName(String clsName, String methodName) {
        String name = clsName.substring(1, clsName.length() - 1);
        return "Java_" + mangle(name) + "_" + mangle(methodName);
    }

    static String longName(String clsName, String methodName, String signature) {
        String signaturePart = signature.substring(1);
        int end = signaturePart.indexOf(')');
        return shortName(clsName, methodName) + "__" + mangle(signaturePart.substring(0, end));
    }
}
