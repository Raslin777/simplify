package org.cf.simplify.strategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.cf.simplify.ConstantBuilder;
import org.cf.simplify.Dependancy;
import org.cf.simplify.ExecutionGraphManipulator;
import org.cf.smalivm.context.HeapItem;
import org.cf.smalivm.opcode.Op;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstantPropigationStrategy implements OptimizationStrategy {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ConstantPropigationStrategy.class.getSimpleName());

    private final ExecutionGraphManipulator manipulator;
    private int constantCount;
    private boolean madeChanges;

    protected ConstantBuilder constantBuilder;

    public ConstantPropigationStrategy(ExecutionGraphManipulator manipulator) {
        getDependancies();
        this.manipulator = manipulator;
        constantCount = 0;
    }

    @Override
    public Map<String, Integer> getOptimizationCounts() {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        counts.put("constantized ops", constantCount);

        return counts;
    }

    @Override
    public boolean perform() {
        madeChanges = false;

        List<Integer> addresses = getValidAddresses();
        Collections.sort(addresses, Collections.reverseOrder());
        for (int address : addresses) {
            madeChanges = true;
            BuilderInstruction original = manipulator.getInstruction(address);
            BuilderInstruction constInstruction = ConstantBuilder.buildConstant(address, manipulator);
            boolean isReturn = original.getOpcode().name().startsWith("RETURN");
            if (isReturn) {
                manipulator.addInstruction(address, constInstruction);
            } else {
                manipulator.replaceInstruction(address, constInstruction);
            }
            constantCount++;
        }

        return madeChanges;
    }

    protected void getDependancies() {
        if (constantBuilder == null) {
            constantBuilder = new ConstantBuilder();
        }
    }

    protected void setDependancies(Dependancy dependancy) {
        constantBuilder = (ConstantBuilder) dependancy;
    }

    private boolean canConstantizeAddress(int address) {
        if (!manipulator.wasAddressReached(address)) {
            return false;
        }

        Op op = manipulator.getOp(address);
        if (!constantBuilder.canConstantizeOp(op)) {
            return false;
        }

        OneRegisterInstruction instruction = (OneRegisterInstruction) manipulator.getInstruction(address);
        if (instruction == null) {
            return false;
        }
        int register = instruction.getRegisterA();
        HeapItem consensus = manipulator.getRegisterConsensus(address, register);
        // Consensus may be null if we have correct syntax without legitimate values (fake code)
        if (consensus == null || consensus.isUnknown()) {
            return false;
        }

        String type = consensus.getType();
        if (!constantBuilder.canConstantizeType(type)) {
            return false;
        }

        return true;
    }

    private List<Integer> getValidAddresses() {
        return IntStream.of(manipulator.getAddresses()).boxed().filter(a -> canConstantizeAddress(a))
                        .collect(Collectors.toList());
    }

}
