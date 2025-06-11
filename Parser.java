package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {

        List<Ast.Field> fields= new ArrayList<Ast.Field>();
        List<Ast.Method> methods= new ArrayList<Ast.Method>();

        while (match("LET")){
            fields.add(parseField());
        }
        while (match ("DEF")) {
            methods.add(parseMethod());
        }
        //else throw new ParseException("Invalid token", tokens.get(-1).getIndex());
        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {

        if (!match(Token.Type.IDENTIFIER)) // Check for LET, advance token
        {
            throw new ParseException("Expected Identifier.", tokens.get(-1).getIndex());
        }

        String name = tokens.get(-1).getLiteral(); // Set name (index 1)

        String type = "";

        if (match(":")) // If semicolon is matched, get type
        {
            type = tokens.get(0).getLiteral();
            //System.out.println("Type: " + type);
            tokens.advance(); // Advance to index 4
        }

        Optional<Ast.Expr> value = Optional.empty();

        if (match("="))
        {
            value = Optional.of(parseExpression());
        }

        if (!match(";"))
        {
            throw new ParseException("Expected semicolon.", tokens.get(-1).getIndex());
        }

        return new Ast.Field(name, type, value);

    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        //throw new UnsupportedOperationException(); //TODO
        //DEF name(): Type DO stmt; END

        // 3 values to build object Method as in Ast class
        String name = "";
        List<String> parameters = new ArrayList<String>();
        Optional<String> typeName = Optional.empty();
        List<String> parameterTypeNames = new ArrayList<String>();
        List<Ast.Stmt> statements = new ArrayList<Ast.Stmt>();

        // 1. check type, get the method's name
        if (!match(Token.Type.IDENTIFIER)) throw new ParseException("Expected Identifier.", tokens.get(-1).getIndex()); // match id type, throw exception if not
        name = tokens.get(-1).getLiteral();

        // 2. check (), loop to get parameter
        if( !match ("(")) throw new ParseException("Expected parenthesis.", tokens.get(-1).getIndex());// match (, loop through list of ids

        while (!match(")")){ // escape loop when detect )

            // 2a.Check type, Get parameter as a string
            if (tokens.get(-1).getType()!=Token.Type.IDENTIFIER) throw new ParseException("Expected Identifier.", tokens.get(-1).getIndex());
            parameters.add(tokens.get(-1).getLiteral());

            if (!peek(")")) {

                if (!match(",")) // Looks for comma next
                    throw new ParseException("Expected comma.", tokens.get(-1).getIndex());
                // check trailing comma
                if (match(")"))
                    throw new ParseException("Trailing comma, expected expression", tokens.get(-1).getIndex());

            }
        }
        // 3. Check for ':', then get type if found
        if (peek(":"))
        {
            match(":");
            typeName = Optional.of(tokens.get(0).getLiteral());
            tokens.advance(); // Advance to next token
        }

        // 4. check keyword DO
        if (!match("DO")) throw new ParseException("Expected keyword DO", tokens.get(-1).getIndex());

        // 5. Statement list (0 or more)
        while (!match("END")) // Loop until 'END' is reached
            statements.add(parseStatement()); // add statement to "then" statement list

        return new Ast.Method(name, parameters, parameterTypeNames, typeName, statements);
    }


    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException
    {
        if (peek("LET"))
        {
            // 'LET' identifier ('=' expression)? ';'
            return parseDeclarationStatement();
        }
        else if (peek("IF"))
        {
            return parseIfStatement();
        }
        else if (peek("FOR"))
        {
            return parseForStatement();
        }
        else if (peek("WHILE"))
        {
            return parseWhileStatement();
        }
        else if (peek("RETURN"))
        {
            return parseReturnStatement();
        }
        else
        {
            Ast.Expr expr = parseExpression(); // Parse first expression

            if (match("=")) // if there is an = sign after first expression
            {
                // if no second expression before ;, throw exception
                if (peek(";"))
                    throw new ParseException("Expected expression after = operator.", tokens.get(-1).getIndex());
                else
                {
                    return new Ast.Stmt.Assignment(expr, parseExpression()); // returns assignment statement
                }
            }
            else if (match("(")) {

                if  (!peek(")"))
                    throw new ParseException("Expected closing parenthesis.", tokens.get(-1).getIndex());
                match(")");
                if (!peek(";")) {
                    throw new ParseException("Expected semicolon.", tokens.get(-1).getIndex());
                }
            }
            if (!match(";"))
            {
                throw new ParseException("Expected semicolon.", tokens.get(-1).getIndex());
            }

            return new Ast.Stmt.Expression(expr); // returns single expression
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {

        match("LET");

        if (!match(Token.Type.IDENTIFIER))
        {
            throw new ParseException("Expected Identifier.", tokens.get(-1).getIndex());
        }

        String name = tokens.get(-1).getLiteral();
        Optional<String> type = Optional.empty();

        Optional<Ast.Expr> value = Optional.empty();

        if (peek("="))
        {
            match("=");
            value = Optional.of(parseExpression());
        }
        else if (peek(":"))
        {
            match(":");
            tokens.index++;
            type = Optional.of(tokens.get(-1).getLiteral());
            //System.out.println("Type: " + type);
        }

        if (!peek(";"))
        {
            throw new ParseException("Expected semicolon.", tokens.get(-1).getIndex());
        }
        match(";");

        return new Ast.Stmt.Declaration(name, type, value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {

        List<Ast.Stmt> thenStatements = new ArrayList<Ast.Stmt>(); // Creates list of "then" statements
        List<Ast.Stmt> elseStatements = new ArrayList<Ast.Stmt>(); // Creates list of "else" statements

        match("IF"); // Consumes 'IF' token

        Ast.Expr expr = parseExpression();
        if (!peek("DO"))
            throw new ParseException("Expected 'DO' after expression.", tokens.get(-1).getIndex());
        else
        {
            match("DO"); // Consumes 'DO' token

            while (!peek("END") && (!peek("ELSE"))) // if 'END' and 'ELSE' don't come after 'DO' token
            {
                Ast.Stmt stmt = parseStatement(); // get statement
                thenStatements.add(stmt); // add statement to "then" statement list
            }

            while (match("ELSE")) // Loops while there are ELSE statements available
            {

                Ast.Stmt stmt2 = parseStatement();
                elseStatements.add(stmt2);
            }
        }

        return new Ast.Stmt.If(expr, thenStatements, elseStatements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        List<Ast.Stmt> statements = new ArrayList<Ast.Stmt>();

        match("FOR");

        if (!match(Token.Type.IDENTIFIER))
        {
            throw new ParseException("Expected Identifier.", tokens.get(-1).getIndex());
        }

        String name = tokens.get(-1).getLiteral();

        if (!peek("IN"))
            throw new ParseException("Expected 'IN' after identifier.", tokens.get(-1).getIndex());

        match("IN"); // Consumes 'IN' token
        Ast.Expr expr = parseExpression();

        if (!peek("DO"))
            throw new ParseException("Expected 'DO' after expression.", tokens.get(-1).getIndex());
        else {
            match("DO"); // Consumes 'DO' token

            while (!match("END")) // Loop until 'END' is reached
            {
                Ast.Stmt stmt = parseStatement(); // get statement
                statements.add(stmt); // add statement to "then" statement list
            }
        }

        return new Ast.Stmt.For(name, expr, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {

        List<Ast.Stmt> statements = new ArrayList<Ast.Stmt>();

        match("WHILE");
        Ast.Expr expr = parseExpression();

        if (!peek("DO"))
            throw new ParseException("Expected 'DO' after expression.", tokens.get(-1).getIndex());
        else {
            match("DO"); // Consumes 'DO' token

            while (!match("END")) // Loop until 'END' is reached
            {
                Ast.Stmt stmt = parseStatement(); // get statement
                statements.add(stmt); // add statement to "then" statement list
                if (peek(";"))
                {
                    match(";");
                }
            }
        }

        return new Ast.Stmt.While(expr, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        match("RETURN"); // Consumes 'RETURN' token
        Ast.Expr expr = parseExpression(); // Parses expression

        if (!peek(";"))
            throw new ParseException("Expected semicolon after expression.", tokens.get(-1).getIndex());
        else
            match(";"); // Consumes ';' token

        return new Ast.Stmt.Return(expr);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {

        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        Ast.Expr expr = parseEqualityExpression();

        while (match("AND") || match("OR"))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr rightOperand = parseEqualityExpression();

            expr = new Ast.Expr.Binary(operator, expr, rightOperand);
        }

        return expr;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        //throw new UnsupportedOperationException(); //TODO
        Ast.Expr expr = parseAdditiveExpression();

        while (match("<") || match("<=") || match(">") || match(">=")
                || match("==") || match("!="))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr rightOperand = parseAdditiveExpression();

            expr = new Ast.Expr.Binary(operator, expr, rightOperand);
        }

        return expr;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        //throw new UnsupportedOperationException(); //TODO
        // Builds and returns an Ast.Expr
        // Begins by parsing left operand

        // While continuing to match on + or -,
        // Store the operator
        // Evaluate the right operand (another multiplicative-expression)
        // Build the binary expression from the operator, left operand, and right operand
        // expression = new Ast.Expr.Binary(operator, expression, right)

        // Returns expression after the loop completes
        Ast.Expr expr = parseMultiplicativeExpression();

        while (match("+") || match("-"))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr rightOperand = parseMultiplicativeExpression();

            expr = new Ast.Expr.Binary(operator, expr, rightOperand);
        }

        return expr;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {

        Ast.Expr expr = parseSecondaryExpression();

        while (match("*") || match("/"))
        {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr rightOperand = parseSecondaryExpression();

            expr = new Ast.Expr.Binary(operator, expr, rightOperand);
        }

        return expr;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() throws ParseException {

        Ast.Expr secondary = parsePrimaryExpression(); // Gets first primary expression
        while (match(".")) // loops if "." is found
        {
            String identifier = tokens.get(0).getLiteral(); // gets identifier after "."
            boolean bool = identifier.matches("[0-9].*");
            // If first character of field is an integer, throw a ParseException
            if (bool)
            {
                throw new ParseException("Field can't start with a digit.", tokens.get(-1).getIndex());
            }

            match(Token.Type.IDENTIFIER);
            List<Ast.Expr> expressions = new ArrayList<Ast.Expr>(); // Creates list of expressions
            if (match("(")) // if opening parenthesis is found
            {
                while (!match(")")) // While closing parenthesis is not found
                {
                    Ast.Expr expr = parseExpression(); // Parse expression
                    expressions.add(expr); // Then add expression to list
                    if (!peek(")")) // If next char is not closing paranthesis
                    {
                        if(!match(",")) // Looks for comma next
                            throw new ParseException("Expected comma.", tokens.get(-1).getIndex());
                        else
                        {
                            if(match(")")) // Looks for final closing parenthesis
                                throw new ParseException("Trailing comma, expected expression", tokens.get(-1).getIndex());
                        }
                    }
                }

                secondary = new Ast.Expr.Function(Optional.of(secondary), identifier, expressions); // Function call
            }

            else
                secondary = new Ast.Expr.Access(Optional.of(secondary), identifier); // Field call
        }

        return secondary; // Returns field or function
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        if (match("TRUE")) {
            return new Ast.Expr.Literal(true);
        } else if (match("FALSE")) {
            return new Ast.Expr.Literal(false);
        } else if (match("NIL")) {
            return new Ast.Expr.Literal(null);
        } else if (match(Token.Type.INTEGER)) {
            //BigInteger temp = BigInteger.valueOf(Integer.parseInt(tokens.get(-1).getLiteral()));
            //return new Ast.Expr.Literal(temp);
            return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.DECIMAL)) {
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        }
        else if (match(Token.Type.STRING))
        {
            String temp = tokens.get(-1).getLiteral();
            String newString = temp.substring(1, temp.length() - 1);
            newString = newString.replaceAll("\\\\n","\\\n");
            newString = newString.replaceAll("\\\\b","\\\b");
            newString = newString.replaceAll("\\\\r","\\\r");
            newString = newString.replaceAll("\\\\t","\\\t");
            newString = newString.replaceAll("\\\\'", "\\\'");
            newString = newString.replaceAll("\\\\\"", "\\\"");
            newString = newString.replaceAll("\\\\", "\\");
            return new Ast.Expr.Literal(newString);
        } else if (match(Token.Type.CHARACTER))
        {
            String temp = tokens.get(-1).getLiteral();
            String newChar = temp.substring(1, temp.length() - 1);
            newChar = newChar.replaceAll("\\\\n","\\\n");
            newChar = newChar.replaceAll("\\\\b","\\\b");
            newChar = newChar.replaceAll("\\\\r","\\\r");
            newChar = newChar.replaceAll("\\\\t","\\\t");
            newChar = newChar.replaceAll("\\\\'", "\\\'");
            newChar = newChar.replaceAll("\\\\\"", "\\\"");
            newChar = newChar.replaceAll("\\\\", "\\");
            return new Ast.Expr.Literal(newChar.charAt(0));
        }
        else if (match(Token.Type.IDENTIFIER))
        {
            String name = tokens.get(-1).getLiteral();
            List<Ast.Expr> expressions = new ArrayList<Ast.Expr>(); // Creates list of expressions
            if (match("(")) // if opening parenthesis is found return function case
            {
                if (peek(")"))
                {
                    tokens.index--;
                    return new Ast.Expr.Function(Optional.empty(), name, expressions);
                }

                while (!match(")")) // While closing parenthesis is not found
                {
                    Ast.Expr expr = parseExpression(); // Parse expression
                    expressions.add(expr); // Then add expression to list
                    if (!peek(")")) // If next char is not closing paranthesis
                    {
                        if (!match(",")) // Looks for comma next
                            throw new ParseException("Expected comma.", tokens.get(-1).getIndex());
                        else {
                            if (match(")")) // Looks for final closing parenthesis
                                throw new ParseException("Trailing comma, expected expression", tokens.get(-1).getIndex());
                        }
                    }
                }

                return new Ast.Expr.Function(Optional.empty(), name, expressions); // Function call
            }

            return new Ast.Expr.Access(Optional.empty(), name);
        }

        else if (match("(")) // "(expression) case
        {
            Ast.Expr expr = parseExpression();
            if (!match(")"))
            {
                throw new ParseException("Expected closing parenthesis.", tokens.get(0).getIndex());
            }
            return new Ast.Expr.Group(expr);
        }


        else
        {
            throw new ParseException("Invalid primary expression.", tokens.get(-1).getIndex());

        }

        // obj.method() <-- this is referred to as the receiver in the above first function argument
        // And is replaced with Optional.empty(), which is an object but prevents a null pointer exception
    }


    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++)
        {
            if (!tokens.has(i))
            {
                return false;
            }
            else if (patterns[i] instanceof Token.Type)
            {
                if (patterns[i] != tokens.get(i).getType())
                {
                    return false;
                }
            }
            else if (patterns[i] instanceof String)
            {
                if (!patterns[i].equals(tokens.get(i).getLiteral()))
                {
                    return false;
                }
            }
            else
            {
                throw new AssertionError("Invalid pattern object: " +
                        patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        //throw new UnsupportedOperationException(); //TODO (in lecture)

        boolean peek = peek(patterns);

        if (peek)
        {
            for (int i = 0; i < patterns.length; i++)
            {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
