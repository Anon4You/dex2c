package alienkrishn.dex2c;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DvmBlock {

    final int start;
    int end;
    final List<DalvikInsn> instructions = new ArrayList<>();
    final List<DvmBlock> childs = new ArrayList<>();
    final Map<Integer, DalvikInsn> specialIns = new HashMap<>();
    ExceptionAnalysis exceptionAnalysis;
    String catchType;

    DvmBlock(int start) {
        this.start = start;
        this.end = start;
    }

    DalvikInsn getSpecialIns(int offset) {
        return specialIns.get(offset);
    }

    @Override
    public String toString() {
        return String.format("DVMBasicBlock@0x%x", start);
    }
}
