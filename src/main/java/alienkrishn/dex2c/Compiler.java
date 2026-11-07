package alienkrishn.dex2c;

interface Compiler {

    CompiledMethod compile(MethodRef method) throws Exception;
}
