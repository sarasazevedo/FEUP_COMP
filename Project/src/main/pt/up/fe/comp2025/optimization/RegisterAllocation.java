package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsLogs;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RegisterAllocation {

    // Estrutura para guardar informações de cada instrução
    private static class InstructionInfo {
        int id; // Identificador único da instrução no método
        String instructionCode;
        Set<String> def = new HashSet<>();
        Set<String> use = new HashSet<>();
        Set<String> in = new HashSet<>();
        Set<String> out = new HashSet<>();
        List<Integer> successors = new ArrayList<>(); // IDs das instruções sucessoras
        List<Integer> predecessors = new ArrayList<>(); // IDs das instruções predecessoras

        InstructionInfo(int id, String instructionCode) {
            this.id = id;
            this.instructionCode = instructionCode.trim();
        }

        @Override
        public String toString() {
            return String.format("ID: %d, Instr: '%s'\n  Def: %s\n  Use: %s\n  In: %s\n  Out: %s\n  Succ: %s\n  Pred: %s",
                    id, instructionCode, def, use, in, out, successors, predecessors);
        }
    }

    // Padrões Regex para extrair informações das instruções OLLIR
    // Variáveis: nome seguido por .tipo (e.g., a.i32, temp1.bool, this.ClassName)
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)*)");
    // Atribuição: var := ...
    private static final Pattern ASSIGN_PATTERN = Pattern.compile("^([^\\s:]+)\\s*:=\\..*");
    private static final Pattern INVOKE_PATTERN = Pattern.compile("invoke\\w+\\(([^,)]+)(?:,.*)?\\)");
    // Instruções de controle de fluxo (goto, if)
    private static final Pattern GOTO_PATTERN = Pattern.compile("goto\\s+(\\w+);");
    private static final Pattern IF_GOTO_PATTERN = Pattern.compile("if\\s*\\((.*)\\)\\s*goto\\s+(\\w+);");
    // Labels
    private static final Pattern LABEL_PATTERN = Pattern.compile("^(\\w+):");
    // Return
    private static final Pattern RETURN_PATTERN = Pattern.compile("ret\\.\\w+\\s+(.*);");

    public static Map<String, String> allocateRegisters(OllirResult ollirResult, int maxRegisters) {

    // Primeiro, executar a análise de vivacidade
    analyze(ollirResult);

    // Construir o grafo de interferência
    Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(ollirResult.getOllirCode());

    // Colorir o grafo para alocar registradores
    return colorGraph(interferenceGraph, maxRegisters);
}

    private static Map<String, Set<String>> buildInterferenceGraph(String ollirCode) {
    Map<String, Set<String>> graph = new HashMap<>();

    // Obter estruturas de dados da análise de vivacidade
    Map<String, List<InstructionInfo>> methodInstructions = analyzeMethodsLiveness(ollirCode);

    // Para cada método analisado
    for (String methodName : methodInstructions.keySet()) {
        List<InstructionInfo> instructions = methodInstructions.get(methodName);

        // Extrair todas as variáveis usadas no método
        Set<String> variables = new HashSet<>();
        for (InstructionInfo instr : instructions) {
            variables.addAll(instr.use);
            variables.addAll(instr.def);
        }

        // Inicializar grafo com todas as variáveis
        for (String var : variables) {
            graph.putIfAbsent(var, new HashSet<>());
        }

        // Duas variáveis interferem se uma está viva no ponto onde a outra é definida
        for (InstructionInfo instr : instructions) {
            // Para cada variável definida nesta instrução
            for (String defVar : instr.def) {
                // Ela interfere com todas as variáveis vivas na saída da instrução
                for (String liveVar : instr.out) {
                    if (!defVar.equals(liveVar)) {
                        addToGraph(graph, defVar, liveVar);
                    }
                }
            }
        }
    }

    return graph;
}

    private static Map<String, List<InstructionInfo>> analyzeMethodsLiveness(String ollirCode) {
    Map<String, List<InstructionInfo>> methodInstructions = new HashMap<>();
    System.out.println("Construindo grafo de interferência...");

    // Separa o código por métodos
    String[] methods = ollirCode.split("\\.method");

    for (int i = 1; i < methods.length; i++) { // Começar do 1 para pular o cabeçalho da classe
        String methodCode = methods[i];

        // Extrai o nome do método usando regex
        Matcher methodNameMatcher = Pattern.compile("\\s*(public\\s+)?([\\w<>]+)\\s+(\\w+)\\s*\\(").matcher(methodCode);
        if (!methodNameMatcher.find()) continue;

        String methodName = methodNameMatcher.group(3);

        // Localiza o início e fim do corpo do método com segurança
        int bodyStart = methodCode.indexOf('{');
        if (bodyStart == -1) continue;

        int bodyEnd = methodCode.lastIndexOf('}');
        if (bodyEnd <= bodyStart) continue;

        // Extrai o corpo do método
        String methodBody = methodCode.substring(bodyStart + 1, bodyEnd).trim();

        // Processa as instruções do método
        List<String> lines = Arrays.stream(methodBody.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());

        List<InstructionInfo> instructions = new ArrayList<>();
        Map<String, Integer> labelToInstructionId = new HashMap<>();
        int instructionIdCounter = 0;

        // Processa cada instrução
        for (String line : lines) {
            // Processa instruções e labels
            InstructionInfo info = new InstructionInfo(instructionIdCounter++, line);
            instructions.add(info);

            // Verifica e registra labels
            Matcher labelMatcher = Pattern.compile("(\\w+)\\s*:").matcher(line);
            if (labelMatcher.find()) {
                labelToInstructionId.put(labelMatcher.group(1), instructionIdCounter - 1);
            }
        }

        if (!instructions.isEmpty()) {
            // Calcular Def/Use para cada instrução
            calculateDefUse(instructions);

            // Construir CFG
            buildCFG(instructions, labelToInstructionId);

            // Calcular conjuntos In/Out
            calculateInOutSets(instructions);

            // Armazena as instruções analisadas
            methodInstructions.put(methodName, instructions);
        }
    }

    return methodInstructions;
}

    private static void addToGraph(Map<String, Set<String>> graph, String var1, String var2) {
        graph.putIfAbsent(var1, new HashSet<>());
        graph.putIfAbsent(var2, new HashSet<>());
        graph.get(var1).add(var2);
        graph.get(var2).add(var1);
    }

    private static Map<String, String> colorGraph(Map<String, Set<String>> interferenceGraph, int maxRegisters) {
    System.out.println("Colorindo grafo com " + maxRegisters + " registradores");
    Map<String, String> registerMap = new HashMap<>();

    // Filtrar apenas variáveis reais
    List<String> variables = interferenceGraph.keySet().stream()
            .filter(var -> {
                int dotIndex = var.indexOf('.');
                return dotIndex > 0 && !var.startsWith(".");
            })
            .collect(Collectors.toList());

    // Ordenar variáveis pelo número de interferências
    variables.sort((v1, v2) ->
            interferenceGraph.getOrDefault(v2, Collections.emptySet()).size() -
            interferenceGraph.getOrDefault(v1, Collections.emptySet()).size());

    // Pré-alocar 'ret.i32' para r1 se existir
    if (variables.contains("ret.i32")) {
        registerMap.put("ret.i32", "r1.i32");
        variables.remove("ret.i32");
    } else if (variables.contains("ret.V")) {
        registerMap.put("ret.V", "r1");
        variables.remove("ret.V");
    }

    // FASE 1: Aplicar algoritmo de coloração tradicional
    for (String variable : variables) {
        // Extrair o tipo da variável
        int dotIndex = variable.indexOf('.');
        String type = dotIndex > 0 ? variable.substring(dotIndex) : "";

        Set<Integer> usedColors = new HashSet<>();

        // Verificar cores usadas por vizinhos
        Set<String> neighbors = interferenceGraph.getOrDefault(variable, Collections.emptySet());
        for (String neighbor : neighbors) {
            String neighborReg = registerMap.get(neighbor);
            if (neighborReg != null && neighborReg.startsWith("r")) {
                int regNum = Integer.parseInt(neighborReg.substring(1, neighborReg.indexOf('.') > 0 ?
                    neighborReg.indexOf('.') : neighborReg.length()));
                usedColors.add(regNum);
            }
        }

        // Encontrar menor cor disponível
        int color = 1;
        while (usedColors.contains(color) && color <= maxRegisters) {
            color++;
        }

        // Se encontrou uma cor válida, atribui
        if (color <= maxRegisters) {
            registerMap.put(variable, "r" + color + type);
            System.out.println("  Variável " + variable + " alocada para r" + color);
        }
    }

    // FASE 2: Alocação de registradores extras para variáveis em spill
    List<String> unallocated = variables.stream()
            .filter(v -> !registerMap.containsKey(v))
            .collect(Collectors.toList());

    if (!unallocated.isEmpty()) {
        // Agrupando variáveis para compartilhamento de registradores
        for (String variable : unallocated) {
            int dotIndex = variable.indexOf('.');
            String type = dotIndex > 0 ? variable.substring(dotIndex) : "";

            // Determinar quais registradores estão em uso por vizinhos
            Set<Integer> unavailableColors = new HashSet<>();
            for (String neighbor : interferenceGraph.getOrDefault(variable, Collections.emptySet())) {
                String neighborReg = registerMap.get(neighbor);
                if (neighborReg != null && neighborReg.startsWith("r")) {
                    int regNum = Integer.parseInt(neighborReg.substring(1, neighborReg.indexOf('.') > 0 ?
                        neighborReg.indexOf('.') : neighborReg.length()));
                    unavailableColors.add(regNum);
                }
            }

            // Encontrar o primeiro registrador disponível
            int regNum = maxRegisters + 1;
            while (unavailableColors.contains(regNum)) {
                regNum++;
            }

            registerMap.put(variable, "r" + regNum + type);
            System.out.println("  Variável " + variable + " alocada para registrador alternativo r" + regNum);
        }
    }

    return registerMap;
}

    public static void analyze(OllirResult ollirResult) {
        System.out.println("--- Iniciando Liveness Analysis ---");
        String ollirCode = ollirResult.getOllirCode();
        // Separa o código por métodos
        String[] methods = ollirCode.split("\\.method");

        for (int i = 1; i < methods.length; i++) { // Pula o cabeçalho da classe
            String methodCode = ".method" + methods[i];
            analyzeMethod(methodCode);
        }
        System.out.println("--- Liveness Analysis Concluída ---");
    }

    private static void analyzeMethod(String methodCode) {
        Matcher methodHeaderMatcher = Pattern.compile("\\.method\\s+(public\\s+)?(\\w+)\\((.*?)\\)(.*?)\\{").matcher(methodCode);
        if (!methodHeaderMatcher.find()) {
            System.err.println("ERRO: Não foi possível parsear o cabeçalho do método: " );
            return;
        }
        String methodName = methodHeaderMatcher.group(2);
        System.out.println("\nAnalisando Método: " + methodName);

        int bodyStart = methodCode.indexOf('{') + 1;
        int bodyEnd = methodCode.lastIndexOf('}');
        if (bodyStart <= 0 || bodyEnd < bodyStart) {
            SpecsLogs.warn("Erro na análise: Não foi possível parsear o cabeçalho do método: " + methodName);
            return;
        }
        String methodBody = methodCode.substring(bodyStart, bodyEnd).trim();

        String[] lines = methodBody.split("\n");
        List<InstructionInfo> instructions = new ArrayList<>();
        Map<String, Integer> labelToInstructionId = new HashMap<>();
        int instructionIdCounter = 0;

        // 1. Parsear instruções e identificar labels
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher labelMatcher = LABEL_PATTERN.matcher(line);
            if (labelMatcher.find()) {
                String label = labelMatcher.group(1);
                labelToInstructionId.put(label, instructionIdCounter); // Próxima instrução pertence a este label
                line = line.substring(labelMatcher.end()).trim();
                if (line.isEmpty()) continue; // Linha continha apenas o label
            }

            instructions.add(new InstructionInfo(instructionIdCounter++, line));
        }

        if (instructions.isEmpty()) {
            System.out.println("Método " + methodName + " não possui instruções.");
            return;
        }

        // 2. Calcular Def e Use para cada instrução
        calculateDefUse(instructions);

        // 3. Construir CFG (calcular sucessores e predecessores)
        buildCFG(instructions, labelToInstructionId);

        // 4. Calcular In e Out iterativamente
        calculateInOutSets(instructions);

        // 5. Imprimir resultados para depuração
        System.out.println("Resultados da Liveness Analysis para o método: " + methodName);
        for (InstructionInfo info : instructions) {
            System.out.println(info);
        }
    }

    private static void calculateDefUse(List<InstructionInfo> instructions) {
        System.out.println("Calculando Def/Use...");
        for (InstructionInfo info : instructions) {
            String instr = info.instructionCode;

            // Def: Variável à esquerda de ':='
            Matcher assignMatcher = ASSIGN_PATTERN.matcher(instr);
            if (assignMatcher.find()) {
                String definedVar = assignMatcher.group(1);
                if (!definedVar.contains("(")) {
                     info.def.add(definedVar);
                }
            }

            // Use: Variáveis usadas na instrução
            Matcher varMatcher = VARIABLE_PATTERN.matcher(instr);
            List<String> potentialUses = new ArrayList<>();
            while (varMatcher.find()) {
                potentialUses.add(varMatcher.group(1));
            }

            // Remover a variável definida (se houver) da lista de usos potenciais
            if (!info.def.isEmpty()) {
                String definedVar = info.def.iterator().next();
                // Remove a primeira ocorrência (lado esquerdo da atribuição)
                potentialUses.remove(definedVar);
            }

            // Remover literais (e.g., 1.i32, true.bool) e 'this' (a menos que seja o único elemento em invoke)
            potentialUses.removeIf(v -> v.matches("\\d+(\\.\\w+)?") || v.matches("true|false") || v.equals("this"));

            // Casos especiais de 'Use'
            Matcher invokeMatcher = INVOKE_PATTERN.matcher(instr);
            if (invokeMatcher.find()) {
                String target = invokeMatcher.group(1);
                 if (!target.matches("\\d+(\\.\\w+)?") && !target.matches("true|false")) {
                    info.use.add(target);
                 }
            }

            Matcher ifMatcher = IF_GOTO_PATTERN.matcher(instr);
            if (ifMatcher.find()) {
                // Variáveis dentro da condição do if são usadas
                String condition = ifMatcher.group(1);
                Matcher conditionVarMatcher = VARIABLE_PATTERN.matcher(condition);
                 while (conditionVarMatcher.find()) {
                     String v = conditionVarMatcher.group(1);
                     if (!v.matches("\\d+(\\.\\w+)?") && !v.matches("true|false") && !v.equals("this")) {
                         info.use.add(v);
                     }
                 }
            }

            Matcher returnMatcher = RETURN_PATTERN.matcher(instr);
            if (returnMatcher.find()) {
                String returnValue = returnMatcher.group(1);
                 if (!returnValue.isEmpty() && !returnValue.matches("\\d+(\\.\\w+)?") && !returnValue.matches("true|false")) {
                     info.use.add(returnValue);
                 }
            }


            // Adiciona as variáveis restantes da direita da atribuição e argumentos de chamadas
            for (String v : potentialUses) {
                 if (!v.isEmpty() && !info.def.contains(v)) {
                    info.use.add(v);
                 }
            }
             System.out.printf("  Instr %d: Def=%s, Use=%s\n", info.id, info.def, info.use);
        }
    }

     private static void buildCFG(List<InstructionInfo> instructions, Map<String, Integer> labelToInstructionId) {
        System.out.println("Construindo CFG...");
        int numInstructions = instructions.size();
        for (int i = 0; i < numInstructions; i++) {
            InstructionInfo current = instructions.get(i);
            String instr = current.instructionCode;
            boolean endsExecution = false;

            Matcher gotoMatcher = GOTO_PATTERN.matcher(instr);
            Matcher ifMatcher = IF_GOTO_PATTERN.matcher(instr);
            Matcher returnMatcher = RETURN_PATTERN.matcher(instr); // Assumindo que ret termina o bloco

            if (gotoMatcher.find()) {
                String label = gotoMatcher.group(1);
                if (labelToInstructionId.containsKey(label)) {
                    int targetId = labelToInstructionId.get(label);
                    current.successors.add(targetId);
                    instructions.get(targetId).predecessors.add(current.id);
                } else {
                    System.err.println("Aviso: Label de goto não encontrado: " + label);
                }
                endsExecution = true; // Goto incondicional
            } else if (ifMatcher.find()) {
                // Sucessor 1: Próxima instrução (se a condição for falsa)
                if (i + 1 < numInstructions) {
                    current.successors.add(i + 1);
                    instructions.get(i + 1).predecessors.add(current.id);
                }
                // Sucessor 2: Label do goto (se a condição for verdadeira)
                String label = ifMatcher.group(2);
                 if (labelToInstructionId.containsKey(label)) {
                    int targetId = labelToInstructionId.get(label);
                    current.successors.add(targetId);
                    instructions.get(targetId).predecessors.add(current.id);
                } else {
                    System.err.println("Aviso: Label de if não encontrado: " + label);
                }
            } else if (returnMatcher.find() || instr.startsWith("ret.")) {
                 endsExecution = true; // Return termina a execução do método
            }

            // Se não for um goto incondicional ou return, a próxima instrução é uma sucessora
            if (!endsExecution && i + 1 < numInstructions) {
                 // Evitar adicionar duplicado caso já seja um target de if
                 if (!current.successors.contains(i + 1)) {
                    current.successors.add(i + 1);
                    instructions.get(i + 1).predecessors.add(current.id);
                 }
            }
             System.out.printf("  Instr %d: Succ=%s, Pred=%s\n", current.id, current.successors, current.predecessors);
        }
    }

    private static void calculateInOutSets(List<InstructionInfo> instructions) {
        System.out.println("Calculando In/Out sets...");
        boolean changed;
        int iteration = 0;
        int maxIterations = 100; // Limite para evitar loops infinitos

        do {
            changed = false;
            iteration++;
            System.out.println("  Iteração " + iteration);

            // Processar instruções na ordem inversa para propagar a informação mais rapidamente
            for (int i = instructions.size() - 1; i >= 0; i--) {
                InstructionInfo current = instructions.get(i);

                // Calcular Out[i] = Union(In[s]) for s in successors(i)
                Set<String> newOut = new HashSet<>();
                for (int successorId : current.successors) {
                    if (successorId < instructions.size()) {
                        newOut.addAll(instructions.get(successorId).in);
                    }
                }

                // Se Out[i] mudou, atualiza e marca changed como true
                if (!current.out.equals(newOut)) {
                    current.out = new HashSet<>(newOut);
                    changed = true;
                }

                // Calcular In[i] = Use[i] ∪ (Out[i] - Def[i])
                Set<String> newIn = new HashSet<>(current.use); // Use[i]

                // Out[i] - Def[i] e adiciona ao In[i]
                Set<String> outMinusDef = new HashSet<>(current.out);
                outMinusDef.removeAll(current.def);
                newIn.addAll(outMinusDef);

                // Se In[i] mudou, atualiza e marca changed como true
                if (!current.in.equals(newIn)) {
                    current.in = new HashSet<>(newIn);
                    changed = true;
                }

                System.out.printf("    Instr %d: In=%s, Out=%s\n", current.id, current.in, current.out);
            }
        } while (changed && iteration < maxIterations);

        if (iteration >= maxIterations) {
            System.out.println("Aviso: Número máximo de iterações atingido!");
        }

        System.out.println("Cálculo de In/Out concluído em " + iteration + " iterações.");
    }

}