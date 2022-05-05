package main.visitor.nameAnalyzer;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.classDec.*;
import main.ast.nodes.declaration.classDec.classMembersDec.*;
import main.ast.nodes.declaration.variableDec.VariableDeclaration;
import main.ast.nodes.expression.Identifier;
import main.symbolTable.SymbolTable;
import main.symbolTable.items.*;
import main.symbolTable.exceptions.*;
import main.compileError.*;
import main.compileError.nameError.*;
import main.visitor.Visitor;

import java.lang.invoke.MethodType;
import java.util.ArrayList;

public class NameCollector extends Visitor<Void> {

    private int newId = 1;

    @Override
    public Void visit(Program program) {
        SymbolTable.push(new SymbolTable());
        SymbolTable.root = SymbolTable.top;
        for (VariableDeclaration varDec : program.getGlobalVariables()) {
            varDec.accept(this);
        }
        for (ClassDeclaration classDec : program.getClasses()) {
            classDec.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
        GlobalVariableSymbolTableItem globalVarSTI = new GlobalVariableSymbolTableItem(variableDeclaration);
        try {
            SymbolTable.root.put(globalVarSTI);
        } catch (ItemAlreadyExistsException e) {
            String varName = variableDeclaration.getVarName().getName();
            GlobalVarRedefinition exception = new GlobalVarRedefinition(variableDeclaration.getLine(), varName);
            variableDeclaration.addError(exception);
            String newName = newId + "@";
            newId += 1;
            variableDeclaration.getVarName().setName(newName);
            GlobalVariableSymbolTableItem newGlobalSTI = new GlobalVariableSymbolTableItem(variableDeclaration);
            try {
                SymbolTable.root.put(newGlobalSTI);
            } catch (ItemAlreadyExistsException ignored) {}
        }
        return null;
    }

    @Override
    public Void visit(ClassDeclaration classDeclaration) {
        ClassSymbolTableItem classSymbolTableItem = new ClassSymbolTableItem(classDeclaration);
        SymbolTable.push(new SymbolTable(SymbolTable.top));
        classSymbolTableItem.setClassSymbolTable(SymbolTable.top);
        try {
            SymbolTable.root.put(classSymbolTableItem);
        } catch (ItemAlreadyExistsException e) {
            ClassRedefinition exception = new ClassRedefinition(classDeclaration.getLine(), classDeclaration.getClassName().getName());
            classDeclaration.addError(exception);
            String newName = newId + "@";
            newId += 1;
            classDeclaration.getClassName().setName(newName);
            ClassSymbolTableItem newClassSTI = new ClassSymbolTableItem(classDeclaration);
            newClassSTI.setClassSymbolTable(SymbolTable.top);
            try {
                SymbolTable.root.put(newClassSTI);
            } catch (ItemAlreadyExistsException ignored) { }
        }

        for (FieldDeclaration fieldDec : classDeclaration.getFields()) {
            fieldDec.accept(this);
        }

        if (classDeclaration.getConstructor() != null) {
            classDeclaration.getConstructor().accept(this);
        }

        for (MethodDeclaration methodDec : classDeclaration.getMethods()) {
            methodDec.accept(this);
        }
        SymbolTable.pop();
        return null;
    }


}
