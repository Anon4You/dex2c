package alienkrishn.dex2c;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class IrMethod {

    final MethodRef method;
    final Graph graph;
    final IrBasicBlock entry;
    final Map<Integer, IrBasicBlock> offsetToNode;
    final Map<IrBasicBlock, LandingPad> nodeToLandingPad;
    final List<LandingPad> landingPads;
    final List<IrBasicBlock> irblocks;
    final String clsName;
    final String name;
    final RegisterAllocator registerAllocator;
    Writer writer;
    String rtype;
    List<Integer> params = new ArrayList<>();
    List<String> paramsType = new ArrayList<>();

    IrMethod(Graph graph, MethodRef method) {
        this.method = method;
        this.graph = graph;
        entry = graph.entry;
        offsetToNode = graph.offsetToNode;
        nodeToLandingPad = graph.nodeToLandingPad;
        landingPads = graph.landingPads;
        irblocks = graph.computeBlockOrder();
        clsName = method.className();
        name = method.name();
        registerAllocator = new RegisterAllocator(entry.varToDeclare);
    }

    String getSource() {
        return writer == null ? "" : writer.toString();
    }

    String getPrototype() {
        return writer == null ? "" : writer.getPrototype();
    }

    final class RegisterAllocator {

        private final Map<String, Integer> slots = new HashMap<>();
        private int nextName;

        RegisterAllocator(List<Value> vars) {
            for (Value value : vars) {
                allocate(value);
            }
        }

        int allocate(Value value) {
            int register = value instanceof Variable ? ((Variable) value).register : -1;
            String atype = Types.cdeclType(value.getType());
            String key = register + ":" + atype;
            Integer slot = slots.get(key);
            if (slot == null) {
                slot = nextName++;
                slots.put(key, slot);
            }
            return slot;
        }
    }
}
