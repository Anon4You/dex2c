package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchElement;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class Blocks {

    final List<DvmBlock> blocks = new ArrayList<>();
    final DvmBlock startBlock;
    final Map<Integer, DalvikInsn> payloads = new HashMap<>();

    private Blocks(List<DvmBlock> blocks, DvmBlock startBlock, Map<Integer, DalvikInsn> payloads) {
        this.blocks.addAll(blocks);
        this.startBlock = startBlock;
        this.payloads.putAll(payloads);
    }

    static Blocks build(MethodImplementation code) {
        List<DalvikInsn> all = new ArrayList<>();
        Map<Integer, DalvikInsn> payloads = new HashMap<>();
        int offset = 0;
        for (Instruction raw : code.getInstructions()) {
            DalvikInsn ins = new DalvikInsn(raw.getOpcode(), raw, offset);
            all.add(ins);
            if (raw instanceof SwitchPayload || raw instanceof ArrayPayload) {
                payloads.put(offset, ins);
            }
            offset += ins.length;
        }
        int codeSize = offset;

        Set<Integer> leaders = new TreeSet<>();
        leaders.add(0);
        for (DalvikInsn ins : all) {
            if (ins.raw instanceof SwitchPayload || ins.raw instanceof ArrayPayload) {
                continue;
            }
            addLeaders(ins, leaders, payloads);
            if (isBranchEnd(ins.opcode)) {
                leaders.add(ins.offset + ins.length);
            }
        }

        Map<String, List<TryInfo>> groups = new LinkedHashMap<>();
        for (TryBlock<? extends ExceptionHandler> tryBlock : code.getTryBlocks()) {
            List<ExceptionHandlerInfo> handlers = new ArrayList<>();
            for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                String type = handler.getExceptionType();
                if (type != null) {
                    handlers.add(new ExceptionHandlerInfo(type,
                            handler.getHandlerCodeAddress() * 2));
                }
            }
            for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                if (handler.getExceptionType() == null) {
                    handlers.add(new ExceptionHandlerInfo("Ljava/lang/Throwable;",
                            handler.getHandlerCodeAddress() * 2));
                }
            }

            StringBuilder key = new StringBuilder();
            for (ExceptionHandlerInfo handler : handlers) {
                key.append(handler.type).append('@').append(handler.target).append(';');
            }
            int start = tryBlock.getStartCodeAddress() * 2;
            int end = start + tryBlock.getCodeUnitCount() * 2 - 1;
            groups.computeIfAbsent(key.toString(), k -> new ArrayList<>())
                    .add(new TryInfo(start, end, handlers));
            leaders.add(tryBlock.getStartCodeAddress() * 2);
            leaders.add(tryBlock.getStartCodeAddress() * 2
                    + tryBlock.getCodeUnitCount() * 2);
            for (ExceptionHandlerInfo handler : handlers) {
                leaders.add(handler.target);
            }
        }
        List<ExceptionAnalysis> analyses = new ArrayList<>();
        for (List<TryInfo> group : groups.values()) {
            for (TryInfo tryInfo : group) {
                analyses.add(new ExceptionAnalysis(tryInfo.start, tryInfo.end,
                        tryInfo.handlers));
            }
        }

        List<Integer> sortedLeaders = new ArrayList<>(leaders);
        List<DvmBlock> blocks = new ArrayList<>();
        Map<Integer, DvmBlock> blockAt = new HashMap<>();
        for (int i = 0; i < sortedLeaders.size(); i++) {
            int start = sortedLeaders.get(i);
            if (start < 0 || start >= codeSize) {
                continue;
            }
            DvmBlock block = new DvmBlock(start);
            block.end = i + 1 < sortedLeaders.size() ? sortedLeaders.get(i + 1) : codeSize;
            blocks.add(block);
            blockAt.put(start, block);
        }

        for (DalvikInsn ins : all) {
            DvmBlock block = blockContaining(blocks, ins.offset);
            if (block == null) {
                continue;
            }
            if (ins.raw instanceof SwitchPayload || ins.raw instanceof ArrayPayload) {
                continue;
            }
            block.instructions.add(ins);
            if (ins.opcode == Opcode.PACKED_SWITCH || ins.opcode == Opcode.SPARSE_SWITCH
                    || ins.opcode == Opcode.FILL_ARRAY_DATA) {
                DalvikInsn payload = payloads.get(ins.offset + ins.codeOffset() * 2);
                if (payload != null) {
                    block.specialIns.put(ins.offset, payload);
                }
            }
        }

        for (ExceptionAnalysis analysis : analyses) {
            for (ExceptionHandlerInfo handler : analysis.exceptions) {
                handler.block = blockContaining(blocks, handler.target);
            }
        }
        for (DvmBlock block : blocks) {
            block.exceptionAnalysis = findException(analyses, block.start, block.end - 1);
        }

        for (DvmBlock block : blocks) {
            DalvikInsn last = block.instructions.isEmpty()
                    ? null : block.instructions.get(block.instructions.size() - 1);
            List<Integer> targets = new ArrayList<>();
            if (last == null) {
                targets.add(block.end);
            } else {
                switch (last.opcode) {
                    case THROW:
                    case RETURN_VOID:
                    case RETURN:
                    case RETURN_WIDE:
                    case RETURN_OBJECT:
                        break;
                    case GOTO:
                    case GOTO_16:
                    case GOTO_32:
                        targets.add(last.offset + last.codeOffset() * 2);
                        break;
                    case IF_EQ:
                    case IF_NE:
                    case IF_LT:
                    case IF_GE:
                    case IF_GT:
                    case IF_LE:
                    case IF_EQZ:
                    case IF_NEZ:
                    case IF_LTZ:
                    case IF_GEZ:
                    case IF_GTZ:
                    case IF_LEZ:
                        targets.add(last.offset + last.length);
                        targets.add(last.offset + last.codeOffset() * 2);
                        break;
                    case PACKED_SWITCH:
                    case SPARSE_SWITCH:
                        targets.add(last.offset + last.length);
                        DalvikInsn payload = block.specialIns.get(last.offset);
                        if (payload != null && payload.switchPayload() != null) {
                            for (SwitchElement element : payload.switchPayload().getSwitchElements()) {
                                targets.add(last.offset + element.getOffset() * 2);
                            }
                        }
                        break;
                    default:
                        targets.add(block.end);
                        break;
                }
            }
            Set<DvmBlock> seen = new LinkedHashSet<>();
            for (int target : targets) {
                DvmBlock child = blockContaining(blocks, target);
                if (child != null && seen.add(child)) {
                    block.childs.add(child);
                }
            }
        }

        DvmBlock start = blockContaining(blocks, 0);
        if (start == null && !blocks.isEmpty()) {
            start = blocks.get(0);
        }
        return new Blocks(blocks, start, payloads);
    }

    private static void addLeaders(DalvikInsn ins, Set<Integer> leaders,
                                   Map<Integer, DalvikInsn> payloads) {
        switch (ins.opcode) {
            case GOTO:
            case GOTO_16:
            case GOTO_32:
                leaders.add(ins.offset + ins.codeOffset() * 2);
                break;
            case IF_EQ:
            case IF_NE:
            case IF_LT:
            case IF_GE:
            case IF_GT:
            case IF_LE:
            case IF_EQZ:
            case IF_NEZ:
            case IF_LTZ:
            case IF_GEZ:
            case IF_GTZ:
            case IF_LEZ:
                leaders.add(ins.offset + ins.codeOffset() * 2);
                leaders.add(ins.offset + ins.length);
                break;
            case PACKED_SWITCH:
            case SPARSE_SWITCH:
                leaders.add(ins.offset + ins.length);
                DalvikInsn payload = payloads.get(ins.offset + ins.codeOffset() * 2);
                if (payload != null && payload.switchPayload() != null) {
                    for (SwitchElement element : payload.switchPayload().getSwitchElements()) {
                        leaders.add(ins.offset + element.getOffset() * 2);
                    }
                }
                break;
            default:
                break;
        }
    }

    private static boolean isBranchEnd(Opcode opcode) {
        switch (opcode) {
            case THROW:
            case RETURN_VOID:
            case RETURN:
            case RETURN_WIDE:
            case RETURN_OBJECT:
            case GOTO:
            case GOTO_16:
            case GOTO_32:
            case IF_EQ:
            case IF_NE:
            case IF_LT:
            case IF_GE:
            case IF_GT:
            case IF_LE:
            case IF_EQZ:
            case IF_NEZ:
            case IF_LTZ:
            case IF_GEZ:
            case IF_GTZ:
            case IF_LEZ:
            case PACKED_SWITCH:
            case SPARSE_SWITCH:
                return true;
            default:
                return false;
        }
    }

    private static final class TryInfo {
        final int start;
        final int end;
        final List<ExceptionHandlerInfo> handlers;

        TryInfo(int start, int end, List<ExceptionHandlerInfo> handlers) {
            this.start = start;
            this.end = end;
            this.handlers = handlers;
        }
    }

    private static DvmBlock blockContaining(List<DvmBlock> blocks, int address) {
        for (DvmBlock block : blocks) {
            if (block.start <= address && address < block.end) {
                return block;
            }
        }
        return null;
    }

    private static ExceptionAnalysis findException(List<ExceptionAnalysis> analyses,
                                                   int start, int end) {
        for (ExceptionAnalysis analysis : analyses) {
            if (analysis.start >= start && analysis.end <= end) {
                return analysis;
            }
            if (end <= analysis.end && start >= analysis.start) {
                return analysis;
            }
        }
        return null;
    }
}
