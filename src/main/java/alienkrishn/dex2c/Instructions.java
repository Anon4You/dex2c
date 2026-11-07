package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.iface.instruction.SwitchElement;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;

import java.util.ArrayList;
import java.util.List;

final class LoadConstant extends Instruction {

    final boolean obfus;

    LoadConstant(Value value, Constant cst) {
        this(value, cst, false);
    }

    LoadConstant(Value value, Constant cst, boolean obfus) {
        this.value = value;
        this.value.refineType(cst.getType());
        this.value.isConst = true;
        if (cst.getType() == null && cst.getConstant() instanceof Number
                && ((Number) cst.getConstant()).doubleValue() != 0) {
            this.value.allowRef = false;
        }
        operands.add(cst);
        this.obfus = obfus;
    }

    Constant getCst() {
        return (Constant) operands.get(0);
    }

    @Override
    String getClassDesc() {
        if ("Ljava/lang/Class;".equals(getCst().getType())) {
            return (String) getCst().getConstant();
        }
        return null;
    }

    @Override
    boolean resolveType() {
        return false;
    }

    @Override
    void visit(Writer writer) {
        writer.visitLoadConstant(this, obfus);
    }

    @Override
    public String toString() {
        return value + " = " + operands.get(0);
    }
}

class MoveExpression extends Instruction {

    MoveExpression(Value lhs, Value rhs) {
        value = lhs;
        operands.add(rhs);
        rhs.addUser(this);
    }

    @Override
    void visit(Writer writer) {
        writer.visitMove(value, operands.get(0));
    }

    @Override
    boolean resolveType() {
        String opType = operands.get(0).getType();
        String valueType = value.getType();
        String newType = Types.mergeType(opType, valueType);
        if (newType == null) {
            return false;
        }
        boolean changed = false;
        if (operands.get(0).typeSealed) {
            changed |= setValueType(newType);
        } else {
            changed |= value.refineType(newType);
            changed |= operands.get(0).refineType(newType);
        }
        return changed;
    }

    @Override
    public String toString() {
        return value + " = " + operands.get(0);
    }
}

final class MoveResultExpression extends MoveExpression {

    MoveResultExpression(Value lhs, Value rhs) {
        super(lhs, rhs);
    }

    @Override
    void visit(Writer writer) {
        writer.visitMoveResult(this, value, operands.get(0));
    }
}

final class ArrayStoreInstruction extends Instruction {

    final String elemType;

    ArrayStoreInstruction(Value rhs, Value array, Value index, String type) {
        rhs.addUser(this);
        array.addUser(this);
        index.addUser(this);
        operands.add(rhs);
        operands.add(array);
        operands.add(index);
        elemType = type;
    }

    Value array() {
        return operands.get(1);
    }

    Value index() {
        return operands.get(2);
    }

    @Override
    boolean resolveType() {
        String et = getElemType();
        boolean changed = index().setType("I");
        if (et != null) {
            changed |= operands.get(0).refineType(et);
            changed |= array().refineType("[" + et);
        }
        return changed;
    }

    String getElemType() {
        if (elemType != null) {
            return elemType;
        }
        String type1 = Types.isArray(array().getType()) ? array().getType().substring(1) : null;
        String type2 = operands.get(0).getType();
        return Types.mergeType(type1, type2);
    }

    @Override
    void visit(Writer writer) {
        writer.visitAstore(this, array(), index(), operands.get(0));
    }

    @Override
    public String toString() {
        return array() + "[" + index() + "] = " + operands.get(0);
    }
}

final class StaticInstruction extends Instruction {

    final String cls;
    final String ftype;
    final String name;
    final String clsdesc;

    StaticInstruction(Value rhs, String klass, String ftype, String name) {
        cls = Types.typeName(klass);
        this.ftype = ftype;
        this.name = name;
        clsdesc = klass;
        rhs.addUser(this);
        operands.add(rhs);
    }

