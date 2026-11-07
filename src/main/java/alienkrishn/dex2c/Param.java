package alienkrishn.dex2c;

class Param {

    boolean declared = true;
    final Value value;
    boolean isThis;

    Param(Value value) {
        this.value = value;
    }

    Value getValue() {
        return value;
    }

    void visit(Writer writer) {
        writer.visitParam(value);
    }
}

final class ThisParam extends Param {

    ThisParam(Value value) {
        super(value);
        isThis = true;
    }

    @Override
    void visit(Writer writer) {
        writer.visitThis();
    }
}

final class MoveParam extends Instruction {

    final Param param;

    MoveParam(Param param) {
        this.param = param;
    }

    Param getParam() {
        return param;
    }

    @Override
    Value getValue() {
        return param.getValue();
    }

    @Override
    void visit(Writer writer) {
        writer.visitMoveParam(this);
    }

    @Override
    public String toString() {
        return "MoveParam(" + param.getValue() + ")";
    }
}
