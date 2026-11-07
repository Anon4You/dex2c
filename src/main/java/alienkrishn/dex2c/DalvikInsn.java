package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;

import java.util.ArrayList;
import java.util.List;

final class DalvikInsn {

    final Opcode opcode;
    final Instruction raw;
    int offset;
    final int length;

    DalvikInsn(Opcode opcode, Instruction raw, int offset) {
        this.opcode = opcode;
        this.raw = raw;
        this.offset = offset;
        this.length = raw.getCodeUnits() * 2;
    }

    int A() {
        if (raw instanceof FiveRegisterInstruction) {
            return ((FiveRegisterInstruction) raw).getRegisterCount();
        }
        return raw instanceof OneRegisterInstruction
                ? ((OneRegisterInstruction) raw).getRegisterA() : 0;
    }

    int B() {
        if (raw instanceof TwoRegisterInstruction) {
            return ((TwoRegisterInstruction) raw).getRegisterB();
        }
        return narrowLiteral();
    }

    int C() {
        return raw instanceof FiveRegisterInstruction
                ? ((FiveRegisterInstruction) raw).getRegisterC() : 0;
    }

    int D() {
        return raw instanceof FiveRegisterInstruction
                ? ((FiveRegisterInstruction) raw).getRegisterD() : 0;
    }

    int E() {
        return raw instanceof FiveRegisterInstruction
                ? ((FiveRegisterInstruction) raw).getRegisterE() : 0;
    }

    int F() {
        return raw instanceof FiveRegisterInstruction
                ? ((FiveRegisterInstruction) raw).getRegisterF() : 0;
    }

    int G() {
        return raw instanceof FiveRegisterInstruction
                ? ((FiveRegisterInstruction) raw).getRegisterG() : 0;
    }

    int AA() {
        return raw instanceof OneRegisterInstruction
                ? ((OneRegisterInstruction) raw).getRegisterA() : 0;
    }

    int BB() {
        return raw instanceof TwoRegisterInstruction
                ? ((TwoRegisterInstruction) raw).getRegisterB() : 0;
    }

    int CC() {
        if (raw instanceof ThreeRegisterInstruction) {
            return ((ThreeRegisterInstruction) raw).getRegisterC();
        }
        return narrowLiteral();
    }

    int startRegister() {
        return raw instanceof RegisterRangeInstruction
                ? ((RegisterRangeInstruction) raw).getStartRegister() : 0;
    }

    int registerCount() {
        if (raw instanceof FiveRegisterInstruction) {
            return ((FiveRegisterInstruction) raw).getRegisterCount();
        }
        if (raw instanceof RegisterRangeInstruction) {
            return ((RegisterRangeInstruction) raw).getRegisterCount();
        }
        return 0;
    }

    int endRegister() {
        return startRegister() + registerCount() - 1;
    }

    List<Integer> registers() {
        List<Integer> out = new ArrayList<>();
        if (raw instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction ins = (FiveRegisterInstruction) raw;
            int count = ins.getRegisterCount();
            int[] all = {ins.getRegisterC(), ins.getRegisterD(), ins.getRegisterE(),
                    ins.getRegisterF(), ins.getRegisterG()};
            for (int i = 0; i < count; i++) {
                out.add(all[i]);
            }
        } else if (raw instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction ins = (RegisterRangeInstruction) raw;
            for (int i = 0; i < ins.getRegisterCount(); i++) {
                out.add(ins.getStartRegister() + i);
            }
        }
        return out;
    }

    int narrowLiteral() {
        return raw instanceof NarrowLiteralInstruction
                ? ((NarrowLiteralInstruction) raw).getNarrowLiteral() : 0;
    }

    long wideLiteral() {
        return raw instanceof WideLiteralInstruction
                ? ((WideLiteralInstruction) raw).getWideLiteral() : 0L;
    }

    int codeOffset() {
        return raw instanceof OffsetInstruction
                ? ((OffsetInstruction) raw).getCodeOffset() : 0;
    }

    Reference reference() {
        return raw instanceof ReferenceInstruction
                ? ((ReferenceInstruction) raw).getReference() : null;
    }

    TypeReference typeRef() {
        Reference ref = reference();
        return ref instanceof TypeReference ? (TypeReference) ref : null;
    }

    FieldReference fieldRef() {
        Reference ref = reference();
        return ref instanceof FieldReference ? (FieldReference) ref : null;
    }

    MethodReference methodRef() {
        Reference ref = reference();
        return ref instanceof MethodReference ? (MethodReference) ref : null;
    }

    String stringValue() {
        Reference ref = reference();
        return ref instanceof StringReference ? ((StringReference) ref).getString() : null;
    }

    SwitchPayload switchPayload() {
        return raw instanceof SwitchPayload ? (SwitchPayload) raw : null;
    }

    ArrayPayload arrayPayload() {
        return raw instanceof ArrayPayload ? (ArrayPayload) raw : null;
    }

    String getName() {
        return opcode.name().toLowerCase().replace('_', '-');
    }

    String getOutput() {
        return String.valueOf(raw);
    }

    @Override
    public String toString() {
        return getName() + " " + getOutput();
    }
}
