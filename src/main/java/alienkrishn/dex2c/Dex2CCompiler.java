package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.iface.MethodImplementation;


/**
 * Dalvik to C++ compiler, ported from the upstream dex2c compiler package.
 */
final class Dex2CCompiler implements Compiler {

    private final boolean obfuscate;
    private final boolean dynamicRegister;

    Dex2CCompiler(boolean obfuscate, boolean dynamicRegister) {
        this.obfuscate = obfuscate;
        this.dynamicRegister = dynamicRegister;
    }

    @Override
    public CompiledMethod compile(MethodRef method) {
        MethodImplementation implementation = method.method().getImplementation();
        if (implementation == null) {
            return null;
        }
        Blocks blocks = Blocks.build(implementation);
        IrBuilder builder = new IrBuilder(method, implementation, obfuscate, dynamicRegister);
        IrMethod irMethod = builder.process(blocks);
        if (irMethod == null) {
            return null;
        }
        return new CompiledMethod(irMethod.getSource(), irMethod.getPrototype());
    }
}
