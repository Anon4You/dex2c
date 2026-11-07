package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchElement;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;

import java.util.ArrayList;
import java.util.List;

final class OpcodeHandlers {

    static final class Op {
        static final String CMP = "cmp";
        static final String CMPL = "cmpl";
        static final String CMPG = "cmpg";
        static final String ADD = "+";
        static final String SUB = "-";
        static final String MUL = "*";
        static final String DIV = "/";
        static final String MOD = "%";
        static final String MODF = "fmodf";
        static final String MODD = "fmod";
        static final String AND = "&";
        static final String OR = "|";
        static final String XOR = "^";
        static final String EQUAL = "==";
        static final String NEQUAL = "!=";
        static final String GREATER = ">";
        static final String LOWER = "<";
        static final String GEQUAL = ">=";
        static final String LEQUAL = "<=";
        static final String NEG = "-";
        static final String NOT = "~";
        static final String INTSHL = "shl-int";
        static final String INTSHR = "shr-int";
        static final String INTUSHR = "ushr-int";
        static final String LONGSHL = "shl-long";
        static final String LONGSHR = "shr-long";
        static final String LONGUSHR = "ushl-long";
    }

    private OpcodeHandlers() {
    }

    static Instruction handle(DalvikInsn ins, IrBuilder builder, Object extra) {
        switch (ins.opcode) {
            case NOP:
                return new NopExpression();
            case MOVE:
            case MOVE_FROM16:
            case MOVE_16:
            case MOVE_WIDE:
            case MOVE_WIDE_FROM16:
            case MOVE_WIDE_16:
            case MOVE_OBJECT:
            case MOVE_OBJECT_FROM16:
            case MOVE_OBJECT_16:
                return move(ins, builder);
            case MOVE_RESULT:
            case MOVE_RESULT_WIDE:
            case MOVE_RESULT_OBJECT:
                return moveResult(ins, builder);
            case MOVE_EXCEPTION:
                return new MoveExceptionExpression(builder.writeVariable(ins.AA()),
                        (String) extra);
            case RETURN_VOID:
                return new ReturnInstruction(null, null);
            case RETURN:
            case RETURN_WIDE:
            case RETURN_OBJECT:
                return new ReturnInstruction(builder.readVariable(ins.AA()),
                        builder.getReturnType());
            case CONST_4:
                return new LoadConstant(builder.writeVariable(ins.A()),
                        new Constant(ins.narrowLiteral()));
            case CONST_16:
                return new LoadConstant(builder.writeVariable(ins.AA()),
                        new Constant(ins.narrowLiteral()));
            case CONST:
                return new LoadConstant(builder.writeVariable(ins.AA()),
                        new Constant(ins.narrowLiteral()));
            case CONST_HIGH16:
                return new LoadConstant(builder.writeVariable(ins.AA()),
                        new Constant(ins.narrowLiteral()));
            case CONST_WIDE_16:
                return new LoadConstant(builder.writeVariable(ins.AA()),
                        new Constant(ins.narrowLiteral()));
            case CONST_WIDE_32:
                return new LoadConstant(builder.writeVariable(ins.AA()),
                        new Constant(ins.narrowLiteral()));
            case CONST_WIDE:
                return new LoadConstant(builder.writeVariable(ins.AA()),
                        new Constant(ins.wideLiteral()));
            case CONST_WIDE_HIGH16:
                return new LoadConstant(builder.writeVariable(ins.AA()),
                        new Constant(ins.wideLiteral()));
            case CONST_STRING:
            case CONST_STRING_JUMBO: {
                Value value = builder.writeVariable(ins.AA());
                value.refineType("Ljava/lang/String;");
                return new LoadConstant(value,
                        new Constant(ins.stringValue(), "Ljava/lang/String;"),
                        builder.obfus);
            }
            case CONST_CLASS: {
                Value value = builder.writeVariable(ins.AA());
                String type = Types.typeName(ins.typeRef().getType());
                return new LoadConstant(value,
                        new Constant(type, "Ljava/lang/Class;"));
            }
            case MONITOR_ENTER:
                return new MonitorEnterExpression(builder.readVariable(ins.AA()));
            case MONITOR_EXIT:
                return new MonitorExitExpression(builder.readVariable(ins.AA()));
            case CHECK_CAST:
                return new CheckCastExpression(builder.readVariable(ins.AA()),
                        ins.typeRef().getType(), ins.typeRef().getType());
            case INSTANCE_OF: {
                Value object = builder.readVariable(ins.B());
                return new InstanceOfExpression(builder.writeVariable(ins.A()),
                        object, ins.typeRef().getType());
            }
            case ARRAY_LENGTH: {
                Value array = builder.readVariable(ins.B());
                return new ArrayLengthExpression(builder.writeVariable(ins.A()), array);
            }
            case NEW_INSTANCE:
                return new NewInstance(builder.writeVariable(ins.AA()),
                        ins.typeRef().getType());
            case NEW_ARRAY: {
                Value size = builder.readVariable(ins.B());
                return new NewArrayExpression(builder.writeVariable(ins.A()),
                        size, ins.typeRef().getType());
            }
            case FILLED_NEW_ARRAY: {
                List<Integer> regs = ins.registers();
                List<Value> args = new ArrayList<>();
                for (int reg : regs) {
                    args.add(builder.readVariable(reg));
                }
                String type = ins.typeRef().getType();
                return new FilledArrayExpression(builder.writeResultVariable(),
                        ins.A(), type, args);
            }
            case FILLED_NEW_ARRAY_RANGE: {
                List<Value> args = new ArrayList<>();
                for (int reg = ins.startRegister(); reg <= ins.endRegister(); reg++) {
                    args.add(builder.readVariable(reg));
                }
                String type = ins.typeRef().getType();
                return new FilledArrayExpression(builder.writeResultVariable(),
                        ins.registerCount(), type, args);
            }
            case FILL_ARRAY_DATA: {
                DalvikInsn payload = payload(extra);
                return new FillArrayExpression(builder.readVariable(ins.AA()),
                        (com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload)
                                payload.raw);
            }
            case THROW:
                return new ThrowExpression(builder.readVariable(ins.AA()));
            case GOTO:
            case GOTO_16:
            case GOTO_32:
                return new GotoInst(ins.codeOffset());
            case PACKED_SWITCH:
            case SPARSE_SWITCH: {
                DalvikInsn payload = payload(extra);
                List<SwitchCase> cases = new ArrayList<>();
                SwitchPayload data = payload.switchPayload();
                for (SwitchElement element : data.getSwitchElements()) {
                    cases.add(new SwitchCase(element.getKey(), element.getOffset() * 2));
                }
                return new SwitchExpression(builder.readVariable(ins.AA()), cases);
            }
            case CMPL_FLOAT:
                return compare(Op.CMPL, ins, builder, "F");
            case CMPG_FLOAT:
                return compare(Op.CMPG, ins, builder, "F");
            case CMPL_DOUBLE:
                return compare(Op.CMPL, ins, builder, "D");
            case CMPG_DOUBLE:
                return compare(Op.CMPG, ins, builder, "D");
            case CMP_LONG:
                return compare(Op.CMP, ins, builder, "J");
            case IF_EQ:
                return conditional(Op.EQUAL, ins, builder);
            case IF_NE:
                return conditional(Op.NEQUAL, ins, builder);
            case IF_LT:
                return conditional(Op.LOWER, ins, builder);
            case IF_GE:
                return conditional(Op.GEQUAL, ins, builder);
            case IF_GT:
                return conditional(Op.GREATER, ins, builder);
            case IF_LE:
                return conditional(Op.LEQUAL, ins, builder);
            case IF_EQZ:
                return conditionalZ(Op.EQUAL, ins, builder);
            case IF_NEZ:
                return conditionalZ(Op.NEQUAL, ins, builder);
            case IF_LTZ:
                return conditionalZ(Op.LOWER, ins, builder);
            case IF_GEZ: {
                Value value = builder.readVariable(ins.AA());
                value.refineType("I");
                return new ConditionalZExpression(Op.GEQUAL, value, ins.codeOffset());
            }
            case IF_GTZ:
                return conditionalZ(Op.GREATER, ins, builder);
            case IF_LEZ:
                return conditionalZ(Op.LEQUAL, ins, builder);
            case AGET:
            case AGET_WIDE:
            case AGET_OBJECT:
            case AGET_BOOLEAN:
            case AGET_BYTE:
            case AGET_CHAR:
            case AGET_SHORT:
                return arrayLoad(ins, builder);
            case APUT:
            case APUT_WIDE:
            case APUT_OBJECT:
            case APUT_BOOLEAN:
            case APUT_BYTE:
            case APUT_CHAR:
            case APUT_SHORT:
                return arrayStore(ins, builder);
            case IGET:
            case IGET_WIDE:
            case IGET_OBJECT:
            case IGET_BOOLEAN:
            case IGET_BYTE:
            case IGET_CHAR:
            case IGET_SHORT: {
                FieldReference field = field(ins);
                Value object = builder.readVariable(ins.B());
                return new InstanceExpression(builder.writeVariable(ins.A()),
                        object, field.getDefiningClass(),
                        field.getType(), field.getName());
            }
            case IPUT:
            case IPUT_WIDE:
            case IPUT_OBJECT:
            case IPUT_BOOLEAN:
            case IPUT_BYTE:
            case IPUT_CHAR:
            case IPUT_SHORT: {
                FieldReference field = field(ins);
                return new InstanceInstruction(builder.readVariable(ins.A()),
                        builder.readVariable(ins.B()), field.getDefiningClass(),
                        field.getType(), field.getName());
            }
            case SGET:
            case SGET_WIDE:
            case SGET_OBJECT:
            case SGET_BOOLEAN:
            case SGET_BYTE:
            case SGET_CHAR:
            case SGET_SHORT: {
                FieldReference field = field(ins);
                Value value = builder.writeVariable(ins.AA());
                value.refineType(field.getType());
                return new StaticExpression(value, field.getDefiningClass(),
                        field.getType(), field.getName());
            }
            case SPUT:
            case SPUT_WIDE:
            case SPUT_OBJECT:
            case SPUT_BOOLEAN:
            case SPUT_BYTE:
            case SPUT_CHAR:
            case SPUT_SHORT: {
                FieldReference field = field(ins);
                return new StaticInstruction(builder.readVariable(ins.AA()),
                        field.getDefiningClass(), field.getType(), field.getName());
            }
            case INVOKE_VIRTUAL:
            case INVOKE_SUPER:
            case INVOKE_DIRECT:
            case INVOKE_STATIC:
            case INVOKE_INTERFACE:
                return invoke(ins, builder, invokeType(ins.opcode), false);
            case INVOKE_VIRTUAL_RANGE:
            case INVOKE_SUPER_RANGE:
            case INVOKE_DIRECT_RANGE:
            case INVOKE_STATIC_RANGE:
            case INVOKE_INTERFACE_RANGE:
                return invoke(ins, builder, invokeType(ins.opcode), true);
            case NEG_INT:
                return unary(ins, builder, Op.NEG, "I");
            case NOT_INT:
                return unary(ins, builder, Op.NOT, "I");
            case NEG_LONG:
                return unary(ins, builder, Op.NEG, "J");
            case NOT_LONG:
                return unary(ins, builder, Op.NOT, "J");
            case NEG_FLOAT:
                return unary(ins, builder, Op.NEG, "F");
            case NEG_DOUBLE:
                return unary(ins, builder, Op.NEG, "D");
            case INT_TO_LONG:
                return cast(ins, builder, "(jlong)", "J", "I");
            case INT_TO_FLOAT:
                return cast(ins, builder, "(jfloat)", "F", "I");
            case INT_TO_DOUBLE:
                return cast(ins, builder, "(jdouble)", "D", "I");
            case LONG_TO_INT:
                return cast(ins, builder, "(jint)", "I", "J");
            case LONG_TO_FLOAT:
                return cast(ins, builder, "(jfloat)", "F", "J");
            case LONG_TO_DOUBLE:
                return cast(ins, builder, "(jdouble)", "D", "J");
            case FLOAT_TO_INT:
                return cast(ins, builder, "(jint)", "I", "F");
            case FLOAT_TO_LONG:
                return cast(ins, builder, "(jlong)", "J", "F");
            case FLOAT_TO_DOUBLE:
                return cast(ins, builder, "(jdouble)", "D", "F");
            case DOUBLE_TO_INT:
                return cast(ins, builder, "(jint)", "I", "D");
            case DOUBLE_TO_LONG:
                return cast(ins, builder, "(jlong)", "J", "D");
            case DOUBLE_TO_FLOAT:
                return cast(ins, builder, "(jfloat)", "F", "D");
            case INT_TO_BYTE:
                return cast(ins, builder, "(jbyte)", "B", "I");
            case INT_TO_CHAR:
                return cast(ins, builder, "(jchar)", "C", "I");
            case INT_TO_SHORT:
                return cast(ins, builder, "(jshort)", "S", "I");
            case ADD_INT:
                return binary(ins, builder, Op.ADD, "I");
            case SUB_INT:
                return binary(ins, builder, Op.SUB, "I");
            case MUL_INT:
                return binary(ins, builder, Op.MUL, "I");
            case DIV_INT:
                return binary(ins, builder, Op.DIV, "I");
            case REM_INT:
                return binary(ins, builder, Op.MOD, "I");
            case AND_INT:
                return binary(ins, builder, Op.AND, "I");
            case OR_INT:
                return binary(ins, builder, Op.OR, "I");
            case XOR_INT:
                return binary(ins, builder, Op.XOR, "I");
            case SHL_INT:
                return binary(ins, builder, Op.INTSHL, "I");
            case SHR_INT:
                return binary(ins, builder, Op.INTSHR, "I");
            case USHR_INT:
                return binary(ins, builder, Op.INTUSHR, "I");
            case ADD_LONG:
                return binary(ins, builder, Op.ADD, "J");
            case SUB_LONG:
                return binary(ins, builder, Op.SUB, "J");
            case MUL_LONG:
                return binary(ins, builder, Op.MUL, "J");
            case DIV_LONG:
                return binary(ins, builder, Op.DIV, "J");
            case REM_LONG:
                return binary(ins, builder, Op.MOD, "J");
            case AND_LONG:
                return binary(ins, builder, Op.AND, "J");
            case OR_LONG:
                return binary(ins, builder, Op.OR, "J");
            case XOR_LONG:
                return binary(ins, builder, Op.XOR, "J");
            case SHL_LONG:
                return binary(ins, builder, Op.LONGSHL, "J");
            case SHR_LONG:
                return binary(ins, builder, Op.LONGSHR, "J");
            case USHR_LONG:
                return binary(ins, builder, Op.LONGUSHR, "J");
            case ADD_FLOAT:
                return binary(ins, builder, Op.ADD, "F");
            case SUB_FLOAT:
                return binary(ins, builder, Op.SUB, "F");
            case MUL_FLOAT:
                return binary(ins, builder, Op.MUL, "F");
            case DIV_FLOAT:
                return binary(ins, builder, Op.DIV, "F");
            case REM_FLOAT:
                return binary(ins, builder, Op.MODF, "F");
            case ADD_DOUBLE:
                return binary(ins, builder, Op.ADD, "D");
            case SUB_DOUBLE:
                return binary(ins, builder, Op.SUB, "D");
            case MUL_DOUBLE:
                return binary(ins, builder, Op.MUL, "D");
            case DIV_DOUBLE:
                return binary(ins, builder, Op.DIV, "D");
            case REM_DOUBLE:
                return binary(ins, builder, Op.MODD, "D");
            case ADD_INT_2ADDR:
                return binary2Addr(ins, builder, Op.ADD, "I");
            case SUB_INT_2ADDR:
                return binary2Addr(ins, builder, Op.SUB, "I");
            case MUL_INT_2ADDR:
                return binary2Addr(ins, builder, Op.MUL, "I");
            case DIV_INT_2ADDR:
                return binary2Addr(ins, builder, Op.DIV, "I");
            case REM_INT_2ADDR:
                return binary2Addr(ins, builder, Op.MOD, "I");
            case AND_INT_2ADDR:
                return binary2Addr(ins, builder, Op.AND, "I");
            case OR_INT_2ADDR:
                return binary2Addr(ins, builder, Op.OR, "I");
            case XOR_INT_2ADDR:
                return binary2Addr(ins, builder, Op.XOR, "I");
            case SHL_INT_2ADDR:
                return binary2Addr(ins, builder, Op.INTSHL, "I");
            case SHR_INT_2ADDR:
                return binary2Addr(ins, builder, Op.INTSHR, "I");
            case USHR_INT_2ADDR:
                return binary2Addr(ins, builder, Op.INTUSHR, "I");
            case ADD_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.ADD, "J");
            case SUB_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.SUB, "J");
            case MUL_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.MUL, "J");
            case DIV_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.DIV, "J");
            case REM_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.MOD, "J");
            case AND_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.AND, "J");
            case OR_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.OR, "J");
            case XOR_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.XOR, "J");
            case SHL_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.LONGSHL, "J");
            case SHR_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.LONGSHR, "J");
            case USHR_LONG_2ADDR:
                return binary2Addr(ins, builder, Op.LONGUSHR, "J");
            case ADD_FLOAT_2ADDR:
                return binary2Addr(ins, builder, Op.ADD, "F");
            case SUB_FLOAT_2ADDR:
                return binary2Addr(ins, builder, Op.SUB, "F");
            case MUL_FLOAT_2ADDR:
                return binary2Addr(ins, builder, Op.MUL, "F");
            case DIV_FLOAT_2ADDR:
                return binary2Addr(ins, builder, Op.DIV, "F");
            case REM_FLOAT_2ADDR:
                return binary2Addr(ins, builder, Op.MODF, "F");
            case ADD_DOUBLE_2ADDR:
                return binary2Addr(ins, builder, Op.ADD, "D");
            case SUB_DOUBLE_2ADDR:
                return binary2Addr(ins, builder, Op.SUB, "D");
            case MUL_DOUBLE_2ADDR:
                return binary2Addr(ins, builder, Op.MUL, "D");
            case DIV_DOUBLE_2ADDR:
                return binary2Addr(ins, builder, Op.DIV, "D");
            case REM_DOUBLE_2ADDR:
                return binary2Addr(ins, builder, Op.MODD, "D");
            case ADD_INT_LIT16:
                return assignLit(ins, builder, Op.ADD, ins.narrowLiteral());
            case RSUB_INT:
                return rsub(ins, builder, ins.narrowLiteral());
            case MUL_INT_LIT16:
                return assignLit(ins, builder, Op.MUL, ins.narrowLiteral());
            case DIV_INT_LIT16:
                return assignLit(ins, builder, Op.DIV, ins.narrowLiteral());
            case REM_INT_LIT16:
                return assignLit(ins, builder, Op.MOD, ins.narrowLiteral());
            case AND_INT_LIT16:
                return assignLit(ins, builder, Op.AND, ins.narrowLiteral());
            case OR_INT_LIT16:
                return assignLit(ins, builder, Op.OR, ins.narrowLiteral());
            case XOR_INT_LIT16:
                return assignLit(ins, builder, Op.XOR, ins.narrowLiteral());
            case ADD_INT_LIT8: {
                int literal = ins.narrowLiteral();
                if (literal < 0) {
                    return assignLit(ins, builder, Op.SUB, -literal);
                }
                return assignLit(ins, builder, Op.ADD, literal);
            }
            case RSUB_INT_LIT8:
                return rsub(ins, builder, ins.narrowLiteral());
            case MUL_INT_LIT8:
                return assignLit(ins, builder, Op.MUL, ins.narrowLiteral());
            case DIV_INT_LIT8:
                return assignLit(ins, builder, Op.DIV, ins.narrowLiteral());
            case REM_INT_LIT8:
                return assignLit(ins, builder, Op.MOD, ins.narrowLiteral());
            case AND_INT_LIT8:
                return assignLit(ins, builder, Op.AND, ins.narrowLiteral());
            case OR_INT_LIT8:
                return assignLit(ins, builder, Op.OR, ins.narrowLiteral());
            case XOR_INT_LIT8:
                return assignLit(ins, builder, Op.XOR, ins.narrowLiteral());
            case SHL_INT_LIT8:
                return assignLit(ins, builder, Op.INTSHL, ins.narrowLiteral());
            case SHR_INT_LIT8:
                return assignLit(ins, builder, Op.INTSHR, ins.narrowLiteral());
            case USHR_INT_LIT8:
                return assignLit(ins, builder, Op.INTUSHR, ins.narrowLiteral());
            default:
                throw new Dex2CException("unsupported instruction: " + ins.getName());
        }
    }

    private static DalvikInsn payload(Object extra) {
        if (!(extra instanceof DalvikInsn)) {
            throw new Dex2CException("missing payload");
        }
        return (DalvikInsn) extra;
    }

    private static FieldReference field(DalvikInsn ins) {
        FieldReference field = ins.fieldRef();
        if (field == null) {
            throw new Dex2CException("missing field reference");
        }
        return field;
    }

    private static Instruction move(DalvikInsn ins, IrBuilder builder) {
        Value rhs = builder.readVariable(ins.B());
        Value lhs = builder.writeVariable(ins.A());
        return new MoveExpression(lhs, rhs);
    }

    private static Instruction moveResult(DalvikInsn ins, IrBuilder builder) {
        Value value = builder.writeVariable(ins.AA());
        Value result = builder.readResultVariable();
        return new MoveResultExpression(value, result);
    }

    private static Instruction compare(String op, DalvikInsn ins, IrBuilder builder,
                                       String type) {
        Value arg1 = builder.readVariable(ins.BB());
        Value arg2 = builder.readVariable(ins.CC());
        Value result = builder.writeVariable(ins.AA());
        return new BinaryCompExpression(op, result, arg1, arg2, type);
    }

    private static Instruction conditional(String op, DalvikInsn ins, IrBuilder builder) {
        Value a = builder.readVariable(ins.A());
        Value b = builder.readVariable(ins.B());
        return new ConditionalExpression(op, a, b, ins.codeOffset());
    }

    private static Instruction conditionalZ(String op, DalvikInsn ins, IrBuilder builder) {
        return new ConditionalZExpression(op, builder.readVariable(ins.AA()),
                ins.codeOffset());
    }

    private static Instruction arrayLoad(DalvikInsn ins, IrBuilder builder) {
        String type = null;
        switch (ins.opcode) {
            case AGET_BOOLEAN:
                type = "Z";
                break;
            case AGET_BYTE:
                type = "B";
                break;
            case AGET_CHAR:
                type = "C";
                break;
            case AGET_SHORT:
                type = "S";
                break;
            default:
                break;
        }
        Value array = builder.readVariable(ins.BB());
        Value index = builder.readVariable(ins.CC());
        Value result = builder.writeVariable(ins.AA());
        return new ArrayLoadExpression(result, array, index, type);
    }

    private static Instruction arrayStore(DalvikInsn ins, IrBuilder builder) {
        String type = null;
        switch (ins.opcode) {
            case APUT_BOOLEAN:
                type = "Z";
                break;
            case APUT_BYTE:
                type = "B";
                break;
            case APUT_CHAR:
                type = "C";
                break;
            case APUT_SHORT:
                type = "S";
                break;
            default:
                break;
        }
        Value value = builder.readVariable(ins.AA());
        Value array = builder.readVariable(ins.BB());
        Value index = builder.readVariable(ins.CC());
        return new ArrayStoreInstruction(value, array, index, type);
    }

    private static String invokeType(Opcode opcode) {
        switch (opcode) {
            case INVOKE_VIRTUAL:
            case INVOKE_VIRTUAL_RANGE:
                return "virtual";
            case INVOKE_SUPER:
            case INVOKE_SUPER_RANGE:
                return "super";
            case INVOKE_DIRECT:
            case INVOKE_DIRECT_RANGE:
                return "direct";
            case INVOKE_STATIC:
            case INVOKE_STATIC_RANGE:
                return "static";
            default:
                return "interface";
        }
    }

    private static Instruction invoke(DalvikInsn ins, IrBuilder builder,
                                      String invokeType, boolean range) {
        MethodReference method = ins.methodRef();
        if (method == null) {
            throw new Dex2CException("missing method reference");
        }
        String clsName = method.getDefiningClass();
        String name = method.getName();
        List<String> ptypes = new ArrayList<>();
        for (CharSequence type : method.getParameterTypes()) {
            ptypes.add(type.toString());
        }
        String rtype = method.getReturnType();

        List<Integer> regs = ins.registers();
        Value thiz;
        List<Integer> argRegs;
        if ("static".equals(invokeType)) {
            thiz = null;
            argRegs = regs;
        } else {
            if (regs.isEmpty()) {
                throw new Dex2CException("missing receiver");
            }
            thiz = builder.readVariable(regs.get(0));
            argRegs = regs.subList(1, regs.size());
        }
        List<Value> args = readArgs(builder, ptypes, argRegs);
        Value returned = "V".equals(rtype) ? null : builder.writeResultVariable();
        return new InvokeInstruction(invokeType, clsName, name, thiz, returned,
                rtype, ptypes, args, "");
    }

    private static List<Value> readArgs(IrBuilder builder, List<String> ptypes,
                                        List<Integer> registers) {
        List<Value> args = new ArrayList<>();
        if (ptypes.size() > registers.size()) {
            return args;
        }
        int index = 0;
        for (String type : ptypes) {
            args.add(builder.readVariable(registers.get(index)));
            index += Types.typeSize(type);
        }
        return args;
    }

    private static Instruction unary(DalvikInsn ins, IrBuilder builder,
                                     String op, String type) {
        Value arg = builder.readVariable(ins.B());
        Value result = builder.writeVariable(ins.A());
        return new UnaryExpression(result, op, arg, type);
    }

    private static Instruction cast(DalvikInsn ins, IrBuilder builder, String op,
                                    String destType, String srcType) {
        Value source = builder.readVariable(ins.B());
        source.refineType(srcType);
        Value result = builder.writeVariable(ins.A());
        result.refineType(destType);
        return new CastExpression(result, op, destType, source, srcType);
    }

    private static Instruction binary(DalvikInsn ins, IrBuilder builder,
                                      String op, String type) {
        Value arg2 = builder.readVariable(ins.CC());
        Value arg1 = builder.readVariable(ins.BB());
        Value result = builder.writeVariable(ins.AA());
        result.refineType(type);
        return new BinaryExpression(op, result, arg1, arg2, type);
    }

    private static Instruction binary2Addr(DalvikInsn ins, IrBuilder builder,
                                           String op, String type) {
        Value arg1 = builder.readVariable(ins.A());
        Value arg2 = builder.readVariable(ins.B());
        Value result = builder.writeVariable(ins.A());
        result.refineType(type);
        return new BinaryExpression2Addr(result, op, arg1, arg2, type);
    }

    private static Instruction assignLit(DalvikInsn ins, IrBuilder builder,
                                         String op, int literal) {
        Value arg = builder.readVariable(ins.B());
        Value result = builder.writeVariable(ins.A());
        result.refineType("I");
        return new BinaryExpressionLit(op, result, arg, new Constant(literal, "I"));
    }

    private static Instruction rsub(DalvikInsn ins, IrBuilder builder, int literal) {
        Value arg = builder.readVariable(ins.B());
        Value result = builder.writeVariable(ins.A());
        result.refineType("I");
        return new BinaryExpressionLit(Op.SUB, result,
                new Constant(literal, "I"), arg);
    }
}
