package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2025.ConfigOptions;

import java.util.*;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        if (semanticsResult == null || semanticsResult.getRootNode() == null) {
            return null;
        }

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        // Verificar se a otimização está habilitada
        boolean optimize = ConfigOptions.getOptimize(semanticsResult.getConfig());

        if (optimize) {
            boolean globalChanged = false;
            boolean iterationChanged = true;
            int iterations = 0;
            int maxIterations = 10;

            // Loop principal de otimização
            while (iterationChanged && iterations < maxIterations) {
                iterationChanged = false;
                iterations++;

                // Aplicar constant propagation na AST
                ConstantPropagationVisitor propVisitor = new ConstantPropagationVisitor(semanticsResult.getSymbolTable());
                boolean propChanged = propVisitor.optimize(semanticsResult.getRootNode());

                if (propChanged) {
                    iterationChanged = true;
                    globalChanged = true;
                }

                // Aplicar constant folding na AST
                ConstantFoldingVisitor foldVisitor = new ConstantFoldingVisitor(semanticsResult.getSymbolTable());
                boolean foldChanged = foldVisitor.optimize(semanticsResult.getRootNode());

                if (foldChanged) {
                    iterationChanged = true;
                    globalChanged = true;
                }

                // Se não houve mudanças, interrompe as otimizações
                if (!iterationChanged) {
                    break;
                }
            }
        }

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        // Aplicar alocação de registradores se especificado
        String registerLimit = ollirResult.getConfig().get(ConfigOptions.getRegister());
        if (registerLimit != null) {
            int maxRegisters = Integer.parseInt(registerLimit);
            ollirResult = applyRegisterAllocation(ollirResult, maxRegisters);
        }

        return ollirResult;
    }

    private OllirResult applyRegisterAllocation(OllirResult ollirResult, int maxRegisters) {
        try {
            Map<String, String> registerMap = RegisterAllocation.allocateRegisters(ollirResult, maxRegisters);

            String ollirCode = ollirResult.getOllirCode();
            OllirResult optimized = new OllirResult(ollirCode, ollirResult.getConfig());

            var ollirClass = optimized.getOllirClass();

            for (var method : ollirClass.getMethods()) {
                var varTable = method.getVarTable();
                Map<Integer, List<String>> registerToVarsMap = new HashMap<>();

                // Aplicar mapeamento de registradores
                for (Map.Entry<String, String> entry : registerMap.entrySet()) {
                    String varName = entry.getKey();
                    String registerMapping = entry.getValue();

                    String baseVarName = varName.contains(".") ? varName.substring(0, varName.indexOf('.')) : varName;

                    if (varTable.containsKey(baseVarName)) {
                        if (registerMapping.startsWith("r")) {
                            int regNum = Integer.parseInt(registerMapping.substring(1, registerMapping.indexOf('.')));

                            varTable.get(baseVarName).setVirtualReg(regNum);

                            registerToVarsMap.putIfAbsent(regNum, new ArrayList<>());
                            registerToVarsMap.get(regNum).add(baseVarName);
                        }
                    }
                }

                // Alocar registradores para variáveis não mapeadas
                int spillRegister = 1;
                while (registerToVarsMap.containsKey(spillRegister)) {
                    spillRegister++;
                }

                for (String varName : varTable.keySet()) {
                    var descriptor = varTable.get(varName);
                    if (descriptor.getVirtualReg() == -1) {
                        descriptor.setVirtualReg(spillRegister);
                        registerToVarsMap.computeIfAbsent(spillRegister, k -> new ArrayList<>()).add(varName);
                    }
                }
            }

            return optimized;

        } catch (Exception e) {
            System.err.println("Erro durante alocação de registros: " + e.getMessage());
            e.printStackTrace();
            return ollirResult;
        }
    }
}
