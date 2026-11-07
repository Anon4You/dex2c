package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class IrBuilder {

    final MethodRef method;
    final String clsName;
    final String name;
    final List<Integer> lparams = new ArrayList<>();
    final List<String> paramsType = new ArrayList<>();
    final String rtype;
    final boolean isStatic;
    final int registerCount;
    final boolean obfus;
    final boolean dynamicRegister;

    private final Map<Integer, Integer> varVersions = new HashMap<>();
    private Graph graph;
    private IrBasicBlock currentBlock;

    IrBuilder(MethodRef method, MethodImplementation code, boolean obfus,
              boolean dynamicRegister) {
        this.method = method;
        this.obfus = obfus;
        this.dynamicRegister = dynamicRegister;
        clsName = method.className();
        name = method.name();
        isStatic = method.isStatic();
        registerCount = code.getRegisterCount();
        rtype = method.returnType();
        for (CharSequence type : method.parameterTypes()) {
            paramsType.add(type.toString());
        }

        int start = registerCount;
        for (String type : paramsType) {
            start -= Types.typeSize(type);
        }
        if (!isStatic) {
            start -= 1;
            lparams.add(start);
            start += 1;
        }
        for (String type : paramsType) {
            lparams.add(start);
            start += Types.typeSize(type);
        }
    }

    String getReturnType() {
        return rtype;
    }

    IrMethod process(Blocks blocks) {
        if (blocks.startBlock == null) {
            return null;
        }
        graph = Graph.construct(blocks.startBlock);
        build();
        IrMethod irmethod = new IrMethod(graph, method);
        irmethod.rtype = rtype;
        irmethod.params = lparams;
        irmethod.paramsType = paramsType;
        Writer writer = new Writer(irmethod, dynamicRegister);
        writer.writeMethod();
        irmethod.writer = writer;
        return irmethod;
    }

    private void build() {
        defineParams();
        graph.entry.sealed = true;

        List<IrBasicBlock> todo = new ArrayList<>();
        todo.add(graph.entry);
        while (!todo.isEmpty()) {
            IrBasicBlock node = todo.remove(0);
            if (node.filled) {
                continue;
            }
            currentBlock = node;
            node = fillNode(node);
            trySealBlock(node);
            todo.addAll(graph.allSucs(node));
        }

        removeTrivialPhi();
        hackPolymorphicConstant();
        inferType();
        fixConstType();
        verifyOperandType();
        verifyPhiOperandType();
        addVarToDeclare();
    }

    private IrBasicBlock fillNode(IrBasicBlock node) {
        DvmBlock block = node.dvmBlock;
        for (DalvikInsn ins : block.instructions) {
            Object extra = null;
            if (ins.opcode == Opcode.PACKED_SWITCH || ins.opcode == Opcode.SPARSE_SWITCH
                    || ins.opcode == Opcode.FILL_ARRAY_DATA) {
                extra = block.getSpecialIns(ins.offset);
            } else if (ins.opcode == Opcode.MOVE_EXCEPTION) {
                extra = node.catchType;
            }
            Instruction last = OpcodeHandlers.handle(ins, this, extra);
            node.addIns(last);

            if (ins.opcode == Opcode.AGET_OBJECT || ins.opcode == Opcode.IGET_OBJECT) {
                Value tmp = writeResultVariable();
                Value val = last.getValue();
                last.setValue(tmp);
                MoveResultExpression move = new MoveResultExpression(val, tmp);
                move.parent = node;
                node.addIns(move);
            }

            last.offset = ins.offset;
            last.parent = node;
            if (last.getValue() != null) {
                last.getValue().definition = last;
            }
            last.nextOffset = ins.offset + ins.length;
            last.dvmInstr = ins;
        }
        node.filled = true;
        return node;
    }

    private void removeTrivialPhi() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrBasicBlock node : graph.rpo) {
                List<Phi> phis = new ArrayList<>(node.phis);
                for (Phi phi : phis) {
                    changed |= phi.removeTrivialPhi();
                }
            }
        }
    }

    private void verifyOperandType() {
        for (IrBasicBlock node : graph.computeBlockOrder()) {
            for (Instruction ins : node.getInstrList()) {
                Value value = ins.getValue();
                if (value != null && value.getType() == null) {
                    throw new Dex2CException("unknown type " + value);
                }
            }
        }
    }

    private void verifyPhiOperandType() {
        for (IrBasicBlock node : graph.rpo) {
            for (Phi phi : new ArrayList<>(node.phis)) {
                if (phi.useEmpty()) {
                    continue;
                }
                String sameType = phi.getType();
                for (Value operand : phi.getOperands().values()) {
                    String opType = operand.getType();
                    if (Types.isInt(sameType) && Types.isInt(opType)) {
                        continue;
                    }
                    if (Types.isFloat(sameType) && Types.isFloat(opType)) {
                        continue;
                    }
                    if (Types.isRef(sameType) && Types.isRef(opType)) {
                        continue;
                    }
                    if (Types.isRef(sameType) && operand.isConst && Types.isInt(opType)) {
                        continue;
                    }
                    throw new Dex2CException("inconsistency phi operand type "
                            + phi + " " + sameType + " " + opType);
                }
            }
        }
    }

    private void hackPolymorphicConstant() {
        List<LoadConstant> todo = new ArrayList<>();
        for (IrBasicBlock node : graph.computeBlockOrder()) {
            for (Instruction ins : node.getInstrList()) {
                if (ins instanceof LoadConstant && ins.getValueType() == null) {
                    todo.add((LoadConstant) ins);
                }
            }
        }
        while (!todo.isEmpty()) {
            LoadConstant ins = todo.remove(0);
            IrBasicBlock block = ins.parent;
            for (User user : ins.getUsers()) {
                Value newValue = writeVariable(((Variable) ins.getValue()).register);
                LoadConstant newIns = new LoadConstant(newValue, ins.getCst(), false);
                block.addInsBefore(newIns, ins);
                user.replaceUseOfWith(ins.getValue(), newValue);
            }
            block.removeIns(ins);
        }
    }

    private void inferType() {
        boolean changed = true;
        List<IrBasicBlock> nodes = graph.computeBlockOrder();
        int max = 500;
        while (changed) {
            max--;
            if (max == 0) {
                throw new Dex2CException("type infer failed");
            }
            changed = false;
            for (IrBasicBlock node : nodes) {
                for (Phi phi : new ArrayList<>(node.phis)) {
                    if (!phi.useEmpty()) {
                        changed |= phi.resolveType();
                    }
                }
                for (Instruction ins : node.getInstrList()) {
                    changed |= ins.resolveType();
                }
            }
        }
    }

    private void fixConstType() {
        boolean changed = false;
        for (IrBasicBlock node : graph.rpo) {
            for (Instruction ins : node.getInstrList()) {
                if (ins instanceof LoadConstant && ins.getValueType() == null) {
                    Object constant = ((LoadConstant) ins).getCst().getConstant();
                    if (!(constant instanceof Number)) {
                        continue;
                    }
                    long value = ((Number) constant).longValue();
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                        ins.setValueType("I");
                    } else {
                        ins.setValueType("J");
                    }
                    changed = true;
                }
            }
        }
        if (changed) {
            inferType();
        }
    }

    private void addVarToDeclare() {
        IrBasicBlock entry = graph.entry;
        for (IrBasicBlock node : graph.computeBlockOrder()) {
            for (Instruction ins : node.getInstrList()) {
                Value value = ins.getValue();
                if (value != null) {
                    entry.varToDeclare.add(value);
                }
                String cls = ins.getClassDesc();
                if (cls != null) {
                    entry.classToDeclare.add(cls);
                }
                String field = ins.getFieldKey();
                if (field != null) {
                    entry.fieldToDeclare.add(field);
                }
                String call = ins.getCallMethodKey();
                if (call != null) {
                    entry.methodToDeclare.add(call);
                }
            }
        }
    }

    private void trySealBlock(IrBasicBlock block) {
        List<IrBasicBlock> sucs = graph.allSucs(block);
        if (sucs.isEmpty()) {
            return;
        }
        for (IrBasicBlock suc : sucs) {
            List<IrBasicBlock> preds = graph.allPreds(suc);
            boolean allFilled = true;
            for (IrBasicBlock pred : preds) {
                if (!pred.filled) {
                    allFilled = false;
                    break;
                }
            }
            if (allFilled) {
                sealBlock(suc);
            }
        }
    }

    private void sealBlock(IrBasicBlock block) {
        List<IrBasicBlock> preds = graph.allPreds(block);
        block.sealed = true;
        for (Phi phi : new ArrayList<>(block.getIncompletePhis())) {
            for (IrBasicBlock pred : preds) {
                Value incoming = readVariable(phi.getRegister(), pred);
                phi.addOperand(pred, incoming);
            }
            block.addPhi(phi);
        }
        block.clearIncompletePhis();
    }

    void setCurrentBlock(IrBasicBlock block) {
        currentBlock = block;
    }

    IrBasicBlock getCurrentBlock() {
        return currentBlock;
    }

    Value readResultVariable() {
        Value var = readVariable(-1, currentBlock);
        if (var instanceof Phi) {
            throw new Dex2CException("invoke result is a Phi node");
        }
        return var;
    }

    Value writeResultVariable() {
        return writeVariable(-1);
    }

    Value writeVariable(int register) {
        Value value = newSsaVariable(register);
        currentBlock.updateCurrentDefinition(register, value);
        return value;
    }

    Value readVariable(int register) {
        return readVariable(register, currentBlock);
    }

    Value readVariable(int register, IrBasicBlock block) {
        Value value = block.readCurrentDefinition(register);
        if (value == null) {
            return readVariableRecursive(register, block);
        }
        return value;
    }

    private Value readVariableRecursive(int register, IrBasicBlock block) {
        List<IrBasicBlock> preds = graph.allPreds(block);
        Value value;
        if (!block.sealed) {
            value = newSsaVariable(register, true);
            block.addIncompletePhi((Phi) value);
            ((Phi) value).setBlock(block);
        } else if (preds.size() == 1) {
            value = readVariable(register, preds.get(0));
        } else {
            value = newSsaVariable(register, true);
            block.updateCurrentDefinition(register, value);
            block.addPhi((Phi) value);
            ((Phi) value).setBlock(block);
            for (IrBasicBlock pred : preds) {
                Value incoming = readVariable(register, pred);
                ((Phi) value).addOperand(pred, incoming);
            }
        }
        block.updateCurrentDefinition(register, value);
        return value;
    }

    private void defineParams() {
        IrBasicBlock entry = graph.entry;
        int start = registerCount;
        for (String type : paramsType) {
            start -= Types.typeSize(type);
        }
        if (!isStatic) {
            start -= 1;
            Variable param = (Variable) newSsaVariable(start);
            param.setType(clsName);
            entry.moveParamInsns.add(new MoveParam(new ThisParam(param)));
            entry.updateCurrentDefinition(start, param);
            entry.varToDeclare.add(param);
            start += 1;
        }
        int numParam = 0;
        for (String ptype : paramsType) {
            int register = start + numParam;
            Variable param = (Variable) newSsaVariable(register);
            param.setType(ptype);
            entry.moveParamInsns.add(new MoveParam(new Param(param)));
            entry.updateCurrentDefinition(register, param);
            entry.varToDeclare.add(param);
            numParam += Types.typeSize(ptype);
        }
    }

    private Value newSsaVariable(int register) {
        return newSsaVariable(register, false);
    }

    private Value newSsaVariable(int register, boolean phi) {
        int version = varVersions.getOrDefault(register, 0);
        Value value = phi ? new Phi(register, version) : new Variable(register, version);
        varVersions.put(register, version + 1);
        return value;
    }
}
