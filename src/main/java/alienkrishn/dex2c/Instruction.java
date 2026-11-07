package alienkrishn.dex2c;

import java.util.ArrayList;
import java.util.List;

abstract class Instruction implements User {

    Value value;
    final List<Value> operands = new ArrayList<>();
    IrBasicBlock parent;
    int offset = -1;
    int nextOffset = -1;
    DalvikInsn dvmInstr;

    void setValue(Value value) {
        this.value = value;
    }

    Value getValue() {
        return value;
    }

    boolean setValueType(String vtype) {
        return value.setType(vtype);
    }

    String getValueType() {
        return value == null ? null : value.getType();
    }

    void setOperandType(int index, String vtype) {
        operands.get(index).refineType(vtype);
    }

    String getOperandType(int index) {
        return operands.get(index).getType();
    }

    int getOperandsNumber() {
        return operands.size();
    }

    boolean resolveType() {
        return false;
    }

    boolean isConst() {
        return false;
    }

    List<User> getUsers() {
        return value == null ? new ArrayList<>() : value.getUsers();
    }

    void addUser(Instruction instr) {
        value.addUser(instr);
    }

    void removeUser(User user) {
        value.removeUser(user);
    }

    @Override
    public void replaceUseOfWith(Value oldVar, Value newVar) {
        for (int i = 0; i < operands.size(); i++) {
            if (operands.get(i) == oldVar) {
                operands.set(i, newVar);
                newVar.addUser(this);
                oldVar.removeUser(this);
            }
        }
    }

    abstract void visit(Writer writer);

    String getClassDesc() {
        return null;
    }

    String getFieldKey() {
        return null;
    }

    String getCallMethodKey() {
        return null;
    }

    String dump() {
        if (dvmInstr == null) {
            return "";
        }
        return String.format("%x:%s %s", offset, dvmInstr.getName(),
                Types.hexEscapeString(dvmInstr.getOutput()));
    }
}
