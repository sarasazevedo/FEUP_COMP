package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    // Map to track constant values for variables
    private final Map<String, String> constantValues = new HashMap<>();
    private boolean optimizationsEnabled = false;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(NEW_ARRAY_EXPR, this::visitNewArrayExpr);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(LOGICAL, this::visitLogicalExpr);
        addVisit(COMPARISON, this::visitComparisonExpr);
        addVisit(BOOLEAN_LITERAL, this::visitBooleanLiteral);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccess);
        addVisit(ARRAY_LENGTH_EXPR, this::visitArrayLength);
        addVisit(NEW_CLASS_EXPR, this::visitNewClassExpr);
        addVisit(EXPR_EXPR, this::visitExprExpr);
        setDefaultVisit(this::defaultVisit);
    }

    public void setOptimizationsEnabled(boolean enabled) {
        this.optimizationsEnabled = enabled;
    }
    public void registerConstantValue(String varName, String value, String typeString) {
        if (optimizationsEnabled) {
            constantValues.put(varName, value + typeString);
        }
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        if (node == null || node.getNumChildren() < 2) {
            return OllirExprResult.EMPTY;
        }

        JmmNode targetNode = node.getChild(0);
        String objCode;
        boolean isStaticCall = false;

        if (targetNode.getKind().equals("ThisExpr")) {
            objCode = "this." + table.getClassName();
        } else if (targetNode.hasAttribute("name")) {
            String objName = targetNode.get("name");

            if (table.getImports().contains(objName)) {
                objCode = objName + ".V";
                isStaticCall = true;
            } else {
                OllirExprResult objResult = visit(targetNode);
                computation.append(objResult.getComputation());
                objCode = objResult.getCode();
            }
        } else {
            OllirExprResult objResult = visit(targetNode);
            computation.append(objResult.getComputation());
            objCode = objResult.getCode();
        }

        String methodName = node.getChild(1).get("name");

        List<String> argCodes = new ArrayList<>();
        JmmNode methodCallNode = node.getChild(1);
        for (int i = 0; i < methodCallNode.getNumChildren(); i++) {
            JmmNode argNode = methodCallNode.getChild(i);
            OllirExprResult arg = visit(argNode);
            computation.append(arg.getComputation());

            String temp = arg.getCode();
            // Se for uma chamada ou operação, forçamos atribuição temporária
            if (argNode.getKind().equals("MethodCall") || arg.getCode().contains("invoke") || arg.getCode().contains("+")) {
                String tmp = ollirTypes.nextTemp() + ".i32";
                computation.append(tmp).append(" :=.i32 ").append(arg.getCode()).append(END_STMT);
                temp = tmp;
            }

            argCodes.add(temp);
        }

        boolean isVarArgs = isVarArgsMethod(methodName);
        if (isVarArgs) {
            int numParams = table.getParameters(methodName).size() - 1;
            int numArgs = (methodCallNode.getNumChildren() - numParams) - 1;
            String arrayTemp = ollirTypes.nextTemp() + ".array.i32";
            computation.append(arrayTemp).append(" :=.array.i32 new(array, ")
                    .append(numArgs).append(".i32).array.i32;").append("\n");

            for (int i = 1; i < numArgs + 1; i++) {
                int idx = i + numParams;
                OllirExprResult arg = visit(methodCallNode.getChild(idx));
                computation.append(arg.getComputation());

                String assign = arrayTemp + "[" + (i - 1) + ".i32].i32 :=.i32 " + arg.getCode() + ";\n";
                computation.append(assign);
            }

            argCodes.clear();
            argCodes.add(arrayTemp);
        }

        Type returnType = TypeUtils.getExprType(node, (JmmSymbolTable) table);
        String returnTypeStr = ollirTypes.toOllirType(returnType);

        String[] aux = objCode.split("\\.");
        String objectCode = (aux.length == 2 && aux[1].equals("V")) ? aux[0] : objCode;

        StringBuilder invocation = new StringBuilder();
        invocation.append(isStaticCall ? "invokestatic(" : "invokevirtual(");
        invocation.append(objectCode).append(", \"").append(methodName).append("\"");
        for (String argCode : argCodes) {
            invocation.append(", ").append(argCode);
        }
        invocation.append(")").append(returnTypeStr);

        boolean haveTempArgs = false;
        for (String arg : argCodes) {
            if (arg.contains("tmp")) {
                haveTempArgs = true;
                break;
            }

        }
        // Decide se deve guardar em temporário ou não
        if (!returnTypeStr.equals(".V") && haveTempArgs) {
            String tempVar = ollirTypes.nextTemp() + returnTypeStr;
            computation.append(tempVar).append(" := ").append(returnTypeStr)
                    .append(" ").append(invocation).append(END_STMT);
            return new OllirExprResult(tempVar, computation);
        }

        return new OllirExprResult(invocation.toString(), computation);
    }

    private OllirExprResult visitNewArrayExpr(JmmNode node, Void unused) {
        // O primeiro filho é a expressão para o tamanho do array
        var sizeExpr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();

        // Código para calcular o tamanho
        computation.append(sizeExpr.getComputation());

        // Código para criar o array
        Type arrayType = new Type("int", true);
        String ollirArrayType = ollirTypes.toOllirType(arrayType);
        String tempVar = ollirTypes.nextTemp();
        String code = tempVar + ollirArrayType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirArrayType).append(SPACE)
                .append("new(array, ").append(sizeExpr.getCode()).append(")").append(ollirArrayType).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        // Garantir que o lhs e rhs não sejam vazios
        if (lhs.getCode().isEmpty() || rhs.getCode().isEmpty()) {
            // Caso um dos operandos seja vazio, retornar o outro
            String validCode = !lhs.getCode().isEmpty() ? lhs.getCode() : rhs.getCode();
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE)
                    .append(validCode).append(END_STMT);
        } else {
            // Operação binária normal
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE)
                    .append(lhs.getCode()).append(SPACE);

            String op = node.get("op");
            computation.append(op).append(resOllirType).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        Type type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        // Check if this variable has a constant value and optimizations are enabled
        if (optimizationsEnabled && constantValues.containsKey(id)) {
            return new OllirExprResult(constantValues.get(id));
        }

        boolean isField = isField(id, node);

        if (isField) {
            String className = table.getClassName();
            StringBuilder computation = new StringBuilder();
            String temp = ollirTypes.nextTemp() + ollirType;
            computation.append(temp).append(" :=").append(ollirType).append(" ").append("getfield(this.")
                    .append(className).append(", ").append(id).append(ollirType).append(")")
                    .append(ollirType).append(END_STMT);
            return new OllirExprResult(temp, computation);
        }

        String code = id + ollirType;
        return new OllirExprResult(code);
    }

    private boolean isField(String varName, JmmNode node) {
        JmmNode methodNode = node;
        while (methodNode != null && !methodNode.getKind().equals("MethodDecl")) {
            methodNode = methodNode.getParent();
        }

        if (methodNode == null) {
            return false;
        }

        String methodName = methodNode.get("name");

        for (var local : ((JmmSymbolTable)table).getLocalVariables(methodName)) {
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

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        if (node == null) {
            return OllirExprResult.EMPTY;
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChild(i);
            if (child != null) {
                visit(child);
            }
        }

        return OllirExprResult.EMPTY;
    }

    private OllirExprResult visitLogicalExpr(JmmNode node, Void unused) {
        String operator = node.get("op");
        var lhs = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());

        Type boolType = TypeUtils.newBoolType();
        String boolOllirType = ollirTypes.toOllirType(boolType);
        String resultTemp = ollirTypes.nextTemp() + boolOllirType;
        String endLabel = "end" + ollirTypes.nextTemp();

        // Store left side result first
        computation.append(resultTemp).append(" :=").append(boolOllirType).append(" ").append(lhs.getCode()).append(END_STMT);

        // Short-circuit condition - for AND we skip if left is false, for OR if left is true
        boolean isAnd = operator.equals("&&");
        String condition = isAnd ? " ==.bool 0.bool" : " ==.bool 1.bool";
        computation.append("if (").append(resultTemp).append(condition).append(") goto ").append(endLabel).append(END_STMT);

        // Evaluate right side only when needed
        var rhs = visit(node.getChild(1));
        computation.append(rhs.getComputation());

        computation.append(resultTemp).append(" :=").append(boolOllirType).append(" ")
                  .append(isAnd ? rhs.getCode() : "1.bool").append(END_STMT);

        computation.append(endLabel).append(":\n");

        return new OllirExprResult(resultTemp, computation);
    }

    private OllirExprResult visitComparisonExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type boolType = TypeUtils.newBoolType(); // Comparações resultam em boolean
        String boolOllirType = ollirTypes.toOllirType(boolType);
        String temp = ollirTypes.nextTemp() + boolOllirType;

        String operator = node.get("op");

        computation.append(temp).append(SPACE)
                .append(ASSIGN).append(boolOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE)
                .append(operator).append(boolOllirType).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitBooleanLiteral(JmmNode node, Void unused) {
        String value = node.get("value");
        String code = value + ".bool";
        return new OllirExprResult(code);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        // O primeiro filho é a referência para o array
        OllirExprResult arrayRef = visit(node.getChild(0));

        // O segundo filho é o índice
        OllirExprResult indexExpr = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(arrayRef.getComputation());
        computation.append(indexExpr.getComputation());

        Type elementType = new Type("int", false);
        String elementOllirType = ollirTypes.toOllirType(elementType);

        // Temporário para o resultado
        String temp = ollirTypes.nextTemp() + elementOllirType;

        // Se o índice for uma chamada ou expressão complexa, força temporário
        String indexCode = indexExpr.getCode();
        if (indexCode.contains("invoke") || indexCode.contains("+") || indexCode.contains("-")) {
            String tmpIndex = ollirTypes.nextTemp() + ".i32";
            computation.append(tmpIndex).append(" :=.i32 ").append(indexCode).append(END_STMT);
            indexCode = tmpIndex;
        }

        computation.append(temp).append(SPACE)
                .append(ASSIGN).append(elementOllirType).append(SPACE)
                .append(arrayRef.getCode()).append("[").append(indexCode).append("]").append(elementOllirType).append(END_STMT);

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitArrayLength(JmmNode node, Void unused) {
        // Process the array reference
        OllirExprResult arrayRef = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(arrayRef.getComputation());

        // Create temporary for array length
        String temp = ollirTypes.nextTemp() + ".i32";

        computation.append(temp).append(" :=.i32 arraylength(")
                .append(arrayRef.getCode()).append(").i32").append(END_STMT);

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitNewClassExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        Type type = TypeUtils.getExprType(node, (JmmSymbolTable) table);
        String ollirType = ollirTypes.toOllirType(type);

        String baseType = ollirType.startsWith(".") ? ollirType.substring(1) : ollirType;
        String tmp = ollirTypes.nextTemp() + '.' + baseType;

        // Linha de criação da instância
        computation.append(String.format("%s :=%s new(%s)%s;\n",
                tmp, ollirType, baseType, ollirType));

        // Linha de invocação do construtor
        computation.append(String.format("invokespecial(%s, \"<init>\").V;\n", tmp));

        return new OllirExprResult(tmp, computation);
    }

    private boolean isVarArgsMethod(String methodName) {
        if (table.getParameters(methodName) != null) {
            for (var param : table.getParameters(methodName)) {
                if (param.getType().hasAttribute("isVarArg") && param.getType().get("isVarArg").equals("true")) {
                    return true;
                }
            }
        }
        return false;
    }

    private OllirExprResult visitExprExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }


}
