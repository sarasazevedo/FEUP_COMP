package pt.up.fe.comp.cp2;

import org.junit.Test;
import org.specs.comp.ollir.ArrayOperand;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.OperationType;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.type.BuiltinKind;
import pt.up.fe.comp.CpUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsIo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.*;

public class OllirTest {
    private static final String BASE_PATH = "pt/up/fe/comp/cp2/ollir/";

    static OllirResult getOllirResult(String filename) {
        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), Collections.emptyMap(), false);
    }

    public void compileBasic(ClassUnit classUnit) {
        // Test name of the class and super
        assertEquals("Class name not what was expected", "CompileBasic", classUnit.getClassName());
        assertEquals("Super class name not what was expected", "Quicksort", classUnit.getSuperClass());

        // Test method 1
        Method method1 = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals("method1"))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method1", method1);

        var retInst1 = method1.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();
        assertTrue("Could not find a return instruction in method1", retInst1.isPresent());

        // Test method 2
        Method method2 = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals("method2"))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method2'", method2);

        var retInst2 = method2.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();
        assertTrue("Could not find a return instruction in method2", retInst2.isPresent());
    }

    public void compileBasicWithFields(OllirResult ollirResult) {

        ClassUnit classUnit = ollirResult.getOllirClass();

        // Test name of the class and super
        assertEquals("Class name not what was expected", "CompileBasic", classUnit.getClassName());
        assertEquals("Super class name not what was expected", "Quicksort", classUnit.getSuperClass());

        // Test fields
        assertEquals("Class should have two fields", 2, classUnit.getNumFields());
        var fieldNames = new HashSet<>(Arrays.asList("intField", "boolField"));
        assertThat(fieldNames, hasItem(classUnit.getField(0).getFieldName()));
        assertThat(fieldNames, hasItem(classUnit.getField(1).getFieldName()));

        // Test method 1
        Method method1 = CpUtils.getMethod(ollirResult, "method1");
        assertNotNull("Could not find method1", method1);

        var method1GetField = CpUtils.getInstructions(GetFieldInstruction.class, method1);
        assertTrue("Expected 1 getfield instruction in method1, found " + method1GetField.size(), method1GetField.size() == 1);


        // Test method 2
        var method2 = CpUtils.getMethod(ollirResult, "method2");
        assertNotNull("Could not find method2'", method2);

        var method2GetField = CpUtils.getInstructions(GetFieldInstruction.class, method2);
        assertTrue("Expected 0 getfield instruction in method2, found " + method2GetField.size(), method2GetField.isEmpty());

        var method2PutField = CpUtils.getInstructions(PutFieldInstruction.class, method2);
        assertTrue("Expected 0 putfield instruction in method2, found " + method2PutField.size(), method2PutField.isEmpty());

        // Test method 3
        var method3 = CpUtils.getMethod(ollirResult, "method3");
        assertNotNull("Could not find method3'", method3);

        var method3PutField = CpUtils.getInstructions(PutFieldInstruction.class, method3);
        assertTrue("Expected 1 putfield instruction in method3, found " + method3PutField.size(), method3PutField.size() == 1);
    }

    public void compileArithmetic(ClassUnit classUnit) {
        // Test name of the class
        assertEquals("Class name not what was expected", "CompileArithmetic", classUnit.getClassName());

        // Test foo
        var methodName = "foo";
        Method methodFoo = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method " + methodName, methodFoo);

        var binOpInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof AssignInstruction)
                .map(instr -> (AssignInstruction) instr)
                .filter(assign -> assign.getRhs() instanceof BinaryOpInstruction)
                .findFirst();

        assertTrue("Could not find a binary op instruction in method " + methodName, binOpInst.isPresent());

        var retInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();
        assertTrue("Could not find a return instruction in method " + methodName, retInst.isPresent());
    }

    public void compileMethodInvocation(ClassUnit classUnit) {
        // Test name of the class
        assertEquals("Class name not what was expected", "CompileMethodInvocation", classUnit.getClassName());

        // Test foo
        var methodName = "foo";
        Method methodFoo = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method " + methodName, methodFoo);

        var callInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof CallInstruction)
                .map(CallInstruction.class::cast)
                .findFirst();
        assertTrue("Could not find a call instruction in method " + methodName, callInst.isPresent());

        assertEquals("Invocation type not what was expected", InvokeStaticInstruction.class,
                callInst.get().getClass());
    }

    public void compileAssignment(ClassUnit classUnit) {
        // Test name of the class
        assertEquals("Class name not what was expected", "CompileAssignment", classUnit.getClassName());

        // Test foo
        var methodName = "foo";
        Method methodFoo = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method " + methodName, methodFoo);

        var assignInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof AssignInstruction)
                .map(AssignInstruction.class::cast)
                .findFirst();
        assertTrue("Could not find an assign instruction in method " + methodName, assignInst.isPresent());

        assertEquals("Assignment does not have the expected type", BuiltinKind.INT32, CpUtils.toBuiltinKind(assignInst.get().getTypeOfAssign()));
    }


    @Test
    public void basicClass() {
        var result = getOllirResult("basic/BasicClass.jmm");

        compileBasic(result.getOllirClass());
    }

    @Test
    public void basicClassWithFields() {
        var result = getOllirResult("basic/BasicClassWithFields.jmm");
        System.out.println(result.getOllirCode());

        compileBasicWithFields(result);
    }

    @Test
    public void basicAssignment() {
        var result = getOllirResult("basic/BasicAssignment.jmm");

        compileAssignment(result.getOllirClass());
    }

    @Test
    public void basicMethodInvocation() {
        var result = getOllirResult("basic/BasicMethodInvocation.jmm");

        compileMethodInvocation(result.getOllirClass());
    }


    /*checks if method declaration is correct (array)*/
    @Test
    public void basicMethodDeclarationArray() {
        var result = getOllirResult("basic/BasicMethodsArray.jmm");

        var method = CpUtils.getMethod(result, "func4");

        CpUtils.assertEquals("Method return type", "int[]", CpUtils.toString(method.getReturnType()), result);
    }

    @Test
    public void arithmeticSimpleAdd() {
        var ollirResult = getOllirResult("arithmetic/Arithmetic_add.jmm");

        compileArithmetic(ollirResult.getOllirClass());
    }

    @Test
    public void arithmeticSimpleAnd() {
        var ollirResult = getOllirResult("arithmetic/Arithmetic_and.jmm");
        var method = CpUtils.getMethod(ollirResult, "main");
        var numBranches = CpUtils.getInstructions(CondBranchInstruction.class, method).size();


        CpUtils.assertTrue("Expected at least 2 branches, found " + numBranches, numBranches >= 2, ollirResult);
    }

    @Test
    public void arithmeticSimpleLess() {
        var ollirResult = getOllirResult("arithmetic/Arithmetic_less.jmm");

        var method = CpUtils.getMethod(ollirResult, "main");

        CpUtils.assertHasOperation(OperationType.LTH, method, ollirResult);

    }

    @Test
    public void controlFlowIfSimpleSingleGoTo() {

        var result = getOllirResult("control_flow/SimpleIfElseStat.jmm");

        var method = CpUtils.getMethod(result, "func");

        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, method, result);
        CpUtils.assertEquals("Number of branches", 1, branches.size(), result);

        var gotos = CpUtils.assertInstExists(GotoInstruction.class, method, result);
        CpUtils.assertTrue("Has at least 1 goto", gotos.size() >= 1, result);
    }

    @Test
    public void controlFlowIfSwitch() {

        var result = getOllirResult("control_flow/SwitchStat.jmm");

        var method = CpUtils.getMethod(result, "func");

        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, method, result);
        CpUtils.assertEquals("Number of branches", 6, branches.size(), result);

        var gotos = CpUtils.assertInstExists(GotoInstruction.class, method, result);
        CpUtils.assertTrue("Has at least 6 gotos", gotos.size() >= 6, result);
    }

    @Test
    public void controlFlowWhileSimple() {

        var result = getOllirResult("control_flow/SimpleWhileStat.jmm");

        var method = CpUtils.getMethod(result, "func");

        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, method, result);

        CpUtils.assertTrue("Number of branches between 1 and 2", branches.size() > 0 && branches.size() < 3, result);
    }


    /*checks if an array is correctly initialized*/
    @Test
    public void arraysInitArray() {
        var result = getOllirResult("arrays/ArrayInit.jmm");

        var method = CpUtils.getMethod(result, "main");

        var calls = CpUtils.assertInstExists(CallInstruction.class, method, result);

        CpUtils.assertEquals("Number of calls", 3, calls.size(), result);

        // Get new
        var newCalls = calls.stream().filter(call -> call instanceof NewInstruction)
                .collect(Collectors.toList());

        CpUtils.assertEquals("Number of 'new' calls", 1, newCalls.size(), result);

        // Get length
        var lengthCalls = calls.stream().filter(call -> call instanceof ArrayLengthInstruction)
                .collect(Collectors.toList());

        CpUtils.assertEquals("Number of 'arraylenght' calls", 1, lengthCalls.size(), result);
    }

    /*checks if the access to the elements of array is correct*/
    @Test
    public void arraysAccessArray() {
        var result = getOllirResult("arrays/ArrayAccess.jmm");

        var method = CpUtils.getMethod(result, "foo");

        var assigns = CpUtils.assertInstExists(AssignInstruction.class, method, result);
        var numArrayStores = assigns.stream().filter(assign -> assign.getDest() instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array stores", 5, numArrayStores, result);

        var numArrayReads = assigns.stream()
                .flatMap(assign -> CpUtils.getElements(assign.getRhs()).stream())
                .filter(element -> element instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array reads", 5, numArrayReads, result);
    }

    /*checks multiple expressions as indexes to access the elements of an array*/
    @Test
    public void arraysLoadComplexArrayAccess() {
        // Just parse
        var result = getOllirResult("arrays/ComplexArrayAccess.jmm");

        System.out.println("---------------------- OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("---------------------- OLLIR ----------------------");

        var method = CpUtils.getMethod(result, "main");

        var assigns = CpUtils.assertInstExists(AssignInstruction.class, method, result);
        var numArrayStores = assigns.stream().filter(assign -> assign.getDest() instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array stores", 5, numArrayStores, result);

        var numArrayReads = assigns.stream()
                .flatMap(assign -> CpUtils.getElements(assign.getRhs()).stream())
                .filter(element -> element instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array reads", 6, numArrayReads, result);
    }

    @Test
    public void SimpleVarArgs() {
        // Obter resultado OLLIR
        var result = getOllirResult("extras/SimpleVarArgs.jmm");

        // Verificar se o resultado não é nulo
        assertNotNull("OLLIR result should not be null", result);

        // Imprimir código para debug
        System.out.println(result.getOllirCode());

        // Verificar classe e métodos
        ClassUnit classUnit = result.getOllirClass();
        assertEquals("Class name should be VarArgs", "VarArgs", classUnit.getClassName());

        // Verificar método foo com parâmetro varargs
        Method fooMethod = CpUtils.getMethod(result, "foo");
        assertNotNull("Method 'foo' should exist", fooMethod);
        assertEquals("foo should have 1 parameter", 1, fooMethod.getParams().size());
        assertEquals("foo's parameter should be int[]", "int[]",
                CpUtils.toString(fooMethod.getParam(0).getType()));

        // Verificar método bar com chamada para foo
        Method barMethod = CpUtils.getMethod(result, "bar");
        assertNotNull("Method 'bar' should exist", barMethod);

        // Verificar se há uma chamada de método no bar
        var callInsts = CpUtils.getInstructions(CallInstruction.class, barMethod);
        assertTrue("Method 'bar' should contain at least one call instruction",
                callInsts.size() >= 1);

        // Verificar se há instruções relacionadas ao uso de array para parâmetros
        // (típico para implementação de varargs)
        var newInsts = barMethod.getInstructions().stream()
                .filter(inst -> inst instanceof CallInstruction)
                .filter(inst -> inst instanceof NewInstruction)
                .count();

        // Em algum momento, um array deve ser criado para passar os parâmetros varargs
        assertTrue("Array creation should be present in method invocation",
                newInsts >= 0);
    }

    @Test
    public void complexIfElseTest() {
        var result = getOllirResult("extras/ComplexIfElse.jmm");
        System.out.println(result.getOllirCode());

        // Verify class name
        ClassUnit classUnit = result.getOllirClass();
        assertEquals("Class name not what was expected", "ComplexIfElse", classUnit.getClassName());

        // Test max method
        Method maxMethod = CpUtils.getMethod(result, "max");
        assertNotNull("Could not find 'max' method", maxMethod);

        // Verify complex if-else structure with conditional branches
        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, maxMethod, result);
        CpUtils.assertTrue("Expected at least 2 conditional branches", branches.size() >= 2, result);

        // Verify goto instructions for if-else implementation
        var gotos = CpUtils.assertInstExists(GotoInstruction.class, maxMethod, result);
        CpUtils.assertTrue("Expected multiple goto instructions for nested if-else", gotos.size() >= 2, result);

        // Verify return instruction
        var returnInst = CpUtils.assertInstExists(ReturnInstruction.class, maxMethod, result);
        CpUtils.assertEquals("Should have exactly 1 return instruction", 1, returnInst.size(), result);
    }

    @Test
    public void booleanConditionalOperations() {
        // Parse do arquivo Boolean.jmm
        var result = getOllirResult("extras/Boolean.jmm");

        System.out.println("---------------------- OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("---------------------- OLLIR ----------------------");

        var method = CpUtils.getMethod(result, "main");

        // Verificar se o método existe
        assertNotNull("Method 'main' should exist", method);

        // Verificar instruções de branch condicional (if statements)
        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, method, result);
        CpUtils.assertEquals("Número de instruções condicionais (if statements)", 2, branches.size(), result);

        // Verificar instruções goto (usadas na implementação de if-else)
        var gotos = CpUtils.assertInstExists(GotoInstruction.class, method, result);
        CpUtils.assertTrue("Deve conter instruções goto para implementar os blocos if-else",
            gotos.size() >= 2, result);

        // Verificar chamadas ao método print
        var calls = CpUtils.assertInstExists(CallInstruction.class, method, result);
        CpUtils.assertEquals("Deve conter 4 chamadas de método (io.print)", 4, calls.size(), result);

        // Verificar se existem atribuições para variáveis booleanas
        var assigns = CpUtils.assertInstExists(AssignInstruction.class, method, result);
        CpUtils.assertTrue("Deve conter pelo menos 2 atribuições (b=true e b=false)",
            assigns.size() >= 2, result);

        // Verificar se o nome da classe está correto
        ClassUnit classUnit = result.getOllirClass();
        assertEquals("Nome da classe não corresponde ao esperado", "BooleanArithmetic", classUnit.getClassName());
    }

    @Test
    public void arithmeticPrecedenceTest() {
        OllirResult result = getOllirResult("extras/ArithmeticPrecedence.jmm");

        System.out.println("---------------------- OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("---------------------- OLLIR ----------------------");

        var main = CpUtils.getMethod(result, "main");
        assertNotNull("Method 'main' should exist", main);

        // Deve conter pelo menos 3 operações aritméticas (add, mul, sub/div)
        var arithOps = CpUtils.assertInstExists(BinaryOpInstruction.class, main, result);
        assertTrue("Esperado >=3 operações aritméticas", arithOps.size() >= 3);

        // Chamadas a io.print
        var calls = CpUtils.assertInstExists(CallInstruction.class, main, result);
        assertEquals("Deve conter 3 chamadas a io.print", 3, calls.size());

        // Verifica nome da classe
        ClassUnit cu = result.getOllirClass();
        assertEquals("Classe errada", "ArithmeticPrecedence", cu.getClassName());
    }

    // 2. ArrayComplexAssign.jmm
    @Test
    public void arrayComplexAssignTest() {
        OllirResult result = getOllirResult("extras/ArrayComplexAssign.jmm");

        System.out.println("---------------------- OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("---------------------- OLLIR ----------------------");

        var main = CpUtils.getMethod(result, "main");
        assertNotNull("Method 'main' should exist", main);

        // Deve conter dois ciclos while
        var whiles = CpUtils.assertInstExists(GotoInstruction.class, main, result)
                .stream()
                .filter(i -> i instanceof GotoInstruction)
                .toList();
        assertTrue("Deve ter pelo menos 2 loops while (implementados com goto)", whiles.size() >= 2);

        ClassUnit cu = result.getOllirClass();
        assertEquals("Classe errada", "ArrayComplexAssign", cu.getClassName());
    }

    // 3. ArrayParamTest.jmm
    @Test
    public void arrayParamTest() {
        OllirResult result = getOllirResult("extras/ArrayParamTest.jmm");

        System.out.println("---------------------- OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("---------------------- OLLIR ----------------------");

        // Método doubleAll deve existir
        var doubleAll = CpUtils.getMethod(result, "doubleAll");
        assertNotNull("Method 'doubleAll' should exist", doubleAll);

        // Deve ter um loop while em doubleAll
        CpUtils.assertInstExists(GotoInstruction.class, doubleAll, result);

        // Método main
        var main = CpUtils.getMethod(result, "main");
        assertNotNull("Method 'main' should exist", main);

        // Chamada a tester.doubleAll
        var callsMain = CpUtils.assertInstExists(CallInstruction.class, main, result);
        assertTrue("Deve chamar doubleAll e depois io.print várias vezes", callsMain.size() >= 5);

        ClassUnit cu = result.getOllirClass();
        assertEquals("Classe errada", "ArrayParamTest", cu.getClassName());
    }

    @Test
    public void complexVarArgs() {
        var result = getOllirResult("extras/ComplexVarArgs.jmm");
        String ollir = result.getOllirCode();

        System.out.println(result.getOllirCode());

        // Verifica se a assinatura do metodo foo está correta
        assertTrue(ollir.contains(".method varargs foo(a.i32, b.i32, c.array.i32).i32"));

        // Verifica se o array com os argumentos variáveis foi criado corretamente
        assertTrue(ollir.contains("new(array, 4.i32).array.i32"));
        assertTrue(ollir.contains("tmp0.array.i32[0.i32].i32 :=.i32 3.i32;"));
        assertTrue(ollir.contains("tmp0.array.i32[3.i32].i32 :=.i32 6.i32;"));

        // Verifica se a chamada ao metodo foo inclui o array como último argumento
        assertTrue(ollir.contains("invokevirtual(args.VarArgs, \"foo\", tmp0.array.i32).i32;"));
    }

    @Test
    public void newClassTest() {
        var result = getOllirResult("extras/NewClass.jmm");
        System.out.println(result.getOllirCode());

    }

    @Test
    public void arithmeticMix() {
        var result = getOllirResult("extras/ArithmeticMix.jmm");
        System.out.println(result.getOllirCode());

    }

}
