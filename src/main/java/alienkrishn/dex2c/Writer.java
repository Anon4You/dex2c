package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Writer {

    private final Graph graph;
    private final MethodRef method;
    private final IrMethod irmethod;
    private final Set<IrBasicBlock> visitedNodes =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder prototype = new StringBuilder();
    private final boolean dynamicRegister;
    private final IrMethod.RegisterAllocator ra;
    private final TmpnameAllocator ca;
    private final TmpnameAllocator fa;
    private final TmpnameAllocator ma;

    Writer(IrMethod irmethod, boolean dynamicRegister) {
        this.graph = irmethod.graph;
        method = irmethod.method;
        this.irmethod = irmethod;
        this.dynamicRegister = dynamicRegister;
        ra = irmethod.registerAllocator;
        IrBasicBlock entry = irmethod.entry;
        ca = new TmpnameAllocator(entry.classToDeclare, "cls");
        fa = new TmpnameAllocator(entry.fieldToDeclare, "fld");
        ma = new TmpnameAllocator(entry.methodToDeclare, "mth");
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    String getPrototype() {
        return prototype.toString();
    }

    void writeMethod() {
        String className = method.className();
        String name = method.name();
        String proto = method.descriptor();
        String jniName = method.jniLongName();

        write("\n/* " + className + "->" + name + proto + " */\n");

        if (dynamicRegister) {
            write(Types.nativeType(irmethod.rtype) + " ");
            prototype.append(Types.nativeType(irmethod.rtype)).append(' ').append(jniName);
        } else {
            write("extern \"C\" JNIEXPORT " + Types.nativeType(irmethod.rtype) + " JNICALL\n");
        }

        write(jniName);
        List<Integer> params = irmethod.params;
        int offset = 0;
        if (!method.isStatic()) {
            offset = 1;
        }
        StringBuilder protoParams = new StringBuilder();
        for (int i = 0; i < irmethod.paramsType.size(); i++) {
            if (i > 0) {
                protoParams.append(", ");
            }
            protoParams.append(Types.nativeType(irmethod.paramsType.get(i)))
                    .append(" p").append(params.get(offset + i));
        }
        if (protoParams.length() > 0) {
            write("(JNIEnv *env, jobject thiz, " + protoParams + ")");
            if (dynamicRegister) {
                prototype.append("(JNIEnv *env, jobject thiz, ")
                        .append(protoParams).append(')');
            }
        } else {
            write("(JNIEnv *env, jobject thiz)");
            if (dynamicRegister) {
                prototype.append("(JNIEnv *env, jobject thiz)");
            }
        }
        write("{\n");
        for (IrBasicBlock node : irmethod.irblocks) {
            visitNode(node);
        }

        for (LandingPad lp : irmethod.landingPads) {
            write("\n");
            visitLandingPad(lp);
        }

        String returnType = irmethod.rtype;
        if (returnType.charAt(0) != 'V') {
            if (returnType.charAt(0) == 'L') {
                write("EX_UnwindBlock: return NULL;\n");
            } else {
                write("EX_UnwindBlock: return (" + Types.nativeType(returnType) + ")0;\n");
            }
        } else {
            write("EX_UnwindBlock: return;\n");
        }

        write("}\n");
    }

    private void visitNode(IrBasicBlock node) {
        if (visitedNodes.contains(node)) {
            return;
        }
        visitedNodes.add(node);

        Set<Integer> varDeclared = new HashSet<>();
        for (Value var : node.varToDeclare) {
            String varType = var.getType();
            int r = ra.allocate(var);
            if (varDeclared.contains(r)) {
                continue;
            }
            varDeclared.add(r);
            write(Types.cdeclType(varType) + " v" + r);
            if (Types.isRef(varType)) {
                write(" = NULL");
            }
            write(";\n");
        }

        if (!node.varToDeclare.isEmpty() && !irmethod.landingPads.isEmpty()) {
            write("jthrowable exception;\n");
        }

        Set<String> declared = new HashSet<>();
        List<String> toDeclare = new ArrayList<>();
        for (String jclass : node.classToDeclare) {
            if (!declared.add(jclass)) {
                continue;
            }
            toDeclare.add(ca.get(jclass) + " = NULL");
        }
        if (!toDeclare.isEmpty()) {
            write("jclass " + String.join(",", toDeclare) + ";\n");
        }

        declared.clear();
        toDeclare.clear();
        for (String jfield : node.fieldToDeclare) {
            if (!declared.add(jfield)) {
                continue;
            }
            toDeclare.add(fa.get(jfield) + " = NULL");
        }
        if (!toDeclare.isEmpty()) {
            write("jfieldID " + String.join(",", toDeclare) + ";\n");
        }

        declared.clear();
        toDeclare.clear();
        for (String jmethod : node.methodToDeclare) {
            if (!declared.add(jmethod)) {
                continue;
            }
            toDeclare.add(ma.get(jmethod) + " = NULL");
        }
        if (!toDeclare.isEmpty()) {
            write("jmethodID " + String.join(", ", toDeclare) + ";\n");
        }

        for (MoveParam ins : node.moveParamInsns) {
            ins.visit(this);
        }

        if (node.num >= 0) {
            write("L" + node.num + ":\n");
        }
        for (Instruction ins : node.getInstrList()) {
            ins.visit(this);
        }
    }

    private void visitLandingPad(LandingPad landingPad) {
        write(landingPad.getLabel() + ":\n");
        write("D2C_GET_PENDING_EX\n");
        for (Map.Entry<String, IrBasicBlock> entry : landingPad.handles.entrySet()) {
            write("if(d2c_is_instance_of(env, exception, \""
                    + Types.typeName(entry.getKey()) + "\")) {\n");
            write("goto L" + entry.getValue().num + ";\n");
            write("}\n");
        }
        write("D2C_GOTO_UNWINDBLOCK\n");
    }

    private void writeDeleteDeadLocalReference(Value val) {
        if (val.useEmpty() && register(val) < 0) {
            writeKillLocalReference(val, true);
        }
    }

    private void writeKillLocalReference(Value val) {
        writeKillLocalReference(val, false);
    }

    private void writeKillLocalReference(Value val, boolean tmp) {
        if (val == null) {
            return;
        }
        if ("jarray".equals(Types.nativeType(val.getType()))
                || "jobject".equals(Types.nativeType(val.getType()))
                || "jstring".equals(Types.nativeType(val.getType()))) {
            if (tmp || register(val) >= 0) {
                int reg = ra.allocate(val);
                write("if (v" + reg + ") {\n");
                write("LOGD(\"env->DeleteLocalRef(%p):v" + reg + "\", v" + reg + ");\n");
                write("env->DeleteLocalRef(v" + reg + ");\n");
                write("}\n");
            }
        }
    }

    void visitSwitchNode(SwitchExpression ins, Value selection, List<SwitchCase> cases) {
        write("switch (");
        selection.visit(this);
        write(") {\n");
        for (SwitchCase c : cases) {
            IrBasicBlock node = irmethod.offsetToNode.get(c.offset + ins.offset);
            if (node == null) {
                throw new Dex2CException("no block for switch target "
                        + Integer.toHexString(c.offset + ins.offset));
            }
            write("case " + c.key + ": goto L" + node.num + ";\n");
        }
        write("}\n");
    }

    void visitLoadConstant(LoadConstant ins, boolean obfus) {
        Value val = ins.getValue();
        String atype = val.getType();
        writeTrace(ins);
        writeKillLocalReference(val);
        Object cst = ins.getCst().getConstant();
        String cstType = ins.getCst().getType();
        if ("F".equals(atype)) {
            write("v" + ra.allocate(val) + " = d2c_bitcast_to_float(" + cst + ");\n");
        } else if ("D".equals(atype)) {
            write("v" + ra.allocate(val) + " = d2c_bitcast_to_double(" + cst + ");\n");
        } else if ("Ljava/lang/String;".equals(cstType)) {
            if (obfus) {
                write("v" + ra.allocate(val) + " = (" + Types.nativeType(atype)
                        + ") env->NewStringUTF(AY_OBFUSCATE(\"" + cst + "\"));\n");
            } else {
                write("v" + ra.allocate(val) + " = (" + Types.nativeType(atype)
                        + ") env->NewStringUTF(\"" + cst + "\");\n");
            }
        } else if ("Ljava/lang/Class;".equals(cstType)) {
            write("{\n");
            writeDefineExHandle(ins);
            write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
            write("D2C_RESOLVE_CLASS(clz,\"" + cst + "\");\n");
            write("v" + ra.allocate(val) + " = env->NewLocalRef(clz);\n");
            writeUndefineExHandle(ins);
            write("}\n");
        } else {
            write("v" + ra.allocate(val) + " = " + cst + ";\n");
        }
    }

    void visitConstant(Constant cst) {
        write(String.valueOf(cst));
    }

    void visitVariable(Variable var) {
        write("v" + ra.allocate(var));
    }

    void visitMoveParam(MoveParam ins) {
        Param param = ins.getParam();
        Value value = param.getValue();
        write("v" + ra.allocate(value) + " = (" + Types.cdeclType(value.getType()) + ")");
        param.visit(this);
        write(";\n");
    }

    void visitParam(Value param) {
        String declType = Types.nativeType(param.getType());
        if ("jstring".equals(declType) || "jobject".equals(declType)
                || "jarray".equals(declType)) {
            write("env->NewLocalRef(p" + register(param) + ")");
        } else {
            write("p" + register(param));
        }
    }

    void visitThis() {
        write("env->NewLocalRef(thiz)");
    }

    void visitMoveResult(MoveResultExpression ins, Value lhs, Value rhs) {
        writeTrace(ins);
        writeKillLocalReference(ins.getValue());
        write("v" + ra.allocate(lhs) + " = (" + Types.cdeclType(lhs.getType())
                + ") " + getVariableOrConst(rhs) + ";\n");
    }

    void visitMove(Value lhs, Value rhs) {
        writeKillLocalReference(lhs);
        write("v" + ra.allocate(lhs) + " = ");
        if (Types.isPrimitiveType(rhs.getType())) {
            write(getVariableOrConst(rhs));
        } else {
            write("(" + Types.cdeclType(lhs.getType()) + ") env->NewLocalRef(v"
                    + ra.allocate(rhs) + ")");
        }
        write(";\n");
    }

    void visitAstore(ArrayStoreInstruction ins, Value array, Value index, Value rhs) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeNotNull(array);
        String elemType = ins.getElemType();
        if (Types.isPrimitiveType(elemType)) {
            write("{" + Types.nativeType(elemType) + " val = "
                    + getVariableOrConst(rhs) + ";");
            write("env->Set" + Types.typeDescriptor(elemType)
                    + "ArrayRegion((" + Types.nativeType(elemType)
                    + "Array) v" + ra.allocate(array) + ", (jint) "
                    + getVariableOrConst(index) + ", 1, &val);");
            write("}\n");
        } else {
            write("env->SetObjectArrayElement((jobjectArray) v" + ra.allocate(array)
                    + ", (jint) " + getVariableOrConst(index) + ", v"
                    + ra.allocate(rhs) + ");");
        }
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitPutStatic(StaticInstruction ins, String clsdesc, String name,
                        String ftype, Value rhs) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("jfieldID &fld = " + fa.get(ins.getFieldKey()) + ";\n");
        write("D2C_RESOLVE_STATIC_FIELD(clz, fld, \""
                + Types.typeName(clsdesc) + "\", \"" + name + "\", \"" + ftype + "\");\n");
        write("env->SetStatic" + Types.typeDescriptor(ftype) + "Field(clz,fld,("
                + Types.nativeType(ftype) + ") " + getVariableOrConst(rhs) + ");\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    private void writeNotNull(Value var) {
        write("D2C_NOT_NULL(" + getVariableOrConst(var) + ");\n");
    }

    void visitPutInstance(InstanceInstruction ins, Value lhs, Value rhs, String ftype,
                          String clsdesc, String name) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeNotNull(lhs);
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("jfieldID &fld = " + fa.get(ins.getFieldKey()) + ";\n");
        write("D2C_RESOLVE_FIELD(clz, fld, \"" + Types.typeName(clsdesc) + "\", \""
                + name + "\", \"" + ftype + "\");\n");
        write("env->Set" + Types.typeDescriptor(ftype) + "Field(v" + ra.allocate(lhs)
                + ",fld,(" + Types.nativeType(ftype) + ") "
                + getVariableOrConst(rhs) + ");\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitNew(NewInstance ins, Value result, String atype) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeKillLocalReference(ins.getValue());
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("D2C_RESOLVE_CLASS(clz,\"" + Types.typeName(atype) + "\");\n");
        write("v" + ra.allocate(result) + " = (" + Types.nativeType(result.getType())
                + ") env->AllocObject(clz);\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitInvoke(InvokeInstruction ins, String invokeType, String name, Value base,
                     List<String> ptype, String rtype, List<Value> args, String clsdesc) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        if (!"static".equals(invokeType)) {
            write("D2C_NOT_NULL(v" + ra.allocate(base) + ");\n");
        }
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("jmethodID &mid = " + ma.get(ins.getCallMethodKey()) + ";\n");
        if (!"static".equals(invokeType)) {
            write("D2C_RESOLVE_METHOD(clz, mid, \"" + Types.typeName(clsdesc)
                    + "\", \"" + name + "\", \"(" + String.join("", ptype)
                    + ")" + rtype + "\");\n");
        } else {
            write("D2C_RESOLVE_STATIC_METHOD(clz, mid, \"" + Types.typeName(clsdesc)
                    + "\", \"" + name + "\", \"(" + String.join("", ptype)
                    + ")" + rtype + "\");\n");
        }
        write("jvalue args[] = {");
        List<String> vars = new ArrayList<>();
        for (int i = 0; i < args.size() && i < ptype.size(); i++) {
            Value arg = args.get(i);
            String atype = ptype.get(i);
            char c = atype.charAt(0);
            switch (c) {
                case 'L':
                case '[':
                    vars.add("{.l = " + getVariableOrConst(arg) + "}");
                    break;
                case 'Z':
                    vars.add("{.z = (jboolean) " + getVariableOrConst(arg) + "}");
                    break;
                case 'B':
                    vars.add("{.b = (jbyte) " + getVariableOrConst(arg) + "}");
                    break;
                case 'C':
                    vars.add("{.c = (jchar) " + getVariableOrConst(arg) + "}");
                    break;
                case 'S':
                    vars.add("{.s = (jshort) " + getVariableOrConst(arg) + "}");
                    break;
                case 'I':
                    vars.add("{.i = " + getVariableOrConst(arg) + "}");
                    break;
                case 'J':
                    vars.add("{.j = (jlong) " + getVariableOrConst(arg) + "}");
                    break;
                case 'F':
                    vars.add("{.f = " + getVariableOrConst(arg) + "}");
                    break;
                case 'D':
                    vars.add("{.d = " + getVariableOrConst(arg) + "}");
                    break;
                default:
                    throw new Dex2CException("unknown signature type " + atype);
            }
        }
        write(String.join(",", vars));
        write("};\n");
        if (!"V".equals(rtype)) {
            writeKillLocalReference(ins.getValue());
            write("v" + ra.allocate(ins.getValue()) + " = (");
            write(Types.nativeType(ins.getValue().getType()));
            write(") ");
        }

        if ("super".equals(invokeType)) {
            write("env->CallNonvirtual" + Types.typeDescriptor(rtype)
                    + "MethodA(v" + ra.allocate(base) + ", clz, mid, args);\n");
        } else if ("static".equals(invokeType)) {
            write("env->CallStatic" + Types.typeDescriptor(rtype)
                    + "MethodA(clz, mid, args);\n");
        } else {
            write("env->Call" + Types.typeDescriptor(rtype)
                    + "MethodA(v" + ra.allocate(base) + ", mid, args);\n");
        }

        writeUndefineExHandle(ins);
        write("}\n");
        if (!"V".equals(rtype)) {
            writeDeleteDeadLocalReference(ins.getValue());
        }
    }

    void visitReturnVoid() {
        write("return");
        endIns();
    }

    void visitReturn(Value arg) {
        String returnType = irmethod.rtype;
        if (returnType.charAt(0) != 'V') {
            write("return (" + Types.nativeType(returnType) + ") "
                    + getVariableOrConst(arg) + ";\n");
        } else {
            write("return;");
        }
    }

    void visitNop() {
    }

    void visitGoto(int target) {
        IrBasicBlock node = irmethod.offsetToNode.get(target);
        if (node == null) {
            throw new Dex2CException("no block for goto target "
                    + Integer.toHexString(target));
        }
        write("goto L" + node.num);
        endIns();
    }

    void visitCheckCast(CheckCastExpression ins, Value arg, String atype) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("D2C_RESOLVE_CLASS(clz,\"" + Types.typeName(atype) + "\");\n");
        write("D2C_CHECK_CAST(" + getVariableOrConst(arg) + ", clz, \""
                + Types.typeName(atype) + "\");\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitInstanceof(InstanceOfExpression ins, Value result, Value arg, String atype) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("D2C_RESOLVE_CLASS(clz,\"" + Types.typeName(atype) + "\");\n");
        write("v" + ra.allocate(result) + " = d2c_is_instance_of(env, v"
                + ra.allocate(arg) + ", clz);\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitAload(ArrayLoadExpression ins, Value result, Value array, Value index) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeNotNull(array);
        String elemType = ins.getElemType();
        if (Types.isPrimitiveType(elemType)) {
            write("{" + Types.nativeType(elemType) + " val;");
            write("env->Get" + Types.typeDescriptor(elemType) + "ArrayRegion(("
                    + Types.nativeType(elemType) + "Array) v" + ra.allocate(array)
                    + ", (jint) " + getVariableOrConst(index) + ", 1, &val);");
            write("v" + ra.allocate(result) + " = val;}");
        } else {
            writeKillLocalReference(result);
            write("v" + ra.allocate(result) + " = (" + Types.nativeType(result.getType())
                    + ") env->GetObjectArrayElement((jobjectArray) v"
                    + ra.allocate(array) + ", (jint) " + getVariableOrConst(index) + ");");
        }
        write("\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitAlength(ArrayLengthExpression ins, Value result, Value array) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeNotNull(array);
        write("v" + ra.allocate(result) + " = env->GetArrayLength((jarray) v"
                + ra.allocate(array) + ");\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    private void writeCheckArraySize(Value size) {
        write("if (" + getVariableOrConst(size) + " < 0) {\n"
                + "d2c_throw_exception(env, \"java/lang/NegativeArraySizeException\", "
                + "\"negative array size\");\n");
        write("goto EX_HANDLE;\n");
        write("}\n");
    }

    void visitNewArray(NewArrayExpression ins, Value result, String atype, Value size) {
        writeTrace(ins);
        String elemType = ins.getElemType();
        write("{\n");
        writeDefineExHandle(ins);
        writeCheckArraySize(size);
        writeKillLocalReference(ins.getValue());
        if (Types.isPrimitiveType(elemType)) {
            write("v" + ra.allocate(result) + " = (" + Types.nativeType(result.getType())
                    + ") env->New" + Types.typeDescriptor(elemType) + "Array((jint) "
                    + getVariableOrConst(size) + ");\n");
        } else {
            write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
            write("D2C_RESOLVE_CLASS(clz,\"" + Types.typeName(elemType) + "\");\n");
            write("v" + ra.allocate(result) + " = env->NewObjectArray((jint) "
                    + getVariableOrConst(size) + ", clz, NULL);\n");
        }
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitFilledNewArray(FilledArrayExpression ins, Value result, String atype,
                             int size, List<Value> args) {
        writeTrace(ins);
        List<String> params = new ArrayList<>();
        for (Value arg : args) {
            params.add(getVariableOrConst(arg));
        }
        write("{\n");
        writeDefineExHandle(ins);
        writeKillLocalReference(ins.getValue());
        String elemType = atype.substring(1);
        if ("D".equals(elemType) || "J".equals(elemType)) {
            throw new Dex2CException("filled-new-array of wide type");
        }
        if (Types.isPrimitiveType(elemType)) {
            write("v" + ra.allocate(result) + " = env->New"
                    + Types.typeDescriptor(elemType) + "Array((jint) " + size + ");\n");
        } else {
            write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
            write("D2C_RESOLVE_CLASS(clz,\"" + Types.typeName(elemType) + "\");\n");
            write("v" + ra.allocate(result) + " = env->NewObjectArray((jint) "
                    + size + ", clz, NULL);\n");
        }
        write("d2c_filled_new_array(env, (jarray) v" + ra.allocate(result) + ", \""
                + elemType + "\", " + params.size() + ", "
                + String.join(", ", params) + ");\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitFillArray(FillArrayExpression ins, Value array, ArrayPayload value) {
        writeTrace(ins);
        write("{\n");
        write("static const unsigned char data[] = {");
        int width = value.getElementWidth();
        List<Number> elements = value.getArrayElements();
        List<String> tab = new ArrayList<>();
        for (Number element : elements) {
            long bits = element.longValue();
            for (int i = 0; i < width; i++) {
                tab.add(Integer.toString((int) ((bits >> (8 * i)) & 0xff)));
            }
        }
        write(String.join(", ", tab));
        write("};\n");

        String elemType = array.getType().substring(1);
        int arraySize = elements.size();

        write("env->Set" + Types.typeDescriptor(elemType) + "ArrayRegion(("
                + Types.nativeType(elemType) + "Array) v" + ra.allocate(array)
                + ", 0, " + arraySize + ", (const " + Types.nativeType(elemType)
                + " *) data);\n");
        write("}\n");
    }

    void visitMoveException(MoveExceptionExpression ins, Value var) {
        writeTrace(ins);
        writeKillLocalReference(var);
        write("v" + ra.allocate(var) + " = exception;\n");
    }

    void visitMonitorEnter(MonitorEnterExpression ins, Value ref) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeNotNull(ref);
        write("env->MonitorEnter(" + getVariableOrConst(ref) + ");\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    void visitMonitorExit(MonitorExitExpression ins, Value ref) {
        write("{\n");
        writeDefineExHandle(ins);
        write("if (env->MonitorExit(" + getVariableOrConst(ref) + ") != JNI_OK) {\n");
        writeUndefineExHandle(ins);
        write("}\n");
        write("}\n");
    }

    void visitThrow(ThrowExpression ins, Value ref) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeNotNull(ref);
        write("env->Throw((jthrowable) v" + ra.allocate(ref) + ");\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    private void writeDivExpression(Instruction ins, Value result, String op,
                                    Value arg1, Value arg2) {
        write("{\n");
        boolean isFloat = "F".equals(result.getType()) || "D".equals(result.getType());
        if (!isFloat) {
            writeDefineExHandle(ins);
            write("if (" + getVariableOrConst(arg2) + " == 0) {\n"
                    + "d2c_throw_exception(env, \"java/lang/ArithmeticException\", "
                    + "\"divide by zero\");\n");
            write("goto EX_HANDLE;\n");
            write("}\n");
            write("#undef EX_HANDLE\n");
        }
        write("v" + ra.allocate(result) + " = ");
        arg1.visit(this);
        write(" " + op + " ");
        arg2.visit(this);
        write(";\n");
        write("}\n");
    }

    void visitBinaryExpression(BinaryExpression ins, Value result, String op,
                               Value arg1, Value arg2) {
        writeTrace(ins);
        if (OpcodeHandlers.Op.DIV.equals(op) || OpcodeHandlers.Op.MOD.equals(op)) {
            writeDivExpression(ins, result, op, arg1, arg2);
            return;
        }

        write("v" + ra.allocate(result) + " = ");
        if (OpcodeHandlers.Op.CMP.equals(op) || OpcodeHandlers.Op.CMPL.equals(op)
                || OpcodeHandlers.Op.CMPG.equals(op)) {
            if (OpcodeHandlers.Op.CMPG.equals(op)) {
                write("(");
                arg1.visit(this);
                write(" == ");
                arg2.visit(this);
                write(") ? 0 :");
                write(" (");
                arg1.visit(this);
                write(" < ");
                arg2.visit(this);
                write(") ? -1 : 1;\n");
            } else {
                write("(");
                arg1.visit(this);
                write(" == ");
                arg2.visit(this);
                write(") ? 0 :");
                write(" (");
                arg1.visit(this);
                write(" > ");
                arg2.visit(this);
                write(") ? 1 : -1;\n");
            }
        } else if (OpcodeHandlers.Op.INTSHR.equals(op)) {
            write("(" + getVariableOrConst(arg1) + ") >> (");
            arg2.visit(this);
            write(" & 0x1f);\n");
        } else if (OpcodeHandlers.Op.INTSHL.equals(op)) {
            write("(" + getVariableOrConst(arg1) + ") << (");
            arg2.visit(this);
            write(" & 0x1f);\n");
        } else if (OpcodeHandlers.Op.INTUSHR.equals(op)) {
            write("((uint32_t) " + getVariableOrConst(arg1) + ") >> (");
            arg2.visit(this);
            write(" & 0x1f);\n");
        } else if (OpcodeHandlers.Op.LONGSHR.equals(op)) {
            write("(v" + ra.allocate(arg1) + ") >> (");
            arg2.visit(this);
            write(" & 0x3f);\n");
        } else if (OpcodeHandlers.Op.LONGSHL.equals(op)) {
            write("(" + getVariableOrConst(arg1) + ") << (");
            arg2.visit(this);
            write(" & 0x3f);\n");
        } else if (OpcodeHandlers.Op.LONGUSHR.equals(op)) {
            write("((uint64_t) v" + ra.allocate(arg1) + ") >> (");
            arg2.visit(this);
            write(" & 0x3f);\n");
        } else if (OpcodeHandlers.Op.MODF.equals(op)) {
            write("fmodf(v" + ra.allocate(arg1) + ", v" + ra.allocate(arg2) + ");\n");
        } else if (OpcodeHandlers.Op.MODD.equals(op)) {
            write("fmod(v" + ra.allocate(arg1) + ", v" + ra.allocate(arg2) + ");\n");
        } else {
            write("(");
            arg1.visit(this);
            write(" " + op + " ");
            arg2.visit(this);
            write(");\n");
        }
    }

    void visitUnaryExpression(UnaryExpression ins, Value result, String op, Value arg) {
        writeTrace(ins);
        write("v" + ra.allocate(result) + " = (" + op + " "
                + getVariableOrConst(arg) + ");\n");
    }

    void visitCast(Value lhs, String op, Value arg) {
        String type1 = arg.getType();
        String type2 = lhs.getType();
        if ("D".equals(type1) && "J".equals(type2)) {
            write("v" + ra.allocate(lhs) + " = d2c_double_to_long(v"
                    + ra.allocate(arg) + ");\n");
        } else if ("D".equals(type1) && "I".equals(type2)) {
            write("v" + ra.allocate(lhs) + " = d2c_double_to_int(v"
                    + ra.allocate(arg) + ");\n");
        } else if ("F".equals(type1) && "J".equals(type2)) {
            write("v" + ra.allocate(lhs) + " = d2c_float_to_long(v"
                    + ra.allocate(arg) + ");\n");
        } else if ("F".equals(type1) && "I".equals(type2)) {
            write("v" + ra.allocate(lhs) + " = d2c_float_to_int(v"
                    + ra.allocate(arg) + ");\n");
        } else {
            write("v" + ra.allocate(lhs) + " = " + op + "("
                    + getVariableOrConst(arg) + ");\n");
        }
    }

    void visitCondExpression(ConditionalExpression ins, String op, Value arg1,
                             Value arg2, int trueTarget, int falseTarget) {
        writeTrace(ins);
        IrBasicBlock trueNode = irmethod.offsetToNode.get(trueTarget);
        IrBasicBlock falseNode = irmethod.offsetToNode.get(falseTarget);
        if (trueNode == null || falseNode == null) {
            throw new Dex2CException("missing conditional target");
        }
        write("if(");
        if (Types.isPrimitiveType(arg1.getType())) {
            arg1.visit(this);
            write(" " + op + " ");
            arg2.visit(this);
        } else {
            if ("!=".equals(op)) {
                write("!");
            }
            write("d2c_is_same_object(env,");
            arg1.visit(this);
            write(",");
            arg2.visit(this);
            write(")");
        }
        write(") ");
        write("{\n");
        write("goto L" + trueNode.num + ";\n");
        write("}\n");
        write("else {\n");
        write("goto L" + falseNode.num + ";\n");
        write("}\n");
    }

    void visitCondzExpression(ConditionalZExpression ins, String op, Value arg,
                              int trueTarget, int falseTarget) {
        writeTrace(ins);
        IrBasicBlock trueNode = irmethod.offsetToNode.get(trueTarget);
        IrBasicBlock falseNode = irmethod.offsetToNode.get(falseTarget);
        if (trueNode == null || falseNode == null) {
            throw new Dex2CException("missing conditional target");
        }
        String atype = arg.getType();
        write("if(");
        arg.visit(this);
        if (atype != null && atype.length() == 1 && "ZBSCIJFD".indexOf(atype.charAt(0)) >= 0) {
            write(" " + op + " 0");
        } else {
            write(" " + op + " NULL");
        }
        write(")");
        write("{\n");
        write("goto L" + trueNode.num + ";\n");
        write("}\n");
        write("else {\n");
        write("goto L" + falseNode.num + ";\n");
        write("}\n");
    }

    void visitGetInstance(InstanceExpression ins, Value result, Value arg,
                          String ftype, String clsdesc, String name) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeNotNull(arg);
        writeKillLocalReference(result);
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("jfieldID &fld = " + fa.get(ins.getFieldKey()) + ";\n");
        write("D2C_RESOLVE_FIELD(clz, fld, \"" + Types.typeName(clsdesc)
                + "\", \"" + name + "\", \"" + ftype + "\");\n");
        write("v" + ra.allocate(result) + " = (" + Types.nativeType(result.getType())
                + ") env->Get" + Types.typeDescriptor(ftype) + "Field(v"
                + ra.allocate(arg) + ",fld);\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    private void writeDefineExHandle(Instruction ins) {
        IrBasicBlock bb = ins.parent;
        LandingPad landingPad = irmethod.nodeToLandingPad.get(bb);
        String label;
        if (landingPad != null) {
            label = landingPad.getLabel();
        } else {
            label = "EX_UnwindBlock";
        }
        write("#define EX_HANDLE " + label + "\n");
    }

    private void writeUndefineExHandle(Instruction ins) {
        write("D2C_CHECK_PENDING_EX;\n");
        write("#undef EX_HANDLE\n");
    }

    void visitGetStatic(StaticExpression ins, Value result, String ftype,
                        String clsdesc, String name) {
        writeTrace(ins);
        write("{\n");
        writeDefineExHandle(ins);
        writeKillLocalReference(result);
        write("jclass &clz = " + ca.get(ins.getClassDesc()) + ";\n");
        write("jfieldID &fld = " + fa.get(ins.getFieldKey()) + ";\n");
        write("D2C_RESOLVE_STATIC_FIELD(clz, fld, \"" + Types.typeName(clsdesc)
                + "\", \"" + name + "\", \"" + ftype + "\");\n");
        write("v" + ra.allocate(result) + " = (" + Types.nativeType(result.getType())
                + ") env->GetStatic" + Types.typeDescriptor(ftype) + "Field(clz,fld);\n");
        writeUndefineExHandle(ins);
        write("}\n");
    }

    private String getVariableOrConst(Value var) {
        if (var instanceof Constant) {
            return String.valueOf(var);
        }
        return "v" + ra.allocate(var);
    }

    private static int register(Value value) {
        return value instanceof Variable ? ((Variable) value).register : -1;
    }

    private void writeTrace(Instruction ins) {
        String s = ins.dump();
        if (s != null && !s.isEmpty()) {
            write("LOGD(\"" + s + "\");\n");
        }
    }

    private void write(String s) {
        buffer.append(s);
    }

    private void endIns() {
        write(";\n");
    }

    static final class TmpnameAllocator {

        private final Map<String, Integer> numbering = new HashMap<>();
        private int nextName;
        private final String prefix;

        TmpnameAllocator(List<String> vars, String prefix) {
            this.prefix = prefix;
            for (String item : vars) {
                if (item == null) {
                    throw new Dex2CException("cannot allocate a tmpname for null");
                }
                numbering.putIfAbsent(item, nextName++);
            }
        }

        String get(String item) {
            Integer number = numbering.get(item);
            if (number == null) {
                throw new Dex2CException("no tmpname allocated for " + item);
            }
            return prefix + number;
        }
    }
}
