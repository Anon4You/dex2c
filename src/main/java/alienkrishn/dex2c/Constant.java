package alienkrishn.dex2c;

class Constant extends Value {

    final Object constant;

    Constant(Object value) {
        this(value, null);
    }

    Constant(Object value, String vtype) {
        constant = value;
        isConst = true;
        if (vtype != null) {
            refineType(vtype);
        }
    }

    Object getConstant() {
        if (varType != null && varType.charAt(0) == 'L') {
            return Types.hexEscapeString(String.valueOf(constant));
        }
        return constant;
    }

    @Override
    public String toString() {
        String atype = varType;
        if ("F".equals(atype)) {
            return "d2c_bitcast_to_float(" + constant + ")";
        }
        if ("D".equals(atype)) {
            return "d2c_bitcast_to_double(" + constant + ")";
        }
        if (Types.isInt(atype) || Types.isLong(atype)) {
            return String.valueOf(constant);
        }
        return String.valueOf(getConstant());
    }
}
