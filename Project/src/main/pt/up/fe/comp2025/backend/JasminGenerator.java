package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    Deque<String> currentLabelStack = new ArrayDeque<>();
    String currentLabel;
    int currentLabelNum = 0;
    boolean insideLabelBranch = false;

    private final JasminUtils types;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        System.out.println("=============== INICIANDO JASMIN GENERATOR ===============");
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        types = new JasminUtils(ollirResult);
        System.out.println("JasminGenerator: Iniciando gerador de código");

        this.generators = new FunctionClassMap<>();
        System.out.println("JasminGenerator: Registrando geradores para cada tipo de nó");
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(NewInstruction.class, this::generateNew);
        generators.put(InvokeSpecialInstruction.class, this::generateInvoke);
        generators.put(InvokeStaticInstruction.class, this::generateInvoke);
        generators.put(InvokeVirtualInstruction.class, this::generateInvoke);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(ArrayLengthInstruction.class, this::generateArrayLength);
        generators.put(ArrayOperand.class, this::generateArrayRef);
    }

    private String generateSingleOpCond(SingleOpCondInstruction condInst) {
        StringBuilder code = new StringBuilder();

        try {
            // Gerar código para o operando - usar getOperands() diretamente da SingleOpCondInstruction
            code.append(apply(condInst.getOperands().get(0)));

            SingleOpInstruction inst = condInst.getCondition();

            if (inst.toString().contains("NOTB") ||
                    (inst.getClass().getSimpleName().equals("UnaryOpInstruction") &&
                            inst.toString().contains("NOT"))) {
                code.append("iconst_1").append(NL);
                code.append("ixor").append(NL);
            }

            // Usar ifne para testar se não é zero
            code.append("ifne ").append(condInst.getLabel()).append(NL);
            currentLabelStack.push(condInst.getLabel());

        } catch (Exception e) {
            System.out.println("ERRO generateSingleOpCond: " + e.getMessage());
            e.printStackTrace();
        }

        return code.toString();
    }

    private String apply(TreeNode node) {
        if (node == null) {
            System.out.println("ERRO apply: O nó é NULL!");
            return "";
        }

        var code = new StringBuilder();
        System.out.println("DEBUG apply: Processando nó do tipo " + node.getClass().getSimpleName());

        try {
            code.append(generators.apply(node));
        } catch (Exception e) {
            System.out.println("ERRO apply: Falha ao processar nó " + node.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
        System.out.println("DEBUG build: Iniciando construção do código Jasmin");

        try {
            // This way, build is idempotent
            if (code == null) {
                code = apply(ollirResult.getOllirClass());
            }

            System.out.println("DEBUG build: Código Jasmin construído com sucesso");
        } catch (Exception e) {
            System.out.println("ERRO build: Falha ao construir código Jasmin: " + e.getMessage());
            e.printStackTrace();
        }

        return code;
    }

    private String generateOpCond(OpCondInstruction opCond) {
        System.out.println("DEBUG generateOpCond: Processando instrução OpCond");
        StringBuilder code = new StringBuilder();

        code.append(apply(opCond.getCondition()));
        code.append("ifne ").append(opCond.getLabel()).append(NL);
        currentLabelStack.push(opCond.getLabel());

        return code.toString();
    }

    private String generateArrayRef(ArrayOperand arrayOp) {
        StringBuilder code = new StringBuilder();

        String arrayName = arrayOp.getName();
        var reg = currentMethod.getVarTable().get(arrayName);

        if (reg.getVirtualReg() <= MAX_DIRECT_REGISTER) {
            code.append("aload_").append(reg.getVirtualReg()).append(NL);
        } else {
            code.append("aload ").append(reg.getVirtualReg()).append(NL);
        }

        // Carregar o índice
        for (Element index : arrayOp.getIndexOperands()) {
            code.append(apply(index));
        }

        return code.toString();
    }

    private String generateClassUnit(ClassUnit classUnit) {
        System.out.println("DEBUG generateClassUnit: Gerando código para a classe " + classUnit.getClassName());
        var code = new StringBuilder();

        try {
            // generate class name
            var className = ollirResult.getOllirClass().getClassName();
            code.append(".class ").append(className).append(NL).append(NL);

            // generate super class name (if exists)
            var fullSuperClass = classUnit.getSuperClass() != null ? classUnit.getSuperClass() : "java/lang/Object";
            code.append(".super ").append(fullSuperClass).append(NL).append(NL);

            // generate fields
            System.out.println("DEBUG generateClassUnit: Gerando campos, quantidade: " + classUnit.getFields().size());
            for (var field : classUnit.getFields()) {
                System.out.println("DEBUG generateClassUnit: Gerando campo " + field.getFieldName());
                code.append(".field ").append(types.getModifier(field.getFieldAccessModifier()))
                        .append("'").append(field.getFieldName()).append("'").append(" ")
                        .append(types.getConvertedType(field.getFieldType())).append(NL);
            }

            // generate a single constructor method
            var defaultConstructor = """
                    ;default constructor
                    .method public <init>()V
                        aload_0
                        invokespecial %s/<init>()V
                        return
                    .end method
                    """.formatted(fullSuperClass);
            code.append(defaultConstructor);

            // generate code for all other methods
            System.out.println("DEBUG generateClassUnit: Gerando métodos, quantidade: " + ollirResult.getOllirClass().getMethods().size());
            for (var method : ollirResult.getOllirClass().getMethods()) {
                System.out.println("DEBUG generateClassUnit: Processando método " + method.getMethodName());

                // Ignore constructor, since there is always one constructor
                // that receives no arguments, and has been already added
                // previously
                if (method.isConstructMethod()) {
                    System.out.println("DEBUG generateClassUnit: Ignorando construtor");
                    continue;
                }

                code.append(apply(method));
            }
        } catch (Exception e) {
            System.out.println("ERRO generateClassUnit: " + e.getMessage());
            e.printStackTrace();
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        System.out.println("DEBUG generateMethod: Gerando código para método " + method.getMethodName());
        // set method
        currentMethod = method;

        var code = new StringBuilder();

        try {
            // calculate modifier
            var modifier = types.getModifier(method.getMethodAccessModifier());

            var methodName = method.getMethodName();

            var params = method.getParams().stream()
                    .map(param -> types.getConvertedType(param.getType()))
                    .collect(Collectors.joining());
            var returnType = types.getConvertedType(method.getReturnType());

            code.append("\n.method ").append(modifier);
            if (method.isStaticMethod()) {
                code.append("static ");
            }
            code.append(methodName);
            code.append("(").append(params).append(")").append(returnType).append(NL);

            // Add limits
            int maxStack = findLimitStack(method);
            int maxLocals = findLimitLocals(method);
            System.out.println("DEBUG generateMethod: Limites - stack: " + maxStack + ", locals: " + maxLocals);
            code.append(TAB).append(".limit stack ").append(maxStack).append(NL);
            code.append(TAB).append(".limit locals ").append(maxLocals).append(NL);

            System.out.println("DEBUG generateMethod: Processando instruções do método, quantidade: " + method.getInstructions().size());

            int insideBranchCntr = 0;
            for (int i = 0; i < method.getInstructions().size(); i++) {
                var inst = method.getInstructions().get(i);

                // Se estiver dentro de um bloco condicional e já passou uma instrução, insere o endif
                if (insideLabelBranch && insideBranchCntr == 1) {
                    code.append(TAB).append(currentLabel).append(":").append(NL);
                    insideLabelBranch = false;
                    insideBranchCntr = 0;
                }

                // Se for a primeira instrução após o branch, apenas marca o contador
                if (insideLabelBranch) {
                    insideBranchCntr++;
                }

                var instCode = StringLines.getLines(apply(inst)).stream()
                        .collect(Collectors.joining(NL + TAB, TAB, NL));
                code.append(instCode);
            }

            code.append(".end method\n");
            System.out.println("DEBUG generateMethod: Finalizado método " + method.getMethodName());

        } catch (Exception e) {
            e.printStackTrace();
        }

        // unset method
        currentMethod = null;
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        System.out.println("DEBUG generateAssign: Processando instrução de atribuição");

        var code = new StringBuilder();

        try {
            // Se o destino é um array, precisamos gerar código especial para armazenamento em array
            if (assign.getDest() instanceof ArrayOperand arrayOp) {
                // Carregar referência do array e índice
                code.append(generateArrayRef(arrayOp));
                // Carregar o valor
                code.append(apply(assign.getRhs()));
                // Gerar instrução iastore sempre para arrays de inteiros/booleanos
                code.append("iastore").append(NL);
                return code.toString();
            }

            // Se o RHS é uma OpCondInstruction, não processar aqui
            if (assign.getRhs() instanceof OpCondInstruction) {
                return "";
            }

            // generate code for loading what's on the right
            System.out.println("DEBUG generateAssign: Gerando código para o lado direito");
            String appliedString = apply(assign.getRhs());
            code.append(appliedString);

            // exit if iinc optimization applied
            if (appliedString.contains("iinc")) {
                return code.toString();
            }

            // store value in the stack in destination
            var lhs = assign.getDest();

            if (!(lhs instanceof Operand operand)) {
                System.out.println("ERRO: LHS não é um Operand: " + lhs.getClass().getName());
                throw new NotImplementedException(lhs.getClass());
            }

            System.out.println("DEBUG generateAssign: Armazenando valor em " + operand.getName());

            var reg = currentMethod.getVarTable().get(operand.getName());
            var type = types.getConvertedType(operand.getType());
            System.out.println("DEBUG generateAssign: Tipo: " + type + ", Registro: " + reg.getVirtualReg());

            boolean isIntegerType = type.equals("I") || type.equals("Z");
            String storePrefix = isIntegerType ? "istore" : "astore";

            if (reg.getVirtualReg() <= MAX_DIRECT_REGISTER) {
                code.append(storePrefix).append("_").append(reg.getVirtualReg()).append(NL);
            } else {
                code.append(storePrefix).append(" ").append(reg.getVirtualReg()).append(NL);
            }
        } catch (Exception e) {
            System.out.println("ERRO generateAssign: " + e.getMessage());
            e.printStackTrace();
        }

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        System.out.println("DEBUG generateSingleOp: Processando instrução de operação única");
        StringBuilder code = new StringBuilder();

        try {
            Element operand = singleOp.getSingleOperand();

            // Check if the operand is a array operand
            if (operand instanceof ArrayOperand) {
                ArrayOperand arrayOp = (ArrayOperand) operand;

                // Load array reference and index
                code.append(generateArrayRef(arrayOp));

                // Add iaload to read the array element
                code.append("iaload").append(NL);
                return code.toString();
            }

            return apply(operand);
        } catch (Exception e) {
            System.out.println("ERRO generateSingleOp: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private String generateLiteral(LiteralElement literal) {
        try {
            String value = literal.getLiteral();
            String type = literal.getType().toString();
            System.out.println("DEBUG generateLiteral: Processando literal " + value + " do tipo " + type);

            if (type.equals("INT32")) {
                int intValue = Integer.parseInt(value);
                // Usar constantes ao invés de valores hardcoded
                if (intValue >= -1 && intValue <= 5) {
                    return "iconst_" + intValue + NL;
                }
                if (intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE) {
                    return "bipush " + intValue + NL;
                }
                if (intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE) {
                    return "sipush " + intValue + NL;
                }
                return "ldc " + value + NL;
            } else if (type.equals("BOOLEAN")) {
                // Melhorar comparação de booleanos
                return value.equals("true") || value.equals("1") ? "iconst_1" + NL : "iconst_0" + NL;
            }

            return "ldc " + value + NL;
        } catch (Exception e) {
            System.out.println("ERRO generateLiteral: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private static final int MAX_DIRECT_REGISTER = 3;

    private String generateOperand(Operand operand) {
        System.out.println("DEBUG generateOperand: Processando operando " + operand.getName());
        try {
            var reg = currentMethod.getVarTable().get(operand.getName());
            if (reg == null) {
                System.out.println("ERRO generateOperand: Registro não encontrado para " + operand.getName());
                return "";
            }

            var type = types.getConvertedType(operand.getType());
            System.out.println("DEBUG generateOperand: Tipo: " + type + ", Registro: " + reg.getVirtualReg());

            boolean isIntegerType = type.equals("I") || type.equals("Z");
            String loadPrefix = isIntegerType ? "iload" : "aload";

            if (reg.getVirtualReg() <= MAX_DIRECT_REGISTER) {
                return loadPrefix + "_" + reg.getVirtualReg() + NL;
            } else {
                return loadPrefix + " " + reg.getVirtualReg() + NL;
            }
        } catch (Exception e) {
            System.out.println("ERRO generateOperand: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private static final String LITERAL_ONE = "1";

    private boolean canOptimizeWithIinc(BinaryOpInstruction binaryOp) {
        return binaryOp.getOperation().getOpType() == OperationType.ADD &&
                binaryOp.getRightOperand() instanceof LiteralElement literal &&
                LITERAL_ONE.equals(literal.getLiteral()) &&
                binaryOp.getLeftOperand() instanceof Operand;
    }

    private String generateIincOptimization(BinaryOpInstruction binaryOp) {
        Operand leftOp = (Operand) binaryOp.getLeftOperand();
        var reg = currentMethod.getVarTable().get(leftOp.getName());
        return "iinc " + reg.getVirtualReg() + " 1" + NL;
    }

    private boolean isZeroLiteral(Element element) {
        return element instanceof LiteralElement literal && "0".equals(literal.getLiteral());
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        StringBuilder code = new StringBuilder();

        // Verificar se é uma adição de constante 1 (caso para otimização iinc)
        if (canOptimizeWithIinc(binaryOp)) {
            return generateIincOptimization(binaryOp);
        }

        boolean isLHSZero = isZeroLiteral(binaryOp.getLeftOperand());
        boolean isRHSZero = isZeroLiteral(binaryOp.getRightOperand());

        var typePrefix = "i";
        String compareInst = "";
        boolean isCompInstr = false;

        switch (binaryOp.getOperation().getOpType()) {
            case ADD, SUB, MUL, DIV, ANDB, ORB -> {
                code.append(apply(binaryOp.getLeftOperand()));
                code.append(apply(binaryOp.getRightOperand()));
                code.append(typePrefix).append(getOperationSuffix(binaryOp.getOperation().getOpType())).append(NL);
            }
            case LTH -> {
                if (isRHSZero) {
                    code.append(apply(binaryOp.getLeftOperand()));
                    compareInst = "iflt";
                } else if (isLHSZero) {
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "ifgt";
                } else {
                    code.append(apply(binaryOp.getLeftOperand()));
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "if_icmplt";
                }
                isCompInstr = true;
            }
            case LTE -> {
                if (isRHSZero) {
                    code.append(apply(binaryOp.getLeftOperand()));
                    compareInst = "ifle";
                } else if (isLHSZero) {
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "ifge";
                } else {
                    code.append(apply(binaryOp.getLeftOperand()));
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "if_icmple";
                }
                isCompInstr = true;
            }
            case GTH -> {
                if (isRHSZero) {
                    code.append(apply(binaryOp.getLeftOperand()));
                    compareInst = "ifgt";
                } else if (isLHSZero) {
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "iflt";
                } else {
                    code.append(apply(binaryOp.getLeftOperand()));
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "if_icmpgt";
                }
                isCompInstr = true;
            }
            case GTE -> {
                if (isRHSZero) {
                    code.append(apply(binaryOp.getLeftOperand()));
                    compareInst = "ifge";
                } else if (isLHSZero) {
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "ifle";
                } else {
                    code.append(apply(binaryOp.getLeftOperand()));
                    code.append(apply(binaryOp.getRightOperand()));
                    compareInst = "if_icmpge";
                }
                isCompInstr = true;
            }
            case EQ -> {
                code.append(apply(binaryOp.getLeftOperand()));
                code.append(apply(binaryOp.getRightOperand()));
                compareInst = "if_icmpeq";
                isCompInstr = true;
            }
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        }

        if (isCompInstr) {
            int condNum = currentLabelNum++;
            code.append(compareInst).append(" j_true_").append(condNum).append(NL);
            code.append(TAB).append("iconst_0").append(NL);
            code.append(TAB).append("goto j_end").append(condNum).append(NL);
            code.append("j_true_").append(condNum).append(":").append(NL);
            code.append(TAB).append("iconst_1").append(NL);
            code.append("j_end").append(condNum).append(":").append(NL);
        }

        return code.toString();
    }

    private String getOperationSuffix(OperationType opType) {
        return switch (opType) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            case ANDB -> "and";
            case ORB -> "or";
            default -> throw new NotImplementedException(opType);
        };
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        code.append(apply(unaryOp.getOperand()));

        if (unaryOp.getOperation().getOpType() == OperationType.NOTB) {
            code.append("iconst_1").append(NL);
            code.append("ixor").append(NL);
        } else {
            throw new NotImplementedException("BITWISE Error: " + unaryOp.getOperation().getOpType());
        }

        return code.toString();
    }

    private String generateArrayLength(ArrayLengthInstruction arrayLengthInst) {
        StringBuilder code = new StringBuilder();

        code.append(apply(arrayLengthInst.getCaller()));

        code.append("arraylength").append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        System.out.println("DEBUG generateReturn: Processando instrução de retorno");
        var code = new StringBuilder();

        try {
            if (returnInst.hasReturnValue()) {
                System.out.println("DEBUG generateReturn: Tem valor de retorno");
                returnInst.getOperand().map(this::apply).ifPresent(code::append);

                var type = types.getConvertedType(returnInst.getReturnType());
                System.out.println("DEBUG generateReturn: Tipo de retorno: " + type);

                switch (type) {
                    case "I", "Z" -> code.append("ireturn");
                    case "V" -> code.append("return");
                    default -> code.append("areturn");
                }
                ;
            } else {
                System.out.println("DEBUG generateReturn: Sem valor de retorno");
                code.append("return");
            }
        } catch (Exception e) {
            System.out.println("ERRO generateReturn: " + e.getMessage());
            e.printStackTrace();
        }

        code.append(NL);
        return code.toString();
    }

    private String generateNew(NewInstruction newInst) {
        System.out.println("DEBUG generateNew: Processando instrução new");
        StringBuilder code = new StringBuilder();

        try {
            // Para alocação de arrays, precisamos processar apenas o tamanho
            boolean isArrayAllocation = false;

            // Verificar se é alocação de array olhando o tipo do caller
            String callerTypeStr = newInst.getCaller().getType().toString();
            if (callerTypeStr.contains("[]")) {
                isArrayAllocation = true;
            }

            System.out.println("DEBUG generateNew: É alocação de array? " + isArrayAllocation);
            System.out.println("DEBUG generateNew: Tipo do caller: " + newInst.getCaller().getType());

            if (isArrayAllocation) {
                // Para arrays, processar apenas o operando que representa o tamanho
                // Pular o primeiro operando se for "array"
                for (int i = 0; i < newInst.getOperands().size(); i++) {
                    var operand = newInst.getOperands().get(i);
                    String opStr = operand.toString();

                    // Pular operandos que contêm "array." pois são marcadores, não variáveis reais
                    if (!opStr.contains("array.")) {
                        System.out.println("DEBUG generateNew: Processando operando de tamanho " + opStr);
                        code.append(apply(operand));
                    }
                }

                String type = types.getConvertedType(newInst.getCaller().getType());
                System.out.println("DEBUG generateNew: Tipo convertido: " + type);

                switch (type) {
                    case "[I" -> code.append("newarray int").append(NL);
                    case "[Z" -> code.append("newarray boolean").append(NL);
                    default -> {
                        // Para outros tipos de array
                        String elementType = type.substring(1); // Remove o [
                        if (elementType.startsWith("L") && elementType.endsWith(";")) {
                            elementType = elementType.substring(1, elementType.length() - 1);
                        }
                        code.append("anewarray ").append(elementType).append(NL);
                    }
                }
            } else {
                // Para criação de objetos normais
                String typeStr = newInst.getCaller().getType().toString();
                String className = extractNameInParentheses(typeStr);
                System.out.println("DEBUG generateNew: Criando nova instância de " + className);
                code.append("new ").append(className).append(NL);
                //code.append("dup").append(NL);
            }
        } catch (Exception e) {
            System.out.println("ERRO generateNew: " + e.getMessage());
            e.printStackTrace();
        }

        return code.toString();
    }

    private String generateInvoke(CallInstruction invoke) {
        System.out.println("DEBUG generateInvoke: Processando chamada de método " + invoke.getClass().getSimpleName());
        var code = new StringBuilder();

        try {
            // Se for invocação de método não-estático, precisamos carregar a referência ao objeto
            if (!(invoke instanceof InvokeStaticInstruction)) {
                System.out.println("DEBUG generateInvoke: Carregando referência do objeto");
                code.append(apply(invoke.getCaller()));
            }

            System.out.println("DEBUG generateInvoke: Número de argumentos: " + invoke.getArguments().size());
            for (var arg : invoke.getArguments()) {
                System.out.println("DEBUG generateInvoke: Processando argumento " + arg);
                code.append(apply(arg));
            }

            String methodFullName = invoke.getMethodName() != null ? invoke.getMethodName().toString() : "null";
            String classFullName = invoke.getCaller() != null ? invoke.getCaller().toString() : "null";
            System.out.println("DEBUG generateInvoke: methodFullName=" + methodFullName);
            System.out.println("DEBUG generateInvoke: classFullName=" + classFullName);

            String methodName;
            String className;

            // Extrair nome do método
            if (methodFullName.contains(": ")) {
                methodFullName = methodFullName.substring(methodFullName.indexOf(": ") + 2);
            }
            if (methodFullName.contains("(")) {
                methodName = methodFullName.substring(0, methodFullName.indexOf("("));
            } else if (methodFullName.contains(".")) {
                methodName = methodFullName.substring(0, methodFullName.indexOf("."));
            } else {
                methodName = methodFullName;
            }

            // Extrair nome da classe
            if (classFullName.contains("this.")) {
                // Se é uma referência "this", usar o nome da classe atual
                className = ollirResult.getOllirClass().getClassName();
            } else if (classFullName.contains(".")) {
                className = classFullName.substring(classFullName.lastIndexOf(".") + 1);

                if (className.startsWith("OBJECT")) {
                    className = extractNameInParentheses(className);
                } else {
                    className = classFullName.substring(0, classFullName.lastIndexOf("."));
                }

                if (className.contains(" ")) {
                    className = className.substring(className.lastIndexOf(" ") + 1);
                }
            } else {
                className = classFullName;
            }

            System.out.println("DEBUG generateInvoke: methodName=" + methodName);
            System.out.println("DEBUG generateInvoke: className=" + className);

            var returnType = types.getConvertedType(invoke.getReturnType());
            System.out.println("DEBUG generateInvoke: returnType=" + returnType);

            var paramTypes = invoke.getArguments().stream()
                    .map(op -> {
                        String type = types.getConvertedType(op.getType());
                        System.out.println("DEBUG generateInvoke: paramType=" + type);
                        return type;
                    })
                    .collect(Collectors.joining());

            var methodDescriptor = "(" + paramTypes + ")" + returnType;
            System.out.println("DEBUG generateInvoke: methodDescriptor=" + methodDescriptor);

            String invokeType = switch (invoke) {
                case InvokeSpecialInstruction ignored -> "invokespecial";
                case InvokeStaticInstruction ignored -> "invokestatic";
                case InvokeVirtualInstruction ignored -> "invokevirtual";
                default -> throw new IllegalArgumentException("Unsupported invoke type: " + invoke.getClass());
            };

            System.out.println("DEBUG generateInvoke: invokeType=" + invokeType);
            System.out.println("DEBUG generateInvoke: Instrução completa: " + invokeType + " " + className + "/" + methodName + methodDescriptor);

            code.append(invokeType).append(" ").append(className).append("/")
                    .append(methodName).append(methodDescriptor).append(NL);

        } catch (Exception e) {
            System.out.println("ERRO em generateInvoke: " + e.getMessage());
            e.printStackTrace();
        }

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        var code = new StringBuilder();

        code.append("goto ").append(gotoInst.getLabel()).append(NL);
        if (!currentLabelStack.isEmpty()) code.append(currentLabelStack.pop()).append(":").append(NL);

        currentLabel = gotoInst.getLabel();
        insideLabelBranch = true;

        return code.toString();
    }

    // +++ Auxiliary Methods +++
    private static final int DEFAULT_MIN_STACK_SIZE = 3;
    private static final int FALLBACK_STACK_SIZE = 99;

    private int findLimitStack(Method method) {
        System.out.println("DEBUG findLimitStack: Calculating max stack size for " + method.getMethodName());

        try {
            int maxStack = 0;
            int currentStack;

            for (var inst : method.getInstructions()) {
                currentStack = calculateStackUsageForInstruction(inst);
                maxStack = Math.max(maxStack, currentStack);
            }

            // Para métodos com chamadas, garantir um mínimo na pilha
            return Math.max(maxStack, DEFAULT_MIN_STACK_SIZE);
        } catch (Exception e) {
            System.out.println("ERROR findLimitStack: " + e.getMessage());
            e.printStackTrace();
            return FALLBACK_STACK_SIZE;
        }
    }

    private int calculateStackUsageForInstruction(Instruction inst) {
        if (inst instanceof BinaryOpInstruction) {
            return 2; // Binary operations need 2 operands and leave 1 result
        } else if (inst instanceof OpCondInstruction) {
            return 2; // Conditionals need 2 operands for comparison
        } else if (inst instanceof CallInstruction call) {
            int stackUsage = call.getArguments().size();
            if (!(call instanceof InvokeStaticInstruction)) {
                stackUsage++; // +1 for receiver
            }
            return stackUsage;
        } else if (inst instanceof AssignInstruction assign) {
            Instruction rhs = assign.getRhs();
            if (rhs instanceof CallInstruction call) {
                int stackUsage = call.getArguments().size();
                if (!(call instanceof InvokeStaticInstruction)) {
                    stackUsage++;
                }
                return stackUsage;
            } else {
                return 1; // Other assignments need at least 1 space
            }
        } else if (inst instanceof SingleOpInstruction) {
            return 1;
        } else if (inst instanceof ReturnInstruction returnInst) {
            return returnInst.hasReturnValue() ? 1 : 0;
        }
        return 0;
    }


    private static final int FALLBACK_LOCALS_SIZE = 99;
    private static final int EXTRA_LOCALS_FOR_ARRAYS = 2;

    private int findLimitLocals(Method method) {
        System.out.println("DEBUG findLimitLocals: Calculating max locals for " + method.getMethodName());

        try {
            // Start with parameter count
            int maxLocals = method.getParams().size();

            // Add 1 for 'this' if instance method
            if (!method.isStaticMethod()) {
                maxLocals++;
            }

            // Find highest used register in variable table
            int maxReg = method.getVarTable().values().stream()
                    .mapToInt(descriptor -> descriptor.getVirtualReg())
                    .max()
                    .orElse(0);

            // The number of locals is the larger of parameters count or highest register + 1
            maxLocals = Math.max(maxLocals, maxReg + 1);

            // Special case: if we have array operations, we might need extra temps
            if (hasArrayOperations(method)) {
                maxLocals = Math.max(maxLocals, maxReg + EXTRA_LOCALS_FOR_ARRAYS);
            }

            System.out.println("DEBUG findLimitLocals: Max locals: " + maxLocals);
            return maxLocals;
        } catch (Exception e) {
            System.out.println("ERROR findLimitLocals: " + e.getMessage());
            e.printStackTrace();
            return FALLBACK_LOCALS_SIZE;
        }
    }

    private boolean hasArrayOperations(Method method) {
        return method.getInstructions().stream()
                .anyMatch(inst -> inst instanceof ArrayLengthInstruction ||
                        (inst instanceof AssignInstruction assign &&
                                assign.getDest() instanceof ArrayOperand));
    }

    private String extractNameInParentheses(String str) {
        System.out.println("DEBUG extractNameInParentheses: Extraindo nome de " + str);
        try {
            int start = str.indexOf('(');
            int end = str.indexOf(')');
            if (start >= 0 && end > start) {
                String result = str.substring(start + 1, end);
                System.out.println("DEBUG extractNameInParentheses: Nome extraído: " + result);
                return result;
            }

            // Alternativa: tenta extrair pelo último ponto
            if (str.contains(".")) {
                String result = str.substring(str.lastIndexOf(".") + 1);
                System.out.println("DEBUG extractNameInParentheses: Nome extraído alternativo: " + result);
                return result;
            }
        } catch (Exception e) {
            System.out.println("ERRO extractNameInParentheses: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("DEBUG extractNameInParentheses: Formato não reconhecido, retornando string original");
        return str;
    }
}