package pt.up.fe.comp2025.optimization;

    import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
    import pt.up.fe.comp.jmm.ast.AJmmVisitor;
    import pt.up.fe.comp.jmm.ast.JmmNode;
    import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

    import java.util.Collections;
    import java.util.HashMap;
    import java.util.Map;

    public class ConstantPropagationVisitor extends AJmmVisitor<Map<String, JmmNode>, Boolean> {
        private final SymbolTable symbolTable;
        private boolean changed = false;

        public ConstantPropagationVisitor(SymbolTable symbolTable) {
            this.symbolTable = symbolTable;
            addVisits();
        }

        private void addVisits() {
            // Visitas para nós de atribuição
            addVisit("AssignStmt", this::visitAssignment);
            addVisit("Assignment", this::visitAssignment);

            // Visitas para referências a variáveis
            addVisit("VarRefExpr", this::visitVarReference);
            addVisit("VarExpr", this::visitVarReference);
            addVisit("Identifier", this::visitVarReference);
            addVisit("IdExpr", this::visitVarReference);

            // Expressões binárias
            addVisit("BinaryExpr", this::visitBinaryExpr);
            addVisit("Comparison", this::visitBinaryExpr);
            addVisit("BinOp", this::visitBinaryExpr);
            addVisit("ComparisonOp", this::visitBinaryExpr);

            // Visitas para estruturas de controle e outros elementos
            addVisit("ReturnStmt", this::visitReturnStmt);
            addVisit("Return", this::visitReturnStmt);
            addVisit("IfElseStmt", this::visitIfStmt);
            addVisit("WhileStmt", this::visitWhileStmt);
            addVisit("MethodDecl", this::visitMethodDecl);
            addVisit("MainMethod", this::visitMethodDecl);
            addVisit("Block", this::visitBlock);

            setDefaultVisit(this::defaultVisit);
        }

        private Boolean visitAssignment(JmmNode node, Map<String, JmmNode> constants) {
            // Primeiro visita o lado direito para propagar constantes
            if (node.getChildren().size() >= 2) {
                visit(node.getChildren().get(1), constants);
            }

            // Obtém informações da atribuição
            JmmNode lhs = node.getChildren().get(0);
            JmmNode rhs = node.getChildren().get(1);

            String varName = getVarName(lhs);

            if (varName != null) {
                // Se o lado direito é um literal, adiciona à tabela de constantes
                if (isLiteral(rhs)) {
                    constants.put(varName, rhs);
                } else {
                    // Remove a variável da tabela de constantes se estiver sendo atualizada
                    constants.remove(varName);
                }
            }

            return true;
        }

        private Boolean visitReturnStmt(JmmNode node, Map<String, JmmNode> constants) {
            // Se o nó de retorno tem uma expressão
            if (!node.getChildren().isEmpty()) {
                JmmNode expr = node.getChildren().get(0);
                String varName = getVarName(expr);

                // Se a expressão de retorno é uma referência a variável
                if (varName != null && constants.containsKey(varName)) {
                    // Substitui a variável pelo seu valor constante
                    JmmNode constantValue = createLiteralCopy(constants.get(varName));
                    expr.replace(constantValue);
                    changed = true;
                }
            }

            return true;
        }

        private Boolean visitBinaryExpr(JmmNode node, Map<String, JmmNode> constants) {
            // Processa todos os filhos da expressão binária
            for (JmmNode child : node.getChildren()) {
                visit(child, constants);
            }
            return true;
        }

        private Boolean visitVarReference(JmmNode node, Map<String, JmmNode> constants) {
            String varName = getVarName(node);

            if (varName != null && constants.containsKey(varName)) {
                // Substitui a referência da variável pelo seu valor constante
                JmmNode constantValue = createLiteralCopy(constants.get(varName));
                node.replace(constantValue);
                changed = true;
            }

            return true;
        }

        private String getVarName(JmmNode node) {
            if (node.hasAttribute("name")) {
                return node.get("name");
            } else if (node.hasAttribute("var")) {
                return node.get("var");
            } else if (node.hasAttribute("id")) {
                return node.get("id");
            } else if (node.hasAttribute("value") && !isLiteral(node)) {
                return node.get("value");
            } else if (node.getKind().equals("VarRefExpr") || node.getKind().equals("VarExpr") ||
                    node.getKind().equals("Identifier") || node.getKind().equals("IdExpr")) {
                return node.get("value");
            }
            return null;
        }

        private boolean isLiteral(JmmNode node) {
            String kind = node.getKind();
            return kind.equals("IntegerLiteral") || kind.equals("IntLiteral") ||
                    kind.equals("BooleanLiteral") || kind.equals("BoolLiteral");
        }

        private JmmNode createLiteralCopy(JmmNode node) {
            String kind = node.getKind();
            String value = node.get("value");

            JmmNode newNode;
            if (kind.equals("IntegerLiteral") || kind.equals("IntLiteral")) {
                newNode = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
            } else {
                newNode = new JmmNodeImpl(Collections.singletonList("BooleanLiteral"));
            }

            newNode.put("value", value);
            return newNode;
        }

        private Boolean visitIfStmt(JmmNode node, Map<String, JmmNode> constants) {
            // Visita a condição
            visit(node.getChildren().get(0), constants);

            // Salva o estado atual das constantes
            Map<String, JmmNode> beforeIf = new HashMap<>(constants);

            // Visita o bloco "then"
            visit(node.getChildren().get(1), constants);
            Map<String, JmmNode> thenConstants = new HashMap<>(constants);

            // Restaura estado anterior e visita o bloco "else"
            constants.clear();
            constants.putAll(beforeIf);

            if (node.getChildren().size() > 2) {
                visit(node.getChildren().get(2), constants);
            }

            // Após os blocos, mantém apenas constantes iguais em ambos os caminhos
            Map<String, JmmNode> finalConstants = new HashMap<>();
            for (String var : thenConstants.keySet()) {
                if (constants.containsKey(var) && nodesHaveSameValue(thenConstants.get(var), constants.get(var))) {
                    finalConstants.put(var, thenConstants.get(var));
                }
            }

            constants.clear();
            constants.putAll(finalConstants);

            return true;
        }

        private boolean nodesHaveSameValue(JmmNode node1, JmmNode node2) {
            return node1.getKind().equals(node2.getKind()) &&
                    node1.get("value").equals(node2.get("value"));
        }

        private Boolean visitWhileStmt(JmmNode node, Map<String, JmmNode> constants) {
            // Visita a condição do loop
            visit(node.getChildren().get(0), constants);

            // Salva constantes antes do loop
            Map<String, JmmNode> beforeLoop = new HashMap<>(constants);

            // Continua a visitar o corpo do loop
            if (node.getChildren().size() > 1) {
                visit(node.getChildren().get(1), constants);

                // Depois de visitar o corpo do loop, verificar quais variáveis foram modificadas
                Map<String, JmmNode> afterLoopConstants = new HashMap<>();
                for (String var : beforeLoop.keySet()) {
                    // Manter apenas constantes não alteradas dentro do loop
                    if (constants.containsKey(var) &&
                            nodesHaveSameValue(beforeLoop.get(var), constants.get(var))) {
                        afterLoopConstants.put(var, beforeLoop.get(var));
                    }
                }

                // Atualizar o mapa de constantes
                constants.clear();
                constants.putAll(afterLoopConstants);
            }

            return true;
        }

        private Boolean visitMethodDecl(JmmNode node, Map<String, JmmNode> constants) {
            // Usar mapa novo para constantes locais do método
            Map<String, JmmNode> methodConstants = new HashMap<>();
            return visitAllChildren(node, methodConstants);
        }

        private Boolean visitBlock(JmmNode node, Map<String, JmmNode> constants) {
            return visitAllChildren(node, constants);
        }

        private Boolean defaultVisit(JmmNode node, Map<String, JmmNode> constants) {
            return visitAllChildren(node, constants);
        }

        public boolean optimize(JmmNode root) {
            changed = false;
            Map<String, JmmNode> globalConstants = new HashMap<>();
            visit(root, globalConstants);
            return changed;
        }

        @Override
        protected void buildVisitor() {
            // Método requerido pela classe pai
        }
    }

