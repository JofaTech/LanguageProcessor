package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        // Create a "class Main {"
        print("public class Main {");
        newline(0);
        newline(++indent);

        // declare fields
        if(!ast.getFields().isEmpty())
        {
            for (int i = 0; i < ast.getFields().size(); ++i)
            {
                print(ast.getFields().get(i));
            }
            newline(0);
        }

        // declare "public static void main(String[] args) {
        //          System.exit(new Main().main());
        //          }"
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");
        newline(0);
        newline(indent);
        // declare each of our methods
        // one of our methods is called main() !
        if(!ast.getMethods().isEmpty())
        {
            for (int i = 0; i < ast.getMethods().size(); ++i)
            {
                print(ast.getMethods().get(i));
                newline(0);
            }
        }

        newline(0);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {

        print(ast.getVariable().getType().getJvmName(), " ",
                ast.getVariable().getJvmName());

        if (ast.getValue().isPresent())
        {
            print(" = ", ast.getVariable().getValue());
        }
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getName(), "(");

        if (!ast.getParameters().isEmpty())
        {
            for (int i = 0; i < ast.getParameters().size() - 1; i++)
            {
                print(ast.getParameterTypeNames().get(i), " ", ast.getParameters().get(i), ", ");
            }
            print(ast.getParameterTypeNames().get(ast.getParameterTypeNames().size() - 1), " ",
                    ast.getParameters().get(ast.getParameters().size() - 1));
        }

        print(") {");

        if (!ast.getStatements().isEmpty())
        {
            newline(++indent);

            for (int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }

                print(ast.getStatements().get(i));
            }


        }
        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        print(ast.getExpression(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        // write: TYPE variable_name
        print(ast.getVariable().getType().getJvmName(), " ",
                ast.getVariable().getJvmName());

        // is there an assigned value?
        // if so, write: = and the value
        if (ast.getValue().isPresent())
        {
            print(" = ", ast.getValue().get());
        }

        // write: ;
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        print("if (", ast.getCondition(), ") {");

        if (!ast.getThenStatements().isEmpty())
        {
            newline(++indent);

            for (int i = 0; i < ast.getThenStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }

                print(ast.getThenStatements().get(i));
            }

            newline(--indent);
            print("}");
        }

        if (!ast.getElseStatements().isEmpty())
        {
            print(" else {");
            newline(++indent);

            for (int i = 0; i < ast.getElseStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }

                print(ast.getElseStatements().get(i));
            }

            newline(--indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        print("for (int ", ast.getName(), " : ", ast.getValue(), ") {");

        if (!ast.getStatements().isEmpty())
        {
            newline(++indent);

            for (int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }

                print(ast.getStatements().get(i));
            }

            newline(--indent);
        }
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        // Print the while structure, including condition
        print("while (", ast.getCondition(), ") {");

        // Determine if there are statements to process
        if (!ast.getStatements().isEmpty())
        {
            // setup the next line
            newline(++indent);
            // handle all statements in the while statement body
            for (int i = 0; i < ast.getStatements().size(); i++) {
                // check if newline and indent are needed
                if (i != 0) {
                    // setup the next line
                    newline(indent);
                }
                // print the next statement
                print(ast.getStatements().get(i));
            }
            // setup the next line
            newline(--indent);
        }

        // close the while loop
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        // write: return (expression value);
        print("return ", ast.getValue(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if (ast.getType().equals(Environment.Type.STRING) || ast.getType().equals(Environment.Type.CHARACTER))
        {
            print("\"", ast.getLiteral(), "\"");
        }
        else if (ast.getType().equals(Environment.Type.NIL))
        {
            print("null");
        }
        else
        {
            print(ast.getLiteral());
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(", ast.getExpression(), ")");

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        if (ast.getOperator().equals("AND")) {
            print(ast.getLeft(), " && ", ast.getRight());
        }
        else if (ast.getOperator().equals("OR")) {
            print(ast.getLeft(), " || ", ast.getRight());

        }
        else {
            print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent())
        {
            print(ast.getReceiver().get(), ".");
        }

        print(ast.getVariable().getJvmName());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if (ast.getReceiver().isPresent())
        {
            print(ast.getReceiver().get(), ".");
        }

        print(ast.getFunction().getJvmName(), "(");

        if (!ast.getArguments().isEmpty())
        {
            for (int i = 0; i < ast.getArguments().size() - 1; i++)
            {
                print(ast.getArguments().get(i), ", ");
            }
            print(ast.getArguments().get(ast.getArguments().size() - 1));
        }

        print(")");

        return null;
    }

}
