package alienkrishn.dex2c;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Graph {

    IrBasicBlock entry;
    final List<IrBasicBlock> nodes = new ArrayList<>();
    final List<LandingPad> landingPads = new ArrayList<>();
    List<IrBasicBlock> rpo = new ArrayList<>();
    final Map<IrBasicBlock, List<IrBasicBlock>> edges = new LinkedHashMap<>();
    final Map<IrBasicBlock, List<IrBasicBlock>> catchEdges = new LinkedHashMap<>();
    final Map<IrBasicBlock, List<IrBasicBlock>> reverseEdges = new LinkedHashMap<>();
    final Map<IrBasicBlock, List<IrBasicBlock>> reverseCatchEdges = new LinkedHashMap<>();
    final Map<Integer, IrBasicBlock> offsetToNode = new HashMap<>();
    final Map<IrBasicBlock, LandingPad> nodeToLandingPad = new LinkedHashMap<>();

    List<IrBasicBlock> sucs(IrBasicBlock node) {
        return new ArrayList<>(edges.getOrDefault(node, Collections.emptyList()));
    }

    List<IrBasicBlock> allSucs(IrBasicBlock node) {
        List<IrBasicBlock> out = new ArrayList<>(edges.getOrDefault(node, Collections.emptyList()));
        out.addAll(catchEdges.getOrDefault(node, Collections.emptyList()));
        return out;
    }

    List<IrBasicBlock> allCatches(IrBasicBlock node) {
        return new ArrayList<>(catchEdges.getOrDefault(node, Collections.emptyList()));
    }

    List<IrBasicBlock> preds(IrBasicBlock node) {
        List<IrBasicBlock> out = new ArrayList<>();
        for (IrBasicBlock pred : reverseEdges.getOrDefault(node, Collections.emptyList())) {
            if (!pred.inCatch) {
                out.add(pred);
            }
        }
        return out;
    }

    List<IrBasicBlock> allPreds(IrBasicBlock node) {
        List<IrBasicBlock> out = new ArrayList<>(reverseEdges.getOrDefault(node, Collections.emptyList()));
        out.addAll(reverseCatchEdges.getOrDefault(node, Collections.emptyList()));
        return out;
    }

    void addNode(IrBasicBlock node) {
        nodes.add(node);
    }

    void addLandingPad(LandingPad pad) {
        landingPads.add(pad);
    }

    void addEdge(IrBasicBlock from, IrBasicBlock to) {
        List<IrBasicBlock> sucs = edges.computeIfAbsent(from, k -> new ArrayList<>());
        if (!sucs.contains(to)) {
            sucs.add(to);
        }
        List<IrBasicBlock> preds = reverseEdges.computeIfAbsent(to, k -> new ArrayList<>());
        if (!preds.contains(from)) {
            preds.add(from);
        }
    }

    void addCatchEdge(IrBasicBlock from, IrBasicBlock to) {
        List<IrBasicBlock> sucs = catchEdges.computeIfAbsent(from, k -> new ArrayList<>());
        if (!sucs.contains(to)) {
            sucs.add(to);
        }
        List<IrBasicBlock> preds = reverseCatchEdges.computeIfAbsent(to, k -> new ArrayList<>());
        if (!preds.contains(from)) {
            preds.add(from);
        }
    }

    void computeRpo() {
        int nb = nodes.size() + 1;
        for (Map.Entry<IrBasicBlock, Integer> entry : postOrder().entrySet()) {
            entry.getKey().num = nb - entry.getValue();
        }
        rpo = new ArrayList<>(nodes);
        rpo.sort(Comparator.comparingInt(n -> n.num));
    }

    private Map<IrBasicBlock, Integer> postOrder() {
        Map<IrBasicBlock, Integer> po = new LinkedHashMap<>();
        Set<IrBasicBlock> visited = new HashSet<>();
        int[] counter = {1};
        visitPostOrder(entry, visited, po, counter);
        return po;
    }

    private int visitPostOrder(IrBasicBlock node, Set<IrBasicBlock> visited,
                               Map<IrBasicBlock, Integer> po, int[] counter) {
        visited.add(node);
        for (IrBasicBlock suc : allSucs(node)) {
            if (!visited.contains(suc)) {
                visitPostOrder(suc, visited, po, counter);
            }
        }
        po.put(node, counter[0]);
        counter[0]++;
        return counter[0];
    }

    List<IrBasicBlock> computeBlockOrder() {
        List<IrBasicBlock> list = new ArrayList<>(nodes);
        list.sort(Comparator.comparingInt(n -> n.start));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).num = i;
        }
        return list;
    }

    static Graph construct(DvmBlock startBlock) {
        Map<DvmBlock, IrBasicBlock> blockToNode = new HashMap<>();
        Map<IrBasicBlock, LandingPad> nodeToLandingPad = new HashMap<>();
        Map<ExceptionAnalysis, LandingPad> exceptionToLandingPad = new HashMap<>();
        Set<DvmBlock> visited = new HashSet<>();
        List<DvmBlock> bfs = new ArrayList<>();
        List<DvmBlock> queue = new ArrayList<>();
        queue.add(startBlock);
        visited.add(startBlock);
        while (!queue.isEmpty()) {
            DvmBlock block = queue.remove(0);
            bfs.add(block);
            if (block.exceptionAnalysis != null) {
                for (ExceptionHandlerInfo handler : block.exceptionAnalysis.exceptions) {
                    if (handler.block != null && visited.add(handler.block)) {
                        queue.add(handler.block);
                    }
                }
            }
            for (DvmBlock child : block.childs) {
                if (visited.add(child)) {
                    queue.add(child);
                }
            }
        }

        Graph graph = new Graph();
        for (DvmBlock block : bfs) {
            IrBasicBlock node = blockToNode.get(block);
            if (node == null) {
                node = new IrBasicBlock(block);
                blockToNode.put(block, node);
            }
            graph.addNode(node);

            if (block.exceptionAnalysis != null) {
                LandingPad landingPad = exceptionToLandingPad.get(block.exceptionAnalysis);
                if (landingPad == null) {
                    landingPad = new LandingPad(node);
                    graph.addLandingPad(landingPad);
                    exceptionToLandingPad.put(block.exceptionAnalysis, landingPad);
                    for (ExceptionHandlerInfo handler : block.exceptionAnalysis.exceptions) {
                        DvmBlock target = handler.block;
                        if (target == null) {
                            continue;
                        }
                        IrBasicBlock catchNode = blockToNode.computeIfAbsent(target, IrBasicBlock::new);
                        catchNode.setCatchType(handler.type);
                        catchNode.inCatch = true;
                        landingPad.addCatchHandle(handler.type, catchNode);
                    }
                }

                for (ExceptionHandlerInfo handler : block.exceptionAnalysis.exceptions) {
                    if (handler.block == null) {
                        continue;
                    }
                    IrBasicBlock catchNode = blockToNode.get(handler.block);
                    node.addCatchSuccessor(catchNode);
                    graph.addCatchEdge(node, catchNode);
                }
                nodeToLandingPad.put(node, landingPad);
            }

            for (DvmBlock childBlock : block.childs) {
                IrBasicBlock childNode = blockToNode.computeIfAbsent(childBlock, IrBasicBlock::new);
                graph.addEdge(node, childNode);
            }
        }

        graph.entry = blockToNode.get(startBlock);
        graph.computeRpo();

        for (IrBasicBlock node : graph.rpo) {
            if (node.start >= 0) {
                graph.offsetToNode.put(node.start, node);
            }
        }
        graph.nodeToLandingPad.putAll(nodeToLandingPad);
        for (IrBasicBlock node : graph.rpo) {
            List<IrBasicBlock> preds = new ArrayList<>();
            for (IrBasicBlock pred : graph.allPreds(node)) {
                if (pred.num < node.num) {
                    preds.add(pred);
                }
            }
            boolean allInCatch = !preds.isEmpty();
            for (IrBasicBlock pred : preds) {
                if (!pred.inCatch) {
                    allInCatch = false;
                    break;
                }
            }
            if (allInCatch) {
                node.inCatch = true;
            }
        }
        return graph;
    }
}
