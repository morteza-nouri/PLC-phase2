package main.visitor.name;
import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.ConstructorDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.FieldDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.MethodDeclaration;
import main.ast.nodes.declaration.variableDec.VariableDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.values.NullValue;
import main.ast.nodes.expression.values.SetValue;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.nodes.statement.*;
import main.ast.nodes.statement.set.SetAdd;
import main.ast.nodes.statement.set.SetDelete;
import main.ast.nodes.statement.set.SetMerge;
import main.compileError.nameError.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.*;
import main.symbolTable.items.*;

import main.symbolTable.utils.graph.Graph;
import main.visitor.Visitor;

import java.lang.invoke.MethodType;
import java.util.ArrayList;

public class NameAnalyser extends Visitor<Void> {

    private int newId = 1;
    private boolean firstVisit = true;
    private boolean isInClass = false;
    private boolean isInMethod = false;
    private String curClassName;
    private String methodName;
    private final Graph<String> structHierarchy = new Graph<>();

    private void createClassSymbolTable(ClassDeclaration classDec) {
        SymbolTable newSymbolTable = new SymbolTable();
        ClassSymbolTableItem newSymbolTableItem = new ClassSymbolTableItem(classDec);
        newSymbolTableItem.setClassSymbolTable(newSymbolTable);
        try {
            SymbolTable.root.put(newSymbolTableItem);

        } catch (ItemAlreadyExistsException e) {
            ClassRedefinition exception = new ClassRedefinition(classDec.getLine(), classDec.getClassName().getName());
            classDec.addError(exception);
            String newName = newId + "@";
            newId += 1;
            classDec.setClassName(new Identifier(newName));
            try {
                ClassSymbolTableItem newClassSym = new ClassSymbolTableItem(classDec);
                newClassSym.setClassSymbolTable(newSymbolTable);
                SymbolTable.root.put(newClassSym);
            } catch (ItemAlreadyExistsException e1) { //Unreachable
            }
        }
    }

    private void createGlobalVarSymbolTable(VariableDeclaration varDec) {
        GlobalVariableSymbolTableItem newSymbolTableItem = new GlobalVariableSymbolTableItem(varDec);
        try {
            SymbolTable.root.put(newSymbolTableItem);

        } catch (ItemAlreadyExistsException e) {
            GlobalVarRedefinition exception = new GlobalVarRedefinition(varDec.getLine(), varDec.getVarName().getName());
            varDec.addError(exception);
            String newName = newId + "@";
            newId += 1;
            varDec.setVarName(new Identifier(newName));
            try {
                GlobalVariableSymbolTableItem newGlobalVarSym = new GlobalVariableSymbolTableItem(varDec);
                SymbolTable.root.put(newGlobalVarSym);
            } catch (ItemAlreadyExistsException e1) { //Unreachable
            }
        }
    }

    private boolean hasConflict(String key) {
        try {
            SymbolTable.root.getItem(key);
            return true;
        } catch (ItemNotFoundException exception) {
            return false;
        }
    }

    @Override
    public Void visit(Program program) {
        SymbolTable root = new SymbolTable();
        SymbolTable.root = root;
        SymbolTable.push(root);

        for (ClassDeclaration structDec : program.getClasses()) {
            createClassSymbolTable(structDec);
            try {
                structHierarchy.addNode(structDec.getClassName().getName());
            }
            catch (Exception e){//unreachable
            }
        }

        for (VariableDeclaration varDecl : program.getGlobalVariables()) {
            createGlobalVarSymbolTable(varDecl);
            try {
                structHierarchy.addNode(varDecl.getVarName().getName());
            }
            catch (Exception e){//unreachable
            }
        }


        for (ClassDeclaration classDecl : program.getClasses()) {
            try {
                String key = ClassSymbolTableItem.START_KEY + classDecl.getClassName().getName();
                ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem(key);
                SymbolTable.push(classSymbolTableItem.getClassSymbolTable());
                isInClass = true;
                curClassName = classDecl.getClassName().getName();
                classDecl.accept(this);
                isInClass = false;
                SymbolTable.pop();
            } catch (ItemNotFoundException e) { //Unreachable
            }
        }

        for (VariableDeclaration varDecl : program.getGlobalVariables()) {
            SymbolTable.push(new SymbolTable());
            varDecl.accept(this);
            SymbolTable.pop();
        }

        SymbolTable.push(new SymbolTable());
        SymbolTable.pop();

        return null;
    }

