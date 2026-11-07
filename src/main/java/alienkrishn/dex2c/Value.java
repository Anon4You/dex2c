package alienkrishn.dex2c;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

class Value {

    String varType;
    boolean typeSealed;
    Instruction definition;
    final Set<Use> uses = new LinkedHashSet<>();
    boolean isConst;
    boolean allowRef = true;

    boolean setType(String vtype) {
        if (vtype == null || typeSealed) {
            return false;
        }
        typeSealed = true;
        if (varType == null) {
            varType = vtype;
            return true;
        }
        if (varType.equals(vtype)) {
            return false;
        }
        varType = vtype;
        return true;
    }

    boolean refineType(String vtype) {
        if (vtype == null || typeSealed || vtype.equals(varType)) {
            return false;
        }
        if (!allowRef && Types.isRef(vtype)) {
            return false;
        }
        if (varType == null) {
            varType = vtype;
            return true;
        }
        if (Types.isJavaLangObject(vtype) && Types.isRef(varType)) {
            return false;
        }
        if (Types.isJavaLangObject(varType) && Types.isRef(vtype)) {
            return false;
        }
        if ((Types.isInt(varType) && Types.isInt(vtype))
                || (Types.isFloat(varType) && Types.isFloat(vtype))) {
            String newType = Types.smallerType(varType, vtype);
            if (!newType.equals(varType)) {
                varType = newType;
                return true;
            }
            return false;
        }
        if (Types.isInt(varType) && (Types.isFloat(vtype) || Types.isRef(vtype))) {
            if (isConst) {
                if (Types.isRef(vtype) && !allowRef) {
                    return false;
                }
                varType = vtype;
                return true;
            }
            if (Types.isFloat(vtype)) {
                return false;
            }
            throw new Dex2CException("unable to refine type " + varType + " " + vtype);
        }
        String newType = Types.mergeType(varType, vtype);
        if (newType == null) {
            throw new Dex2CException("unable to refine type " + varType + " " + vtype);
        }
        if (!varType.equals(newType)) {
            varType = newType;
            return true;
        }
        return false;
    }

    String getType() {
        return varType;
    }

    boolean useEmpty() {
        return uses.isEmpty();
    }

    Set<Use> getUses() {
        return uses;
    }

    ArrayList<User> getUsers() {
        ArrayList<User> users = new ArrayList<>();
        for (Use use : uses) {
            if (!users.contains(use.user)) {
                users.add(use.user);
            }
        }
        return users;
    }

    void addUser(User user) {
        for (Use use : uses) {
            if (use.user == user) {
                return;
            }
        }
        uses.add(new Use(this, user));
    }

    void removeUser(User user) {
        uses.removeIf(use -> use.user == user);
    }

    void visit(Writer writer) {
        if (this instanceof Constant) {
            writer.visitConstant((Constant) this);
        } else {
            writer.visitVariable((Variable) this);
        }
    }

    void replaceAllUsesWith(Value newValue) {
        for (Use use : new ArrayList<>(uses)) {
            use.user.replaceUseOfWith(use.value, newValue);
        }
        uses.clear();
    }
}

interface User {

    void replaceUseOfWith(Value oldVar, Value newVar);
}

final class Use {

    final Value value;
    final User user;

    Use(Value value, User user) {
        this.value = value;
        this.user = user;
    }
}
