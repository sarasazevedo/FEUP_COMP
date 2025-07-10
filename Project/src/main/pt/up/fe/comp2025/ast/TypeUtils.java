package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newBoolType() {
        return new Type("boolean", false);
    }

    public static Type newVoidType() {
        return new Type("void", false);
    }

    public static Type newStringType() {
        return new Type("String", false);
    }

    public static Type convertType(JmmNode typeNode) {
        var name = typeNode.get("name");
        var isArray = typeNode.getKind().equals("ArrayType");
        return new Type(name, isArray);
    }
    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {
        // TODO: Update when there are new types
        // return new Type("int", false);
        return getExprType(expr, this.table);
    }

    public static Type getExprType(JmmNode expr, JmmSymbolTable table) {
        String kind = expr.getKind();

        return switch (kind) {
            case "IntegerLiteral", "ArrayAccessExpr", "ArrayLengthExpr", "MethodLength" -> new Type("int", false);
            case "BooleanLiteral", "NotExpr", "Comparison", "Equality", "Logical" -> new Type("boolean", false);
            case "StringLiteral" -> new Type("String", false);
            case "VarRefExpr" -> getVarType(expr, table);
            case "BinaryExpr" -> getBinaryExprType(expr, table);
            case "NewArrayExpr", "ArrayExpr" -> new Type("int", true);
            case "NewClassExpr" -> new Type(expr.get("name"), false);
            case "MethodCall" -> getMethodCallType(expr, table);
            case "MethodRefExpr" -> getMethodRefType(expr, table);
            case "ThisExpr" -> new Type(table.getClassName(), false);
            case "ExprExpr" -> getExprType(expr.getChild(0), table);
            default -> new Type("unknown", false);
        };
    }

    public static Type getVarType(JmmNode varRef, JmmSymbolTable table) {
        if (varRef.getKind().equals("ArrayAccessExpr")) {
            JmmNode arrayExpr = varRef.getChildren().get(0);
            Type arrayType = getVarType(arrayExpr, table);
            // Verificar se é realmente um array
            if (arrayType.isArray()) {
                return new Type(arrayType.getName(), false);
            } else {
                return null;
            }
        }

        JmmNode auxNode = varRef;
        String currentMethod ="";
        while (auxNode != null) {
            if (auxNode.getKind().equals("MethodDecl")) {
                currentMethod = auxNode.get("name");
            }
            auxNode = auxNode.getParent();
        }

        String lookUpVariable = varRef.get("name");
        for (var local : table.getLocalVariables(currentMethod)) {
            if (local.getName().equals(lookUpVariable)) {
                return local.getType();
            }
        }

        for (var param : table.getParameters(currentMethod)) {
            if (param.getName().equals(lookUpVariable)) {
                return param.getType();
            }
        }

        for (var field : table.getFields()) {
            if (field.getName().equals(lookUpVariable)) {
                return field.getType();
            }
        }

        return new Type("unknown", false);
    }

    // Determines the resulting type of binary expression
    private static Type getBinaryExprType(JmmNode expr, JmmSymbolTable table) {
        Type leftType = getExprType(expr.getChild(0), table);
        Type rightType = getExprType(expr.getChild(1), table);
        String operator = expr.get("op");

        // All arithmetic operators from the grammar
        switch (operator) {
            // Arithmetic operators
            case "+", "-", "*", "/" -> {
                if (leftType.getName().equals("int") && rightType.getName().equals("int")) {
                    return newIntType();
                }
            }
            // Logical operators
            case "&&", "||" -> {
                if (leftType.getName().equals("boolean") && rightType.getName().equals("boolean")) {
                    return newBoolType();
                }
            }
            // Comparison operators
            case "<", ">", "<=", ">=" -> {
                if (leftType.getName().equals("int") && rightType.getName().equals("int")) {
                    return newBoolType();
                }
            }
            // Equality operators
            case "==", "!=" -> {
                return newBoolType();
            }
        }

        System.err.println("Invalid binary expression: " + leftType.getName() + " " + operator + " " + rightType.getName());
        return new Type("unknown", false);
    }

    // Determines the returning type of a method call
    private static Type getMethodCallType(JmmNode methodCall, JmmSymbolTable table) {
        String methodName = methodCall.getChild(1).get("name");
        // Return the return type that is defined for the method
        if (table.getMethods().contains(methodName)) {
            return table.getReturnType(methodName);
        } else if (methodName.equals("length")) {
            return new Type("int", false);
        } else {
            // For methods not defined in the class
            JmmNode objectNode = methodCall.getChild(0);
            Type objectType = getExprType(objectNode, table);
            // For imported classes or superclass methods
            if (table.getImports().contains(objectType.getName()) ||
                    (table.getSuper() != null && objectType.getName().equals(table.getClassName()))) {
                return new Type("unknown", false);
            }
        }
        //System.err.println("Unknown method: " + methodName);
        return new Type("unknown", false);
    }

    private static Type getMethodRefType(JmmNode methodCall, JmmSymbolTable table) {
        String methodName = methodCall.get("name");

        if (table.getMethods().contains(methodName)) {
            return table.getReturnType(methodName);
        }

        return new Type("unknown", false);
    }
}
