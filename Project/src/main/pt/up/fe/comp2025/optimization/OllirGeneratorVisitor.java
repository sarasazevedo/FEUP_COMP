package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.Map;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;
/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";

    private final SymbolTable table;
    private final TypeUtils types;
    private final OptUtils ollirTypes;
    private final OllirExprGeneratorVisitor exprVisitor;
    private boolean optimizationsEnabled = false;


    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }

    public void setOptimizationsEnabled(boolean enabled) {
        this.optimizationsEnabled = enabled;
        exprVisitor.setOptimizationsEnabled(enabled);
    }
    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IMPORT_STMT, this::visitImportStmt);
        addVisit(STMT, this::visitStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var rhs = exprVisitor.visit(node.getChild(1));
        var left = node.getChild(0);

        StringBuilder code = new StringBuilder();

        code.append(rhs.getComputation());

        if (left.getKind().equals("ArrayAccessExpr")) {
            OllirExprResult arrayResult = exprVisitor.visit(left.getChild(0));
            OllirExprResult indexResult = exprVisitor.visit(left.getChild(1));
            code.append(arrayResult.getComputation());
            code.append(indexResult.getComputation());
            Type elementType = types.getExprType(node.getChild(1));
            String elementOllirType = ollirTypes.toOllirType(elementType);
            code.append(arrayResult.getCode()).append("[").append(indexResult.getCode()).append("]")
                    .append(elementOllirType)
                    .append(SPACE).append(ASSIGN).append(elementOllirType).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);
        } else {
            String varName = left.get("name");
            boolean isField = isField(varName, left);

            if (isField) {
                String className = table.getClassName();
                Type fieldType = types.getExprType(left);
                String fieldTypeStr = ollirTypes.toOllirType(fieldType);

                code.append("putfield(this.")
                        .append(className)
                        .append(", ")
                        .append(varName)
                        .append(fieldTypeStr)
                        .append(", ")
                        .append(rhs.getCode())
                        .append(").V")
                        .append(END_STMT);
            } else {
                Type thisType = types.getExprType(left);
                String typeString = ollirTypes.toOllirType(thisType);
                var varCode = varName + typeString;
                code.append(varCode).append(SPACE)
                        .append(ASSIGN).append(typeString).append(SPACE)
                        .append(rhs.getCode()).append(END_STMT);

                // Register constant value if RHS is a literal
                JmmNode rhsNode = node.getChild(1);
                if (optimizationsEnabled && rhsNode.getKind().equals("IntegerLiteral")) {
                    exprVisitor.registerConstantValue(varName, rhsNode.get("value"), typeString);
                }
            }
        }

        return code.toString();
    }

    // Método auxiliar para verificar se uma variável é um campo da classe
    private boolean isField(String varName, JmmNode node) {
        JmmNode methodNode = node;
        while (methodNode != null && !methodNode.getKind().equals("MethodDecl")) {
            methodNode = methodNode.getParent();
        }

        if (methodNode == null) {
            return false;
        }

        String methodName = methodNode.get("name");

        for (var local : table.getLocalVariables(methodName)) {
            if (local.getName().equals(varName)) {
                return false;
            }
        }

        for (var param : table.getParameters(methodName)) {
            if (param.getName().equals(varName)) {
                return false;
            }
        }

        for (var field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return true;
            }
        }

        return false;
    }

    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        if (node.getNumChildren() > 0) {
            JmmNode expr = node.getChild(0);
            var exprResult = exprVisitor.visit(expr);
            Type retType = types.getExprType(expr);
            String typeString = ollirTypes.toOllirType(retType);

            if (!exprResult.getComputation().isEmpty()) {
                String temp = ollirTypes.nextTemp() + typeString;
                code.append(exprResult.getComputation());
                code.append(temp).append(" := ").append(typeString)
                        .append(" ").append(exprResult.getCode()).append(END_STMT);
                code.append("ret").append(typeString).append(" ").append(temp).append(END_STMT);
            } else {
                code.append("ret").append(typeString).append(" ").append(exprResult.getCode()).append(END_STMT);
            }
        } else {
            code.append("ret.V").append(END_STMT);;
        }

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {
        // Verificar se o nó tem um filho tipo
        if (node.getNumChildren() == 0) {
            System.out.println("ERRO: Nó de parâmetro não tem filhos (sem tipo)");
            return node.get("name") + ".unknown";
        }

        var typeNode = node.getChild(0);
        Type paramType = types.convertType(typeNode);


        // Se for vararg e não for array, transformamos em array primeiro
        boolean isVarArg = node.hasAttribute("isVarArg");
        if (isVarArg && !paramType.isArray()) {
            paramType = new Type(paramType.getName(), true);
        }

        var typeCode = ollirTypes.toOllirType(paramType);
        var id = node.get("name");

        return id + typeCode;
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = node.getBoolean("isPublic", false);
        String name = node.get("name");

        if (isPublic) {
            code.append("public ");
        }

        // Tratamento especial para método main
        if (name.equals("main")) {
            code.append("static ");
        }

        var params = node.getChildren(PARAM);
        boolean isVarArg = false;
        for (var param : params) {
            isVarArg = param.hasAttribute("isVarArg");
        }
        if (isVarArg) {
            code.append("varargs ");
        }

        code.append(name);
        code.append("(");

        // Adiciona parâmetro args para método main
        if (name.equals("main") && params.isEmpty()) {
            code.append("args.array.String");
        } else if (!params.isEmpty()) {
            String paramsCode = params.stream()
                    .map(this::visit)
                    .collect(Collectors.joining(", "));
            code.append(paramsCode);
        }
        code.append(")");

        var returnTypeNode = node.getChild(0); // primeiro filho é o tipo de retorno
        var retType = ollirTypes.toOllirType(types.convertType(returnTypeNode));
        code.append(retType);
        code.append(L_BRACKET);

        // rest of its children stmts
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);

        // adiciona ret.V caso o metodo não contenha return e seja do tipo Void
        if (retType.equals(".V") && !stmtsCode.contains("ret.V;")) {
            code.append(NL);
            code.append("ret.V").append(END_STMT);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        // Adicionar a superclasse se existir
        if (table.getSuper() != null) {
            code.append(" extends ").append(table.getSuper());
        }

        code.append(L_BRACKET);
        code.append(NL);

        for (var field : table.getFields()) {
            String typeStr = ollirTypes.toOllirType(field.getType());
            code.append("    .field ").append(field.getName()).append(typeStr).append(END_STMT);
        }

        code.append(NL);
        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(NL);
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }

    // Como as importações não geram código OLLIR diretamente retornamos uma string vazia
    private String visitImportStmt(JmmNode node, Void unused) {
        return "";
    }

    private String visitStmt(JmmNode node, Void unused) {
        if (node.getKind().equals("ExprStmt")) {
            StringBuilder code = new StringBuilder();

            if (!node.getChildren().isEmpty()) {
                var expr = exprVisitor.visit(node.getChild(0));
                code.append(expr.getComputation());
                code.append(expr.getCode());
                code.append(END_STMT);
            }

            return code.toString();
        }
        return visit(node.getChild(0));
    }

    private String visitIfStmt(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        var conditionResult = exprVisitor.visit(node.getChild(0));
        code.append(conditionResult.getComputation());

        // labels únicos
        String thenLabel = ollirTypes.nextIfLabel();
        String endLabel = ollirTypes.nextEndIfLabel();

        // if (cond) goto then
        code.append("if (").append(conditionResult.getCode()).append(") goto ").append(thenLabel).append(END_STMT);

        // else branch (child 2)
        var elseBranch = node.getChild(2);
        for (var stmt : elseBranch.getChildren()) {
            code.append(visit(stmt));
        }

        // goto endif
        code.append("goto ").append(endLabel).append(END_STMT);

        // then label
        code.append(thenLabel).append(":").append(NL);
        var thenBranch = node.getChild(1);
        for (var stmt : thenBranch.getChildren()) {
            code.append(visit(stmt));
        }

        // endif label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        // labels únicos
        String whileLabel = ollirTypes.nextWhileLabel();
        String endLabel = "end" + whileLabel;

        // label de início do loop
        code.append(whileLabel).append(":").append(NL);

        var conditionResult = exprVisitor.visit(node.getChild(0));
        code.append(conditionResult.getComputation());

        // if (!cond) goto end
        code.append("if (!.bool ").append(conditionResult.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // corpo do loop
        var body = node.getChild(1);
        for (var stmt : body.getChildren()) {
            code.append(visit(stmt));
        }

        // volta ao início do loop
        code.append("goto ").append(whileLabel).append(END_STMT);

        // label de fim
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }


}