    @Override
    String getClassDesc() {
        return clsdesc;
    }

    @Override
    String getFieldKey() {
        return clsdesc + "." + name;
    }

    @Override
    boolean resolveType() {
        return operands.get(0).refineType(ftype);
    }

    @Override
    void visit(Writer writer) {
        writer.visitPutStatic(this, clsdesc, name, ftype, operands.get(0));
    }

    @Override
    public String toString() {
        return cls + "." + name + " = " + operands.get(0);
    }
}

final class InstanceInstruction extends Instruction {

    final String atype;
    final String cls;
    final String name;
    final String clsdesc;

    InstanceInstruction(Value rhs, Value lhs, String klass, String atype, String name) {
        this.atype = atype;
        cls = Types.typeName(klass);
        this.name = name;
        clsdesc = klass;
        lhs.addUser(this);
        rhs.addUser(this);
        operands.add(lhs);
        operands.add(rhs);
    }

    @Override
    String getClassDesc() {
        return clsdesc;
    }

    @Override
    String getFieldKey() {
        return clsdesc + "." + name;
    }

    @Override
    boolean resolveType() {
        boolean changed = operands.get(0).refineType(clsdesc);
        changed |= operands.get(1).refineType(atype);
        return changed;
    }

    @Override
    void visit(Writer writer) {
        writer.visitPutInstance(this, operands.get(0), operands.get(1), atype, clsdesc, name);
    }

    @Override
    public String toString() {
        return operands.get(0) + "." + name + " = " + operands.get(1);
    }
}

final class NewInstance extends Instruction {

    final String type;

    NewInstance(Value lhs, String type) {
        value = lhs;
        value.refineType(type);
        this.type = type;
    }

    String getType() {
        return type;
    }

    @Override
    String getClassDesc() {
        return type;
    }

    @Override
    boolean resolveType() {
        return setValueType(type);
    }

    @Override
    void visit(Writer writer) {
        writer.visitNew(this, value, type);
    }

    @Override
    public String toString() {
        return value + " = NEW(" + type + ")";
    }
}

final class InvokeInstruction extends Instruction {

    final String invokeType;
    final boolean isStatic;
    final String clsdesc;
    final String name;
    final String rtype;
    final List<String> ptype;
    final List<Value> args;
    final String triple;

    InvokeInstruction(String invokeType, String clsname, String name, Value thiz, Value result,
                      String rtype, List<String> ptype, List<Value> args, String triple) {
        this.invokeType = invokeType;
        this.isStatic = "static".equals(invokeType);
        this.clsdesc = clsname;
        this.value = result;
        this.name = name;
        if (!isStatic) {
            operands.add(thiz);
            thiz.addUser(this);
        }
        if (result != null) {
            result.refineType(rtype);
        }
        this.rtype = rtype;
        this.ptype = ptype;
        this.args = new ArrayList<>();
        for (Value arg : args) {
            arg.addUser(this);
            operands.add(arg);
            this.args.add(arg);
        }
        this.triple = triple;
    }

    Value thiz() {
        return isStatic ? null : operands.get(0);
    }

    @Override
    String getClassDesc() {
        return clsdesc;
    }

    @Override
    String getCallMethodKey() {
        return clsdesc + "->" + name + "(" + String.join("", ptype) + ")";
    }

    @Override
    boolean resolveType() {
        boolean changed = false;
        List<Value> callArgs = isStatic ? operands : operands.subList(1, operands.size());
        Value thiz = thiz();
        if (thiz != null && !clsdesc.equals(thiz.getType())) {
            changed |= thiz.refineType(clsdesc);
        }
        for (int i = 0; i < callArgs.size() && i < ptype.size(); i++) {
            changed |= callArgs.get(i).refineType(ptype.get(i));
        }
        if (value != null) {
            changed |= setValueType(rtype);
        }
        return changed;
    }

