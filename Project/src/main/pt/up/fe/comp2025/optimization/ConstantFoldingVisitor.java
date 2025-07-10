package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.Collections;

public class ConstantFoldingVisitor extends AJmmVisitor<Boolean, Boolean> {
    private final SymbolTable symbolTable;
    private boolean changed = false;

    public ConstantFoldingVisitor(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        addVisits();
    }

    private void addVisits() {
        // Adicionar visitas para expressões binárias
        addVisit("BinaryExpr", this::visitBinaryOp);
        addVisit("BinOp", this::visitBinaryOp);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("ArithmeticOp", this::visitBinaryOp);
        addVisit("ComparisonOp", this::visitBinaryOp);
        addVisit("LogicalOp", this::visitBinaryOp);

        // Operações unárias
        addVisit("UnaryExpr", this::visitUnaryOp);
        addVisit("UnaryOp", this::visitUnaryOp);
        addVisit("NotOp", this::visitUnaryOp);

        // Default para outros nós
        setDefaultVisit(this::defaultVisit);
    }
    private Boolean visitBinaryOp(JmmNode node, Boolean data) {
        // Visitar recursivamente os operandos primeiro
        for (JmmNode child : node.getChildren()) {
            visit(child, data);
        }

        // Se não tiver 2 operandos após as otimizações, não podemos continuar
        if (node.getChildren().size() < 2) {
            return true;
        }

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        // Identificar o operador
        String op = null;
        if (node.hasAttribute("op")) {
            op = node.get("op");
        } else if (node.hasAttribute("operator")) {
            op = node.get("operator");
        }

        if (op == null) {
            return true;
        }

        // Folding para expressões com inteiros
        if ((left.getKind().equals("IntegerLiteral") || left.getKind().equals("IntLiteral")) &&
                (right.getKind().equals("IntegerLiteral") || right.getKind().equals("IntLiteral"))) {

            // Extrair os valores dos literais
            int leftValue = Integer.parseInt(left.get("value"));
            int rightValue = Integer.parseInt(right.get("value"));

            // Aplicar a operação de acordo com o operador
            switch (op) {
                case "+":
                    createIntLiteral(node, leftValue + rightValue);
                    break;
                case "-":
                    createIntLiteral(node, leftValue - rightValue);
                    break;
                case "*":
                    createIntLiteral(node, leftValue * rightValue);
                    break;
                case "/":
                    if (rightValue == 0) {
                        return true; // Evitar divisão por zero
                    }
                    createIntLiteral(node, leftValue / rightValue);
                    break;
                case "<":
                    createBoolLiteral(node, leftValue < rightValue);
                    break;
                case "<=":
                    createBoolLiteral(node, leftValue <= rightValue);
                    break;
                case ">":
                    createBoolLiteral(node, leftValue > rightValue);
                    break;
                case ">=":
                    createBoolLiteral(node, leftValue >= rightValue);
                    break;
                case "==":
                    createBoolLiteral(node, leftValue == rightValue);
                    break;
                case "!=":
                    createBoolLiteral(node, leftValue != rightValue);
                    break;
            }
        }
        // Folding para expressões com booleanos
        else if ((left.getKind().equals("BooleanLiteral") || left.getKind().equals("BoolLiteral")) &&
                (right.getKind().equals("BooleanLiteral") || right.getKind().equals("BoolLiteral"))) {

            boolean leftValue = Boolean.parseBoolean(left.get("value"));
            boolean rightValue = Boolean.parseBoolean(right.get("value"));

            switch (op) {
                case "&&":
                    createBoolLiteral(node, leftValue && rightValue);
                    break;
                case "||":
                    createBoolLiteral(node, leftValue || rightValue);
                    break;
            }
        }

        return true;
    }

    private Boolean visitUnaryOp(JmmNode node, Boolean data) {
        visitAllChildren(node, data);

        if (node.getChildren().size() < 1) {
            return true;
        }

        JmmNode operand = node.getChildren().get(0);
        String op = node.hasAttribute("op") ? node.get("op") : null;

        if (op == null) {
            return true;
        }

        if (op.equals("!") && (operand.getKind().equals("BooleanLiteral") || operand.getKind().equals("BoolLiteral"))) {
            boolean value = Boolean.parseBoolean(operand.get("value"));
            createBoolLiteral(node, !value);
        } else if (op.equals("-") && (operand.getKind().equals("IntegerLiteral") || operand.getKind().equals("IntLiteral"))) {
            int value = Integer.parseInt(operand.get("value"));
            createIntLiteral(node, -value);
        }

        return true;
    }

    private void createIntLiteral(JmmNode node, int value) {
        JmmNode resultNode = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
        resultNode.put("value", String.valueOf(value));

        // Substituir o nó atual pelo literal calculado
        node.replace(resultNode);
        changed = true;
    }

    private void createBoolLiteral(JmmNode node, boolean value) {
        JmmNode resultNode = new JmmNodeImpl(Collections.singletonList("BooleanLiteral"));
        resultNode.put("value", String.valueOf(value));

        // Substituir o nó atual pelo literal calculado
        node.replace(resultNode);
        changed = true;
    }

    private Boolean defaultVisit(JmmNode node, Boolean data) {
        return visitAllChildren(node, data);
    }

    public boolean optimize(JmmNode root) {
        changed = false;
        visit(root, true);
        return changed;
    }

    @Override
    protected void buildVisitor() {
        // Método requerido pela classe pai
    }

    @Override
    public Boolean visit(JmmNode node, Boolean data) {
        return super.visit(node, data);
    }
}

