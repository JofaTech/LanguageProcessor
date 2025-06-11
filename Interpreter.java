package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast)
    {
        if (ast.getFields().isEmpty() && ast.getMethods().isEmpty())
            throw new RuntimeException("Function doesn't exist.");
        else
        {
            List<Environment.PlcObject> list = new ArrayList<Environment.PlcObject>();

            ast.getFields().forEach(this::visit);
            ast.getMethods().forEach(this::visit);

            return scope.lookupFunction("main", 0).invoke(list);
        }

    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        if (ast.getValue().isPresent())
        {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        else
        {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        // DEF main() DO RETURN 0; END   <-- First Test

        Scope childScope = scope;

        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            Scope thirdScope = scope;

            scope = new Scope(childScope);

            for (int i = 0; i < ast.getParameters().size(); i++)
            {
                scope.defineVariable(ast.getParameters().get(i), args.get(i));
            }

            try {
                for (int j = 0; j < ast.getStatements().size(); j++)
                {
                    visit(ast.getStatements().get(j));
                }
            }
            catch(Return returnException)
            {
                return returnException.value;
            }
            finally {
                scope = thirdScope;
            }

            return Environment.NIL;
        });

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {

        if (ast.getValue().isPresent())
        {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        else
        {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {

        if (ast.getReceiver() instanceof Ast.Expr.Access) // Receiver has to be of type Ast.Expr.Access
        {

            if (((Ast.Expr.Access) ast.getReceiver()).getReceiver().isPresent())
            {
                // assign field
                visit(((Ast.Expr.Access) ast.getReceiver()).getReceiver().get()).setField((((Ast.Expr.Access) ast.getReceiver()).getName()), visit(ast.getValue()));
            }
            else
            {
                // Set variable in current scope
                scope.lookupVariable(((Ast.Expr.Access) ast.getReceiver()).getName()).setValue(visit(ast.getValue()));
            }
        }

        else // else not type of Ast.Expr.Access, throw exception
        {
            throw new RuntimeException("Not of type Ast.Expr.Access, so not assignable.");
        }

        return Environment.NIL;
    }

    @Override
    //
    public Environment.PlcObject visit(Ast.Stmt.If ast) {

        scope = new Scope(scope);

        // if statement is true, evaluate THEN statement
        if (requireType(Boolean.class, visit(ast.getCondition()))) {
            ast.getThenStatements().forEach(this::visit);
        }
        else {
            ast.getElseStatements().forEach(this::visit); // otherwise evaluate else statements
        }

        scope = scope.getParent();

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {

        requireType(Iterable.class, visit(ast.getValue()));

        try {
            for (Ast.Stmt stmt : ast.getStatements()) {
                scope = new Scope(scope);
                // scope.defineVariable(ast.getName(), visit(ast.getStatements().get()));
                scope.defineVariable(ast.getName(), visit(stmt));
            }
        }
        finally {
            scope = scope.getParent();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while (requireType(Boolean.class, visit(ast.getCondition())))
        {
            try
            {
                scope = new Scope(scope);

                // ast.getStatements().forEach(this::visit); <-- This could replace the for loop below...shorthand

                for (Ast.Stmt stmt : ast.getStatements())
                {
                    visit(stmt);
                }
            }
            finally
            {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }


    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() == null) // If literal is a null
        {
            return Environment.NIL;
        }

        return Environment.create(ast.getLiteral()); // Returns literal value
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return Environment.create(visit(ast.getExpression()).getValue());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast)
    {
        if (ast.getOperator().equals("AND") || ast.getOperator().equals("OR"))
        {
            Object left = visit(ast.getLeft()).getValue();

            //System.out.println(left);

            while (requireType(Boolean.class, visit(ast.getLeft()))) {
                // System.out.println("Looks like the left operand is a boolean!");
                // System.out.println("Left value: " + visit(ast.getLeft()).getValue());
                if (ast.getOperator().equals("AND"))
                {
                    Object right = visit(ast.getRight()).getValue();
                    //System.out.println("The AND part returns: " + equals);
                    if (left == Boolean.TRUE)
                    {
                        //System.out.println("The left operand is True.");
                        if (right == Boolean.TRUE)
                        {
                            //System.out.println("The right operand is True.");
                            return Environment.create(true);
                        }
                    }
                    //System.out.println("AND returns False.");
                    return Environment.create(false);
                }
                //if (ast.getOperator().equals("OR"))
                else
                {
                    System.out.println("This returns the OR operator: " + ast.getOperator());

                    if (left == Boolean.TRUE) {
                        //System.out.println("The left OR operand is True.");
                        return Environment.create(true);
                    }

                    Object right = visit(ast.getRight()).getValue();

                    if (right == Boolean.TRUE)
                    {
                        //System.out.println("The right OR operand is True.");
                        return Environment.create(true);
                    }

                    return Environment.create(false);
                }
            }
        } // End of AND/OR if statement

        else if (ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">")
                || ast.getOperator().equals(">="))
        {
            // System.out.println("This is the comparable operator section");

            Environment.PlcObject left = visit(ast.getLeft());
            int result = requireType(Comparable.class, left).compareTo(requireType(left.getValue().getClass(), visit(ast.getRight())));
            // System.out.println("Comparable result: " + result);

            if (ast.getOperator().equals("<"))
            {
                if (result == -1)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
            else if (ast.getOperator().equals("<="))
            {
                if (result == -1 || result == 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
            else if (ast.getOperator().equals(">"))
            {
                if (result == 1)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
            //else if (ast.getOperator().equals(">="))
            else
            {
                if (result == 0 || result == 1)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }

        }

        else if (ast.getOperator().equals("==") || ast.getOperator().equals("!="))
        {
            Boolean decision = ast.getLeft().equals(ast.getRight());
            return Environment.create(decision);
        }
        else if (ast.getOperator().equals("+"))
        {
            if(visit(ast.getLeft()).getValue() instanceof BigInteger ) // If adding BigInteger values
            {
                BigInteger left = (BigInteger) visit(ast.getLeft()).getValue();
                //System.out.println("BigInteger left value: " + left);

                if(visit(ast.getRight()).getValue() instanceof BigInteger ) {
                    BigInteger right = (BigInteger) visit(ast.getRight()).getValue();
                    //System.out.println("BigInteger right value: " + right);
                    return Environment.create(left.add(right));
                }
                else
                    throw new RuntimeException("BigInteger addition type mismatch.");
            }

            else if(visit(ast.getLeft()).getValue() instanceof BigDecimal ) // If adding BigDecimal values
            {
                BigDecimal left = (BigDecimal) visit(ast.getLeft()).getValue();

                if(visit(ast.getRight()).getValue() instanceof BigDecimal ) {
                    BigDecimal right = (BigDecimal) visit(ast.getRight()).getValue();
                    return Environment.create(left.add(right));
                }
                else
                    throw new RuntimeException("BigDecimal addition type mismatch.");
            }

            else if(visit(ast.getLeft()).getValue() instanceof String ) // If adding String values
            {
                String left = (String) visit(ast.getLeft()).getValue();

                if(visit(ast.getRight()).getValue() instanceof String ) {
                    String right = (String) visit(ast.getRight()).getValue();
                    return Environment.create(left.concat(right)); // Returns concatenated strings
                }
                else
                    throw new RuntimeException("String concatenation type mismatch.");
            }
        }

        else if (ast.getOperator().equals("-") || ast.getOperator().equals("*"))
        {
            if(visit(ast.getLeft()).getValue() instanceof BigInteger ) // BigInteger left operand
            {
                BigInteger left = (BigInteger) visit(ast.getLeft()).getValue();

                if(visit(ast.getRight()).getValue() instanceof BigInteger ) {
                    BigInteger right = (BigInteger) visit(ast.getRight()).getValue();
                    if (ast.getOperator().equals("-"))
                    {
                        //System.out.println("Subtracting elements.");
                        return Environment.create(left.subtract(right));
                    }
                    else
                    {
                        //System.out.println("Subtracting elements.");
                        return Environment.create(left.multiply(right));
                    }
                }
                else
                    throw new RuntimeException("BigInteger -/* type mismatch.");
            }

            else if(visit(ast.getLeft()).getValue() instanceof BigDecimal ) // BigDecimal left operand
            {
                BigDecimal left = (BigDecimal) visit(ast.getLeft()).getValue();

                if(visit(ast.getRight()).getValue() instanceof BigDecimal ) {
                    BigDecimal right = (BigDecimal) visit(ast.getRight()).getValue();
                    if (ast.getOperator().equals("-"))
                    {
                        //System.out.println("Subtracting elements.");
                        return Environment.create(left.subtract(right));
                    }
                    else
                    {
                        //System.out.println("Subtracting elements.");
                        return Environment.create(left.multiply(right));
                    }
                }
                else
                    throw new RuntimeException("BigDecimal -/* type mismatch.");
            }

        }
        else if (ast.getOperator().equals("/"))
        {
            if(visit(ast.getLeft()).getValue() instanceof BigInteger ) // If dividing BigInteger values
            {
                BigInteger left = (BigInteger) visit(ast.getLeft()).getValue();

                if(visit(ast.getRight()).getValue() instanceof BigInteger ) {
                    BigInteger right = (BigInteger) visit(ast.getRight()).getValue();
                    if (right.equals(0))
                    {
                        throw new RuntimeException("Can't divide by 0.");
                    }
                    return Environment.create(left.divide(right));
                }
                else
                    throw new RuntimeException("BigInteger division type mismatch.");
            }

            else if(visit(ast.getLeft()).getValue() instanceof BigDecimal ) // If dividing BigDecimal values
            {
                BigDecimal left = (BigDecimal) visit(ast.getLeft()).getValue();

                if(visit(ast.getRight()).getValue() instanceof BigDecimal ) {
                    BigDecimal right = (BigDecimal) visit(ast.getRight()).getValue();
                    if (right.equals(0))
                    {
                        throw new RuntimeException("Can't divide by 0.");
                    }
                    BigDecimal rounded = left.divide(right, 1, RoundingMode.HALF_EVEN);
                    return Environment.create(rounded);
                }
                else
                    throw new RuntimeException("BigDecimal division type mismatch.");
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) // If there is a receiver...
            return visit(ast.getReceiver().get()).getField(ast.getName()).getValue();

        else // If it is a variable, return variable name
            return scope.lookupVariable(ast.getName()).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {

        // turn list of Expr into list of PlcObj to use in callMethod and lookupFunc
        List<Environment.PlcObject> temp = new ArrayList<>();
        for(int i = 0; i < ast.getArguments().size(); i++)
            temp.add(visit(ast.getArguments().get(i)));

        if (ast.getReceiver().isPresent())
            return visit(ast.getReceiver().get()).callMethod(ast.getName(), temp);
        else
            return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(temp);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