    @Override
    void visit(Writer writer) {
        List<Value> callArgs = isStatic ? operands : operands.subList(1, operands.size());
        writer.visitInvoke(this, invokeType, name, thiz(), ptype, rtype, callArgs, clsdesc);
    }

    @Override
    public String toString() {
        List<Value> all = new ArrayList<>();
        if (!isStatic) {
            all.add(thiz());
        }
        all.addAll(args);
        StringBuilder out = new StringBuilder(clsdesc).append('.').append(name).append('(');
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(all.get(i));
        }
        return out.append(')').toString();
    }
}

final class ReturnInstruction extends Instruction {

    final String rtype;

    ReturnInstruction(Value arg, String rtype) {
        this.rtype = rtype;
        if (arg != null) {
            arg.addUser(this);
            operands.add(arg);
        }
    }

    Value retval() {
        return rtype != null ? operands.get(0) : null;
    }

    @Override
    boolean resolveType() {
        return rtype != null && retval().refineType(rtype);
    }

    @Override
    void visit(Writer writer) {
        if (rtype == null) {
            writer.visitReturnVoid();
        } else {
            writer.visitReturn(retval());
        }
    }

    @Override
    public String toString() {
        return retval() != null ? "RETURN(" + retval() + ")" : "RETURN";
    }
}

final class NopExpression extends Instruction {

    @Override
    void visit(Writer writer) {
        writer.visitNop();
    }
}

final class GotoInst extends Instruction {

    final int target;

    GotoInst(int target) {
        this.target = target;
    }

    @Override
    void visit(Writer writer) {
        writer.visitGoto((offset / 2 + target) * 2);
    }

    @Override
    public String toString() {
        return "GOTO(" + Integer.toHexString(target) + ")";
    }
}

final class SwitchCase {

    final long key;
    final int offset;

    SwitchCase(long key, int offset) {
        this.key = key;
        this.offset = offset;
    }
}

final class SwitchExpression extends Instruction {

    final List<SwitchCase> cases;

    SwitchExpression(Value src, List<SwitchCase> cases) {
        src.addUser(this);
        this.cases = cases;
        operands.add(src);
    }

    @Override
    boolean resolveType() {
        return operands.get(0).refineType("I");
    }

    @Override
    void visit(Writer writer) {
        writer.visitSwitchNode(this, operands.get(0), cases);
    }

    @Override
    public String toString() {
        return "SWITCH(" + operands.get(0) + ")";
    }
}

final class CheckCastExpression extends Instruction {

    final String type;
    final String clsdesc;

    CheckCastExpression(Value arg, String type, String descriptor) {
        this.type = descriptor;
        clsdesc = descriptor;
        arg.addUser(this);
        operands.add(arg);
    }

    String getType() {
        return type;
    }

    @Override
    String getClassDesc() {
        return clsdesc;
    }

    @Override
    boolean resolveType() {
        return operands.get(0).refineType(type);
    }

    @Override
    void visit(Writer writer) {
        writer.visitCheckCast(this, operands.get(0), clsdesc);
    }

    @Override
    public String toString() {
        return "CAST(" + type + ") " + operands.get(0);
    }
}

final class InstanceOfExpression extends Instruction {

    final String clsdesc;

    InstanceOfExpression(Value result, Value obj, String clsdesc) {
        this.clsdesc = clsdesc;
        value = result;
        obj.addUser(this);
        operands.add(obj);
    }

    @Override
    String getClassDesc() {
        return clsdesc;
    }

    @Override
    boolean resolveType() {
        boolean changed = operands.get(0).refineType(clsdesc);
        changed |= setValueType("Z");
        return changed;
    }

    @Override
    void visit(Writer writer) {
        writer.visitInstanceof(this, value, operands.get(0), clsdesc);
    }

    @Override
    public String toString() {
        return "(" + value + " instanceof " + clsdesc + ")";
    }
}

abstract class ArrayExpression extends Instruction {
}

final class ArrayLoadExpression extends ArrayExpression {

