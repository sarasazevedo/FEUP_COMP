package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks if a class used in method calls or instantiations has been imported.
 * Also verifies if a method call is made to a declared or inherited method.
 */
public class UndeclaredClass extends AnalysisVisitor {

    private final List<String> importedClasses;
    private String currentClass;

    // Creates a new UnderclaredClass visitor to track imported classes
    public UndeclaredClass() {
        this.importedClasses = new ArrayList<>();
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit(Kind.IMPORT_STMT, this::visitImportStmt);
        addVisit(Kind.METHOD_CALL, this::visitMethodCall);
        addVisit(Kind.NEW_CLASS_EXPR, this::visitNewClassExpr);
    }

    // Records the name of the current class being visited
    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        currentClass = classDecl.get("name");
        return null;
    }

    // Records the imported class names
    private Void visitImportStmt(JmmNode importStmt, SymbolTable table) {
        String className = importStmt.get("name");
        importedClasses.add(className);
        return null;
    }

    // Checks if the referenced method exists in its target class, reports an error if not
    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {
        JmmNode objectNode = methodCall.getChild(0);
        String className = TypeUtils.getExprType(objectNode, (JmmSymbolTable) table).getName();
        String methodName = methodCall.getChild(1).get("name");

        if (className.equals("unknown")) {
            className = objectNode.get("name");
        }

        if (!isMethodDeclared(className, methodName, (JmmSymbolTable) table)) {
            String message = String.format("Method '%s' is called on class '%s' but is not declared.", methodName, className);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodCall.getLine(),
                    methodCall.getColumn(),
                    message,
                    null)
            );
        }
        return null;
    }

    // Checks if the class of the object in a new class instantiation has been imported, reports an error otherwise
    private Void visitNewClassExpr(JmmNode newClassExpr, SymbolTable table) {
        String className = newClassExpr.get("name");
        if (!importedClasses.contains(className) && !className.equals(currentClass) &&
                (table.getSuper() == null || !table.getSuper().equals(className))) {
            String message = String.format("Class '%s' is used but not imported.", className);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    newClassExpr.getLine(),
                    newClassExpr.getColumn(),
                    message,
                    null)
            );
        }
        return null;
    }

    // Checks if the method is declared in the current class or in an imported class
    private boolean isMethodDeclared(String className, String methodName, JmmSymbolTable table) {
        boolean result = methodName.equals("length");

        if (className.equals(currentClass)) {
            if (table.getMethods().stream().anyMatch(m -> m.equals(methodName)))
                result = true;
        }

        if (importedClasses.contains(className) || table.getSuper() != null){
            result = true;
        }

        if (className.equals("String")) {
            result = true;
        }

        return result;
    }
}
