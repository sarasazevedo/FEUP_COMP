package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import static pt.up.fe.comp2025.ast.Kind.TYPE;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {

    private int whileCounter = 0;
    private int ifCounter = 0;
    private int tempCounter = 0;

    public String nextWhileLabel() {
        return "while" + whileCounter++;
    }

    public String nextIfLabel() {
        return "if" + ifCounter++;
    }

    public String nextEndIfLabel() {
        return "endif" + ifCounter;
    }


    private final AccumulatorMap<String> temporaries;

    private final TypeUtils types;

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
    }


    public String nextTemp() {

        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {

        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;

        return prefix + nextTempNum;
    }


    public String toOllirType(JmmNode typeNode) {

        TYPE.checkOrThrow(typeNode);

        return toOllirType(types.convertType(typeNode));
    }

    public String toOllirType(Type type) {
        String typeName = type.getName();

        if (type.isArray()) {
            return ".array" + toOllirType(typeName);
        }

        return toOllirType(typeName);
    }

    private String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "String" -> "array.String";
            case "unknown" -> "V"; // Tratar como void temporariamente
            //default -> throw new NotImplementedException(typeName);
            default -> typeName;
        };

        return type;
    }


}
