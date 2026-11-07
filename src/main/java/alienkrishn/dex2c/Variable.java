package alienkrishn.dex2c;

class Variable extends Value {

    final int register;
    final int version;

    Variable(int register, int version) {
        this.register = register;
        this.version = version;
    }

    int getRegister() {
        return register;
    }

    @Override
    public String toString() {
        return "v" + (register >= 0 ? register : "Result") + "_" + version;
    }
}
