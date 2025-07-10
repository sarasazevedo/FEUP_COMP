package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.*;

public class UndeclaredMethod extends AnalysisVisitor {
    private String currentMethod;
    private final Map<String, Boolean> listOfStaticMethods = new HashMap<>();
    List<String> listOfParams = new ArrayList<>();
    Map<String, String> listOfVars = new HashMap<>();

    private final Set<String> declaredMethods = new HashSet<>();
    private final Map<String, JmmNode> importSimpleNames = new HashMap<>();

    // Build the visitor for different types of nodes
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_REF_EXPR, this::visitMethodCall);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.METHOD_CALL, this::visitMethodCallExpr);
        addVisit(Kind.THIS_EXPR, this::visitThisExpr);
        addVisit(Kind.PARAM, this::visitParam);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.IF_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.IMPORT_STMT, this::visitImportStmt);

    }
    private Void visitImportStmt(JmmNode importStmt, SymbolTable table) {
        String importPath = importStmt.get("name");
        String simpleName = getSimpleName(importPath);

        if (importSimpleNames.containsKey(simpleName)) {
            JmmNode previousNode = importSimpleNames.get(simpleName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    importStmt.getLine(),
                    importStmt.getColumn(),
                    "Duplicate import: '" + simpleName + "' was already imported at line " + previousNode.getLine(),
                    null
            ));
        } else {
            importSimpleNames.put(simpleName, importStmt);
        }

        return null;
    }

    private String getSimpleName(String importPath) {
        int lastDot = importPath.lastIndexOf('.');
        return lastDot != -1 ? importPath.substring(lastDot + 1) : importPath;
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

    private Void visitBinaryExpr(JmmNode expr, SymbolTable table) {
        String operator = expr.get("op");
        // Verificar apenas operadores lógicos
        if (operator.equals("&&") || operator.equals("||")) {
            Type leftType = TypeUtils.getExprType(expr.getChild(0), (JmmSymbolTable) table);
            Type rightType = TypeUtils.getExprType(expr.getChild(1), (JmmSymbolTable) table);

            if (leftType == null || !leftType.getName().equals("boolean") || leftType.isArray()) {
                addTypeError(expr.getChild(0), "Left operand of logical operator '" + operator + "' must be of type boolean.");
            }

            if (rightType == null || !rightType.getName().equals("boolean") || rightType.isArray()) {
                addTypeError(expr.getChild(1), "Right operand of logical operator '" + operator + "' must be of type boolean.");
            }
        }
        return null;
    }

    //Handles the method calls
    private Void visitMethodCall(JmmNode methodRef, SymbolTable table) {
        String methodName = methodRef.get("name");

        if (table.getMethods().contains(methodName)) {
            // Apenas os argumentos do método, não todos os nós filhos
            List<JmmNode> args = new ArrayList<>();

            // Verificar se existem argumentos no nó
            for (JmmNode child : methodRef.getChildren()) {
                if (child.getKind().equals("Argument") ||
                        child.getKind().equals("ExprStmt") ||
                        !child.hasAttribute("name")) {
                    args.add(child);
                }
            }

            List<Symbol> params = table.getParameters(methodName);

            // Verificação básica de número de parâmetros antes da verificação completa
            if (args.size() != params.size() &&
                    !(params.size() > 0 && params.get(params.size() - 1).getType().hasAttribute("isVarArg"))) {
                addTypeError(methodRef, "Incompatible number of arguments for method '" + methodName +
                        "'. Expected " + params.size() + ", got " + args.size() + ".");
            }
            else if (!areArgumentsCompatible(args, params, (JmmSymbolTable) table)) {
                addTypeError(methodRef, "Incompatible arguments for method '" + methodName + "'.");
            }
        }
        return null;
    }

    private boolean areArgumentsCompatible(List<JmmNode> args, List<Symbol> params, JmmSymbolTable table) {
        // Empty parameter list
        if (params.isEmpty()) {
            return args.isEmpty();
        }

        // Check varargs validity: only the last parameter can be an array
        for (int i = 0; i < params.size() - 1; i++) {
            if (params.get(i).getType().hasAttribute("isVarArg")) {
                // only the last can be varargs
                return false;
            }
        }

        Symbol lastParam = params.get(params.size() - 1);
        boolean isVarargs = lastParam.getType().hasAttribute("isVarArg");

        int fixedParamCount = isVarargs ? params.size() - 1 : params.size();

        // For non-varargs methods, argument count must match exactly
        if (!isVarargs && args.size() != params.size()) {
            return false;
        }

        // For varargs methods, must have at least fixed parameters
        if (isVarargs && args.size() < fixedParamCount) {
            return false;
        }

        // Check varargs arguments
        if (isVarargs) {
            Type varargElementType = new Type(lastParam.getType().getName(), false);
            for (int i = fixedParamCount; i < args.size(); i++) {
                Type actual = TypeUtils.getExprType(args.get(i), table);
                if (!typesAreCompatible(varargElementType, actual, table)) {
                    return false;
                }
            }
        } else {
            // Check fixed parameters
            for (int i = 0; i < fixedParamCount; i++) {
                Type expected = params.get(i).getType();
                Type actual = TypeUtils.getExprType(args.get(i), table);
                if (!typesAreCompatible(expected, actual, table)) {
                    return false;
                }
            }
        }

        return true;
    }


    // Check if the return type of a method is compatible with the declared return type
    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        JmmNode methodNode = returnStmt.getAncestor(Kind.METHOD_DECL).orElse(null);
        if (methodNode == null) return null;

        String methodName = methodNode.get("name");
        Type expectedReturnType = table.getReturnType(methodName);

        // Se o método não retornar valor (void), mas tiver uma expressão de retorno
        if (expectedReturnType.getName().equals("void") && returnStmt.getNumChildren() > 0) {
            addTypeError(returnStmt, "Cannot return a value from a void method.");
            return null;
        }

        // Se o método retornar valor, mas não tiver expressão de retorno
        if (!expectedReturnType.getName().equals("void") && returnStmt.getNumChildren() == 0) {
            addTypeError(returnStmt, "Missing return value for non-void method '" + methodName + "'.");
            return null;
        }

        // Verificação para métodos não void
        if (!expectedReturnType.getName().equals("void")) {
            Type actualReturnType = TypeUtils.getExprType(returnStmt.getChild(0), (JmmSymbolTable) table);

            if (!actualReturnType.getName().equals("unknown")) {
                // Verificação específica para arrays
                if (expectedReturnType.isArray() != actualReturnType.isArray()) {
                    if (expectedReturnType.isArray()) {
                        addTypeError(returnStmt, "Method '" + methodName + "' should return array type '" +
                                expectedReturnType.getName() + "[]', but returns non-array type '" +
                                actualReturnType.getName() + "'.");
                    } else {
                        addTypeError(returnStmt, "Method '" + methodName + "' should return non-array type '" +
                                expectedReturnType.getName() + "', but returns array type '" +
                                actualReturnType.getName() + "[]'.");
                    }
                }
                // Verificação do tipo base
                else if (!typesAreCompatible(expectedReturnType, actualReturnType, (JmmSymbolTable) table)) {
                    addTypeError(returnStmt, "Return type mismatch: expected '" + expectedReturnType.getName() +
                            (expectedReturnType.isArray() ? "[]" : "") + "' but found '" +
                            actualReturnType.getName() + (actualReturnType.isArray() ? "[]" : "") + "'.");
                }
            }
        }

        return null;
    }

    // Helper to check if two types are compatible
    private boolean typesAreCompatible(Type expected, Type actual, JmmSymbolTable table) {
        // A característica de array deve corresponder exatamente
        if (expected.isArray() != actual.isArray()) {
            return false;
        }

        if (expected.getName().equals(actual.getName())) {
            return true;
        }

        String parent = table.getSuper();
        if (parent != null && parent.equals(expected.getName()) && actual.getName().equals(table.getClassName())) {
            return true;
        }

        return table.getImports().contains(actual.getName()) ||
                (table.getImports().contains(expected.getName()) && !expected.getName().equals(table.getClassName()));
    }

    //Handles the method calls
    private Void visitMethodCallExpr(JmmNode methodCall, SymbolTable table) {
        // Get object node (this) and method node
        JmmNode objectNode = methodCall.getChild(0);
        JmmNode methodNode = methodCall.getChild(1);

        // Process only "this" method calls
        if (objectNode.getKind().equals("ThisExpr")) {
            String methodName = methodNode.get("name");
            if (table.getMethods().contains(methodName)) {
                List<JmmNode> args = methodCall.getChildren().subList(0, methodCall.getNumChildren());
                List<Symbol> params = table.getParameters(methodName);
                if (!areArgumentsCompatible(args, params, (JmmSymbolTable) table)) {
                    addTypeError(methodCall, "Incompatible arguments for method '" + methodName + "'.");
                }
            }
        }
        return null;
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

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        String methodName = method.get("name");
        currentMethod = methodName;
        listOfParams = null;

        // Verificação de método duplicado
        if (declaredMethods.contains(methodName)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    method.getLine(),
                    method.getColumn(),
                    "Duplicate method: '" + methodName + "' was already declared",
                    null
            ));
        } else {
            declaredMethods.add(methodName);
        }

        boolean isStatic = Boolean.parseBoolean(method.get("isStatic"));
        listOfStaticMethods.put(currentMethod, isStatic);
        Type returnType = table.getReturnType(methodName);

        List<JmmNode> stmts = method.getChildren(Kind.STMT);

        boolean isVoid = returnType.getName().equals("void");
        int returnCount = 0;

        for (int i = 0; i < stmts.size(); i++) {
            JmmNode stmt = stmts.get(i);

            if (stmt.getKind().equals("ReturnStmt")) {
                returnCount++;

                // Só pode haver um return por método
                if (returnCount > 1) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            stmt.getLine(),
                            stmt.getColumn(),
                            "Multiple return statements in method '" + methodName + "'. Only one is allowed.",
                            null
                    ));
                }

                // O return deve ser o último statement
                if (i != stmts.size() - 1) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            stmts.get(i + 1).getLine(),
                            stmts.get(i + 1).getColumn(),
                            "Unreachable code after return statement in method '" + methodName + "'.",
                            null
                    ));
                }
            }
        }

        // Para métodos não-void: deve haver exatamente 1 return, e deve estar no final
        if (!isVoid) {
            if (returnCount == 0) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        "Non-void method '" + methodName + "' must end with a return statement.",
                        null
                ));
            }
        } else {
            // Para métodos void: no máximo um return vazio (opcional), também no final
            if (returnCount > 1) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        "Void method '" + methodName + "' has multiple return statements. Only one is allowed.",
                        null
                ));
            }
        }

        return null;
    }


    private Void visitThisExpr(JmmNode node, SymbolTable table) {
        // Obtém o método em que 'this' é usado
        var methodOpt = node.getAncestor(Kind.METHOD_DECL);

        if (methodOpt.isEmpty()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "'this' cannot be used outside a method context.",
                    null
            ));
            return null;
        }

        String methodName = methodOpt.get().get("name");

        // Verifica se a lista de métodos está corretamente inicializada
        boolean isStatic = listOfStaticMethods != null &&
                listOfStaticMethods.getOrDefault(methodName, false);

        if (isStatic) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Cannot use 'this' in static method '" + methodName + "'.",
                    null
            ));
        }

        // Verifica se 'this' está sendo usado dentro de 'main'
        if (methodName.equals("main")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Cannot use 'this' inside the 'main' method.",
                    null
            ));
        }

        return null;
    }

    private Void visitParam(JmmNode node, SymbolTable table) {
        // Inicializa apenas se for o primeiro parâmetro do metodo
        if (listOfParams == null) {
            listOfParams = new ArrayList<>();
        }

        String paramName = node.get("name");

        // Verifica duplicados
        if (listOfParams.contains(paramName)) {
            var message = String.format("Parameter '%s' already declared in method '%s'.", paramName, currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null));
            return null;
        }

        // Verifica tipo inválido (Void)
        String childType = node.getChild(0).get("name");
        if ("Void".equals(childType)) {
            var message = String.format("Cannot use type Void for parameter '%s' in method '%s'.", paramName, currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null));
            return null;
        }

        listOfParams.add(paramName);
        return null;
    }


    private Void visitVarDecl (JmmNode node, SymbolTable table) {
        String varName = node.get("name");

        if (listOfParams != null && listOfParams.contains(varName)) {
            var message = String.format("Variable '%s' already declared in method '%s' parameters.", varName, currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        if (listOfVars.containsKey(currentMethod) && listOfVars.get(currentMethod).equals(varName)) {
            var message = String.format("Variable '%s' already declared in method '%s'.", varName, currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        listOfVars.put(currentMethod, varName);
        return null;
    }


}