    public Void visit(ClassDeclaration classDec) {
        classDec.getClassName().accept(this);
        if(classDec.getParentClassName() != null)
            classDec.getParentClassName().accept(this);
        if(classDec.getConstructor() != null) {
            classDec.getConstructor().accept(this);
        }
        for(MethodDeclaration md : classDec.getMethods()) {
            md.accept(this);
        }

        for(FieldDeclaration fd : classDec.getFields()) {
            fd.accept(this);
        }
        return null;
    }

    public Void visit(ConstructorDeclaration constructorDeclaration) {
        SymbolTable.push(new SymbolTable());
        isInMethod = true;
        methodName = "initialize";
        if (constructorDeclaration.getArgs() != null)
            for(VariableDeclaration arg : constructorDeclaration.getArgs()) {
                arg.accept(this);
            }

        if (constructorDeclaration.getLocalVars() != null) {
            for (VariableDeclaration localVar : constructorDeclaration.getLocalVars()) {
                localVar.accept(this);
            }
        }
        if (constructorDeclaration.getBody() != null)
            for(Statement body : constructorDeclaration.getBody()) {
                body.accept(this);
            }

        isInMethod = false;
        SymbolTable.pop();
        return null;
    }

    public Void visit(MethodDeclaration methodDeclaration) {
        String name = methodDeclaration.getMethodName().getName();
        MethodSymbolTableItem methodSymbolTableItem = new MethodSymbolTableItem(methodDeclaration);
        try {
            SymbolTable.top.getItem(methodSymbolTableItem.getKey());
            MethodRedefinition exception = new MethodRedefinition(methodDeclaration.getLine(), name);
            methodDeclaration.addError(exception);
        } catch (ItemNotFoundException exception2) {
            try {
                SymbolTable.top.put(methodSymbolTableItem);
            } catch (ItemAlreadyExistsException exception3) { //unreachable
            }
        }

        SymbolTable.push(new SymbolTable());
        isInMethod = true;
        methodDeclaration.getMethodName().accept(this);
        methodName = methodDeclaration.getMethodName().getName();

        for(VariableDeclaration arg : methodDeclaration.getArgs()) {
            arg.accept(this);
        }

        if (methodDeclaration.getLocalVars() != null) {
            for (VariableDeclaration localVar : methodDeclaration.getLocalVars()) {
                localVar.accept(this);
            }
        }

        for(Statement body : methodDeclaration.getBody()) {
            body.accept(this);
        }
        isInMethod = false;

        SymbolTable.pop();

        return null;
    }

    public Void visit(VariableDeclaration varDecl) {
        String name = varDecl.getVarName().getName();
        LocalVariableSymbolTableItem variableSymbolTableItem = new LocalVariableSymbolTableItem(varDecl);
        try {
            SymbolTable.top.getItem(variableSymbolTableItem.getKey());
            LocalVarRedefinition exception = new LocalVarRedefinition(varDecl.getLine(), name);
            varDecl.addError(exception);
        } catch (ItemNotFoundException exception2) {
            try {
                SymbolTable.top.put(variableSymbolTableItem);
            } catch (ItemAlreadyExistsException exception3) { //unreachable
            }
        }
        return null;
    }

    public Void visit(AssignmentStmt assignmentStmt) {
        assignmentStmt.getlValue().accept(this);
        assignmentStmt.getrValue().accept(this);
        return null;
    }

