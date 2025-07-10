package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;

public class IncompatibleType extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.IF_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
    }

    private Void visitBinaryExpr(JmmNode expr, SymbolTable table) {
        String operator = expr.get("op");

        Type leftType = TypeUtils.getExprType(expr.getChild(0), (JmmSymbolTable) table);
        Type rightType = TypeUtils.getExprType(expr.getChild(1), (JmmSymbolTable) table);

        if (leftType == null || rightType == null) {
            addTypeError(expr, "Could not resolve operand types in binary expression.");
            return null;
        }

        switch (operator) {
            case "+": case "-": case "*": case "/":
                if (!leftType.getName().equals("int") || !rightType.getName().equals("int")) {
                    addTypeError(expr, "Arithmetic operations require both operands to be of type int.");
                }
                if (leftType.isArray() || rightType.isArray()) {
                    addTypeError(expr, "Cannot perform arithmetic operations on arrays.");
                }
                break;

            case ">": case "<": case ">=": case "<=":
                if (!leftType.getName().equals("int") || !rightType.getName().equals("int")) {
                    addTypeError(expr, "Comparison operations require both operands to be of type int.");
                }
                break;

            case "==": case "!=":
                if (!isAssignable((JmmSymbolTable) table, leftType, rightType) &&
                        !isAssignable((JmmSymbolTable) table, rightType, leftType)) {
                    addTypeError(expr, "Incompatible types for equality comparison.");
                }
                break;

            case "&&": case "||":
                if (!leftType.getName().equals("boolean") || !rightType.getName().equals("boolean")) {
                    addTypeError(expr, "Logical operations require both operands to be of type boolean.");
                }
                break;

            default:
                addTypeError(expr, "Unknown binary operator: " + operator);
                break;
        }

        return null;
    }

    private Void visitArrayAccessExpr(JmmNode access, SymbolTable table) {
        Type arrayType = TypeUtils.getExprType(access.getChild(0), (JmmSymbolTable) table);
        Type indexType = TypeUtils.getExprType(access.getChild(1), (JmmSymbolTable) table);

        if (arrayType == null || indexType == null) {
            addTypeError(access, "Could not resolve types in array access.");
            return null;
        }

        if (!arrayType.isArray()) {
            addTypeError(access, "Array access must be performed on an array type.");
        }

        if (!indexType.getName().equals("int") || indexType.isArray()) {
            addTypeError(access, "Array index must be of type int.");
        }

        return null;
    }

    private Void visitAssignStmt(JmmNode assign, SymbolTable table) {
        JmmSymbolTable jmmTable = (JmmSymbolTable) table;

        Type varType = TypeUtils.getVarType(assign.getChild(0), jmmTable);
        Type exprType = TypeUtils.getExprType(assign.getChild(1), jmmTable);
        JmmNode rightExpr = assign.getChild(1);

        if (varType == null || exprType == null) {
            addTypeError(assign, "Could not resolve types in assignment.");
            return null;
        }

        // Check array literal assignments
        if (rightExpr.getKind().equals(Kind.ARRAY_EXPR.toString())) {
            if (!varType.isArray()) {
                addTypeError(assign, "Cannot assign an array to a non-array variable.");
            }

            List<JmmNode> elements = rightExpr.getChildren();
            if (!elements.isEmpty()) {
                Type firstElementType = TypeUtils.getExprType(elements.get(0), jmmTable);
                for (JmmNode element : elements) {
                    Type elementType = TypeUtils.getExprType(element, jmmTable);
                    if (elementType == null || !firstElementType.getName().equals(elementType.getName()) ||
                            firstElementType.isArray() != elementType.isArray()) {
                        addTypeError(assign, "All array elements must have the same type.");
                        break;
                    }
                }
            }
        }

        // Check assignability
        if (!isAssignable(jmmTable, varType, exprType)) {
            addTypeError(assign, String.format("Cannot assign type %s%s to variable of type %s%s.",
                    exprType.getName(), exprType.isArray() ? "[]" : "",
                    varType.getName(), varType.isArray() ? "[]" : ""));
        }

        return null;
    }

    private Void visitIfStmt(JmmNode ifStmt, SymbolTable table) {
        JmmNode condition = ifStmt.getChild(0);
        Type conditionType = TypeUtils.getExprType(condition, (JmmSymbolTable) table);

        if (conditionType == null || !conditionType.getName().equals("boolean") || conditionType.isArray()) {
            addTypeError(condition, "Condition in 'if' statement must be of type boolean.");
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        JmmNode condition = whileStmt.getChild(0);
        Type conditionType = TypeUtils.getExprType(condition, (JmmSymbolTable) table);

        if (conditionType == null || !conditionType.getName().equals("boolean") || conditionType.isArray()) {
            addTypeError(condition, "Condition in 'while' statement must be of type boolean.");
        }

        return null;
    }

    private boolean isAssignable(JmmSymbolTable table, Type target, Type source) {
        // Must match array structure
        if (target.isArray() != source.isArray()) return false;

        // Primitive types must match
        if (isPrimitive(target.getName()) || isPrimitive(source.getName())) {
            return target.getName().equals(source.getName());
        }

        // If same type
        if (target.getName().equals(source.getName())) {
            return true;
        }

        // Check inheritance chain
        String sourceType = source.getName();
        while (sourceType != null) {
            if (sourceType.equals(target.getName())) {
                return true;
            }
            sourceType = table.getSuper();
        }

        // Allow imported types as fallback
        return table.getImports().contains(source.getName());
    }

    private boolean isPrimitive(String name) {
        return name.equals("int") || name.equals("boolean");
    }

    private void addTypeError(JmmNode node, String message) {
        addReport(Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null)
        );
    }
}
