package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Optional;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        //throw new UnsupportedOperationException();  // TODO
        //List<Environment.PlcObject> list = new ArrayList<Environment.PlcObject>();
        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);
        scope.lookupFunction("main", 0);
        requireAssignable(Environment.Type.INTEGER, scope.lookupFunction("main",0).getReturnType());

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Method ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {

        if (!ast.getTypeName().isPresent() && !ast.getValue().isPresent())
        {
            throw new RuntimeException("Declaration must have type or value to infer type.");
        }

        Environment.Type type = null;

        if (ast.getTypeName().isPresent())
        {
            type = Environment.getType(ast.getTypeName().get());
        }

        if (ast.getValue().isPresent())
        {
            visit(ast.getValue().get());

            if (type == null)
            {
                type = ast.getValue().get().getType();
            }

            requireAssignable(type, ast.getValue().get().getType());
        }

        ast.setVariable(scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL));

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
       // throw new UnsupportedOperationException();  // TODO
        //System.out.println(ast);
       // if (ast.getReceiver().equals(null))
        visit(ast.getReceiver());
        if (!(ast.getReceiver() instanceof Ast.Expr.Access))
        {
           // System.out.println("not instance of access");
            throw new RuntimeException("Receiver not an access expression.");
        }
       // System.out.println(ast.getValue());
        if (ast.getReceiver().getType().getJvmName().equals("int"))
        {
           // System.out.println("It's an integer!");
            requireAssignable(Environment.Type.INTEGER, ((Ast.Expr.Access) ast.getReceiver()).getVariable().getType());
        }
        //requireAssignable();

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {

        // 1. handle conditions
       // if (ast.getCondition().getType()!= Environment.Type.BOOLEAN)
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

        if (ast.getThenStatements().isEmpty()){
            throw new RuntimeException("Empty statement is not allowed");
        }
        else {
            // 2. visit then (inside new scope for each one)
            //System.out.println("There should be then statements.");

            scope = new Scope(scope);
            List<Environment.PlcObject> temp = new ArrayList<>();
            for (int i = 0; i < ast.getThenStatements().size(); i++) {
               visit(ast.getThenStatements().get(i));
            }


            // 3. visit else
                scope = new Scope(scope);
                for (int i = 0; i < ast.getElseStatements().size(); i++) {
                    visit(ast.getElseStatements().get(i));
                }

        }
       // throw new UnsupportedOperationException();
        return null;

    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.While ast)
    {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try {
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getStatements())
            {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        // throw new UnsupportedOperationException();  // TODO
        

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
       // TODO: Still need to implement Nil and Decimal


        System.out.println(ast.getLiteral());
        if (ast.getLiteral() instanceof Boolean)
        {
            //System.out.println("This is a Boolean.");
            ast.setType(Environment.Type.BOOLEAN);
            //System.out.println(ast);
        }
        else if (ast.getLiteral() instanceof Character)
        {
            ast.setType(Environment.Type.CHARACTER);
        }
        else if (ast.getLiteral() instanceof String)
        {
            ast.setType(Environment.Type.STRING);
        }
        else if (ast.getLiteral() instanceof BigInteger)
        {
            //System.out.println("This is a big integer");
            int bitCount = ((BigInteger) ast.getLiteral()).bitLength();
            //System.out.println(bitCount);
            if (bitCount > 32)
                throw new RuntimeException("The integer value is out of range!");
            else
                ast.setType(Environment.Type.INTEGER);
        }


        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        // TODO: Check to make sure expression is binary

        // requireAssignable(Ast.Expr.Binary, ast.getExpression().getType());
        ast.setType(ast.getExpression().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {

        if (ast.getOperator().equals("AND") || ast.getOperator().equals("OR"))
        {
            visit(ast.getLeft());
            visit(ast.getRight());
            requireAssignable(Environment.Type.BOOLEAN, ast.getLeft().getType());
            requireAssignable(Environment.Type.BOOLEAN, ast.getRight().getType());

            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">")
                || ast.getOperator().equals(">=") || ast.getOperator().equals("==") || ast.getOperator().equals("!="))
        {
            visit(ast.getLeft());
            visit(ast.getRight());
            requireAssignable(Environment.Type.COMPARABLE, ast.getLeft().getType());
            requireAssignable(Environment.Type.COMPARABLE, ast.getRight().getType());
            requireAssignable(ast.getLeft().getType(), ast.getRight().getType());

            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (ast.getOperator().equals("+"))
        {
            visit(ast.getLeft());
            visit(ast.getRight());
            if ((ast.getLeft().getType().equals(Environment.Type.STRING)) || (ast.getRight().getType().equals(Environment.Type.STRING)))
            {
                //System.out.println("Either side is a string.");
                ast.setType(Environment.Type.STRING);
            }

            else if (ast.getLeft().getType().equals(Environment.Type.INTEGER))
            {
                //System.out.println("We found a left integer!");
                if (ast.getRight().getType().equals(Environment.Type.INTEGER))
                {
                    ast.setType(Environment.Type.INTEGER);
                }
                else
                    throw new RuntimeException("Left side is an integer, right must be as well.");
            }
            else if (ast.getLeft().getType().equals(Environment.Type.DECIMAL))
            {
                //System.out.println("We found a left decimal!");
                if (ast.getRight().getType().equals(Environment.Type.DECIMAL))
                {
                    ast.setType(Environment.Type.DECIMAL);
                }
                else
                    throw new RuntimeException("Left side is a decimal, right must be as well.");
            }
            else
            {
                //System.out.println("ast if not string on left side: " + ast.getLeft());
                throw new RuntimeException("Must be a string, integer, or decimal, and match.");
            }
        }
        else if (ast.getOperator().equals("-") || ast.getOperator().equals("*") || ast.getOperator().equals("/"))
        {
            visit(ast.getLeft());
            visit(ast.getRight());

            if (ast.getLeft().getType().equals(Environment.Type.INTEGER))
            {
                if (ast.getRight().getType().equals(Environment.Type.INTEGER))
                {
                    ast.setType(Environment.Type.INTEGER);
                }
                else
                    throw new RuntimeException("Left side is an integer, right must be as well.");
            }
            else if (ast.getLeft().getType().equals(Environment.Type.DECIMAL))
            {
                if (ast.getRight().getType().equals(Environment.Type.DECIMAL))
                {
                    ast.setType(Environment.Type.DECIMAL);
                }
                else
                    throw new RuntimeException("Left side is an decimal, right must be as well.");
            }
            else
                throw new RuntimeException("Must be an integer or a decimal.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {

        if (ast.getReceiver().isPresent())
        {
            // Do something
            //System.out.println(ast.getReceiver());
            visit(ast.getReceiver().get());
            ast.setVariable(ast.getReceiver().get().getType().getField(ast.getName()));
        }
        else
        {
            // Do something else
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {

        if (ast.getReceiver().isPresent())
        {
            visit(ast.getReceiver().get());
            ast.setFunction(ast.getReceiver().get().getType().getMethod(ast.getName(), ast.getArguments().size()));
        }
        else
        {
            ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));
        }

        for (int i = 0; i < ast.getArguments().size(); i++)
        {
            requireAssignable(scope.lookupFunction(ast.getName(), ast.getArguments().size()).getParameterTypes().get(i), ast.getArguments().get(i).getType());
        }

        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type)
    {
        if (target.getName().equals("Integer"))
        {
            if (!type.getName().equals("Integer"))
            {
                throw new RuntimeException("Value must be of type Integer.");
            }
        }
        else if (target.getName().equals("Decimal"))
        {
            if (!type.getName().equals("Decimal"))
            {
                throw new RuntimeException("Value must be of type Decimal.");
            }
        }
        else if (target.getName().equals("Character"))
        {
            if (!type.getName().equals("Character"))
            {
                throw new RuntimeException("Value must be of type Character.");
            }
        }
        else if (target.getName().equals("String"))
        {
            if (!type.getName().equals("String"))
            {
                throw new RuntimeException("Value must be of type String.");
            }
        }
        else if (target.getName().equals("Boolean"))
        {
            if (!type.getName().equals("Boolean"))
            {
                throw new RuntimeException("Value must be of type Boolean.");
            }
        }
        else if (target.getName().equals("IntegerIterable"))
        {
            if (!type.getName().equals("IntegerIterable"))
            {
                throw new RuntimeException("Value must be of type IntegerIterable.");
            }
        }
        else if (target.getName().equals("Nil"))
        {
            if (!type.getName().equals("Nil"))
            {
                throw new RuntimeException("Value must be of type Nil.");
            }
        }
        else if (target.getName().equals("Comparable"))
        {
            if (!type.getName().equals("Comparable") && !type.getName().equals("String")
                    && !type.getName().equals("Character") && !type.getName().equals("Integer")
                    && !type.getName().equals("Decimal"))
            {
                throw new RuntimeException("Value must be of type Comparable.");
            }
        }
    }

}
