package alienkrishn.dex2c;

import java.util.LinkedHashMap;
import java.util.Map;

class Phi extends Variable implements User {

    final Map<IrBasicBlock, Value> operands = new LinkedHashMap<>();
    IrBasicBlock block;

    Phi(int register, int version) {
        super(register, version);
    }

    Map<IrBasicBlock, Value> getOperands() {
        return operands;
    }

    void setBlock(IrBasicBlock block) {
        this.block = block;
    }

    IrBasicBlock getBlock() {
        return block;
    }

    void addOperand(IrBasicBlock pred, Value value) {
        operands.put(pred, value);
        value.addUser(this);
    }

    void removeOperand(IrBasicBlock pred) {
        operands.remove(pred);
    }

    @Override
    public void replaceUseOfWith(Value old, Value newValue) {
        for (Map.Entry<IrBasicBlock, Value> entry : operands.entrySet()) {
            if (entry.getValue() == old) {
                old.removeUser(this);
                newValue.addUser(this);
                entry.setValue(newValue);
            }
        }
    }

    boolean resolveType() {
        String sameOpType = null;
        for (Value op : operands.values()) {
            if (op instanceof Constant) {
                continue;
            }
            String opType = op.getType();
            if (sameOpType != null && !java.util.Objects.equals(opType, sameOpType)) {
                opType = Types.mergeType(sameOpType, opType);
            }
            sameOpType = opType;
        }

        String newType = sameOpType != null ? sameOpType : varType;
        boolean changed = false;
        if (newType != null) {
            if (!newType.equals(varType)) {
                changed |= refineType(newType);
            }
            for (Value op : operands.values()) {
                if (op instanceof Constant) {
                    continue;
                }
                if (!newType.equals(op.getType())) {
                    changed |= op.refineType(newType);
                }
            }
        }
        return changed;
    }

    boolean removeTrivialPhi() {
        Value same = null;
        for (Value op : operands.values()) {
            if (op == same || op == this) {
                continue;
            }
            if (same != null) {
                return false;
            }
            same = op;
        }
        if (same == null) {
            return false;
        }
        for (Value op : operands.values()) {
            op.removeUser(this);
        }
        replaceAllUsesWith(same);
        block.removePhi(this);
        return true;
    }

    String print() {
        StringBuilder out = new StringBuilder("v").append(register).append('_').append(version)
                .append(" = Phi(");
        boolean first = true;
        for (Value value : operands.values()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(value);
        }
        return out.append(')').toString();
    }
}