    public Void visit(BlockStmt blockStmt) {
        for(Statement stmt : blockStmt.getStatements()) {
            stmt.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(ConditionalStmt conditionalStmt) {
        //todo: done
        conditionalStmt.getCondition().accept(this);
        conditionalStmt.getThenBody().accept(this);

        if (conditionalStmt.getElseBody() != null)
            conditionalStmt.getElseBody().accept(this);

        if (conditionalStmt.getElsif() != null)
            for(ElsifStmt stmt : conditionalStmt.getElsif()) {
                stmt.accept(this);
            }
        return null;
    }

    @Override
    public Void visit(ElsifStmt elsifStmt) {
        elsifStmt.getCondition().accept(this);
        elsifStmt.getThenBody().accept(this);
        return null;
    }

    @Override
    public Void visit(MethodCallStmt methodCallStmt) {
        methodCallStmt.getMethodCall().accept(this);
        return null;
    }

    @Override
    public Void visit(PrintStmt print) {
        print.getArg().accept(this);
        return null;
    }

    @Override
    public Void visit(ReturnStmt returnStmt) {
        returnStmt.getReturnedExpr().accept(this);
        return null;
    }

    @Override
    public Void visit(EachStmt eachStmt) {
        eachStmt.getVariable().accept(this);
        eachStmt.getList().accept(this);
        eachStmt.getBody().accept(this);

        return null;
    }

    @Override
    public Void visit(BinaryExpression binaryExpression) {
        binaryExpression.getFirstOperand().accept(this);
        binaryExpression.getSecondOperand().accept(this);
        return null;
    }

    @Override
    public Void visit(UnaryExpression unaryExpression) {
        unaryExpression.getOperand().accept(this);
        return null;
    }

    @Override
    public Void visit(TernaryExpression ternaryExpression) {
        ternaryExpression.getCondition().accept(this);
        ternaryExpression.getTrueExpression().accept(this);
        ternaryExpression.getFalseExpression().accept(this);
        return null;
    }

    @Override
    public Void visit(ObjectMemberAccess objectOrListMemberAccess) {
        objectOrListMemberAccess.getInstance().accept(this);
        objectOrListMemberAccess.getMemberName().accept(this);
        return null;
    }

    @Override
    public Void visit(Identifier identifier) {
        return null;
    }

    @Override
    public Void visit(ArrayAccessByIndex arrayAccessByIndex) {
        arrayAccessByIndex.getInstance().accept(this);
        arrayAccessByIndex.getIndex().accept(this);
        return null;
    }

    @Override
    public Void visit(MethodCall methodCall) {
        methodCall.getInstance().accept(this);
        for(Expression arg : methodCall.getArgs()) {
            arg.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(NewClassInstance newClassInstance) {
        for(Expression arg : newClassInstance.getArgs()) {
            arg.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(SelfClass selfClass) {
        return null;
    }

    @Override
    public Void visit(NullValue nullValue) {
        return null;
    }

    @Override
    public Void visit(IntValue intValue) {
        return null;
    }

    @Override
    public Void visit(BoolValue boolValue) {
        return null;
    }

    @Override
    public Void visit(SetInclude setAdd) {
        setAdd.getSetArg().accept(this);
        setAdd.getElementArg().accept(this);
        return null;
    }

    @Override
    public Void visit(SetValue setValue) {
        return null;
    }

    @Override
    public Void visit(SetMerge setMerge) {
        setMerge.getSetArg().accept(this);
        for(Expression arg : setMerge.getElementArgs()) {
            arg.accept(this);
        }
        return null;
    }


    @Override
    public Void visit(SetDelete setDelete) {
        setDelete.getSetArg().accept(this);
        setDelete.getElementArg().accept(this);
        return null;
    }

    @Override
    public Void visit(SetAdd setAdd) {
        setAdd.getSetArg().accept(this);
        setAdd.getElementArg().accept(this);
        return null;
    }

    @Override
    public Void visit(RangeExpression rangeExpression) {
        rangeExpression.getLeftExpression().accept(this);
        rangeExpression.getRightExpression().accept(this);
        return null;
    }
}