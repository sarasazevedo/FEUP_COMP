package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.up.fe.comp2025.ast.Kind.*;
import static pt.up.fe.comp2025.ast.TypeUtils.convertType;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    // Helper method to add a new semantic error report
    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {
        reports = new ArrayList<>();

        if (root.getNumChildren() == 0) {
            throw new IllegalArgumentException("Error: The root of the tree has no children.");
        }

        // Get the class declaration node
        var classDecl = root.getChildren(CLASS_DECL).getFirst();

        // Ensure the node is a class declaration
        SpecsCheck.checkArgument(
                Kind.CLASS_DECL.check(classDecl),
                () -> "Error: CLASS_DECL expected, but received " + classDecl.getKind()
        );

        // Extract the information needed for the symbol table
        String className = classDecl.get("name");
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var imports = buildImports(root);
        var fields = buildFields(classDecl);
        var superClassName = getSuperClassName(classDecl);

        // Build and return the symbol table
        return new JmmSymbolTable(className, methods, returnTypes, params, fields, imports, superClassName, locals);
    }

    // Extracts the superclass name if the class extends another class
    private String getSuperClassName(JmmNode classDecl) {
        if (classDecl.hasAttribute("sname")) {
                return classDecl.get("sname");
            }
        return null;
    }

    // Build a list of all import statements in the program
    private List<String> buildImports(JmmNode root) {
        List<String> imports = new ArrayList<>();
        //  Considers that the import nodes have the kind “ImportStmt” as defined in the grammar label
        for (var importNode : root.getChildren("ImportStmt")) {
            // Builds the import's full name
            StringBuilder importBuilder = new StringBuilder(importNode.get("name"));
            // Each import node can have children representing the additional parts (the IDs after the dots)
            for (var child : importNode.getChildren()) {
                if (child.get("name") != null) {
                    importBuilder.append(".").append(child.get("name"));
                }
            }
            imports.add(importBuilder.toString());
        }
        return imports;
    }

    // Extracts the class fields from the class declaration node
    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();
        // Process each variable declaration at the class level
        for (var fieldNode : classDecl.getChildren("VarDecl")) {
            var type = convertType(fieldNode.getChild(0));
            String name = fieldNode.get("name");

            System.out.println("Field found: " + name + "of type " + type.getName());

            fields.add(new Symbol(type, name));
        }

        return fields;
    }

    // Extracts the return types of the methods in the class declaration node
    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();
        // Iterate through all method declarations
        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            var typeNodes = method.getChildren(TYPE);
            // Determine return type (void if not specified)
            Type returnType;
            if (typeNodes.isEmpty()) {
                returnType = TypeUtils.newVoidType();
            } else {
                returnType = convertType(typeNodes.getFirst());
            }

            map.put(name, returnType);
        }

        return map;
    }

    // Maps method names to their parameter lists, handles varargs and regular parameters
    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();
        // Process each method declaration
        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            // Process each parameter for the method
            var params = method.getChildren(PARAM).stream()
                    .map(param -> {
                        JmmNode typeNode = param.getChildren().getFirst();
                        String typeStr = typeNode.get("name");
                        // Handle varargs parameters - detected by array type or "..." attribute
                        boolean isArray = typeNode.getKind().equals("ArrayType") ||
                                (param.hasAttribute("isVarArg") && param.get("isVarArg").equals("..."));
                        Type type = new Type(typeStr, isArray);
                        if (param.hasAttribute("isVarArg") && param.get("isVarArg").equals("...")) {
                            type.put("isVarArg", "true");
                        }
                        return new Symbol(type, param.get("name"));
                    })
                    .toList();
            map.put(name, params);
        }
        return map;
    }

    // Maps method names to their local variables
    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        var map = new HashMap<String, List<Symbol>>();
        // Process each method declaration
        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            var locals = new ArrayList<Symbol>();
            // Collect all local variable declarations in the method
            for (var varDecl : method.getChildren(VAR_DECL)) {
                var typeNodes = varDecl.getChildren(TYPE);
                if (!typeNodes.isEmpty()) {
                    var type = convertType(typeNodes.getFirst());
                    locals.add(new Symbol(type, varDecl.get("name")));
                }
            }
            map.put(name, locals);
        }
        return map;
    }

    // Extracts the method names from the class declaration
    private List<String> buildMethods(JmmNode classDecl) {
        var methods = classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();

        return methods;
    }


}
