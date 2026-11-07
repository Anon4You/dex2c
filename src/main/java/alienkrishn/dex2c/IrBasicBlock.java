package alienkrishn.dex2c;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class IrBasicBlock {

    final DvmBlock dvmBlock;
    final List<Instruction> instrList = new ArrayList<>();
    final List<MoveParam> moveParamInsns = new ArrayList<>();
    final List<Value> varToDeclare = new ArrayList<>();
    final List<String> classToDeclare = new ArrayList<>();
    final List<String> fieldToDeclare = new ArrayList<>();
    final List<String> methodToDeclare = new ArrayList<>();
    final List<IrBasicBlock> catchSuccessors = new ArrayList<>();
    final Map<Integer, Value> currentDefinitions = new HashMap<>();
    final Set<Phi> incompletePhis = new LinkedHashSet<>();
    final Set<Phi> phis = new LinkedHashSet<>();

    int num = -1;
    final int start;
    boolean inCatch;
    String catchType;
    boolean filled;
    boolean sealed;

    IrBasicBlock(DvmBlock dvmBlock) {
        this.dvmBlock = dvmBlock;
        start = dvmBlock == null ? -1 : dvmBlock.start;
    }

    List<Instruction> getInstrList() {
        return instrList;
    }

    String getLabel() {
        if (dvmBlock != null) {
            return String.format("BB_%x", dvmBlock.start);
        }
        return "BB_" + num;
    }

    void addInsBefore(Instruction newIns, Instruction beforeIns) {
        int pos = instrList.indexOf(beforeIns);
        if (pos < 0) {
            throw new Dex2CException("instruction not in block: " + beforeIns);
        }
        instrList.add(pos, newIns);
    }

    void removeIns(Instruction ins) {
        instrList.remove(ins);
    }

    void addIns(Instruction newIns) {
        instrList.add(newIns);
    }

    void setCatchType(String type) {
        catchType = type;
    }

    void updateCurrentDefinition(int register, Value var) {
        currentDefinitions.put(register, var);
    }

    Value readCurrentDefinition(int register) {
        return currentDefinitions.get(register);
    }

    void addIncompletePhi(Phi phi) {
        incompletePhis.add(phi);
    }

    Set<Phi> getIncompletePhis() {
        return incompletePhis;
    }

    void clearIncompletePhis() {
        incompletePhis.clear();
    }

    void addPhi(Phi phi) {
        phis.add(phi);
    }

    void removePhi(Phi phi) {
        phis.remove(phi);
    }

    void addCatchSuccessor(IrBasicBlock node) {
        if (!catchSuccessors.contains(node)) {
            catchSuccessors.add(node);
        }
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(getLabel());
        for (Phi phi : phis) {
            out.append('\n').append(phi.print());
        }
        for (Instruction ins : instrList) {
            out.append('\n').append(ins);
        }
        return out.toString();
    }
}

final class LandingPad extends IrBasicBlock {

    final IrBasicBlock node;
    final Map<String, IrBasicBlock> handles = new LinkedHashMap<>();

    LandingPad(IrBasicBlock source) {
        super(null);
        node = source;
    }

    void addCatchHandle(String atype, IrBasicBlock catchNode) {
        if (handles.containsKey(atype) && !"Ljava/lang/Throwable;".equals(atype)) {
            throw new Dex2CException("duplicate catch handle for " + atype);
        }
        handles.putIfAbsent(atype, catchNode);
    }

    @Override
    String getLabel() {
        return "EX_LandingPad_" + node.num;
    }

    @Override
    public String toString() {
        return String.valueOf(node.num);
    }
}

final class ExceptionAnalysis {

    final int start;
    final int end;
    final List<ExceptionHandlerInfo> exceptions;

    ExceptionAnalysis(int start, int end, List<ExceptionHandlerInfo> exceptions) {
        this.start = start;
        this.end = end;
        this.exceptions = exceptions;
    }
}

final class ExceptionHandlerInfo {

    final String type;
    final int target;
    DvmBlock block;

    ExceptionHandlerInfo(String type, int target) {
        this.type = type;
        this.target = target;
    }
}