    final String elemType;

    ArrayLoadExpression(Value result, Value arg, Value index, String type) {
        value = result;
        arg.addUser(this);
        index.addUser(this);
        operands.add(arg);
        operands.add(index);
        elemType = type;
    }

    Value array() {
        return operands.get(0);
    }

    Value idx() {
        return operands.get(1);
    }

    @Override
    boolean resolveType() {
        boolean changed = false;
        String et = getElemType();
        if (et != null) {
            changed |= value.refineType(et);
            changed |= array().refineType("[" + et);
        }
        changed |= idx().refineType("I");
        return changed;
    }

    String getElemType() {
        if (elemType != null) {
            return elemType;
        }
        String type1 = Types.isArray(array().getType()) ? array().getType().substring(1) : null;
        String type2 = getValueType();
        return Types.mergeType(type1, type2);
    }

    @Override
    void visit(Writer writer) {
        writer.visitAload(this, value, array(), idx());
    }

    @Override
    public String toString() {
        return value + " = ARRAYLOAD(" + array() + ", " + idx() + ")";
    }
}

final class ArrayLengthExpression extends ArrayExpression {

    ArrayLengthExpression(Value result, Value array) {
        value = result;
        array.addUser(this);
        operands.add(array);
    }

    Value array() {
        return operands.get(0);
    }

    @Override
    boolean resolveType() {
        return setValueType("I");
    }

    @Override
    void visit(Writer writer) {
        writer.visitAlength(this, value, array());
    }

    @Override
    public String toString() {
        return "ARRAYLEN(" + array() + ")";
    }
}

final class NewArrayExpression extends ArrayExpression {

    final String type;
    final String elemType;

    NewArrayExpression(Value result, Value asize, String atype) {
        value = result;
        asize.addUser(this);
        type = atype;
        elemType = atype.substring(1);
        operands.add(asize);
    }

    Value getSize() {
        return operands.get(0);
    }

    String getElemType() {
        return elemType;
    }

    @Override
    String getClassDesc() {
        return elemType;
    }

    @Override
    boolean resolveType() {
        boolean changed = setValueType(type);
        changed |= operands.get(0).setType("I");
        return changed;
    }

    @Override
    void visit(Writer writer) {
        writer.visitNewArray(this, value, type, getSize());
    }

    @Override
    public String toString() {
        return value + " = NEWARRAY_" + type + "[" + getSize() + "]";
    }
}

final class FilledArrayExpression extends ArrayExpression {

    final int size;
    final String type;
    final String elemType;

    FilledArrayExpression(Value result, int size, String atype, List<Value> args) {
        value = result;
        result.refineType(atype);
        this.size = size;
        type = atype;
        elemType = atype.substring(1);
        for (Value arg : args) {
            arg.addUser(this);
            operands.add(arg);
        }
    }

    @Override
    String getClassDesc() {
        return elemType;
    }

    @Override
    boolean resolveType() {
        boolean changed = setValueType(type);
        for (Value arg : operands) {
            changed |= arg.refineType(elemType);
        }
        return changed;
    }

    @Override
    void visit(Writer writer) {
        writer.visitFilledNewArray(this, value, type, size, operands);
    }
}

final class FillArrayExpression extends ArrayExpression {

    final ArrayPayload fillData;

    FillArrayExpression(Value reg, ArrayPayload value) {
        fillData = value;
        operands.add(reg);
        reg.addUser(this);
    }

    Value reg() {
        return operands.get(0);
    }

    @Override
    boolean resolveType() {
        return false;
    }

    @Override
    void visit(Writer writer) {
        writer.visitFillArray(this, reg(), fillData);
    }
}

final class MoveExceptionExpression extends Instruction {

    final String type;

    MoveExceptionExpression(Value value, String type) {
        this.value = value;
        this.value.refineType(type);
        this.type = type;
    }

    @Override
    boolean resolveType() {
        return value.refineType(type);
    }

