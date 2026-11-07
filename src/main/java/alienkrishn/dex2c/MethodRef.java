package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;

import java.util.List;

final class MethodRef {

    private final String dexEntry;
    private final ClassDef classDef;
    private final Method method;
    private final String descriptor;

    MethodRef(String dexEntry, ClassDef classDef, Method method) {
        this.dexEntry = dexEntry;
        this.classDef = classDef;
        this.method = method;
        this.descriptor = Types.methodDescriptor(method.getParameterTypes(), method.getReturnType());
    }

    String dexEntry() {
        return dexEntry;
    }

    ClassDef classDef() {
        return classDef;
    }

    Method method() {
        return method;
    }

    String className() {
        return method.getDefiningClass();
    }

    String name() {
        return method.getName();
    }

    String descriptor() {
        return descriptor;
    }

    List<? extends CharSequence> parameterTypes() {
        return method.getParameterTypes();
    }

    String returnType() {
        return method.getReturnType();
    }

    int accessFlags() {
        return method.getAccessFlags();
    }

    boolean isStatic() {
        return AccessFlags.STATIC.isSet(accessFlags());
    }

    boolean isNative() {
        return AccessFlags.NATIVE.isSet(accessFlags());
    }

    boolean isSynthetic() {
        return AccessFlags.SYNTHETIC.isSet(accessFlags());
    }

    /** class + name + full descriptor, the form the filter regexes are matched against */
    String filterName() {
        return className() + name() + descriptor;
    }

    /** class + name + descriptor without the return type, the JNI long-name key */
    String overloadKey() {
        return className() + "->" + name() + descriptor.substring(0, descriptor.indexOf(')') + 1);
    }

    String jniLongName() {
        return JniNames.longName(className(), name(), descriptor);
    }

    String key() {
        return className() + "->" + name() + descriptor;
    }

    @Override
    public String toString() {
        return className() + "->" + name() + descriptor;
    }
}
