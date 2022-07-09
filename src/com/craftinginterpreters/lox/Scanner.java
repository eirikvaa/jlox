package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    // The index of the first character of the lexeme being scanned.
    private int start = 0;
    // The index of the current character of the lexeme being scanned.
    private int current = 0;
    // Tracks which line the current character is on.
    private int line = 1;

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
        keywords.put("break", BREAK);
    }

    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();

        switch (c) {
            case '(':
                addToken(LEFT_PAREN);
                break;
            case ')':
                addToken(RIGHT_PAREN);
                break;
            case '{':
                addToken(LEFT_BRACE);
                break;
            case '}':
                addToken(RIGHT_BRACE);
                break;
            case ',':
                addToken(COMMA);
                break;
            case '.':
                addToken(DOT);
                break;
            case '-':
                addToken(MINUS);
                break;
            case '+':
                addToken(PLUS);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '*':
                addToken(STAR);
                break;
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;
            case '\n':
                line++;
                break;
            case '"':
                string();
                break;
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    // There is a reason why we don't match on something like 'o' => OR and instead
                    // match identifiers in the default block. The principle is called maximal munch
                    // and means that when there are two lexical grammar rules that both match, we
                    // want the grammar rule that matches the most characters to win. So, when matching
                    // between 'orchid' and 'OR', we want the former to win since it's longer.
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }

    private void number() {
        // Make sure that we peek at the next character and if we see
        // a number, only then should we advance.
        while (isDigit(peek())) advance();

        // Look for a fractional part
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (isDigit(peek())) advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    // A consequence of the implementation below is that Lox supports
    // multi-line strings.
    private void string() {
        // Keep going until we 1) find an ending quotation mark or 2) we hit the
        // end of the file.
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        // This means we didn't find an ending quotation mark, so we hit the
        // end of the file.
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        advance();

        // Increase start by one to drop the starting quotation mark
        // and decrease currenet by one to drop the ending quotation mark.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    // peek() represents a technique called 'lookahead'. It only looks one character ahead,
    // so we say we have 'one character of lookahead'.
    // It's sort of like advance, but we're not consuming any characters.
    private char peek() {
        // '\0' is a null terminated string.
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    // Like peek(), but checks the character after the next character. So 'two characters of lookahead'.
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // match is like a conditional advance; we only consume the current
    // character if it's what we're looking for.
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    // The last valid index is one less than the length of the source string,
    // so hitting that or anyone beyond it is an invalid index, hence we're at
    // the end.
    private boolean isAtEnd() {
        return current >= source.length();
    }

    // Consume the next character in the source code and return it.
    // This is for input.
    private char advance() {
        current++;

        // Here we can possibly find a character that we won't recognize,
        // but it's important that we consume it. Also, if we do find an error,
        // we don't stop because we want to find as many errors as possible in one go.
        // It'll obviously never get executed.
        return source.charAt(current - 1);
    }

    // Tokenize the current lexeme.
    // This is for output.
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    // Tokenize the current lexeme.
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}