    @Override
    void visit(Writer writer) {
        writer.visitMoveException(this, value);
    }

    @Override
    public String toString() {
        return "MOVE_EXCEPT " + value + "(" + value.getType() + ")";
    }
}

final class MonitorEnterExpression extends Instruction {

    MonitorEnterExpression(Value ref) {
        ref.addUser(this);
        operands.add(ref);
    }

    @Override
    boolean resolveType() {
        return operands.get(0).refineType("Ljava/lang/Object;");
    }

    @Override
    void visit(Writer writer) {
        writer.visitMonitorEnter(this, operands.get(0));
    }
}

final class MonitorExitExpression extends Instruction {

    MonitorExitExpression(Value ref) {
        ref.addUser(this);
        operands.add(ref);
    }

    @Override
    boolean resolveType() {
        return operands.get(0).refineType("Ljava/lang/Object;");
    }

    @Override
    void visit(Writer writer) {
        writer.visitMonitorExit(this, operands.get(0));
    }
}

final class ThrowExpression extends Instruction {

    ThrowExpression(Value ref) {
        ref.addUser(this);
        operands.add(ref);
    }

    @Override
    boolean resolveType() {
        return operands.get(0).setType("Ljava/lang/Throwable;");
    }

    @Override
    void visit(Writer writer) {
        writer.visitThrow(this, operands.get(0));
    }
}

class BinaryExpression extends Instruction {

    String op;
    final String opType;

    BinaryExpression(String op, Value result, Value arg1, Value arg2, String vtype) {
        value = result;
        this.op = op;
        operands.add(arg1);
        operands.add(arg2);
        opType = vtype;
        arg1.addUser(this);
        arg2.addUser(this);
    }

    @Override
    boolean resolveType() {
        boolean changed = value.refineType(opType);
        for (Value op : operands) {
            changed |= op.refineType(opType);
        }
        return changed;
    }

    @Override
    void visit(Writer writer) {
        writer.visitBinaryExpression(this, value, op, operands.get(0), operands.get(1));
    }

    @Override
    public String toString() {
        return value + " = " + operands.get(0) + " " + op + " " + operands.get(1);
    }
}

final class BinaryCompExpression extends BinaryExpression {

    BinaryCompExpression(String op, Value result, Value arg1, Value arg2, String type) {
        super(op, result, arg1, arg2, type);
        result.refineType("I");
    }

    @Override
    boolean resolveType() {
        boolean changed = value.refineType("I");
        for (Value op : operands) {
            changed |= op.refineType(opType);
        }
        return changed;
    }
}

final class BinaryExpression2Addr extends BinaryExpression {

    BinaryExpression2Addr(Value result, String op, Value dest, Value arg, String type) {
        super(op, result, dest, arg, type);
    }
}

final class BinaryExpressionLit extends BinaryExpression {

    BinaryExpressionLit(String op, Value result, Value arg1, Value arg2) {
        super(op, result, arg1, arg2, "I");
    }
}

final class UnaryExpression extends Instruction {

    final String op;
    final String type;

    UnaryExpression(Value result, String op, Value arg, String type) {
        result.refineType(type);
        value = result;
        this.op = op;
        this.type = type;
        operands.add(arg);
        arg.addUser(this);
    }

    @Override
    boolean resolveType() {
        return setValueType(type);
    }

    @Override
    void visit(Writer writer) {
        writer.visitUnaryExpression(this, value, op, operands.get(0));
    }

    @Override
    public String toString() {
        return "(" + op + ", " + operands.get(0) + ")";
    }
}

final class CastExpression extends Instruction {

    final String type;
    final String srcType;
    final String op;

    CastExpression(Value result, String op, String destType, Value arg, String srcType) {
        type = destType;
        this.srcType = srcType;
        this.op = op;
        value = result;
        value.refineType(destType);
        operands.add(arg);
        arg.addUser(this);
    }

    String getType() {
        return type;
    }

    @Override
    boolean resolveType() {
        boolean changed = value.refineType(type);
        changed |= operands.get(0).refineType(srcType);
        return changed;
    }

    @Override
    void visit(Writer writer) {
        writer.visitCast(value, op, operands.get(0));
    }

    @Override
    public String toString() {
        return value + " = " + op + "(" + operands.get(0) + ")";
    }
}

final class ConditionalExpression extends Instruction {

    final String op;
    final int target;

    ConditionalExpression(String op, Value arg1, Value arg2, int target) {
        this.op = op;
        this.target = target;
        arg1.addUser(this);
        arg2.addUser(this);
        operands.add(arg1);
        operands.add(arg2);
    }

    int getTarget() {
        return target;
    }

    @Override
    boolean resolveType() {
        String type1 = operands.get(0).getType();
        String type2 = operands.get(1).getType();
        if ((Types.isRef(type1) || Types.isArray(type1))
                && (Types.isRef(type2) || Types.isArray(type2))) {
            return false;
        }
        String newType = Types.mergeType(type1, type2);
        boolean changed = operands.get(0).refineType(newType);
        changed |= operands.get(1).refineType(newType);
        return changed;
    }

    @Override
    void visit(Writer writer) {
        writer.visitCondExpression(this, op, operands.get(0), operands.get(1),
                (offset / 2 + target) * 2, nextOffset);
    }

    @Override
    public String toString() {
        return "IF " + operands.get(0) + " " + op + " " + operands.get(1);
    }
}

final class ConditionalZExpression extends Instruction {

    final String op;
    final int target;

    ConditionalZExpression(String op, Value arg, int target) {
        this.op = op;
        this.target = target;
        arg.addUser(this);
        operands.add(arg);
    }

    @Override
    boolean resolveType() {
        return false;
    }

    @Override
    void visit(Writer writer) {
        writer.visitCondzExpression(this, op, operands.get(0),
                (offset / 2 + target) * 2, nextOffset);
    }

    @Override
    public String toString() {
        return "IF " + operands.get(0) + " " + op + " 0";
    }
}

final class InstanceExpression extends Instruction {

    final String cls;
    final String ftype;
    final String name;
    final String clsdesc;

    InstanceExpression(Value result, Value arg, String klass, String ftype, String name) {
        value = result;
        cls = Types.typeName(klass);
        this.ftype = ftype;
        this.name = name;
        clsdesc = klass;
        arg.addUser(this);
        operands.add(arg);
    }

    @Override
    String getClassDesc() {
        return clsdesc;
    }

    @Override
    String getFieldKey() {
        return clsdesc + "." + name;
    }

    @Override
    boolean resolveType() {
        boolean changed = setValueType(ftype);
        changed |= operands.get(0).refineType(clsdesc);
        return changed;
    }

    String getType() {
        return ftype;
    }

    @Override
    void visit(Writer writer) {
        writer.visitGetInstance(this, value, operands.get(0), ftype, clsdesc, name);
    }

    @Override
    public String toString() {
        return value + " = " + operands.get(0) + "." + name;
    }
}

final class StaticExpression extends Instruction {

    final String cls;
    final String ftype;
    final String name;
    final String clsdesc;

    StaticExpression(Value result, String clsName, String fieldType, String fieldName) {
        value = result;
        cls = Types.typeName(clsName);
        ftype = fieldType;
        name = fieldName;
        clsdesc = clsName;
    }

    @Override
    String getClassDesc() {
        return clsdesc;
    }

    @Override
    String getFieldKey() {
        return clsdesc + "." + name;
    }

    @Override
    boolean resolveType() {
        return setValueType(ftype);
    }

    @Override
    void visit(Writer writer) {
        writer.visitGetStatic(this, value, ftype, clsdesc, name);
    }

    @Override
    public String toString() {
        return value + " = " + cls + "." + name;
    }
}
